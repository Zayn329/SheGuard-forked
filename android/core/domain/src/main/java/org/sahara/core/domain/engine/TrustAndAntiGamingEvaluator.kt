package org.sahara.core.domain.engine

import org.sahara.core.domain.models.MicroReport
import org.sahara.core.domain.models.PatternState
import org.sahara.core.domain.models.SpatioTemporalPattern
import org.sahara.core.domain.models.TrustEvaluationConfig
import org.sahara.core.domain.models.TrustEvaluationResult
import java.util.UUID
import kotlin.math.abs

class TrustAndAntiGamingEvaluator(
    val config: TrustEvaluationConfig = TrustEvaluationConfig()
) {

    /**
     * Evaluates trust and anti-gaming signals for a candidate pattern and its contributing reports.
     * Guaranteed Input-Order Independent: reports are deterministically sorted by timestamp ascending,
     * then reportId ascending.
     * Guaranteed State Boundary: resulting state can only be PATTERN_EMERGING or PATTERN_CANDIDATE.
     * NEVER promotes to ALERT_ACTIVE.
     */
    fun evaluate(
        pattern: SpatioTemporalPattern,
        allReports: List<MicroReport>
    ): TrustEvaluationResult {
        // Find contributing reports for this pattern
        val matchingReports = if (pattern.contributingReportIds.isNotEmpty()) {
            val idSet = pattern.contributingReportIds.toSet()
            allReports.filter { it.reportId in idSet }
        } else {
            allReports.filter { it.category == pattern.category }
        }

        // Deterministic sorting to guarantee input-order independence
        val sortedReports = matchingReports.sortedWith(
            compareBy<MicroReport> { it.timestamp }.thenBy { it.reportId.toString() }
        )

        val totalReportCount = sortedReports.size
        val reasons = mutableListOf<String>()

        // Safe handling for empty or single-report candidates
        if (totalReportCount < 2) {
            reasons.add("Insufficient reports for trust evaluation (found $totalReportCount, requires at least 2)")
            return TrustEvaluationResult(
                patternId = pattern.patternId,
                trustScore = 0.0f,
                isEmerging = false,
                resultingState = PatternState.PATTERN_CANDIDATE,
                uniqueReporterCount = sortedReports.map { it.anonymousReporterToken }.toSet().size,
                totalReportCount = totalReportCount,
                duplicateReportCount = 0,
                floodedReportCount = 0,
                diversityScore = 0.0f,
                temporalIndependenceScore = 0.0f,
                spatialConsistencyScore = 0.0f,
                duplicatePenalty = 0.0f,
                floodPenalty = 0.0f,
                reasons = reasons
            )
        }

        // Step 1: Duplicate Detection
        // Detect identical or near-identical submissions from the same reporter token
        val duplicateReportIds = mutableSetOf<UUID>()
        for (i in 0 until sortedReports.size) {
            val base = sortedReports[i]
            if (base.reportId in duplicateReportIds) continue

            for (j in (i + 1) until sortedReports.size) {
                val candidate = sortedReports[j]
                if (candidate.reportId in duplicateReportIds) continue

                if (candidate.anonymousReporterToken == base.anonymousReporterToken &&
                    candidate.category == base.category
                ) {
                    val timeDiff = abs(candidate.timestamp - base.timestamp)
                    if (timeDiff <= config.duplicateTimeWindowMillis) {
                        val isSpatialDuplicate = if (base.latitude != null && base.longitude != null &&
                            candidate.latitude != null && candidate.longitude != null
                        ) {
                            val dist = SpatioTemporalPatternEngine.calculateHaversineDistanceMeters(
                                base.latitude, base.longitude, candidate.latitude, candidate.longitude
                            )
                            dist <= config.duplicateDistanceMeters
                        } else {
                            // Missing coordinates fallback: match on approximateArea
                            base.approximateArea.equals(candidate.approximateArea, ignoreCase = true)
                        }

                        if (isSpatialDuplicate) {
                            duplicateReportIds.add(candidate.reportId)
                        }
                    }
                }
            }
        }

        val nonDuplicateReports = sortedReports.filter { it.reportId !in duplicateReportIds }

        // Step 2: Rate / Flood Resistance
        // Detect rapid burst submissions from the same reporter token beyond maxBurstReportsPerReporter
        val floodedReportIds = mutableSetOf<UUID>()
        val reportsByReporter = nonDuplicateReports.groupBy { it.anonymousReporterToken }

        for ((_, reporterReports) in reportsByReporter) {
            if (reporterReports.size > config.maxBurstReportsPerReporter) {
                // Check within flood window
                for (i in reporterReports.indices) {
                    val windowStart = reporterReports[i].timestamp
                    val burstInWindow = reporterReports.filter {
                        it.timestamp >= windowStart && it.timestamp <= windowStart + config.floodWindowMillis
                    }
                    if (burstInWindow.size > config.maxBurstReportsPerReporter) {
                        // Mark excess reports beyond the allowed burst limit as flooded
                        burstInWindow.drop(config.maxBurstReportsPerReporter).forEach {
                            floodedReportIds.add(it.reportId)
                        }
                    }
                }
            }
        }

        val validIndependentReports = nonDuplicateReports.filter { it.reportId !in floodedReportIds }
        val uniqueReporterTokens = validIndependentReports.map { it.anonymousReporterToken }.toSet()
        val uniqueReporterCount = uniqueReporterTokens.size

        // Step 3: Reporter Diversity Evaluation
        // Invariant: Raw report count alone MUST NOT determine trust.
        // If unique reporters < minUniqueReportersForEmerging, diversity is severely constrained.
        val diversityScore: Float = when {
            validIndependentReports.isEmpty() -> 0.0f
            uniqueReporterCount < config.minUniqueReportersForEmerging -> {
                // Only 1 unique reporter: diversity score capped at 0.15f
                0.15f
            }
            else -> {
                // 2+ unique reporters: scale according to ratio of unique to total valid reports
                val diversityRatio = uniqueReporterCount.toFloat() / validIndependentReports.size.toFloat()
                // Scaled from 0.6f up to 1.0f for higher independence
                (0.6f + 0.4f * diversityRatio).coerceIn(0.0f, 1.0f)
            }
        }

        // Step 4: Temporal Independence Evaluation
        val temporalIndependenceScore: Float = if (validIndependentReports.size < 2) {
            0.0f
        } else {
            val minTime = validIndependentReports.minOf { it.timestamp }
            val maxTime = validIndependentReports.maxOf { it.timestamp }
            val timeSpan = maxTime - minTime
            if (timeSpan < 10000L) {
                // Less than 10 seconds separation: almost simultaneous, negligible independence
                0.10f
            } else {
                val spanRatio = (timeSpan.toFloat() / config.temporalIndependenceWindowMillis.toFloat()).coerceIn(0.0f, 1.0f)
                (0.30f + 0.70f * spanRatio).coerceIn(0.0f, 1.0f)
            }
        }

        // Step 5: Spatial Consistency Evaluation
        // Reports without coordinates are handled safely without crashing or fabricating coordinates.
        val spatialConsistencyScore: Float = if (validIndependentReports.isEmpty()) {
            0.0f
        } else {
            val scores = validIndependentReports.map { report ->
                if (report.latitude != null && report.longitude != null) {
                    val dist = SpatioTemporalPatternEngine.calculateHaversineDistanceMeters(
                        pattern.centerLatitude, pattern.centerLongitude,
                        report.latitude, report.longitude
                    )
                    val effectiveRadius = pattern.radiusMeters.coerceAtLeast(100.0)
                    if (dist <= effectiveRadius) {
                        (1.0 - 0.5 * (dist / effectiveRadius)).toFloat().coerceIn(0.5f, 1.0f)
                    } else {
                        (1.0 - (dist / effectiveRadius)).toFloat().coerceIn(0.0f, 0.5f)
                    }
                } else {
                    // Safe neutral score for missing location
                    0.5f
                }
            }
            scores.average().toFloat().coerceIn(0.0f, 1.0f)
        }

        // Step 6: Volume Contribution (Modest, capped weight - volume alone never suffices)
        val volumeScore: Float = if (validIndependentReports.isEmpty()) {
            0.0f
        } else {
            (validIndependentReports.size.toFloat() / 5.0f).coerceIn(0.0f, 1.0f)
        }

        // Step 7: Penalties
        val duplicatePenalty = (duplicateReportIds.size * config.duplicatePenaltyPerDuplicate).coerceIn(0.0f, 0.5f)
        val floodPenalty = (floodedReportIds.size * config.floodPenalty).coerceIn(0.0f, 0.5f)

        // Step 8: Final Normalized Trust Score
        val weightedScore = (config.diversityWeight * diversityScore) +
                (config.temporalWeight * temporalIndependenceScore) +
                (config.spatialWeight * spatialConsistencyScore) +
                (config.volumeWeight * volumeScore)

        val trustScore = (weightedScore - duplicatePenalty - floodPenalty).coerceIn(0.0f, 1.0f)

        // Step 9: Invariant Check and State Determination
        // Invariant: Must meet minTrustScore AND minUniqueReporters
        val passesReporterDiversity = uniqueReporterCount >= config.minUniqueReportersForEmerging
        val passesTrustThreshold = trustScore >= config.minTrustScoreForEmerging

        val isEmerging = passesReporterDiversity && passesTrustThreshold
        val resultingState = if (isEmerging) {
            PatternState.PATTERN_EMERGING
        } else {
            PatternState.PATTERN_CANDIDATE
        }

        // Audit reasons
        reasons.add("Reporters: $uniqueReporterCount unique token(s) (diversity score: ${String.format("%.2f", diversityScore)})")
        reasons.add("Temporal independence: score ${String.format("%.2f", temporalIndependenceScore)}")
        reasons.add("Spatial consistency: score ${String.format("%.2f", spatialConsistencyScore)}")
        if (duplicateReportIds.isNotEmpty()) {
            reasons.add("${duplicateReportIds.size} duplicate report(s) filtered (penalty: ${String.format("%.2f", duplicatePenalty)})")
        }
        if (floodedReportIds.isNotEmpty()) {
            reasons.add("${floodedReportIds.size} flooded report(s) rate-limited (penalty: ${String.format("%.2f", floodPenalty)})")
        }
        if (isEmerging) {
            reasons.add("State transition: PATTERN_EMERGING (trustScore ${String.format("%.2f", trustScore)} >= ${config.minTrustScoreForEmerging})")
        } else {
            if (!passesReporterDiversity) {
                reasons.add("Remains PATTERN_CANDIDATE: insufficient reporter diversity ($uniqueReporterCount < ${config.minUniqueReportersForEmerging})")
            } else {
                reasons.add("Remains PATTERN_CANDIDATE: trust score ${String.format("%.2f", trustScore)} below threshold ${config.minTrustScoreForEmerging}")
            }
        }

        return TrustEvaluationResult(
            patternId = pattern.patternId,
            trustScore = trustScore,
            isEmerging = isEmerging,
            resultingState = resultingState,
            uniqueReporterCount = uniqueReporterCount,
            totalReportCount = totalReportCount,
            duplicateReportCount = duplicateReportIds.size,
            floodedReportCount = floodedReportIds.size,
            diversityScore = diversityScore,
            temporalIndependenceScore = temporalIndependenceScore,
            spatialConsistencyScore = spatialConsistencyScore,
            duplicatePenalty = duplicatePenalty,
            floodPenalty = floodPenalty,
            reasons = reasons
        )
    }

    /**
     * Evaluates the pattern and returns an updated SpatioTemporalPattern with the resulting
     * trustScore and state (PATTERN_EMERGING or PATTERN_CANDIDATE).
     */
    fun evaluatePattern(
        pattern: SpatioTemporalPattern,
        allReports: List<MicroReport>
    ): SpatioTemporalPattern {
        val result = evaluate(pattern, allReports)
        return pattern.copy(
            trustScore = result.trustScore,
            state = result.resultingState
        )
    }
}
