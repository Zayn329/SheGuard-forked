package org.sahara.app.sync

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.sahara.app.ui.SaharaApiClient
import org.sahara.core.domain.models.SyncStatus
import org.sahara.core.domain.repository.MicroReportRepository

class SheGuardSyncManager(
    private val microReportRepository: MicroReportRepository
) {
    suspend fun syncPendingReports(): Int = withContext(Dispatchers.IO) {
        val unsynced = microReportRepository.getUnsyncedReports()
        if (unsynced.isEmpty()) return@withContext 0

        var syncedCount = 0
        for (report in unsynced) {
            try {
                val eventId = "evt_${report.reportId}"
                val jsonBody = """
                    {
                        "events": [
                            {
                                "event_id": "$eventId",
                                "incident_id": "${report.reportId}",
                                "event_type": "MICRO_REPORT_CREATED",
                                "occurred_at": ${report.timestamp / 1000},
                                "payload": {
                                    "category": "${report.category.name}",
                                    "latitude": ${report.latitude ?: "null"},
                                    "longitude": ${report.longitude ?: "null"},
                                    "approximate_area": "${report.approximateArea.replace("\"", "\\\"")}"
                                }
                            }
                        ]
                    }
                """.trimIndent()

                val token = SaharaApiClient.savedAccessToken ?: "bearer_demo_token"
                SaharaApiClient.postJson("/api/v1/sync/batch", jsonBody, bearerToken = token)
                microReportRepository.updateSyncStatus(report.reportId, SyncStatus.SYNCED)
                syncedCount++
            } catch (e: Exception) {
                android.util.Log.w("SheGuardSync", "Sync failed for report ${report.reportId}: ${e.message}")
            }
        }
        syncedCount
    }
}
