package org.sahara.app

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.Bundle
import android.os.IBinder
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
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

    private lateinit var database: SaharaDatabase
    private lateinit var incidentRepository: IncidentRepositoryImpl
    private lateinit var evidenceRepository: EvidenceRepositoryImpl
    private lateinit var auditRepository: AuditRepositoryImpl
    private lateinit var microReportRepository: MicroReportRepositoryImpl
    private lateinit var patternRepository: PatternRepositoryImpl
    private lateinit var alertRepository: AlertRepositoryImpl
    private lateinit var stateMachine: IncidentStateMachine
    private lateinit var panicController: PanicController
    private lateinit var keyManager: KeyStorageManagerImpl
    private lateinit var captureEngine: EvidenceCaptureEngine
    private lateinit var manifestManager: EvidenceManifestManager
    private lateinit var preRollBuffer: BoundedAudioPreRollBuffer

    private var foregroundService: SafetyForegroundService? = null
    private var isServiceBound = false

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

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        database = SaharaDatabase.getDatabase(applicationContext)
        incidentRepository = IncidentRepositoryImpl(database.incidentDao())
        evidenceRepository = EvidenceRepositoryImpl(database.evidenceDao())
        auditRepository = AuditRepositoryImpl(database.auditEventDao())
        microReportRepository = MicroReportRepositoryImpl(database.microReportDao())
        patternRepository = PatternRepositoryImpl(database.patternDao())
        alertRepository = AlertRepositoryImpl(database.alertDao())
        val contactRepository = ContactRepositoryImpl(database.notifyContactDao())

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
        val smsProvider = EscalationFallbackManager.createSmsProvider(isDebug = true)
        val fallbackManager = EscalationFallbackManager(meshRelay, smsProvider, isDebug = true)
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
        var currentScreen by remember { mutableStateOf(Screen.HOME) }
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

        var notifyContacts by remember {
            mutableStateOf(
                listOf(
                    org.sahara.core.domain.models.NotifyContact(
                        displayName = "Aisha",
                        type = org.sahara.core.domain.models.ContactType.SMS_ONLY,
                        phoneNumber = "+91 9876543210"
                    ),
                    org.sahara.core.domain.models.NotifyContact(
                        displayName = "Sara",
                        type = org.sahara.core.domain.models.ContactType.SMS_ONLY,
                        phoneNumber = "+91 9876543211"
                    )
                )
            )
        }

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
                        notifyContacts = notifyContacts + org.sahara.core.domain.models.NotifyContact(
                            displayName = name,
                            type = org.sahara.core.domain.models.ContactType.SMS_ONLY,
                            phoneNumber = phone
                        )
                    },
                    onRemoveContact = { contact ->
                        notifyContacts = notifyContacts.filterNot { it.displayName == contact.displayName }
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
        if (isServiceBound) {
            unbindService(serviceConnection)
            isServiceBound = false
        }
    }
}
