package org.sahara.core.domain.models

import java.util.UUID

enum class ReportCategory {
    POOR_LIGHTING,
    HARASSMENT,
    FEELING_FOLLOWED,
    UNSAFE_GATHERING,
    SUSPICIOUS_ACTIVITY
}

enum class SyncStatus {
    LOCAL,
    MESH_QUEUED,
    SYNCED
}

data class MicroReport(
    val reportId: UUID = UUID.randomUUID(),
    val anonymousReporterToken: String,
    val category: ReportCategory,
    val latitude: Double? = null,
    val longitude: Double? = null,
    val approximateArea: String = "Unknown Area",
    val timestamp: Long = System.currentTimeMillis(),
    val contextDescription: String? = null,
    val syncStatus: SyncStatus = SyncStatus.LOCAL
)
