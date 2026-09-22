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
import kotlinx.coroutines.launch
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
import org.sahara.core.domain.repository.MicroReportRepository
import org.sahara.core.domain.repository.PatternRepository
import org.sahara.services.mesh.relay.MeshStatus
import org.sahara.services.mesh.relay.SheGuardMeshAdapter
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SheGuardReportingScreen(
    repository: MicroReportRepository,
    patternRepository: PatternRepository? = null,
    alertRepository: org.sahara.core.domain.repository.AlertRepository? = null,
    meshAdapter: SheGuardMeshAdapter? = null,
    anonymousToken: String = UUID.randomUUID().toString().take(12),
    onBack: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val coroutineScope = rememberCoroutineScope()
    var selectedCategory by remember { mutableStateOf(ReportCategory.POOR_LIGHTING) }
    var contextText by remember { mutableStateOf("") }
    var approximateArea by remember { mutableStateOf("Dadated Street / Mumbai Central") }
    var showConfirmation by remember { mutableStateOf(false) }
    var isSubmitting by remember { mutableStateOf(false) }
    var isMeshAvailable by remember { mutableStateOf(true) }

    val actualMeshAdapter = meshAdapter ?: remember(alertRepository) {
        SheGuardMeshAdapter(alertRepository = alertRepository)
    }

    val reportsState by repository.getAllReports().collectAsState(initial = emptyList())
    val persistedAlerts by (alertRepository?.getAllAlerts()?.collectAsState(initial = emptyList())
        ?: remember { mutableStateOf(emptyList()) })

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

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(SheGuardColors.Background)
    ) {
        // Top App Bar (Pink & White)
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(SheGuardColors.SurfaceCard)
                .border(width = 1.dp, color = SheGuardColors.BorderSubtle, shape = RoundedCornerShape(bottomStart = 24.dp, bottomEnd = 24.dp))
                .shadow(4.dp, RoundedCornerShape(bottomStart = 24.dp, bottomEnd = 24.dp), spotColor = SheGuardColors.Primary.copy(alpha = 0.1f))
                .padding(horizontal = 20.dp, vertical = 16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
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
                    Column {
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
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(12.dp))
                            .background(SheGuardColors.PrimaryContainer)
                            .border(1.dp, SheGuardColors.BorderHighlight, RoundedCornerShape(12.dp))
                            .clickable { onBack() }
                            .padding(horizontal = 12.dp, vertical = 6.dp)
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

            // Phase E: Mesh Relay & Offline Status Bar
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(14.dp))
                    .background(if (isMeshAvailable) SheGuardColors.EmeraldBg else SheGuardColors.SurfaceElevated)
                    .border(
                        1.dp,
                        if (isMeshAvailable) SheGuardColors.EmeraldBorder else SheGuardColors.BorderSubtle,
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
                            .background(if (isMeshAvailable) SheGuardColors.EmeraldSuccess else SheGuardColors.TextMuted)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = if (isMeshAvailable) "Mesh Relay: P2P Active" else "Mesh: Offline (Local Mode)",
                        color = if (isMeshAvailable) SheGuardColors.EmeraldText else SheGuardColors.TextSecondary,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(10.dp))
                        .background(SheGuardColors.SurfaceCard)
                        .border(1.dp, if (isMeshAvailable) SheGuardColors.EmeraldBorder else SheGuardColors.BorderSubtle, RoundedCornerShape(10.dp))
                        .clickable {
                            isMeshAvailable = !isMeshAvailable
                            actualMeshAdapter.setMeshStatus(
                                if (isMeshAvailable) MeshStatus.AVAILABLE else MeshStatus.UNAVAILABLE
                            )
                        }
                        .padding(horizontal = 10.dp, vertical = 4.dp)
                ) {
                    Text(
                        text = if (isMeshAvailable) "Simulate Offline" else "Enable Mesh",
                        color = if (isMeshAvailable) SheGuardColors.EmeraldText else SheGuardColors.Primary,
                        fontSize = 11.sp,
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
                        Column {
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
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Phase A · Safety Micro-Report",
                            style = MaterialTheme.typography.titleMedium.copy(
                                fontWeight = FontWeight.Bold,
                                color = SheGuardColors.TextPrimary
                            )
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
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                val cat1 = categories[i]
                                val cat2 = if (i + 1 < categories.size) categories[i + 1] else null

                                HazardCategoryChip(
                                    category = cat1,
                                    isSelected = selectedCategory == cat1,
                                    onClick = { selectedCategory = cat1 },
                                    modifier = Modifier.weight(1f)
                                )

                                if (cat2 != null) {
                                    HazardCategoryChip(
                                        category = cat2,
                                        isSelected = selectedCategory == cat2,
                                        onClick = { selectedCategory = cat2 },
                                        modifier = Modifier.weight(1f)
                                    )
                                } else {
                                    Spacer(modifier = Modifier.weight(1f))
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    // Location context pill
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .background(SheGuardColors.SurfaceElevated)
                            .border(1.dp, SheGuardColors.BorderSubtle, RoundedCornerShape(12.dp))
                            .padding(horizontal = 14.dp, vertical = 10.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(text = "📍", fontSize = 15.sp)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "Location context: $approximateArea",
                                style = MaterialTheme.typography.bodySmall.copy(color = SheGuardColors.TextPrimary, fontWeight = FontWeight.Medium)
                            )
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
                            coroutineScope.launch {
                                isSubmitting = true
                                val report = MicroReport(
                                    anonymousReporterToken = anonymousToken,
                                    category = selectedCategory,
                                    latitude = 19.0760,
                                    longitude = 72.8777,
                                    approximateArea = approximateArea,
                                    contextDescription = contextText.ifBlank { null },
                                    syncStatus = SyncStatus.LOCAL
                                )
                                repository.saveReport(report)

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
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Text(text = "🚨", fontSize = 18.sp)
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Text(
                                            text = "ALERT: ${alertEngine.categoryDisplayName(alert.category)}",
                                            color = trustTextColor,
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 15.sp
                                        )
                                    }
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

                                val sourceText = if (alert.isRelayed) "📡 Relayed via nearby device (Mesh)" else "🏠 Locally evaluated"
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
                                fontSize = 14.sp
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
                                fontSize = 14.sp
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
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Local Room Reports (${reportsState.size})",
                            style = MaterialTheme.typography.titleMedium.copy(
                                fontWeight = FontWeight.Bold,
                                color = SheGuardColors.TextPrimary
                            )
                        )
                        Text(
                            text = "Room Storage (Local)",
                            style = MaterialTheme.typography.labelSmall.copy(color = SheGuardColors.Primary, fontWeight = FontWeight.Bold)
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
                            reportsState.take(8).forEach { report ->
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
                                            horizontalArrangement = Arrangement.SpaceBetween
                                        ) {
                                            Text(
                                                text = report.category.name.replace("_", " "),
                                                color = SheGuardColors.Primary,
                                                fontWeight = FontWeight.Bold,
                                                fontSize = 13.sp
                                            )
                                            Text(
                                                text = SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date(report.timestamp)),
                                                color = SheGuardColors.TextMuted,
                                                fontSize = 10.sp
                                            )
                                        }
                                        if (report.contextDescription != null) {
                                            Text(
                                                text = report.contextDescription!!,
                                                color = SheGuardColors.TextPrimary,
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
            .padding(horizontal = 12.dp, vertical = 12.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(text = icon, fontSize = 15.sp)
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = displayName,
                    color = if (isSelected) SheGuardColors.Primary else SheGuardColors.TextPrimary,
                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                    fontSize = 12.sp
                )
            }
            if (isSelected) {
                Text(text = "✓", color = SheGuardColors.Primary, fontSize = 13.sp, fontWeight = FontWeight.Bold)
            }
        }
    }
}
