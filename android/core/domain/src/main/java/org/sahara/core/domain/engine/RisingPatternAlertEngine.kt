package org.sahara.core.domain.engine

import org.sahara.core.domain.models.AlertEngineConfig
import org.sahara.core.domain.models.PatternState
import org.sahara.core.domain.models.ReportCategory
import org.sahara.core.domain.models.RisingPatternAlert
import org.sahara.core.domain.models.SpatioTemporalPattern
import org.sahara.core.domain.models.TrustLevel
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID
import kotlin.math.roundToInt

/**
 * Rising Pattern Alert Engine — Phase D of the SheGuard pipeline.
 *
 * Consumes a [SpatioTemporalPattern] whose state is [PatternState.PATTERN_EMERGING]
 * and produces a [RisingPatternAlert].
 *
 * INVARIANTS (from architecture.yaml and AGENTS.md):
 * - Returns null for any pattern NOT in state PATTERN_EMERGING.
 * - Does NOT re-evaluate trust. Trust was already evaluated by TrustAndAntiGamingEvaluator.
 * - Does NOT accept raw report count as sufficient evidence (trust gating already done).
 * - Alert ID is deterministically derived from pattern ID (idempotent).
 * - approximateLocation uses only coarse centroid — never sub-50m precision.
 * - Disclaimer field is fixed and must not be modified.
 * - No network, no LLM, no external calls — purely deterministic, on-device.
 */
class RisingPatternAlertEngine(
    private val config: AlertEngineConfig = AlertEngineConfig()
) {
    companion object {
        private const val DISCLAIMER =
            "EARLY WARNING PATTERN ALERT. NOT A GUARANTEED EMERGENCY RESPONSE."
    }

    /**
     * Attempt to generate a [RisingPatternAlert] for the given [pattern].
     *
     * Returns null if:
     * - The pattern state is not [PatternState.PATTERN_EMERGING].
     * - The pattern's trust score is below [AlertEngineConfig.minTrustScoreForAlert].
     *
     * Rule A: Trust gating happens inside TrustAndAntiGamingEvaluator. The alert engine
     * checks trustScore only to guard against persisted patterns with stale scores that
     * no longer meet the minimum threshold after a config change.
     */
    fun generateAlert(pattern: SpatioTemporalPattern): RisingPatternAlert? {
        // Gate 1: State must be PATTERN_EMERGING (trust already evaluated upstream)
        if (pattern.state != PatternState.PATTERN_EMERGING) return null

        // Gate 2: Trust score floor (guards against config-drift edge cases)
        if (pattern.trustScore < config.minTrustScoreForAlert) return null

        val alertId = deterministicAlertId(pattern.patternId)
        val approximateLocation = buildApproximateLocation(pattern)
        val timeWindow = buildTimeWindow(pattern.firstReportedAt, pattern.lastReportedAt)
        val trustLevel = TrustLevel.fromScore(pattern.trustScore)

        return RisingPatternAlert(
            alertId = alertId,
            patternId = pattern.patternId,
            category = pattern.category,
            approximateLocation = approximateLocation,
            timeWindow = timeWindow,
            trustLevel = trustLevel,
            trustScore = pattern.trustScore,
            createdAt = System.currentTimeMillis(),
            disclaimer = DISCLAIMER
        )
    }

    /**
     * Batch variant — filters and maps a list of patterns, returning only alerts that
     * pass both gates. Order matches input order.
     */
    fun generateAlerts(patterns: List<SpatioTemporalPattern>): List<RisingPatternAlert> {
        return patterns.mapNotNull { generateAlert(it) }
    }

    // ─── Private helpers ──────────────────────────────────────────────────────

    /**
     * Deterministic alert ID derived from pattern ID.
     * Guarantees idempotency: same pattern always yields the same alert ID.
     */
    private fun deterministicAlertId(patternId: UUID): UUID {
        return UUID.nameUUIDFromBytes("alert_$patternId".toByteArray(Charsets.UTF_8))
    }

    /**
     * Builds a privacy-preserving approximate location string.
     * Rounds radius to the nearest [AlertEngineConfig.locationRadiusGranularityMeters],
     * and truncates lat/lng to 2 decimal places (~1 km precision at equator).
     * This deliberately avoids exposing exact reporter locations.
     */
    private fun buildApproximateLocation(pattern: SpatioTemporalPattern): String {
        // Truncate to 2 decimal places for coarse location (~1 km resolution)
        val coarseLat = "%.2f".format(pattern.centerLatitude)
        val coarseLng = "%.2f".format(pattern.centerLongitude)
        val roundedRadius = (pattern.radiusMeters / config.locationRadiusGranularityMeters)
            .roundToInt() * config.locationRadiusGranularityMeters.toInt()
        return "approx. ${coarseLat}°N ${coarseLng}°E within ~${roundedRadius}m"
    }

    /**
     * Builds a human-readable time window string from first and last report timestamps.
     */
    private fun buildTimeWindow(firstReportedAt: Long, lastReportedAt: Long): String {
        val fmt = SimpleDateFormat("HH:mm", Locale.getDefault())
        return "${fmt.format(Date(firstReportedAt))}–${fmt.format(Date(lastReportedAt))}"
    }

    /**
     * Returns a display label for a category, used in alert messages.
     */
    fun categoryDisplayName(category: ReportCategory): String {
        return category.name.replace("_", " ").lowercase()
            .replaceFirstChar { it.uppercase() }
    }
}
