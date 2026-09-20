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
import org.sahara.core.domain.models.MicroReport
import org.sahara.core.domain.models.ReportCategory
import org.sahara.core.domain.models.SyncStatus
import org.sahara.core.domain.repository.MicroReportRepository
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SheGuardReportingScreen(
    repository: MicroReportRepository,
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
            modifier = Modifier.padding(bottom = 16.dp)
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

        Spacer(modifier = Modifier.height(12.dp))

        // Context Description
        OutlinedTextField(
            value = contextText,
            onValueChange = { contextText = it },
            label = { Text("Optional Context / Details", color = Color(0xFF94A3B8)) },
            modifier = Modifier.fillMaxWidth(),
            colors = TextFieldDefaults.outlinedTextFieldColors(
                focusedBorderColor = Color(0xFF38BDF8),
                unfocusedBorderColor = Color(0xFF334155),
                focusedTextColor = Color.White,
                unfocusedTextColor = Color.White
            ),
            maxLines = 2
        )

        Spacer(modifier = Modifier.height(12.dp))

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
                    contextText = ""
                    isSubmitting = false
                    showConfirmation = true
                }
            },
            enabled = !isSubmitting,
            modifier = Modifier
                .fillMaxWidth()
                .height(48.dp),
            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2563EB)),
            shape = RoundedCornerShape(8.dp)
        ) {
            Text(
                text = if (isSubmitting) "Saving..." else "Submit Micro-Report (Offline)",
                color = Color.White,
                fontWeight = FontWeight.Bold
            )
        }

        Spacer(modifier = Modifier.height(20.dp))

        // Local Reports History
        Text(
            text = "Local Stored Reports (${reportsState.size})",
            style = MaterialTheme.typography.titleMedium.copy(
                fontWeight = FontWeight.Bold,
                color = Color.White
            ),
            modifier = Modifier.padding(bottom = 8.dp)
        )

        if (reportsState.isEmpty()) {
            Text(
                text = "No micro-reports stored locally yet.",
                color = Color(0xFF64748B),
                fontSize = 13.sp
            )
        } else {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                items(reportsState) { report ->
                    Surface(
                        color = Color(0xFF1E293B),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(10.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(
                                    text = report.category.name.replace("_", " "),
                                    color = Color(0xFF38BDF8),
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 13.sp
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
                                    fontSize = 12.sp,
                                    modifier = Modifier.padding(top = 4.dp)
                                )
                            }
                            Text(
                                text = "Status: ${report.syncStatus} | Token: ${report.anonymousReporterToken.take(8)}...",
                                color = Color(0xFF64748B),
                                fontSize = 10.sp,
                                modifier = Modifier.padding(top = 4.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}
