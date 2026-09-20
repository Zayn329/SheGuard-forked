package org.sahara.core.domain.engine

import org.junit.Assert.*
import org.junit.Test
import org.sahara.core.domain.models.*
import java.util.UUID

class SpatioTemporalPatternEngineTest {

    private val engine = SpatioTemporalPatternEngine(
        PatternEngineConfig(
            spatialThresholdMeters = 500.0,
            temporalWindowMillis = 2 * 60 * 60 * 1000L, // 2 hours
            minReportsForCandidate = 2,
            enforceCategorySeparation = true
        )
    )

    @Test
    fun testMultipleReportsInSameAreaAndTimeWindowFormCandidatePattern() {
        val now = System.currentTimeMillis()
        val report1 = MicroReport(
            anonymousReporterToken = "anon_1",
            category = ReportCategory.POOR_LIGHTING,
            latitude = 19.0760,
            longitude = 72.8777,
            timestamp = now
        )
        val report2 = MicroReport(
            anonymousReporterToken = "anon_2",
            category = ReportCategory.POOR_LIGHTING,
            latitude = 19.0762, // approx 25 meters away
            longitude = 72.8779,
            timestamp = now + 10 * 60 * 1000L // 10 minutes later
        )

        val patterns = engine.detectCandidatePatterns(listOf(report1, report2))

        assertEquals(1, patterns.size)
        val pattern = patterns[0]
        assertEquals(ReportCategory.POOR_LIGHTING, pattern.category)
        assertEquals(2, pattern.reportCount)
        assertEquals(PatternState.PATTERN_CANDIDATE, pattern.state) // Strict State Isolation
        assertEquals(0.0f, pattern.trustScore, 0.0f) // Phase B does NOT evaluate trust
        assertTrue(pattern.contributingReportIds.contains(report1.reportId))
        assertTrue(pattern.contributingReportIds.contains(report2.reportId))
    }

    @Test
    fun testInputOrderIndependence() {
        val now = System.currentTimeMillis()
        val r1 = MicroReport(reportId = UUID.fromString("00000000-0000-0000-0000-000000000001"), anonymousReporterToken = "anon_1", category = ReportCategory.HARASSMENT, latitude = 19.0760, longitude = 72.8777, timestamp = now)
        val r2 = MicroReport(reportId = UUID.fromString("00000000-0000-0000-0000-000000000002"), anonymousReporterToken = "anon_2", category = ReportCategory.HARASSMENT, latitude = 19.0762, longitude = 72.8778, timestamp = now + 5000)
        val r3 = MicroReport(reportId = UUID.fromString("00000000-0000-0000-0000-000000000003"), anonymousReporterToken = "anon_3", category = ReportCategory.HARASSMENT, latitude = 19.0761, longitude = 72.8779, timestamp = now + 10000)

        val patternsOrder1 = engine.detectCandidatePatterns(listOf(r1, r2, r3))
        val patternsOrder2 = engine.detectCandidatePatterns(listOf(r3, r1, r2))
        val patternsOrder3 = engine.detectCandidatePatterns(listOf(r2, r3, r1))

        assertEquals(1, patternsOrder1.size)
        assertEquals(patternsOrder1.size, patternsOrder2.size)
        assertEquals(patternsOrder1.size, patternsOrder3.size)

        val p1 = patternsOrder1[0]
        val p2 = patternsOrder2[0]
        val p3 = patternsOrder3[0]

        assertEquals(p1.patternId, p2.patternId)
        assertEquals(p1.patternId, p3.patternId)
        assertEquals(p1.contributingReportIds, p2.contributingReportIds)
        assertEquals(p1.contributingReportIds, p3.contributingReportIds)
        assertEquals(p1.centerLatitude, p2.centerLatitude, 0.000001)
        assertEquals(p1.centerLongitude, p3.centerLongitude, 0.000001)
        assertEquals(p1.reportCount, p2.reportCount)
        assertEquals(p1.state, PatternState.PATTERN_CANDIDATE)
        assertEquals(0.0f, p1.trustScore, 0.0f)
    }

    @Test
    fun testTimestampOrderIndependency() {
        val t1 = 1000000L
        val t2 = 1030000L
        val t3 = 1100000L

        // Inserted out of timestamp order
        val r3 = MicroReport(reportId = UUID.randomUUID(), anonymousReporterToken = "a3", category = ReportCategory.POOR_LIGHTING, latitude = 19.0760, longitude = 72.8777, timestamp = t3)
        val r1 = MicroReport(reportId = UUID.randomUUID(), anonymousReporterToken = "a1", category = ReportCategory.POOR_LIGHTING, latitude = 19.0761, longitude = 72.8778, timestamp = t1)
        val r2 = MicroReport(reportId = UUID.randomUUID(), anonymousReporterToken = "a2", category = ReportCategory.POOR_LIGHTING, latitude = 19.0762, longitude = 72.8779, timestamp = t2)

        val patterns = engine.detectCandidatePatterns(listOf(r3, r1, r2))

        assertEquals(1, patterns.size)
        val p = patterns[0]
        assertEquals(t1, p.firstReportedAt)
        assertEquals(t3, p.lastReportedAt)
    }

    @Test
    fun testDetectionIdempotency() {
        val now = System.currentTimeMillis()
        val reports = listOf(
            MicroReport(anonymousReporterToken = "a", category = ReportCategory.POOR_LIGHTING, latitude = 19.0760, longitude = 72.8777, timestamp = now),
            MicroReport(anonymousReporterToken = "b", category = ReportCategory.POOR_LIGHTING, latitude = 19.0761, longitude = 72.8778, timestamp = now + 1000)
        )

        val run1 = engine.detectCandidatePatterns(reports)
        val run2 = engine.detectCandidatePatterns(reports)

        assertEquals(1, run1.size)
        assertEquals(run1.size, run2.size)
        assertEquals(run1[0].patternId, run2[0].patternId)
    }

    @Test
    fun testStateIsolationStrictlyPatternCandidate() {
        val now = System.currentTimeMillis()
        val reports = listOf(
            MicroReport(anonymousReporterToken = "a", category = ReportCategory.POOR_LIGHTING, latitude = 19.0760, longitude = 72.8777, timestamp = now),
            MicroReport(anonymousReporterToken = "b", category = ReportCategory.POOR_LIGHTING, latitude = 19.0761, longitude = 72.8778, timestamp = now + 1000),
            MicroReport(anonymousReporterToken = "c", category = ReportCategory.POOR_LIGHTING, latitude = 19.0762, longitude = 72.8779, timestamp = now + 2000)
        )

        val patterns = engine.detectCandidatePatterns(reports)

        assertEquals(1, patterns.size)
        val p = patterns[0]
        assertEquals(PatternState.PATTERN_CANDIDATE, p.state)
        assertNotEquals(PatternState.TRUST_EVALUATING, p.state)
        assertNotEquals(PatternState.PATTERN_EMERGING, p.state)
        assertNotEquals(PatternState.ALERT_ACTIVE, p.state)
        assertEquals(0.0f, p.trustScore, 0.0f)
    }

    @Test
    fun testReportsOutsideSpatialBoundaryDoNotCluster() {
        val now = System.currentTimeMillis()
        val report1 = MicroReport(anonymousReporterToken = "anon_1", category = ReportCategory.HARASSMENT, latitude = 19.0760, longitude = 72.8777, timestamp = now)
        val report2 = MicroReport(anonymousReporterToken = "anon_2", category = ReportCategory.HARASSMENT, latitude = 18.5204, longitude = 73.8567, timestamp = now + 10 * 60 * 1000L)

        val patterns = engine.detectCandidatePatterns(listOf(report1, report2))
        assertTrue("Reports outside 500m boundary must not cluster", patterns.isEmpty())
    }

    @Test
    fun testReportsOutsideTemporalBoundaryDoNotCluster() {
        val now = System.currentTimeMillis()
        val report1 = MicroReport(anonymousReporterToken = "anon_1", category = ReportCategory.FEELING_FOLLOWED, latitude = 19.0760, longitude = 72.8777, timestamp = now)
        val report2 = MicroReport(anonymousReporterToken = "anon_2", category = ReportCategory.FEELING_FOLLOWED, latitude = 19.0761, longitude = 72.8778, timestamp = now + (3 * 60 * 60 * 1000L))

        val patterns = engine.detectCandidatePatterns(listOf(report1, report2))
        assertTrue("Reports outside 2-hour window must not cluster", patterns.isEmpty())
    }

    @Test
    fun testDifferentCategoriesDoNotClusterWhenSeparationEnforced() {
        val now = System.currentTimeMillis()
        val report1 = MicroReport(anonymousReporterToken = "anon_1", category = ReportCategory.POOR_LIGHTING, latitude = 19.0760, longitude = 72.8777, timestamp = now)
        val report2 = MicroReport(anonymousReporterToken = "anon_2", category = ReportCategory.HARASSMENT, latitude = 19.0761, longitude = 72.8778, timestamp = now + 5 * 60 * 1000L)

        val patterns = engine.detectCandidatePatterns(listOf(report1, report2))
        assertTrue("Different categories should not cluster when separation is enforced", patterns.isEmpty())
    }

    @Test
    fun testReportsWithMissingLocationToleratedWithoutCrashing() {
        val now = System.currentTimeMillis()
        val report1 = MicroReport(anonymousReporterToken = "anon_1", category = ReportCategory.UNSAFE_GATHERING, latitude = null, longitude = null, timestamp = now)
        val report2 = MicroReport(anonymousReporterToken = "anon_2", category = ReportCategory.UNSAFE_GATHERING, latitude = 19.0760, longitude = 72.8777, timestamp = now)

        val patterns = engine.detectCandidatePatterns(listOf(report1, report2))
        assertTrue("Location-less reports must be ignored safely without crashing", patterns.isEmpty())
    }

    @Test
    fun testSingleReportDoesNotFormCandidatePattern() {
        val report = MicroReport(anonymousReporterToken = "anon_1", category = ReportCategory.SUSPICIOUS_ACTIVITY, latitude = 19.0760, longitude = 72.8777, timestamp = System.currentTimeMillis())
        val patterns = engine.detectCandidatePatterns(listOf(report))
        assertTrue("Single report must not form candidate pattern", patterns.isEmpty())
    }

    @Test
    fun testHaversineDistanceCalculation() {
        val distanceMeters = SpatioTemporalPatternEngine.calculateHaversineDistanceMeters(18.9696, 72.8193, 18.9220, 72.8347)
        assertTrue(distanceMeters in 5000.0..6000.0)
    }
}
