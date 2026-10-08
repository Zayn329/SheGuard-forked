package org.sahara.app.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.compose.ui.platform.LocalContext
import org.sahara.app.location.DeviceLocationManager
import org.sahara.app.location.LocationState
import org.sahara.app.location.SheGuardLocation
import org.sahara.core.domain.engine.RisingPatternAlertEngine
import org.sahara.core.domain.engine.SpatioTemporalPatternEngine
import org.sahara.core.domain.engine.TrustAndAntiGamingEvaluator
import org.sahara.core.domain.models.MicroReport
import org.sahara.core.domain.models.PatternState
import org.sahara.core.domain.models.ReportCategory
import org.sahara.core.domain.models.RisingPatternAlert
import org.sahara.core.domain.models.SpatioTemporalPattern
import org.sahara.core.domain.models.SyncStatus
import org.sahara.core.domain.models.TrustLevel
import org.sahara.core.domain.repository.MAX_SAVED_REPORTS
import org.sahara.core.domain.repository.MicroReportRepository
import org.sahara.core.domain.repository.PatternRepository
import org.sahara.services.mesh.relay.SheGuardMeshAdapter
import org.sahara.app.sync.ReportUploader
import java.text.SimpleDateFormat
import java.util.*
import kotlin.math.roundToInt


/** Safely unwraps a Context (possibly wrapped by Compose/Hilt/etc.) to its Activity. */
private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SheGuardReportingScreen(
    repository: MicroReportRepository,
    patternRepository: PatternRepository? = null,
    alertRepository: org.sahara.core.domain.repository.AlertRepository? = null,
    meshAdapter: SheGuardMeshAdapter? = null,
    anonymousToken: String = ReportUploader.reporterToken(androidx.compose.ui.platform.LocalContext.current),
    onBack: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val locationManager = remember { DeviceLocationManager(context) }
    var locationState by remember { mutableStateOf<LocationState>(LocationState.Idle) }

    val activity = remember(context) { context.findActivity() }
    val locationPermissions = arrayOf(
        Manifest.permission.ACCESS_FINE_LOCATION,
        Manifest.permission.ACCESS_COARSE_LOCATION
    )

    fun shouldShowRationale(): Boolean {
        val act = activity ?: return false
        return locationPermissions.any {
            ActivityCompat.shouldShowRequestPermissionRationale(act, it)
        }
    }

    val permanentDenialError = LocationState.Error(
        message = "Location permission permanently denied. Enable it in App Settings to use location features.",
        isGpsDisabled = false
    )

    val locationPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val granted = permissions[Manifest.permission.ACCESS_FINE_LOCATION] == true ||
                permissions[Manifest.permission.ACCESS_COARSE_LOCATION] == true
        if (granted) {
            coroutineScope.launch {
                locationState = LocationState.Fetching
                locationState = locationManager.getCurrentDeviceLocation()
            }
        } else {
            // A request just happened, so rationale == false means permanent denial
            locationState = if (shouldShowRationale()) LocationState.PermissionRequired
            else permanentDenialError
        }
    }

    fun refreshDeviceLocation() {
        coroutineScope.launch {
            if (!locationManager.hasAnyLocationPermission()) {
                locationState = LocationState.PermissionRequired
                locationPermissionLauncher.launch(locationPermissions)
            } else {
                locationState = LocationState.Fetching
                locationState = locationManager.getCurrentDeviceLocation()
            }
        }
    }

    // Initialize location state
    var permissionRequested by remember { mutableStateOf(false) }
    var settingsRequested by remember { mutableStateOf(false) }

    // Launcher for Android Settings
    val settingsLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) {
        // When returning from Settings, re-check location state
        settingsRequested = false
        if (locationManager.hasAnyLocationPermission() && locationManager.isLocationServicesEnabled()) {
            coroutineScope.launch {
                locationState = LocationState.Fetching
                locationState = locationManager.getCurrentDeviceLocation()
            }
        } else if (locationManager.hasAnyLocationPermission()) {
            locationState = LocationState.Error(
                message = "Location Services are disabled. Enable GPS or network location in Settings to use this feature.",
                isGpsDisabled = true
            )
        }
    }

    // Helper function to get appropriate action text based on location state
    fun getLocationActionText(): String {
        return when (locationState) {
            is LocationState.Error -> {
                if ((locationState as LocationState.Error).isGpsDisabled) {
                    "Turn On Location"
                } else if (!locationManager.hasAnyLocationPermission()) {
                    "Open Settings"
                } else {
                    "Retry GPS"
                }
            }
            is LocationState.PermissionRequired -> "Retry GPS"
            else -> "🔄 Refresh"
        }
    }

    // Helper function to handle location action based on state
    fun handleLocationAction() {
        when (locationState) {
            is LocationState.Error -> {
                if ((locationState as LocationState.Error).isGpsDisabled) {
                    // Location services disabled - open Android Settings
                    settingsRequested = true
                    settingsLauncher.launch(Intent(android.provider.Settings.ACTION_LOCATION_SOURCE_SETTINGS))
                } else if (!locationManager.hasAnyLocationPermission()) {
                    // Permission permanently denied - open this app's settings page
                    settingsRequested = true
                    settingsLauncher.launch(
                        Intent(android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
                            .setData(Uri.fromParts("package", context.packageName, null))
                    )
                } else {
                    // Other error - refresh location
                    refreshDeviceLocation()
                }
            }
            is LocationState.PermissionRequired -> {
                // Permission needed - request permissions
                permissionRequested = true
                locationPermissionLauncher.launch(locationPermissions)
            }
            else -> {
                // Default case - refresh location
                refreshDeviceLocation()
            }
        }
    }

    LaunchedEffect(Unit) {
        if (!locationManager.hasAnyLocationPermission()) {
            permissionRequested = true
            locationState = LocationState.PermissionRequired
            locationPermissionLauncher.launch(locationPermissions)
        } else if (!locationManager.isLocationServicesEnabled()) {
            // Permission granted but location services disabled
            locationState = LocationState.Error(
                message = "Location Services are disabled. Enable GPS or network location in Settings to use this feature.",
                isGpsDisabled = true
            )
        } else {
            // Permission already granted and location services enabled, get location
            locationState = LocationState.Fetching
            locationState = locationManager.getCurrentDeviceLocation()
        }
    }

    var selectedCategory by remember { mutableStateOf(ReportCategory.POOR_LIGHTING) }
    var contextText by remember { mutableStateOf("") }
    var showConfirmation by remember { mutableStateOf(false) }
    var isSubmitting by remember { mutableStateOf(false) }
    var showLimitDialog by remember { mutableStateOf(false) }
    var reportToRemove by remember { mutableStateOf<MicroReport?>(null) }
    var removalNotice by remember { mutableStateOf<String?>(null) }

    val actualMeshAdapter = meshAdapter ?: remember(alertRepository) {
        SheGuardMeshAdapter(alertRepository = alertRepository)
    }
    val transportStatusState = actualMeshAdapter.transport?.status?.collectAsState()
    val transportStatus = transportStatusState?.value
    val peerState = actualMeshAdapter.transport?.peers?.collectAsState()
    val peerCount = peerState?.value?.size ?: 0
    val isMeshConnected = transportStatus == org.sahara.services.mesh.transport.MeshTransportStatus.CONNECTED

    val reportsState by repository.getAllReports().collectAsState(initial = emptyList())
    LaunchedEffect(Unit) { ReportUploader.syncPending(repository) }
    val persistedAlerts: List<RisingPatternAlert> by (
            alertRepository?.getAllAlerts()?.collectAsState(initial = emptyList<RisingPatternAlert>())
                ?: remember { mutableStateOf(emptyList<RisingPatternAlert>()) }
            )

    val patternEngine = remember { SpatioTemporalPatternEngine() }
    val trustEvaluator = remember { TrustAndAntiGamingEvaluator() }
    val alertEngine = remember { RisingPatternAlertEngine() }

    val evaluatedPatterns = remember(reportsState) {
        val candidates = patternEngine.detectCandidatePatterns(reportsState)
        candidates.map { candidate ->
            trustEvaluator.evaluatePattern(candidate, reportsState)
        }
    }
    val emergingPatterns = remember(evaluatedPatterns) {
        evaluatedPatterns.filter { it.state == PatternState.PATTERN_EMERGING }
    }
    val candidatePatterns = remember(evaluatedPatterns) {
        evaluatedPatterns.filter { it.state == PatternState.PATTERN_CANDIDATE }
    }
    // Phase D: generate rising-pattern alerts from emerging patterns (deterministic, on-device)
    val activeAlerts = remember(emergingPatterns) {
        alertEngine.generateAlerts(emergingPatterns)
    }

    // Combine locally generated active alerts with persisted (and relayed) alerts
    val displayAlerts = remember(activeAlerts, persistedAlerts) {
        (persistedAlerts + activeAlerts).distinctBy { it.alertId }
    }

    fun reportSummaryLabel(report: MicroReport): String {
        val time = SimpleDateFormat("dd MMM, HH:mm", Locale.getDefault()).format(Date(report.timestamp))
        return "${report.category.name.replace("_", " ")} · $time"
    }

    // Limit reached: explain and let the user choose which report to remove (never automatic)
    if (showLimitDialog) {
        AlertDialog(
            onDismissRequest = { showLimitDialog = false },
            title = { Text("Report limit reached", fontWeight = FontWeight.Bold) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("You can save up to $MAX_SAVED_REPORTS reports.")
                    Text("Report limit reached. Remove an existing report to save a new one.")
                    reportsState.take(MAX_SAVED_REPORTS).forEach { report ->
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = reportSummaryLabel(report),
                                fontSize = 12.sp,
                                modifier = Modifier.weight(1f)
                            )
                            TextButton(onClick = {
                                showLimitDialog = false
                                reportToRemove = report
                            }) {
                                Text("Remove Report", color = SheGuardColors.RoseText, fontSize = 12.sp)
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showLimitDialog = false }) { Text("Cancel") }
            }
        )
    }

    // Explicit confirmation for removing one specific report
    reportToRemove?.let { target ->
        AlertDialog(
            onDismissRequest = { reportToRemove = null },
            title = { Text("Remove this report?", fontWeight = FontWeight.Bold) },
            text = {
                Text("${reportSummaryLabel(target)}\n\nThis report will be permanently removed from this device. Your other reports are not affected.")
            },
            confirmButton = {
                TextButton(onClick = {
                    val toDelete = target
                    reportToRemove = null
                    coroutineScope.launch {
                        repository.deleteReport(toDelete.reportId)
                        // Keep persisted patterns consistent with the remaining reports
                        val remaining = repository.getAllReports().first()
                        patternRepository?.let { repo ->
                            val candidates = patternEngine.detectCandidatePatterns(remaining)
                            val evaluated = candidates.map { trustEvaluator.evaluatePattern(it, remaining) }
                            repo.clearPatterns()
                            evaluated.forEach { repo.savePattern(it) }
                        }
                        removalNotice = "Report removed. You can now save a new report."
                        delay(3000)
                        removalNotice = null
                    }
                }) {
                    Text("Remove", color = SheGuardColors.RoseText, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { reportToRemove = null }) { Text("Keep Report") }
            }
        )
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(SheGuardColors.Background)
    ) {
        // Top App Bar (Pink & White)
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .shadow(4.dp, RoundedCornerShape(bottomStart = 24.dp, bottomEnd = 24.dp), spotColor = SheGuardColors.Primary.copy(alpha = 0.1f))
                .background(SheGuardColors.SurfaceCard, RoundedCornerShape(bottomStart = 24.dp, bottomEnd = 24.dp))
                .border(width = 1.dp, color = SheGuardColors.BorderSubtle, shape = RoundedCornerShape(bottomStart = 24.dp, bottomEnd = 24.dp))
                .padding(horizontal = 20.dp, vertical = 16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.weight(1f)
                ) {
                    Box(
                        modifier = Modifier
                            .size(40.dp)
                            .clip(CircleShape)
                            .background(SheGuardColors.PrimaryContainer)
                            .border(1.5.dp, SheGuardColors.PrimaryLight, CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(text = "🛡️", fontSize = 20.sp)
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "SheGuard Micro-Report",
                            style = MaterialTheme.typography.titleLarge.copy(
                                fontWeight = FontWeight.Bold,
                                color = SheGuardColors.TextPrimary
                            )
                        )
                        Text(
                            text = "Offline-First Safety Pattern Pipeline",
                            style = MaterialTheme.typography.bodySmall.copy(
                                color = SheGuardColors.Primary,
                                fontWeight = FontWeight.SemiBold
                            )
                        )
                    }
                }
                if (onBack != null) {
                    Spacer(modifier = Modifier.width(10.dp))
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(12.dp))
                            .background(SheGuardColors.PrimaryContainer)
                            .border(1.dp, SheGuardColors.BorderHighlight, RoundedCornerShape(12.dp))
                            .clickable { onBack() }
                            .padding(horizontal = 12.dp, vertical = 8.dp)
                    ) {
                        Text(
                            text = "✕ Close",
                            color = SheGuardColors.Primary,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // Phase E: Nearby Device Support & Offline Status Bar
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(14.dp))
                    .background(if (isMeshConnected) SheGuardColors.EmeraldBg else SheGuardColors.SurfaceElevated)
                    .border(
                        1.dp,
                        if (isMeshConnected) SheGuardColors.EmeraldBorder else SheGuardColors.BorderSubtle,
                        RoundedCornerShape(14.dp)
                    )
                    .padding(horizontal = 14.dp, vertical = 10.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(10.dp)
                            .clip(CircleShape)
                            .background(if (isMeshConnected) SheGuardColors.EmeraldSuccess else SheGuardColors.TextMuted)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = if (isMeshConnected) {
                            "Nearby Device Support Active ($peerCount nearby)"
                        } else {
                            when (transportStatus) {
                                org.sahara.services.mesh.transport.MeshTransportStatus.PERMISSION_REQUIRED -> "Nearby Devices: Permission needed"
                                org.sahara.services.mesh.transport.MeshTransportStatus.UNAVAILABLE,
                                org.sahara.services.mesh.transport.MeshTransportStatus.ERROR -> "Nearby Devices: Unavailable"
                                org.sahara.services.mesh.transport.MeshTransportStatus.STARTING,
                                org.sahara.services.mesh.transport.MeshTransportStatus.ADVERTISING,
                                org.sahara.services.mesh.transport.MeshTransportStatus.DISCOVERING,
                                org.sahara.services.mesh.transport.MeshTransportStatus.CONNECTING -> "Nearby Devices: Searching..."
                                else -> "Nearby Devices: Not connected (saved on this device)"
                            }
                        },
                        color = if (isMeshConnected) SheGuardColors.EmeraldText else SheGuardColors.TextSecondary,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }

        // Scrollable Body Content
        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Success Confirmation Banner
            AnimatedVisibility(
                visible = showConfirmation,
                enter = fadeIn(),
                exit = fadeOut()
            ) {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .border(1.dp, SheGuardColors.EmeraldBorder, RoundedCornerShape(16.dp)),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = SheGuardColors.EmeraldBg),
                    elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(text = "✓", color = SheGuardColors.EmeraldSuccess, fontWeight = FontWeight.Bold, fontSize = 20.sp)
                        Spacer(modifier = Modifier.width(10.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Micro-Report Saved Locally!",
                                color = SheGuardColors.EmeraldText,
                                fontWeight = FontWeight.Bold,
                                fontSize = 14.sp
                            )
                            Text(
                                text = "Stored offline in Room database & evaluated in deterministic pipeline.",
                                color = SheGuardColors.EmeraldText.copy(alpha = 0.85f),
                                fontSize = 11.sp
                            )
                        }
                    }
                }
            }

            // SECTION 1: Report Submission Card (Pink & White)
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .border(1.dp, SheGuardColors.BorderSubtle, RoundedCornerShape(20.dp)),
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = SheGuardColors.SurfaceCard),
                elevation = CardDefaults.cardElevation(defaultElevation = 3.dp)
            ) {
                Column(modifier = Modifier.padding(18.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Phase A · Safety Micro-Report",
                            style = MaterialTheme.typography.titleMedium.copy(
                                fontWeight = FontWeight.Bold,
                                color = SheGuardColors.TextPrimary
                            ),
                            modifier = Modifier
                                .weight(1f)
                                .padding(end = 8.dp)
                        )
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(10.dp))
                                .background(SheGuardColors.PrimaryContainer)
                                .border(1.dp, SheGuardColors.BorderSubtle, RoundedCornerShape(10.dp))
                                .padding(horizontal = 8.dp, vertical = 4.dp)
                        ) {
                            Text(
                                text = "Token: ${anonymousToken.take(6)}...",
                                style = MaterialTheme.typography.labelSmall.copy(color = SheGuardColors.Primary, fontWeight = FontWeight.Bold)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    Text(
                        text = "Select Hazard Category",
                        style = MaterialTheme.typography.labelMedium.copy(
                            color = SheGuardColors.TextSecondary,
                            fontWeight = FontWeight.Bold
                        )
                    )

                    Spacer(modifier = Modifier.height(10.dp))

                    // 2-Column Hazard Grid with exact ReportCategory values
                    val categories = ReportCategory.values().toList()
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        for (i in categories.indices step 2) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(IntrinsicSize.Min),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                val cat1 = categories[i]
                                val cat2 = if (i + 1 < categories.size) categories[i + 1] else null

                                HazardCategoryChip(
                                    category = cat1,
                                    isSelected = selectedCategory == cat1,
                                    onClick = { selectedCategory = cat1 },
                                    modifier = Modifier.weight(1f).fillMaxHeight()
                                )

                                if (cat2 != null) {
                                    HazardCategoryChip(
                                        category = cat2,
                                        isSelected = selectedCategory == cat2,
                                        onClick = { selectedCategory = cat2 },
                                        modifier = Modifier.weight(1f).fillMaxHeight()
                                    )
                                } else {
                                    Spacer(modifier = Modifier.weight(1f))
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    // Location context card (Live GPS / Address / Accuracy)
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(14.dp))
                            .background(SheGuardColors.SurfaceElevated)
                            .border(1.dp, SheGuardColors.BorderSubtle, RoundedCornerShape(14.dp))
                            .padding(14.dp)
                    ) {
                        Column {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier
                                        .weight(1f)
                                        .padding(end = 8.dp)
                                ) {
                                    Text(text = "📍", fontSize = 16.sp)
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = "Device Location Context",
                                            style = MaterialTheme.typography.labelSmall.copy(
                                                color = SheGuardColors.Primary,
                                                fontWeight = FontWeight.Bold
                                            )
                                        )
                                        when (val state = locationState) {
                                            is LocationState.Fetching -> {
                                                Row(
                                                    verticalAlignment = Alignment.CenterVertically,
                                                    modifier = Modifier.padding(top = 2.dp)
                                                ) {
                                                    CircularProgressIndicator(
                                                        modifier = Modifier.size(12.dp),
                                                        strokeWidth = 2.dp,
                                                        color = SheGuardColors.Primary
                                                    )
                                                    Spacer(modifier = Modifier.width(6.dp))
                                                    Text(
                                                        text = "Fetching GPS location...",
                                                        style = MaterialTheme.typography.bodySmall.copy(
                                                            color = SheGuardColors.TextSecondary,
                                                            fontWeight = FontWeight.Medium
                                                        )
                                                    )
                                                }
                                            }
                                            is LocationState.Success -> {
                                                Text(
                                                    text = state.location.readableAddress,
                                                    style = MaterialTheme.typography.bodyMedium.copy(
                                                        color = SheGuardColors.TextPrimary,
                                                        fontWeight = FontWeight.SemiBold
                                                    )
                                                )
                                            }
                                            is LocationState.Error -> {
                                                Text(
                                                    text = state.message,
                                                    style = MaterialTheme.typography.bodySmall.copy(
                                                        color = SheGuardColors.RoseText,
                                                        fontWeight = FontWeight.Bold
                                                    )
                                                )
                                            }
                                            is LocationState.PermissionRequired -> {
                                                Text(
                                                    text = "Location Permission Required",
                                                    style = MaterialTheme.typography.bodySmall.copy(
                                                        color = SheGuardColors.AmberText,
                                                        fontWeight = FontWeight.Bold
                                                    )
                                                )
                                            }
                                            is LocationState.Idle -> {
                                                Text(
                                                    text = "Location not acquired",
                                                    style = MaterialTheme.typography.bodySmall.copy(
                                                        color = SheGuardColors.TextSecondary
                                                    )
                                                )
                                            }
                                        }
                                    }
                                }

                                // Action button - changes based on location state
                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(SheGuardColors.PrimaryContainer)
                                        .border(1.dp, SheGuardColors.BorderHighlight, RoundedCornerShape(8.dp))
                                        .clickable { handleLocationAction() }
                                        .padding(horizontal = 10.dp, vertical = 6.dp)
                                ) {
                                    Text(
                                        text = getLocationActionText(),
                                        color = SheGuardColors.Primary,
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }

                            // Subtitle / Accuracy / Diagnostics
                            when (val state = locationState) {
                                is LocationState.Success -> {
                                    Spacer(modifier = Modifier.height(6.dp))
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        verticalAlignment = Alignment.Top
                                    ) {
                                        Text(
                                            text = String.format(Locale.US, "Coords: %.4f° N, %.4f° E", state.location.latitude, state.location.longitude),
                                            style = MaterialTheme.typography.labelSmall.copy(color = SheGuardColors.TextMuted, fontSize = 10.sp),
                                            modifier = Modifier.weight(1f)
                                        )
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Text(
                                            text = state.location.getAccuracyDescription(),
                                            style = MaterialTheme.typography.labelSmall.copy(
                                                color = if (state.location.isApproximateOnly) SheGuardColors.AmberText else SheGuardColors.EmeraldText,
                                                fontWeight = FontWeight.SemiBold,
                                                fontSize = 10.sp
                                            )
                                        )
                                    }
                                }
                                is LocationState.Error -> {
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text(
                                        text = state.message,
                                        style = MaterialTheme.typography.labelSmall.copy(color = SheGuardColors.RoseText, fontSize = 10.sp)
                                    )
                                }
                                is LocationState.PermissionRequired -> {
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text(
                                        text = "Tap Retry GPS to grant permissions for spatio-temporal cluster verification.",
                                        style = MaterialTheme.typography.labelSmall.copy(color = SheGuardColors.AmberText, fontSize = 10.sp)
                                    )
                                }
                                else -> {}
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    // Optional details TextField
                    OutlinedTextField(
                        value = contextText,
                        onValueChange = { contextText = it },
                        label = { Text("Optional Context / Quick Note", color = SheGuardColors.TextMuted, fontSize = 12.sp) },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(14.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedContainerColor = SheGuardColors.SurfaceCard,
                            unfocusedContainerColor = SheGuardColors.SurfaceElevated,
                            focusedBorderColor = SheGuardColors.Primary,
                            unfocusedBorderColor = SheGuardColors.BorderSubtle,
                            focusedTextColor = SheGuardColors.TextPrimary,
                            unfocusedTextColor = SheGuardColors.TextPrimary
                        ),
                        maxLines = 2
                    )

                    Spacer(modifier = Modifier.height(16.dp))

                    // Submit Button (Pink & White Gradient)
                    SaharaPrimaryButton(
                        text = if (isSubmitting) "Saving Report..." else "Submit Micro-Report (Offline)",
                        onClick = {
                            if (isSubmitting) return@SaharaPrimaryButton
                            isSubmitting = true
                            coroutineScope.launch {
                                // Enforce the limit against the actual persisted reports before building/saving
                                if (repository.getReportCount() >= MAX_SAVED_REPORTS) {
                                    isSubmitting = false
                                    showLimitDialog = true
                                    return@launch
                                }
                                val loc = (locationState as? LocationState.Success)?.location
                                val reportLat = loc?.latitude
                                val reportLng = loc?.longitude
                                val reportArea = loc?.readableAddress ?: "Location Unknown / Unavailable"
                                val reportAcc = loc?.accuracy

                                val report = MicroReport(
                                    anonymousReporterToken = anonymousToken,
                                    category = selectedCategory,
                                    latitude = reportLat,
                                    longitude = reportLng,
                                    approximateArea = reportArea,
                                    contextDescription = contextText.ifBlank { null },
                                    syncStatus = SyncStatus.LOCAL,
                                    accuracy = reportAcc
                                )
                                val wasSaved = repository.saveReport(report)
                                if (!wasSaved) {
                                    // Persistence layer blocked it (limit reached); nothing was deleted or overwritten
                                    isSubmitting = false
                                    showLimitDialog = true
                                    return@launch
                                }

                                // Re-run pattern engine and trust evaluation, then persist evaluated patterns
                                val updatedReports = reportsState + report
                                val detectedCandidates = patternEngine.detectCandidatePatterns(updatedReports)
                                val evaluatedPatternsToSave = detectedCandidates.map { candidate ->
                                    trustEvaluator.evaluatePattern(candidate, updatedReports)
                                }
                                patternRepository?.let { repo ->
                                    repo.clearPatterns()
                                    evaluatedPatternsToSave.forEach { repo.savePattern(it) }
                                }

                                // Phase D & E: persist rising-pattern alerts and queue for mesh relay
                                alertRepository?.let { repo ->
                                    repo.clearAlerts()
                                    val alerts = alertEngine.generateAlerts(evaluatedPatternsToSave)
                                    alerts.forEach { alert ->
                                        repo.saveAlert(alert)
                                        actualMeshAdapter.queueAlertForRelay(alert)
                                    }
                                }
                                coroutineScope.launch { ReportUploader.syncPending(repository) }
                                contextText = ""
                                isSubmitting = false
                                showConfirmation = true
                                delay(3000)
                                showConfirmation = false
                            }
                        },
                        enabled = !isSubmitting
                    )
                }
            }

            // SECTION 2: Phase D & E — Rising Pattern Early-Warning Alerts
            if (displayAlerts.isNotEmpty()) {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        text = "Phase D & E · Active Early-Warning Alerts (${displayAlerts.size})",
                        style = MaterialTheme.typography.titleMedium.copy(
                            fontWeight = FontWeight.Bold,
                            color = SheGuardColors.TextPrimary
                        )
                    )

                    displayAlerts.forEach { alert ->
                        val trustBg = when (alert.trustLevel) {
                            TrustLevel.HIGH -> SheGuardColors.RoseBg
                            TrustLevel.MEDIUM -> SheGuardColors.AmberBg
                            TrustLevel.LOW -> SheGuardColors.CyanContainer
                        }
                        val trustBorder = when (alert.trustLevel) {
                            TrustLevel.HIGH -> SheGuardColors.RoseBorder
                            TrustLevel.MEDIUM -> SheGuardColors.AmberBorder
                            TrustLevel.LOW -> SheGuardColors.CyanAccent.copy(alpha = 0.4f)
                        }
                        val trustTextColor = when (alert.trustLevel) {
                            TrustLevel.HIGH -> SheGuardColors.RoseText
                            TrustLevel.MEDIUM -> SheGuardColors.AmberText
                            TrustLevel.LOW -> SheGuardColors.CyanAccent
                        }

                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .border(1.dp, trustBorder, RoundedCornerShape(18.dp)),
                            shape = RoundedCornerShape(18.dp),
                            colors = CardDefaults.cardColors(containerColor = trustBg),
                            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                        ) {
                            Column(modifier = Modifier.padding(16.dp)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        modifier = Modifier.weight(1f)
                                    ) {
                                        Text(text = "🚨", fontSize = 18.sp)
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Text(
                                            text = "ALERT: ${alertEngine.categoryDisplayName(alert.category)}",
                                            color = trustTextColor,
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 15.sp,
                                            modifier = Modifier.weight(1f)
                                        )
                                    }
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Box(
                                        modifier = Modifier
                                            .clip(RoundedCornerShape(8.dp))
                                            .background(SheGuardColors.SurfaceCard)
                                            .border(1.dp, trustBorder, RoundedCornerShape(8.dp))
                                            .padding(horizontal = 8.dp, vertical = 3.dp)
                                    ) {
                                        Text(
                                            text = "Trust: ${alert.trustLevel.name}",
                                            color = trustTextColor,
                                            fontWeight = FontWeight.ExtraBold,
                                            fontSize = 10.sp
                                        )
                                    }
                                }

                                Spacer(modifier = Modifier.height(8.dp))

                                val sourceText = if (alert.isRelayed) "📡 Received from a nearby device" else "🏠 Locally evaluated"
                                Text(
                                    text = sourceText,
                                    color = if (alert.isRelayed) SheGuardColors.CyanAccent else SheGuardColors.EmeraldText,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold
                                )

                                Spacer(modifier = Modifier.height(4.dp))

                                Text(
                                    text = "📍 ${alert.approximateLocation}",
                                    color = SheGuardColors.TextPrimary,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Medium
                                )
                                Text(
                                    text = "🕒 ${alert.timeWindow} · Trust Score: ${String.format("%.2f", alert.trustScore)}",
                                    color = SheGuardColors.TextSecondary,
                                    fontSize = 11.sp
                                )

                                Spacer(modifier = Modifier.height(8.dp))

                                Text(
                                    text = alert.disclaimer,
                                    color = SheGuardColors.TextMuted,
                                    fontSize = 10.sp,
                                    lineHeight = 13.sp
                                )
                            }
                        }
                    }
                }
            }

            // SECTION 3: Phase C — Verified Emerging Patterns (Trust Stage)
            if (emergingPatterns.isNotEmpty()) {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .border(1.dp, SheGuardColors.EmeraldBorder, RoundedCornerShape(18.dp)),
                    shape = RoundedCornerShape(18.dp),
                    colors = CardDefaults.cardColors(containerColor = SheGuardColors.EmeraldBg),
                    elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(text = "🛡️", fontSize = 18.sp)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "Phase C · Multi-Signal Verified Emerging Patterns (${emergingPatterns.size})",
                                color = SheGuardColors.EmeraldText,
                                fontWeight = FontWeight.Bold,
                                fontSize = 14.sp,
                                modifier = Modifier.weight(1f)
                            )
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                        emergingPatterns.forEach { pattern ->
                            Text(
                                text = "• [${pattern.category.name.replace("_", " ")}]: Trust Score ${String.format("%.2f", pattern.trustScore)} | Multi-Reporter Verified (${String.format("%.0f", pattern.radiusMeters)}m cluster)",
                                color = SheGuardColors.EmeraldText,
                                fontSize = 12.sp,
                                modifier = Modifier.padding(vertical = 2.dp)
                            )
                        }
                    }
                }
            }

            // SECTION 4: Phase B — Candidate Patterns (Detect Stage)
            if (candidatePatterns.isNotEmpty()) {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .border(1.dp, SheGuardColors.AmberBorder, RoundedCornerShape(18.dp)),
                    shape = RoundedCornerShape(18.dp),
                    colors = CardDefaults.cardColors(containerColor = SheGuardColors.AmberBg),
                    elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(text = "🔍", fontSize = 18.sp)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "Phase B · Detected Candidate Patterns (${candidatePatterns.size})",
                                color = SheGuardColors.AmberText,
                                fontWeight = FontWeight.Bold,
                                fontSize = 14.sp,
                                modifier = Modifier.weight(1f)
                            )
                        }
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "Awaiting multi-reporter diversity. Raw report volume alone does NOT cause escalation.",
                            color = SheGuardColors.AmberText.copy(alpha = 0.85f),
                            fontSize = 11.sp
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        candidatePatterns.forEach { pattern ->
                            Text(
                                text = "• [${pattern.category.name.replace("_", " ")}]: ${pattern.reportCount} reports (${String.format("%.0f", pattern.radiusMeters)}m radius) [Trust Score: ${String.format("%.2f", pattern.trustScore)}]",
                                color = SheGuardColors.AmberText,
                                fontSize = 12.sp,
                                modifier = Modifier.padding(vertical = 2.dp)
                            )
                        }
                    }
                }
            }

            // SECTION 5: Local Reports Log (Pink & White)
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .border(1.dp, SheGuardColors.BorderSubtle, RoundedCornerShape(20.dp)),
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = SheGuardColors.SurfaceCard),
                elevation = CardDefaults.cardElevation(defaultElevation = 3.dp)
            ) {
                Column(modifier = Modifier.padding(18.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Saved Reports (${reportsState.size}/$MAX_SAVED_REPORTS)",
                            style = MaterialTheme.typography.titleMedium.copy(
                                fontWeight = FontWeight.Bold,
                                color = SheGuardColors.TextPrimary
                            ),
                            modifier = Modifier
                                .weight(1f)
                                .padding(end = 8.dp)
                        )
                        Text(
                            text = "Room Storage (Local)",
                            style = MaterialTheme.typography.labelSmall.copy(color = SheGuardColors.Primary, fontWeight = FontWeight.Bold)
                        )
                    }

                    removalNotice?.let { notice ->
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(text = notice, color = SheGuardColors.EmeraldText, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                    }
                    if (reportsState.size >= MAX_SAVED_REPORTS) {
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "You can save up to $MAX_SAVED_REPORTS reports. Remove an existing report to save a new one.",
                            color = SheGuardColors.AmberText,
                            fontSize = 12.sp
                        )
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    if (reportsState.isEmpty()) {
                        Text(
                            text = "No micro-reports stored locally yet. Use the selector above to log an anonymous report.",
                            color = SheGuardColors.TextMuted,
                            fontSize = 12.sp,
                            modifier = Modifier.padding(vertical = 12.dp)
                        )
                    } else {
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            reportsState.take(MAX_SAVED_REPORTS).forEach { report ->
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(14.dp))
                                        .background(SheGuardColors.SurfaceElevated)
                                        .border(1.dp, SheGuardColors.BorderSubtle, RoundedCornerShape(14.dp))
                                        .padding(12.dp)
                                ) {
                                    Column {
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Text(
                                                text = report.category.name.replace("_", " "),
                                                color = SheGuardColors.Primary,
                                                fontWeight = FontWeight.Bold,
                                                fontSize = 13.sp,
                                                modifier = Modifier.weight(1f)
                                            )
                                            Spacer(modifier = Modifier.width(8.dp))
                                            Text(
                                                text = SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date(report.timestamp)),
                                                color = SheGuardColors.TextMuted,
                                                fontSize = 10.sp
                                            )
                                        }
                                        val lat = report.latitude
                                        val lng = report.longitude
                                        val acc = report.accuracy
                                        val locLabel = buildString {
                                            append(report.approximateArea)
                                            if (lat != null && lng != null) {
                                                append(" (${String.format(Locale.US, "%.4f, %.4f", lat, lng)}")
                                                if (acc != null) {
                                                    append(" ±${acc.roundToInt()}m")
                                                }
                                                append(")")
                                            }
                                        }
                                        Text(
                                            text = "📍 $locLabel",
                                            color = SheGuardColors.TextPrimary,
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.Medium,
                                            modifier = Modifier.padding(top = 2.dp)
                                        )
                                        if (report.contextDescription != null) {
                                            Text(
                                                text = report.contextDescription!!,
                                                color = SheGuardColors.TextSecondary,
                                                fontSize = 12.sp,
                                                modifier = Modifier.padding(top = 3.dp)
                                            )
                                        }
                                        Text(
                                            text = "Status: ${report.syncStatus} · Token: ${report.anonymousReporterToken.take(8)}...",
                                            color = SheGuardColors.TextSecondary,
                                            fontSize = 10.sp,
                                            modifier = Modifier.padding(top = 3.dp)
                                        )
                                        Text(
                                            text = "Remove Report",
                                            color = SheGuardColors.RoseText,
                                            fontSize = 12.sp,
                                            fontWeight = FontWeight.Bold,
                                            modifier = Modifier
                                                .padding(top = 6.dp)
                                                .clickable { reportToRemove = report }
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun HazardCategoryChip(
    category: ReportCategory,
    isSelected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val icon = when (category) {
        ReportCategory.POOR_LIGHTING -> "💡"
        ReportCategory.HARASSMENT -> "⚠️"
        ReportCategory.FEELING_FOLLOWED -> "👁️"
        ReportCategory.UNSAFE_GATHERING -> "👥"
        ReportCategory.SUSPICIOUS_ACTIVITY -> "🚨"
    }

    val displayName = when (category) {
        ReportCategory.POOR_LIGHTING -> "Poor Lighting"
        ReportCategory.HARASSMENT -> "Harassment"
        ReportCategory.FEELING_FOLLOWED -> "Feeling Followed"
        ReportCategory.UNSAFE_GATHERING -> "Unsafe Gathering"
        ReportCategory.SUSPICIOUS_ACTIVITY -> "Suspicious Activity"
    }

    Box(
        modifier = modifier
            .clip(RoundedCornerShape(14.dp))
            .background(if (isSelected) SheGuardColors.PrimaryContainer else SheGuardColors.SurfaceCard)
            .border(
                width = if (isSelected) 1.5.dp else 1.dp,
                color = if (isSelected) SheGuardColors.Primary else SheGuardColors.BorderSubtle,
                shape = RoundedCornerShape(14.dp)
            )
            .clickable { onClick() }
            .heightIn(min = 48.dp)
            .padding(horizontal = 12.dp, vertical = 10.dp),
        contentAlignment = Alignment.CenterStart
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(text = icon, fontSize = 15.sp)
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = displayName,
                color = if (isSelected) SheGuardColors.Primary else SheGuardColors.TextPrimary,
                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                fontSize = 12.sp,
                modifier = Modifier.weight(1f)
            )
            if (isSelected) {
                Spacer(modifier = Modifier.width(4.dp))
                Text(text = "✓", color = SheGuardColors.Primary, fontSize = 13.sp, fontWeight = FontWeight.Bold)
            }
        }
    }
}