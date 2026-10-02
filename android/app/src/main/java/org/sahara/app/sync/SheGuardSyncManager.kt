package org.sahara.app.sync

import kotlinx.coroutines.flow.first
import org.sahara.app.ui.SaharaApiClient
import org.sahara.core.domain.models.SyncStatus
import org.sahara.core.domain.repository.MicroReportRepository

class SheGuardSyncManager(
    private val microReportRepository: MicroReportRepository
) {
    suspend fun syncPendingMicroReports(bearerToken: String? = null): Int {
        val allReports = microReportRepository.getAllReports().first()
        val pendingReports = allReports.filter { it.syncStatus == SyncStatus.LOCAL || it.syncStatus == SyncStatus.MESH_QUEUED }
        if (pendingReports.isEmpty()) return 0

        var syncedCount = 0
        for (report in pendingReports) {
            val eventId = "sync_evt_${report.reportId}"
            val occurredAt = report.timestamp / 1000
            val payloadJson = """
                {
                    "report_id": "${report.reportId}",
                    "category": "${report.category.name}",
                    "latitude": ${report.latitude ?: "null"},
                    "longitude": ${report.longitude ?: "null"},
                    "approximate_area": "${report.approximateArea}",
                    "anonymous_reporter_token": "${report.anonymousReporterToken}"
                }
            """.trimIndent().replace("\n", "")

            val batchBody = """
                {
                    "events": [
                        {
                            "event_id": "$eventId",
                            "incident_id": "${report.reportId}",
                            "event_type": "MICRO_REPORT_CREATED",
                            "occurred_at": $occurredAt,
                            "payload": $payloadJson
                        }
                    ]
                }
            """.trimIndent()

            try {
                SaharaApiClient.postJson("/api/v1/sync/batch", batchBody, bearerToken = bearerToken)
                val syncedReport = report.copy(syncStatus = SyncStatus.SYNCED)
                microReportRepository.saveReport(syncedReport)
                syncedCount++
            } catch (e: Exception) {
                android.util.Log.e("SheGuardSync", "Failed to sync report ${report.reportId}: ${e.message}")
            }
        }
        return syncedCount
    }
}
