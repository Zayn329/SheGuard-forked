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

enum class PatternState {
    INACTIVE,
    PATTERN_CANDIDATE,
    TRUST_EVALUATING,
    PATTERN_EMERGING,
    ALERT_ACTIVE,
    RESOLVED,
    ARCHIVED
}

data class SpatioTemporalPattern(
    val patternId: UUID = UUID.randomUUID(),
    val centerLatitude: Double,
    val centerLongitude: Double,
    val radiusMeters: Double,
    val category: ReportCategory,
    val firstReportedAt: Long,
    val lastReportedAt: Long,
    val reportCount: Int,
    val contributingReportIds: List<UUID> = emptyList(),
    val trustScore: Float = 0.0f,
    val state: PatternState = PatternState.PATTERN_CANDIDATE
)

data class PatternEngineConfig(
    val spatialThresholdMeters: Double = 500.0,
    val temporalWindowMillis: Long = 2 * 60 * 60 * 1000L, // 2 hours
    val minReportsForCandidate: Int = 2,
    val enforceCategorySeparation: Boolean = true
)
