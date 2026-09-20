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

data class TrustEvaluationConfig(
    val minUniqueReportersForEmerging: Int = 2,
    val minTrustScoreForEmerging: Float = 0.6f,
    val duplicateTimeWindowMillis: Long = 5 * 60 * 1000L, // 5 minutes
    val duplicateDistanceMeters: Double = 50.0,
    val temporalIndependenceWindowMillis: Long = 10 * 60 * 1000L, // 10 minutes
    val floodWindowMillis: Long = 10 * 60 * 1000L, // 10 minutes
    val maxBurstReportsPerReporter: Int = 2,
    val diversityWeight: Float = 0.40f,
    val temporalWeight: Float = 0.25f,
    val spatialWeight: Float = 0.20f,
    val volumeWeight: Float = 0.15f,
    val duplicatePenaltyPerDuplicate: Float = 0.15f,
    val floodPenalty: Float = 0.20f
)

data class TrustEvaluationResult(
    val patternId: UUID,
    val trustScore: Float,
    val isEmerging: Boolean,
    val resultingState: PatternState,
    val uniqueReporterCount: Int,
    val totalReportCount: Int,
    val duplicateReportCount: Int,
    val floodedReportCount: Int,
    val diversityScore: Float,
    val temporalIndependenceScore: Float,
    val spatialConsistencyScore: Float,
    val duplicatePenalty: Float,
    val floodPenalty: Float,
    val reasons: List<String> = emptyList()
)

// ─── Phase D: Rising-Pattern Alert Engine ────────────────────────────────────

/**
 * Trust level derived from a pattern's trust score.
 * LOW  < 0.60 | MEDIUM 0.60–0.79 | HIGH ≥ 0.80
 */
enum class TrustLevel {
    LOW, MEDIUM, HIGH;

    companion object {
        fun fromScore(score: Float): TrustLevel = when {
            score >= 0.80f -> HIGH
            score >= 0.60f -> MEDIUM
            else -> LOW
        }
    }
}

/**
 * An actionable early-warning pattern alert.
 * Produced only for PATTERN_EMERGING patterns that pass trust evaluation.
 *
 * IMPORTANT: This alert is NOT a guaranteed emergency response.
 */
data class RisingPatternAlert(
    val alertId: java.util.UUID,
    val patternId: java.util.UUID,
    val category: ReportCategory,
    /** Coarse centroid description — never raw high-precision coordinates. */
    val approximateLocation: String,
    val timeWindow: String,
    val trustLevel: TrustLevel,
    val trustScore: Float,
    val createdAt: Long = System.currentTimeMillis(),
    /** Fixed disclaimer — must not be altered. */
    val disclaimer: String = "EARLY WARNING PATTERN ALERT. NOT A GUARANTEED EMERGENCY RESPONSE.",
    val isRelayed: Boolean = false
)

data class AlertEngineConfig(
    /** Minimum trust score required to generate an alert (must match TrustLevel.MEDIUM lower bound). */
    val minTrustScoreForAlert: Float = 0.60f,
    /** Location precision for alert description — radius rounded to this many metres. */
    val locationRadiusGranularityMeters: Double = 100.0
)
