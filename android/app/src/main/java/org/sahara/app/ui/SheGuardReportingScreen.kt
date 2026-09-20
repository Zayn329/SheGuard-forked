package org.sahara.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
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
            .background(Color(0xFF0F172A))
            .padding(16.dp)
    ) {
        // Header
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "SheGuard Anonymous Safety Report",
                style = MaterialTheme.typography.titleLarge.copy(
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
            )
            if (onBack != null) {
                Surface(
                    color = Color(0xFF334155),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.clickable { onBack() }
                ) {
                    Text(
                        text = "✕ Back",
                        color = Color(0xFFCBD5E1),
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                    )
                }
            }
        }
        Text(
            text = "Submit a low-friction micro-report. Stored locally offline.",
            style = MaterialTheme.typography.bodySmall.copy(color = Color(0xFF94A3B8)),
            modifier = Modifier.padding(bottom = 8.dp)
        )

        // Phase E: Mesh Status Indicator Bar
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Surface(
                color = if (isMeshAvailable) Color(0xFF065F46) else Color(0xFF334155),
                shape = RoundedCornerShape(16.dp)
            ) {
                Text(
                    text = if (isMeshAvailable) "● Mesh: Available (Offline P2P)" else "○ Mesh: Unavailable (Local Mode Active)",
                    color = if (isMeshAvailable) Color(0xFFA7F3D0) else Color(0xFFCBD5E1),
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Medium,
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                )
            }
            Text(
                text = if (isMeshAvailable) "Simulate Offline" else "Enable Mesh",
                color = Color(0xFF38BDF8),
                fontSize = 11.sp,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.clickable {
                    isMeshAvailable = !isMeshAvailable
                    actualMeshAdapter.setMeshStatus(
                        if (isMeshAvailable) MeshStatus.AVAILABLE else MeshStatus.UNAVAILABLE
                    )
                }
            )
        }

        // Confirmation Banner
        if (showConfirmation) {
            Surface(
                color = Color(0xFF166534),
                shape = RoundedCornerShape(8.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 12.dp)
            ) {
                Row(
                    modifier = Modifier.padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "✓ Report saved locally in Room database!",
                        color = Color.White,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }
        }

        // Category Selection
        Text(
            text = "Hazard Category",
            style = MaterialTheme.typography.labelMedium.copy(color = Color(0xFFCBD5E1)),
            modifier = Modifier.padding(bottom = 6.dp)
        )

        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            ReportCategory.values().forEach { category ->
                val isSelected = category == selectedCategory
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .background(if (isSelected) Color(0xFF2563EB) else Color(0xFF1E293B))
                        .clickable { selectedCategory = category }
                        .padding(horizontal = 12.dp, vertical = 10.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = category.name.replace("_", " "),
                            color = Color.White,
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                            fontSize = 14.sp
                        )
                        if (isSelected) {
                            Text("● Selected", color = Color(0xFF93C5FD), fontSize = 12.sp)
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        // Context Description
        OutlinedTextField(
            value = contextText,
            onValueChange = { contextText = it },
            label = { Text("Optional Context / Details", color = Color(0xFF94A3B8)) },
            modifier = Modifier.fillMaxWidth(),
            colors = TextFieldDefaults.colors(
                focusedContainerColor = Color(0xFF1E293B),
                unfocusedContainerColor = Color(0xFF1E293B),
                focusedIndicatorColor = Color(0xFF38BDF8),
                unfocusedIndicatorColor = Color(0xFF334155),
                focusedTextColor = Color.White,
                unfocusedTextColor = Color.White
            ),
            maxLines = 2
        )

        Spacer(modifier = Modifier.height(10.dp))

        // Submit Button
        Button(
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
                }
            },
            enabled = !isSubmitting,
            modifier = Modifier
                .fillMaxWidth()
                .height(44.dp),
            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2563EB)),
            shape = RoundedCornerShape(8.dp)
        ) {
            Text(
                text = if (isSubmitting) "Saving..." else "Submit Micro-Report (Offline)",
                color = Color.White,
                fontWeight = FontWeight.Bold
            )
        }

        Spacer(modifier = Modifier.height(16.dp))

        // ALERT STAGE: Rising Pattern Early-Warning Cards (Phase D & E)
        if (displayAlerts.isNotEmpty()) {
            displayAlerts.forEach { alert ->
                val trustColor = when (alert.trustLevel) {
                    TrustLevel.HIGH -> Color(0xFF7F1D1D)   // deep red — high confidence
                    TrustLevel.MEDIUM -> Color(0xFF92400E) // deep orange-amber — medium
                    TrustLevel.LOW -> Color(0xFF1E3A5F)    // blue — low (shown for completeness)
                }
                Surface(
                    color = trustColor,
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 8.dp)
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "🚨 ALERT: ${alertEngine.categoryDisplayName(alert.category)}",
                                color = Color.White,
                                fontWeight = FontWeight.Bold,
                                fontSize = 13.sp
                            )
                            Surface(
                                color = Color.White.copy(alpha = 0.2f),
                                shape = RoundedCornerShape(4.dp)
                            ) {
                                Text(
                                    text = alert.trustLevel.name,
                                    color = Color.White,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 10.sp,
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                )
                            }
                        }
                        val sourceBadge = if (alert.isRelayed) "📡 Received via nearby device" else "🏠 Generated locally"
                        Text(
                            text = sourceBadge,
                            color = if (alert.isRelayed) Color(0xFF93C5FD) else Color(0xFFFDE047),
                            fontSize = 10.sp,
                            fontWeight = FontWeight.SemiBold,
                            modifier = Modifier.padding(top = 2.dp)
                        )
                        Text(
                            text = "📍 ${alert.approximateLocation}",
                            color = Color(0xFFFFD0D0),
                            fontSize = 11.sp,
                            modifier = Modifier.padding(top = 4.dp)
                        )
                        Text(
                            text = "🕒 ${alert.timeWindow}  |  Trust: ${String.format("%.2f", alert.trustScore)}",
                            color = Color(0xFFFFD0D0),
                            fontSize = 11.sp,
                            modifier = Modifier.padding(top = 2.dp)
                        )
                        Text(
                            text = alert.disclaimer,
                            color = Color(0xFFFFD0D0).copy(alpha = 0.7f),
                            fontSize = 9.sp,
                            modifier = Modifier.padding(top = 4.dp)
                        )
                    }
                }
            }
        }

        // TRUST STAGE: Verified Emerging Patterns Banner (Phase C)
        if (emergingPatterns.isNotEmpty()) {
            Surface(
                color = Color(0xFF065F46), // Emerald background for verified emerging patterns
                shape = RoundedCornerShape(8.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 12.dp)
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Text(
                        text = "🛡️ TRUST STAGE: ${emergingPatterns.size} Verified Emerging Pattern(s)",
                        color = Color.White,
                        fontWeight = FontWeight.Bold,
                        fontSize = 13.sp
                    )
                    emergingPatterns.forEach { pattern ->
                        Text(
                            text = "• Emerging [${pattern.category.name.replace("_", " ")}]: Trust Score ${String.format("%.2f", pattern.trustScore)} | Multi-Reporter Verified (${String.format("%.0f", pattern.radiusMeters)}m radius)",
                            color = Color(0xFFA7F3D0),
                            fontSize = 11.sp,
                            modifier = Modifier.padding(top = 2.dp)
                        )
                    }
                }
            }
        }

        // DETECT STAGE: Unverified Candidate Patterns Banner
        if (candidatePatterns.isNotEmpty()) {
            Surface(
                color = Color(0xFF854D0E), // Amber background for candidate detection
                shape = RoundedCornerShape(8.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 12.dp)
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Text(
                        text = "🔍 DETECT STAGE: ${candidatePatterns.size} Candidate Pattern(s) (Unverified)",
                        color = Color.White,
                        fontWeight = FontWeight.Bold,
                        fontSize = 13.sp
                    )
                    candidatePatterns.forEach { pattern ->
                        Text(
                            text = "• Candidate [${pattern.category.name.replace("_", " ")}]: ${pattern.reportCount} reports (${String.format("%.0f", pattern.radiusMeters)}m radius) [Trust Score: ${String.format("%.2f", pattern.trustScore)} - Awaiting Diversity]",
                            color = Color(0xFFFEF08A),
                            fontSize = 11.sp,
                            modifier = Modifier.padding(top = 2.dp)
                        )
                    }
                }
            }
        }

        // Local Reports History
        Text(
            text = "Local Stored Reports (${reportsState.size})",
            style = MaterialTheme.typography.titleMedium.copy(
                fontWeight = FontWeight.Bold,
                color = Color.White
            ),
            modifier = Modifier.padding(bottom = 6.dp)
        )

        if (reportsState.isEmpty()) {
            Text(
                text = "No micro-reports stored locally yet.",
                color = Color(0xFF64748B),
                fontSize = 13.sp
            )
        } else {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                items(reportsState) { report ->
                    Surface(
                        color = Color(0xFF1E293B),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(8.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(
                                    text = report.category.name.replace("_", " "),
                                    color = Color(0xFF38BDF8),
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 12.sp
                                )
                                Text(
                                    text = SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date(report.timestamp)),
                                    color = Color(0xFF94A3B8),
                                    fontSize = 11.sp
                                )
                            }
                            if (report.contextDescription != null) {
                                Text(
                                    text = report.contextDescription!!,
                                    color = Color.White,
                                    fontSize = 11.sp,
                                    modifier = Modifier.padding(top = 2.dp)
                                )
                            }
                            Text(
                                text = "Status: ${report.syncStatus} | Token: ${report.anonymousReporterToken.take(8)}...",
                                color = Color(0xFF64748B),
                                fontSize = 10.sp,
                                modifier = Modifier.padding(top = 2.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}
