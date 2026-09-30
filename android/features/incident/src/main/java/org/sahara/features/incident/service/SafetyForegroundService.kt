package org.sahara.features.incident.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.os.Binder
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import org.sahara.core.domain.models.IncidentState
import org.sahara.features.incident.statemachine.IncidentStateMachine
import org.sahara.services.detection.detectors.MotionDetector
import org.sahara.services.detection.detectors.ScreamDetector
import org.sahara.services.detection.fusion.SignalFusionEngine
import org.sahara.services.detection.models.DetectionConfig
import org.sahara.services.evidence.engine.EvidenceCaptureEngine
import org.sahara.services.evidence.preroll.AudioChunk
import org.sahara.services.evidence.preroll.BoundedAudioPreRollBuffer

import org.sahara.core.data.db.SaharaDatabase
import org.sahara.core.data.repository.AuditRepositoryImpl
import org.sahara.core.data.repository.ContactRepositoryImpl
import org.sahara.core.data.repository.IncidentRepositoryImpl
import org.sahara.features.notifycircle.manager.NotifyCircleManager
import org.sahara.services.mesh.fallback.EscalationFallbackManager
import org.sahara.services.mesh.relay.NearbyConnectionsMeshRelay
import org.sahara.services.mesh.relay.SheGuardMeshAdapter

class SafetyForegroundService : Service(), SensorEventListener {

    private val binder = LocalBinder()
    var stateMachine: IncidentStateMachine? = null
    var notifyCircleManager: NotifyCircleManager? = null
    var sheGuardMeshAdapter: SheGuardMeshAdapter? = null

    // Real audio & sensor detection infrastructure
    private val detectionConfig = DetectionConfig()
    val screamDetector = ScreamDetector(detectionConfig)
    val motionDetector = MotionDetector(detectionConfig)
    val fusionEngine = SignalFusionEngine(detectionConfig)
    val preRollBuffer = BoundedAudioPreRollBuffer()

    var evidenceCaptureEngine: EvidenceCaptureEngine? = null
        set(value) {
            field = value
            if (value != null) {
                // Synchronize any pre-existing audio chunks into capture engine buffer
                for (chunk in preRollBuffer.getBufferedChunks()) {
                    value.preRollBuffer.offerChunk(chunk)
                }
            }
        }

    private var audioRecord: AudioRecord? = null
    private var isRecordingAudio = false
    private var audioRecordingThread: Thread? = null
    @Volatile private var latestAudioBuffer: ShortArray? = null

    private var sensorManager: SensorManager? = null
    private var accelerometer: Sensor? = null

    private val serviceScope = CoroutineScope(Dispatchers.Default + Job())

    inner class LocalBinder : Binder() {
        fun getService(): SafetyForegroundService = this@SafetyForegroundService
    }

    override fun onBind(intent: Intent?): IBinder = binder

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()

        // Initialize TFLite Scream Classifier from application assets
        try {
            val classifier = org.sahara.services.detection.tflite.TFLiteScreamClassifier(applicationContext)
            screamDetector.tfliteClassifier = classifier
            android.util.Log.d("SaharaDetection", "TFLite Scream Classifier initialized. Loaded=${classifier.isModelLoaded}, Version=${classifier.modelVersion}")
        } catch (e: Throwable) {
            android.util.Log.w("SaharaDetection", "Failed to load TFLite Scream Classifier, falling back to hybrid DSP mode: ${e.message}")
        }

        sensorManager = getSystemService(Context.SENSOR_SERVICE) as SensorManager
        accelerometer = sensorManager?.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
        accelerometer?.let {
            sensorManager?.registerListener(this, it, SensorManager.SENSOR_DELAY_GAME)
        }

        startAudioRecording()

        // Launch detection signal collection & fusion processing
        serviceScope.launch {
            screamDetector.detectionFlow.collect { signal ->
                fusionEngine.onSignalReceived(signal)
                org.sahara.services.detection.log.DetectionLogManager.logEvent(signal, latestAudioBuffer)
            }
        }
        serviceScope.launch {
            motionDetector.detectionFlow.collect { signal ->
                fusionEngine.onSignalReceived(signal)
                org.sahara.services.detection.log.DetectionLogManager.logEvent(signal)
            }
        }

        // Ensure state machine is initialized for standalone background execution
        getOrCreateStateMachine()

        // Collect fusion engine decisions and update state machine
        serviceScope.launch {
            fusionEngine.decisionFlow.collect { decision ->
                val activeSm = getOrCreateStateMachine()
                android.util.Log.d("SaharaDetection", "Fusion decision emitted: $decision")
                when (decision) {
                    is org.sahara.services.detection.fusion.FusionDecision.EnterPossibleDistress -> {
                        activeSm.onSuspiciousSignalDetected(decision.primarySignal.detectorType.name)
                        activeSm.transitionToCandidate()
                        updateNotificationForState(IncidentState.CANDIDATE_INCIDENT)
                    }
                    is org.sahara.services.detection.fusion.FusionDecision.ConfirmIncident -> {
                        activeSm.activateIncident(decision.activeSignals.joinToString { it.detectorType.name })
                        updateNotificationForState(IncidentState.ACTIVE_INCIDENT)
                    }
                    is org.sahara.services.detection.fusion.FusionDecision.CandidateExpired -> {
                        if (activeSm.currentState.value != IncidentState.ACTIVE_INCIDENT && activeSm.currentState.value != IncidentState.SEALED) {
                            activeSm.cancelIncident()
                            updateNotificationForState(IncidentState.MONITORING)
                        }
                    }
                }
            }
        }

        // Periodic timeout loop to check confirmation window expirations
        serviceScope.launch {
            while (true) {
                kotlinx.coroutines.delay(1000)
                fusionEngine.checkConfirmationTimeout(System.currentTimeMillis())
            }
        }
    }

    fun getOrCreateNotifyCircleManager(): NotifyCircleManager {
        if (notifyCircleManager == null) {
            val db = SaharaDatabase.getDatabase(applicationContext)
            val contactRepo = ContactRepositoryImpl(db.notifyContactDao())
            val auditRepo = AuditRepositoryImpl(db.auditEventDao())
            val meshRelay = sheGuardMeshAdapter?.meshRelay ?: NearbyConnectionsMeshRelay()
            val smsProvider = EscalationFallbackManager.createSmsProvider(isDebug = true)
            val fallbackManager = EscalationFallbackManager(
                meshRelay = meshRelay,
                smsProvider = smsProvider,
                isDebug = true,
                meshAdapter = sheGuardMeshAdapter
            )
            notifyCircleManager = NotifyCircleManager(contactRepo, auditRepo, fallbackManager)
        }
        return notifyCircleManager!!
    }

    fun getOrCreateStateMachine(): IncidentStateMachine {
        if (stateMachine == null) {
            val db = SaharaDatabase.getDatabase(applicationContext)
            val incRepo = IncidentRepositoryImpl(db.incidentDao())
            val auditRepo = AuditRepositoryImpl(db.auditEventDao())
            stateMachine = IncidentStateMachine(incRepo, auditRepo)
        }
        val sm = stateMachine!!
        sm.onStateChanged = { newState ->
            fusionEngine.updateCurrentState(newState)
        }
        if (sm.onIncidentActivated == null) {
            sm.onIncidentActivated = { incident ->
                try {
                    evidenceCaptureEngine?.processBufferedPreRoll(incident.incidentId)
                } catch (e: Throwable) {
                    android.util.Log.e("Sahara", "Pre-roll capture error in service: ${e.message}")
                }
                try {
                    val refCode = "SAHARA-${incident.incidentId.toString().take(6).uppercase()}"
                    val manager = getOrCreateNotifyCircleManager()
                    serviceScope.launch {
                        manager.dispatchAlert(
                            incidentId = incident.incidentId,
                            locationText = "Background Distress Location",
                            locationAgeSeconds = 0,
                            evidenceHash = incident.finalMerkleRoot ?: "ACTIVE_${incident.incidentId.toString().take(8)}",
                            referenceCode = refCode
                        )
                    }
                } catch (e: Throwable) {
                    android.util.Log.e("Sahara", "Notification dispatch error in service: ${e.message}")
                }
            }
        }
        return sm
    }

    private fun startAudioRecording() {
        if (isRecordingAudio) return

        val sampleRate = 16000
        val channelConfig = AudioFormat.CHANNEL_IN_MONO
        val audioFormat = AudioFormat.ENCODING_PCM_16BIT
        val minBufferSize = AudioRecord.getMinBufferSize(sampleRate, channelConfig, audioFormat)
        val bufferSize = Math.max(minBufferSize, 3200)

        try {
            audioRecord = AudioRecord(
                MediaRecorder.AudioSource.MIC,
                sampleRate,
                channelConfig,
                audioFormat,
                bufferSize
            )

            if (audioRecord?.state == AudioRecord.STATE_INITIALIZED) {
                audioRecord?.startRecording()
                isRecordingAudio = true

                audioRecordingThread = Thread {
                    val buffer = ShortArray(1600) // 100ms at 16kHz
                    var chunkIndex = 0
                    while (isRecordingAudio) {
                        val readSize = audioRecord?.read(buffer, 0, buffer.size) ?: 0
                        if (readSize > 0) {
                            val chunk = AudioChunk("chunk_${System.currentTimeMillis()}", buffer.clone())

                            // Always populate service preRollBuffer and evidenceCaptureEngine preRollBuffer
                            preRollBuffer.offerChunk(chunk)
                            evidenceCaptureEngine?.preRollBuffer?.offerChunk(chunk)

                            latestAudioBuffer = buffer.clone()
                            val screamConf = screamDetector.processAudioChunk(buffer, sampleRate)

                            if (chunkIndex % 50 == 0) { // Log diagnostic summary every ~5 seconds
                                android.util.Log.d("SaharaDetection", "Audio chunk #$chunkIndex processed. scream_conf=%.2f (mode=${screamDetector.modeStatus})".format(screamConf))
                            }

                            // If active incident, save real encrypted chunk
                            val activeSm = getOrCreateStateMachine()
                            activeSm.currentIncident.value?.let { incident ->
                                if (incident.state == IncidentState.ACTIVE_INCIDENT && evidenceCaptureEngine != null) {
                                    serviceScope.launch {
                                        try {
                                            evidenceCaptureEngine?.capturePreRollAndAudioChunk(
                                                incident.incidentId, chunk, chunkIndex++
                                            )
                                        } catch (e: Throwable) {
                                            // Handle storage/capture error
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
                audioRecordingThread?.start()
            } else {
                android.util.Log.w("SaharaDetection", "AudioRecord failed to initialize (State != INITIALIZED). Permissions or mic unavailable.")
            }
        } catch (e: SecurityException) {
            android.util.Log.e("SaharaDetection", "SecurityException on AudioRecord: RECORD_AUDIO permission missing or revoked.")
        } catch (e: Throwable) {
            android.util.Log.e("SaharaDetection", "Error initializing AudioRecord: ${e.message}")
        }
    }

    override fun onSensorChanged(event: SensorEvent?) {
        if (event?.sensor?.type == Sensor.TYPE_ACCELEROMETER) {
            val x = event.values[0]
            val y = event.values[1]
            val z = event.values[2]
            motionDetector.processSensorData(x, y, z)
        }
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val notification = createNotification("Sahara Safety Monitoring Active", "Listening for screams or impacts...")
        startForeground(NOTIFICATION_ID, notification)
        return START_STICKY
    }

    fun updateNotificationForState(state: IncidentState) {
        val title: String
        val content: String

        when (state) {
            IncidentState.MONITORING -> {
                title = "Sahara Monitoring Active"
                content = "Listening for screams or impacts..."
            }
            IncidentState.CANDIDATE_INCIDENT, IncidentState.PENDING_CONFIRMATION -> {
                title = "Possible Distress Detected"
                content = "Evaluating confirmation rules..."
            }
            IncidentState.ACTIVE_INCIDENT -> {
                title = "EMERGENCY: Incident Active"
                content = "Evidence protection and alert dispatch in progress."
            }
            IncidentState.CANCELLED -> {
                title = "Incident Cancelled"
                content = "Captured evidence is safely preserved locally."
            }
            IncidentState.SEALED -> {
                title = "Incident Sealed"
                content = "Evidence package sealed with cryptographic integrity."
            }
            else -> {
                title = "Sahara Safety Companion"
                content = "Status: $state"
            }
        }

        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.notify(NOTIFICATION_ID, createNotification(title, content))
    }

    private fun createNotification(title: String, content: String): Notification {
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(title)
            .setContentText(content)
            .setSmallIcon(android.R.drawable.ic_dialog_alert)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .build()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Sahara Safety Monitoring",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Persistent notification for Sahara offline background distress detection"
            }
            val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.createNotificationChannel(channel)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        isRecordingAudio = false
        try {
            audioRecord?.stop()
            audioRecord?.release()
        } catch (e: Throwable) {}
        audioRecord = null

        try {
            screamDetector.tfliteClassifier?.close()
        } catch (_: Throwable) {}

        sensorManager?.unregisterListener(this)
    }

    companion object {
        const val CHANNEL_ID = "sahara_safety_channel"
        const val NOTIFICATION_ID = 1001
    }
}
