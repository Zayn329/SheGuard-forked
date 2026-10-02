package org.sahara.app

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.Bundle
import android.os.IBinder
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import androidx.lifecycle.lifecycleScope
import org.sahara.app.export.EvidenceExporter
import org.sahara.app.export.ExportPackage
import org.sahara.app.ui.ActiveIncidentScreen
import org.sahara.app.ui.AnchoringScreen
import org.sahara.app.ui.AuthScreen
import org.sahara.app.ui.DetectionLogScreen
import org.sahara.app.ui.ExportVerifierScreen
import org.sahara.app.ui.HelpDirectoryScreen
import org.sahara.app.ui.HomeDashboardScreen
import org.sahara.app.ui.IncidentSealedScreen
import org.sahara.app.ui.IncidentTimelineScreen
import org.sahara.app.ui.NotifyCircleManagementScreen
import org.sahara.app.ui.NotifyCircleSetupScreen
import org.sahara.app.ui.PermissionsConsentScreen
import org.sahara.app.ui.QuickPreferencesScreen
import org.sahara.app.ui.SafetyWatchScreen
import org.sahara.app.ui.SaharaTheme
import org.sahara.app.ui.TrustedContactAlertScreen
import org.sahara.app.ui.WelcomeScreen
import org.sahara.app.ui.SheGuardReportingScreen
import org.sahara.core.data.db.SaharaDatabase
import org.sahara.core.data.repository.AlertRepositoryImpl
import org.sahara.core.data.repository.AuditRepositoryImpl
import org.sahara.core.data.repository.ContactRepositoryImpl
import org.sahara.core.data.repository.EvidenceRepositoryImpl
import org.sahara.core.data.repository.IncidentRepositoryImpl
import org.sahara.core.data.repository.MicroReportRepositoryImpl
import org.sahara.core.data.repository.PatternRepositoryImpl
import org.sahara.features.notifycircle.manager.NotifyCircleManager
import org.sahara.services.mesh.fallback.EscalationFallbackManager
import org.sahara.services.mesh.relay.NearbyConnectionsMeshRelay
import org.sahara.services.mesh.relay.SheGuardMeshAdapter
import org.sahara.services.mesh.transport.MeshPermissionManager
import org.sahara.services.mesh.transport.NearbyConnectionsTransport
import org.sahara.core.domain.models.Incident
import org.sahara.core.domain.models.IncidentState
import org.sahara.core.security.crypto.AesGcmFileStorage
import org.sahara.core.security.crypto.KeyStorageManagerImpl
import org.sahara.features.incident.service.SafetyForegroundService
import org.sahara.features.incident.statemachine.IncidentStateMachine
import org.sahara.features.panic.controller.PanicController
import org.sahara.services.evidence.engine.EvidenceCaptureEngine
import org.sahara.services.evidence.manifest.EvidenceManifestManager
import org.sahara.services.evidence.manifest.EvidenceVerifier
import org.sahara.services.evidence.preroll.BoundedAudioPreRollBuffer
import java.io.File
import java.util.UUID

enum class Screen {
    WELCOME,
    PERMISSIONS,
    CIRCLE_SETUP,
    PREFERENCES,
    HOME,
    SAFETY_WATCH,
    ACTIVE_INCIDENT,
    INCIDENT_SEALED,
    INCIDENT_TIMELINE,
    TRUSTED_ALERT,
    CIRCLE_MANAGE,
    HELP_DIRECTORY,
    VERIFIER,
    AUTH,
    LEGAL_DRAFTING,
    ANCHORING,
    DETECTION_LOG,
    SHEGUARD_REPORTING
}

class MainActivity : ComponentActivity() {

    private var initialScreenState = mutableStateOf(Screen.HOME)

    private lateinit var database: SaharaDatabase
    private lateinit var incidentRepository: IncidentRepositoryImpl
    private lateinit var evidenceRepository: EvidenceRepositoryImpl
    private lateinit var auditRepository: AuditRepositoryImpl
    private lateinit var microReportRepository: MicroReportRepositoryImpl
    private lateinit var patternRepository: PatternRepositoryImpl
    private lateinit var alertRepository: AlertRepositoryImpl
    private lateinit var contactRepository: ContactRepositoryImpl
    private lateinit var stateMachine: IncidentStateMachine
    private lateinit var panicController: PanicController
    private lateinit var keyManager: KeyStorageManagerImpl
    private lateinit var captureEngine: EvidenceCaptureEngine
    private lateinit var manifestManager: EvidenceManifestManager
    private lateinit var preRollBuffer: BoundedAudioPreRollBuffer
    private lateinit var meshTransport: NearbyConnectionsTransport
    private lateinit var sheGuardMeshAdapter: SheGuardMeshAdapter

    private var foregroundService: SafetyForegroundService? = null
    private var isServiceBound = false

    private val meshPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { grants ->
        if (grants.values.all { it }) {
            startMeshTransport()
        }
    }

    private val serviceConnection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
            val binder = service as SafetyForegroundService.LocalBinder
            foregroundService = binder.getService()
            foregroundService?.stateMachine = stateMachine
            foregroundService?.evidenceCaptureEngine = captureEngine
            isServiceBound = true
        }

        override fun onServiceDisconnected(name: ComponentName?) {
            foregroundService = null
            isServiceBound = false
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        val target = intent.getStringExtra("TARGET_SCREEN")
        if (!target.isNullOrBlank()) {
            try {
                initialScreenState.value = Screen.valueOf(target)
            } catch (_: Exception) {}
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val target = intent?.getStringExtra("TARGET_SCREEN")
        if (!target.isNullOrBlank()) {
            try {
                initialScreenState.value = Screen.valueOf(target)
            } catch (_: Exception) {}
        }

        database = SaharaDatabase.getDatabase(applicationContext)
        incidentRepository = IncidentRepositoryImpl(database.incidentDao())
        evidenceRepository = EvidenceRepositoryImpl(database.evidenceDao())
        auditRepository = AuditRepositoryImpl(database.auditEventDao())
        microReportRepository = MicroReportRepositoryImpl(database.microReportDao())
        patternRepository = PatternRepositoryImpl(database.patternDao())
        alertRepository = AlertRepositoryImpl(database.alertDao())
        contactRepository = ContactRepositoryImpl(database.notifyContactDao())

        stateMachine = IncidentStateMachine(incidentRepository, auditRepository)
        panicController = PanicController(stateMachine)

        stateMachine.onStateChanged = { newState ->
            foregroundService?.fusionEngine?.updateCurrentState(newState)
        }

        keyManager = KeyStorageManagerImpl()
        val gcmStorage = AesGcmFileStorage()
        preRollBuffer = BoundedAudioPreRollBuffer()
        val storageDir = File(filesDir, "evidence")
        storageDir.mkdirs()

        captureEngine = EvidenceCaptureEngine(evidenceRepository, keyManager, gcmStorage, preRollBuffer, storageDir)
        manifestManager = EvidenceManifestManager(incidentRepository, keyManager)

        val meshRelay = NearbyConnectionsMeshRelay()
        meshTransport = NearbyConnectionsTransport(applicationContext)
        sheGuardMeshAdapter = SheGuardMeshAdapter(
            meshRelay = meshRelay,
            alertRepository = alertRepository,
            transport = meshTransport
        )
        lifecycleScope.launch {
            meshTransport.incomingPayloads.collect { bytes ->
                val result = sheGuardMeshAdapter.handleIncomingWirePayload(bytes)
                when (result) {
                    is org.sahara.services.mesh.relay.SheGuardMeshProcessResult.DistressRelayed -> {
                        showIncomingMeshNotification(
                            title = "🚨 EMERGENCY: Nearby Distress Signal",
                            content = "Received emergency distress alert via BLE mesh.",
                            screen = Screen.TRUSTED_ALERT
                        )
                    }
                    is org.sahara.services.mesh.relay.SheGuardMeshProcessResult.AcceptedAndPersisted -> {
                        showIncomingMeshNotification(
                            title = "🛡️ Early Warning Alert Received",
                            content = "Received verified safety pattern: ${result.payload.category}",
                            screen = Screen.SHEGUARD_REPORTING
                        )
                    }
                    else -> { /* Deduplicated or invalid */ }
                }
            }
        }
        lifecycleScope.launch {
            meshTransport.status.collect { status ->
                if (status == org.sahara.services.mesh.transport.MeshTransportStatus.CONNECTED) {
                    sheGuardMeshAdapter.drainOutboundQueue()
                }
            }
        }
        startMeshTransport()

        val smsProvider = EscalationFallbackManager.createSmsProvider(isDebug = true)
        val fallbackManager = EscalationFallbackManager(
            meshRelay = meshRelay,
            smsProvider = smsProvider,
            isDebug = true,
            meshAdapter = sheGuardMeshAdapter
        )
        val notifyCircleManager = NotifyCircleManager(contactRepository, auditRepository, fallbackManager)

        stateMachine.onIncidentActivated = { incident ->
            try {
                captureEngine.processBufferedPreRoll(incident.incidentId)
            } catch (e: Throwable) {
                android.util.Log.e("Sahara", "Pre-roll capture error: ${e.message}")
            }
            try {
                val refCode = "SAHARA-${incident.incidentId.toString().take(6).uppercase()}"
                notifyCircleManager.dispatchAlert(
                    incidentId = incident.incidentId,
                    locationText = "Bandra West, Mumbai",
                    locationAgeSeconds = 0,
                    evidenceHash = incident.finalMerkleRoot ?: "ACTIVE_${incident.incidentId.toString().take(8)}",
                    referenceCode = refCode
                )
            } catch (e: Throwable) {
                android.util.Log.e("Sahara", "Notification dispatch error: ${e.message}")
            }
        }

        // Bind SafetyForegroundService
        val serviceIntent = Intent(this, SafetyForegroundService::class.java)
        startService(serviceIntent)
        bindService(serviceIntent, serviceConnection, Context.BIND_AUTO_CREATE)

        setContent {
            SaharaTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    SaharaAppNavigation()
                }
            }
        }
    }

    @Composable
    fun SaharaAppNavigation() {
        var currentScreen by remember { mutableStateOf(initialScreenState.value) }
        LaunchedEffect(initialScreenState.value) {
            currentScreen = initialScreenState.value
        }
        var isMonitoringActive by remember { mutableStateOf(true) }
        var activeIncidentState by remember { mutableStateOf(IncidentState.IDLE) }
        var recentExportPackage by remember { mutableStateOf<ExportPackage?>(null) }
        var elapsedIncidentSeconds by remember { mutableStateOf(18) }
        var recordedIncidentsCount by remember { mutableStateOf(0) }
        val scope = rememberCoroutineScope()

        LaunchedEffect(Unit) {
            try {
                val recovered = stateMachine.recoverActiveIncident()
                if (recovered != null && recovered.state == IncidentState.ACTIVE_INCIDENT) {
                    activeIncidentState = IncidentState.ACTIVE_INCIDENT
                    currentScreen = Screen.ACTIVE_INCIDENT
                }
            } catch (_: Exception) {}
        }

        LaunchedEffect(currentScreen) {
            try {
                val incidents = incidentRepository.getAllIncidents().first()
                recordedIncidentsCount = incidents.size
            } catch (_: Exception) {}
        }

        val notifyContacts by contactRepository.getContacts().collectAsState(initial = emptyList())

        when (currentScreen) {
            Screen.WELCOME -> {
                WelcomeScreen(
                    onGetStarted = { currentScreen = Screen.PERMISSIONS },
                    onLearnMore = { currentScreen = Screen.PERMISSIONS }
                )
            }
            Screen.PERMISSIONS -> {
                PermissionsConsentScreen(
                    onContinue = { currentScreen = Screen.CIRCLE_SETUP },
                    onBack = { currentScreen = Screen.WELCOME }
                )
            }
            Screen.CIRCLE_SETUP -> {
                NotifyCircleSetupScreen(
                    contacts = notifyContacts,
                    onAddContact = { name, phone ->
                        scope.launch {
                            contactRepository.saveContact(
                                org.sahara.core.domain.models.NotifyContact(
                                    displayName = name,
                                    type = org.sahara.core.domain.models.ContactType.SMS_ONLY,
                                    phoneNumber = phone
                                )
                            )
                        }
                    },
                    onRemoveContact = { contact ->
                        scope.launch {
                            contactRepository.deleteContact(contact.contactId)
                        }
                    },
                    onContinue = { currentScreen = Screen.PREFERENCES },
                    onBack = { currentScreen = Screen.PERMISSIONS }
                )
            }
            Screen.PREFERENCES -> {
                QuickPreferencesScreen(
                    onFinishSetup = { currentScreen = Screen.HOME },
                    onBack = { currentScreen = Screen.CIRCLE_SETUP }
                )
            }
            Screen.HOME -> {
                HomeDashboardScreen(
                    isMonitoringActive = isMonitoringActive,
                    recentIncidentsCount = recordedIncidentsCount,
                    alertRepository = alertRepository,
                    onToggleMonitoring = { enabled ->
                        isMonitoringActive = enabled
                        scope.launch {
                            if (enabled) {
                                stateMachine.startMonitoring()
                                activeIncidentState = IncidentState.MONITORING
                            } else {
                                stateMachine.stopMonitoring()
                                activeIncidentState = IncidentState.IDLE
                            }
                        }
                    },
                    onStartSafetyWatch = {
                        currentScreen = Screen.SAFETY_WATCH
                    },
                    onNeedHelp = {
                        scope.launch {
                            panicController.triggerPanicImmediately("IN_APP_HELP_BUTTON")
                            activeIncidentState = IncidentState.ACTIVE_INCIDENT
                            currentScreen = Screen.ACTIVE_INCIDENT
                        }
                    },
                    onOpenCircle = { currentScreen = Screen.CIRCLE_MANAGE },
                    onOpenRecords = { currentScreen = Screen.INCIDENT_TIMELINE },
                    onOpenSettings = { currentScreen = Screen.PREFERENCES },
                    onOpenDirectory = { currentScreen = Screen.HELP_DIRECTORY },
                    onOpenVerifier = { currentScreen = Screen.VERIFIER },
                    onOpenLegalDraft = { currentScreen = Screen.LEGAL_DRAFTING },
                    onOpenAnchoring = { currentScreen = Screen.ANCHORING },
                    onOpenDetectionLog = { currentScreen = Screen.DETECTION_LOG },
                    onOpenSheGuardReport = { currentScreen = Screen.SHEGUARD_REPORTING }
                )
            }
            Screen.DETECTION_LOG -> {
                DetectionLogScreen(
                    onBack = { currentScreen = Screen.HOME }
                )
            }
            Screen.SHEGUARD_REPORTING -> {
                SheGuardReportingScreen(
                    repository = microReportRepository,
                    patternRepository = patternRepository,
                    alertRepository = alertRepository,
                    meshAdapter = sheGuardMeshAdapter,
                    onBack = { currentScreen = Screen.HOME }
                )
            }
            Screen.SAFETY_WATCH -> {
                SafetyWatchScreen(
                    onImSafe = { currentScreen = Screen.HOME },
                    onNeedHelpNow = {
                        scope.launch {
                            panicController.triggerPanicImmediately("SAFETY_WATCH_HELP_NOW")
                            activeIncidentState = IncidentState.ACTIVE_INCIDENT
                            currentScreen = Screen.ACTIVE_INCIDENT
                        }
                    }
                )
            }
            Screen.ACTIVE_INCIDENT -> {
                ActiveIncidentScreen(
                    elapsedSeconds = elapsedIncidentSeconds,
                    onEndIncident = {
                        scope.launch {
                            val currentInc = stateMachine.currentIncident.value
                            if (currentInc != null) {
                                captureEngine.processBufferedPreRoll(currentInc.incidentId)
                                val entries = evidenceRepository.getEvidenceForIncident(currentInc.incidentId).first()
                                if (entries.isNotEmpty()) {
                                    val manifest = manifestManager.createAndSignManifest(currentInc, entries)
                                    stateMachine.sealIncident(manifest.merkleRoot, manifest.sealedAt)
                                } else {
                                    stateMachine.cancelIncident()
                                }
                            }
                            currentScreen = Screen.INCIDENT_SEALED
                        }
                    }
                )
            }
            Screen.INCIDENT_SEALED -> {
                IncidentSealedScreen(
                    onViewRecord = { currentScreen = Screen.INCIDENT_TIMELINE },
                    onShareCircle = { currentScreen = Screen.TRUSTED_ALERT },
                    onReturnHome = { currentScreen = Screen.HOME }
                )
            }
            Screen.INCIDENT_TIMELINE -> {
                IncidentTimelineScreen(
                    onExportVerified = { currentScreen = Screen.VERIFIER },
                    onBack = { currentScreen = Screen.HOME }
                )
            }
            Screen.TRUSTED_ALERT -> {
                TrustedContactAlertScreen(
                    onCheckIn = { currentScreen = Screen.HOME },
                    onCall = { /* Initiates phone call */ },
                    onGetDirections = { /* Opens map */ },
                    onBack = { currentScreen = Screen.HOME }
                )
            }
            Screen.CIRCLE_MANAGE -> {
                NotifyCircleManagementScreen(
                    contacts = notifyContacts,
                    onAddContact = { name, phone ->
                        scope.launch {
                            contactRepository.saveContact(
                                org.sahara.core.domain.models.NotifyContact(
                                    displayName = name,
                                    type = org.sahara.core.domain.models.ContactType.SMS_ONLY,
                                    phoneNumber = phone
                                )
                            )
                        }
                    },
                    onRemoveContact = { contact ->
                        scope.launch {
                            contactRepository.deleteContact(contact.contactId)
                        }
                    },
                    onBack = { currentScreen = Screen.HOME }
                )
            }
            Screen.HELP_DIRECTORY -> {
                HelpDirectoryScreen(
                    onBack = { currentScreen = Screen.HOME }
                )
            }
            Screen.VERIFIER -> {
                ExportVerifierScreen(
                    exportPackage = recentExportPackage,
                    onVerifyPackage = {
                        scope.launch {
                            val allIncidents = incidentRepository.getAllIncidents().first()
                            val targetIncident = stateMachine.currentIncident.value ?: allIncidents.lastOrNull()
                            if (targetIncident != null) {
                                val entries = evidenceRepository.getEvidenceForIncident(targetIncident.incidentId).first()
                                if (entries.isEmpty() || targetIncident.state != IncidentState.SEALED) {
                                    recentExportPackage = EvidenceExporter.createExportPackage(
                                        incident = targetIncident,
                                        manifest = null,
                                        evidenceEntries = entries,
                                        outputDir = filesDir,
                                        isIntegrityVerified = false
                                    )
                                } else {
                                    val manifest = manifestManager.createAndSignManifest(targetIncident, entries)
                                    val isVerified = EvidenceVerifier.verifyPackageIntegrity(
                                        manifest = manifest,
                                        evidenceEntries = entries,
                                        keyStorageManager = keyManager,
                                        incidentState = targetIncident.state
                                    )
                                    recentExportPackage = EvidenceExporter.createExportPackage(
                                        incident = targetIncident,
                                        manifest = manifest,
                                        evidenceEntries = entries,
                                        outputDir = filesDir,
                                        isIntegrityVerified = isVerified
                                    )
                                }
                            } else {
                                recentExportPackage = null
                            }
                        }
                    },
                    onBack = { currentScreen = Screen.HOME }
                )
            }
            Screen.AUTH -> {
                AuthScreen(onBack = { currentScreen = Screen.HOME })
            }
            Screen.LEGAL_DRAFTING -> {
                org.sahara.app.ui.LegalDraftingScreen(onBack = { currentScreen = Screen.HOME })
            }
            Screen.ANCHORING -> {
                AnchoringScreen(onBack = { currentScreen = Screen.HOME })
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        if (::meshTransport.isInitialized) {
            meshTransport.stop()
        }
        if (isServiceBound) {
            unbindService(serviceConnection)
            isServiceBound = false
        }
    }

    private fun startMeshTransport() {
        if (!::meshTransport.isInitialized) return
        val missing = MeshPermissionManager.missingPermissions(this)
        if (missing.isNotEmpty()) {
            meshPermissionLauncher.launch(missing)
            return
        }
        meshTransport.startAdvertising()
        meshTransport.startDiscovery()
    }

    fun showIncomingMeshNotification(title: String, content: String, screen: Screen) {
        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as android.app.NotificationManager
        val channelId = "sahara_mesh_alerts"
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
            val channel = android.app.NotificationChannel(
                channelId,
                "Sahara Mesh Early Warning Alerts",
                android.app.NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Notifications for incoming BLE/P2P mesh safety and distress alerts"
            }
            notificationManager.createNotificationChannel(channel)
        }

        val intent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra("TARGET_SCREEN", screen.name)
        }
        val pendingIntent = android.app.PendingIntent.getActivity(
            this,
            System.currentTimeMillis().toInt(),
            intent,
            android.app.PendingIntent.FLAG_UPDATE_CURRENT or android.app.PendingIntent.FLAG_IMMUTABLE
        )

        val notification = androidx.core.app.NotificationCompat.Builder(this, channelId)
            .setContentTitle(title)
            .setContentText(content)
            .setSmallIcon(android.R.drawable.ic_dialog_alert)
            .setPriority(androidx.core.app.NotificationCompat.PRIORITY_HIGH)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .build()

        notificationManager.notify(System.currentTimeMillis().toInt(), notification)
    }
}
