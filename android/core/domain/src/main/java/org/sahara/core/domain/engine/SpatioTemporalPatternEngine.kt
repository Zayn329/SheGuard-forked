package org.sahara.core.domain.engine

import org.sahara.core.domain.models.MicroReport
import org.sahara.core.domain.models.PatternEngineConfig
import org.sahara.core.domain.models.PatternState
import org.sahara.core.domain.models.SpatioTemporalPattern
import java.util.UUID
import kotlin.math.*

class SpatioTemporalPatternEngine(
    val config: PatternEngineConfig = PatternEngineConfig()
) {

    /**
     * Deterministically analyzes a list of MicroReports and returns candidate patterns.
     * Guaranteed Input-Order Independent: reports are sorted by timestamp and ID before clustering.
     * State Isolation: Output patterns are STRICTLY assigned `PatternState.PATTERN_CANDIDATE` and `trustScore = 0.0f`.
     */
    fun detectCandidatePatterns(reports: List<MicroReport>): List<SpatioTemporalPattern> {
        // Filter out reports with missing location coordinates
        val validReports = reports.filter { it.latitude != null && it.longitude != null }
        if (validReports.isEmpty()) return emptyList()

        // Group by category if category separation is enabled
        val groupedReports = if (config.enforceCategorySeparation) {
            validReports.groupBy { it.category }
        } else {
            mapOf(validReports.first().category to validReports)
        }

        val candidatePatterns = mutableListOf<SpatioTemporalPattern>()

        for ((category, categoryReports) in groupedReports) {
            // Sort reports deterministically by timestamp ascending, then reportId string to guarantee order independence
            val sortedReports = categoryReports.sortedWith(
                compareBy<MicroReport> { it.timestamp }.thenBy { it.reportId.toString() }
            )
            val visited = mutableSetOf<UUID>()

            for (i in sortedReports.indices) {
                val baseReport = sortedReports[i]
                if (baseReport.reportId in visited) continue

                val clusterReports = mutableListOf(baseReport)

                for (j in (i + 1) until sortedReports.size) {
                    val candidateReport = sortedReports[j]
                    if (candidateReport.reportId in visited) continue

                    // Check temporal proximity
                    val timeDiff = abs(candidateReport.timestamp - baseReport.timestamp)
                    if (timeDiff > config.temporalWindowMillis) continue

                    // Check spatial proximity using Haversine distance
                    val distanceMeters = calculateHaversineDistanceMeters(
                        baseReport.latitude!!, baseReport.longitude!!,
                        candidateReport.latitude!!, candidateReport.longitude!!
                    )

                    if (distanceMeters <= config.spatialThresholdMeters) {
                        clusterReports.add(candidateReport)
                    }
                }

                // If cluster meets minimum reports threshold, form a candidate pattern
                if (clusterReports.size >= config.minReportsForCandidate) {
                    clusterReports.forEach { visited.add(it.reportId) }

                    val sortedContributingIds = clusterReports.map { it.reportId }.sortedBy { it.toString() }
                    val avgLat = clusterReports.map { it.latitude!! }.average()
                    val avgLng = clusterReports.map { it.longitude!! }.average()
                    val firstObserved = clusterReports.minOf { it.timestamp }
                    val lastObserved = clusterReports.maxOf { it.timestamp }

                    // Compute maximum distance from centroid for radius
                    val maxRadiusMeters = clusterReports.maxOf {
                        calculateHaversineDistanceMeters(avgLat, avgLng, it.latitude!!, it.longitude!!)
                    }.coerceAtLeast(100.0) // Minimum 100m bounding radius

                    val pattern = SpatioTemporalPattern(
                        patternId = UUID.nameUUIDFromBytes("pattern_${category.name}_${sortedContributingIds.joinToString("_")}".toByteArray()),
                        centerLatitude = avgLat,
                        centerLongitude = avgLng,
                        radiusMeters = maxRadiusMeters,
                        category = category,
                        firstReportedAt = firstObserved,
                        lastReportedAt = lastObserved,
                        reportCount = clusterReports.size,
                        contributingReportIds = sortedContributingIds,
                        trustScore = 0.0f, // Phase B does NOT evaluate trust
                        state = PatternState.PATTERN_CANDIDATE // Strict state isolation
                    )
                    candidatePatterns.add(pattern)
                }
            }
        }

        // Sort candidate patterns deterministically by category and firstReportedAt
        return candidatePatterns.sortedWith(
            compareBy<SpatioTemporalPattern> { it.category.name }.thenBy { it.firstReportedAt }
        )
    }

    companion object {
        /**
         * Calculates the great-circle distance between two points in meters using Haversine formula.
         */
        fun calculateHaversineDistanceMeters(
            lat1: Double, lon1: Double,
            lat2: Double, lon2: Double
        ): Double {
            val earthRadiusMeters = 6371000.0
            val dLat = Math.toRadians(lat2 - lat1)
            val dLon = Math.toRadians(lon2 - lon1)
            val a = sin(dLat / 2).pow(2) +
                    cos(Math.toRadians(lat1)) * cos(Math.toRadians(lat2)) *
                    sin(dLon / 2).pow(2)
            val c = 2 * atan2(sqrt(a), sqrt(1 - a))
            return earthRadiusMeters * c
        }
    }
}
