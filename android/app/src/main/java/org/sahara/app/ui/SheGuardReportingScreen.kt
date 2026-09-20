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
import org.sahara.core.domain.engine.SpatioTemporalPatternEngine
import org.sahara.core.domain.models.MicroReport
import org.sahara.core.domain.models.ReportCategory
import org.sahara.core.domain.models.SpatioTemporalPattern
import org.sahara.core.domain.models.SyncStatus
import org.sahara.core.domain.repository.MicroReportRepository
import org.sahara.core.domain.repository.PatternRepository
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SheGuardReportingScreen(
    repository: MicroReportRepository,
    patternRepository: PatternRepository? = null,
    anonymousToken: String = UUID.randomUUID().toString().take(12),
    modifier: Modifier = Modifier
) {
    val coroutineScope = rememberCoroutineScope()
    var selectedCategory by remember { mutableStateOf(ReportCategory.POOR_LIGHTING) }
    var contextText by remember { mutableStateOf("") }
    var approximateArea by remember { mutableStateOf("Dadated Street / Mumbai Central") }
    var showConfirmation by remember { mutableStateOf(false) }
    var isSubmitting by remember { mutableStateOf(false) }

    val reportsState by repository.getAllReports().collectAsState(initial = emptyList())
    val patternEngine = remember { SpatioTemporalPatternEngine() }
    val candidatePatterns = remember(reportsState) {
        patternEngine.detectCandidatePatterns(reportsState)
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(Color(0xFF0F172A))
            .padding(16.dp)
    ) {
        // Header
        Text(
            text = "SheGuard Anonymous Safety Report",
            style = MaterialTheme.typography.titleLarge.copy(
                fontWeight = FontWeight.Bold,
                color = Color.White
            )
        )
        Text(
            text = "Submit a low-friction micro-report. Stored locally offline.",
            style = MaterialTheme.typography.bodySmall.copy(color = Color(0xFF94A3B8)),
            modifier = Modifier.padding(bottom = 12.dp)
        )

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

                    // Re-run pattern engine and persist candidate patterns
                    val updatedReports = reportsState + report
                    val detected = patternEngine.detectCandidatePatterns(updatedReports)
                    patternRepository?.let { repo ->
                        repo.clearPatterns()
                        detected.forEach { repo.savePattern(it) }
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

        // DETECT STAGE: Detected Candidate Patterns Banner
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
                        text = "🔍 DETECT STAGE: ${candidatePatterns.size} Candidate Pattern(s) Identified",
                        color = Color.White,
                        fontWeight = FontWeight.Bold,
                        fontSize = 13.sp
                    )
                    candidatePatterns.forEach { pattern ->
                        Text(
                            text = "• Candidate [${pattern.category.name.replace("_", " ")}]: ${pattern.reportCount} reports in area (${String.format("%.1f", pattern.radiusMeters)}m radius) [State: ${pattern.state}]",
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
