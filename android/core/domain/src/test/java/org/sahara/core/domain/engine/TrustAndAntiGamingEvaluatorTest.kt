package org.sahara.core.domain.engine

import org.junit.Assert.*
import org.junit.Test
import org.sahara.core.domain.models.*
import java.util.UUID

class TrustAndAntiGamingEvaluatorTest {

    private val evaluator = TrustAndAntiGamingEvaluator(
        TrustEvaluationConfig(
            minUniqueReportersForEmerging = 2,
            minTrustScoreForEmerging = 0.6f,
            duplicateTimeWindowMillis = 5 * 60 * 1000L,
            duplicateDistanceMeters = 50.0,
            temporalIndependenceWindowMillis = 10 * 60 * 1000L,
            floodWindowMillis = 10 * 60 * 1000L,
            maxBurstReportsPerReporter = 2
        )
    )

    private fun createBasePattern(
        category: ReportCategory = ReportCategory.POOR_LIGHTING,
        centerLat: Double = 19.0760,
        centerLng: Double = 72.8777,
        radiusMeters: Double = 250.0,
        contributingReportIds: List<UUID> = emptyList()
    ): SpatioTemporalPattern {
        return SpatioTemporalPattern(
            patternId = UUID.randomUUID(),
            centerLatitude = centerLat,
            centerLongitude = centerLng,
            radiusMeters = radiusMeters,
            category = category,
            firstReportedAt = System.currentTimeMillis() - 60000,
            lastReportedAt = System.currentTimeMillis(),
            reportCount = contributingReportIds.size,
            contributingReportIds = contributingReportIds,
            trustScore = 0.0f,
            state = PatternState.PATTERN_CANDIDATE
        )
    }

    /**
     * Test 1 — Raw volume must not equal trust.
     * Pattern A: 10 reports from a single reporter token.
     * Pattern B: 2 reports from independent reporter tokens with temporal separation.
     * Invariant: Raw report count alone MUST NOT determine trust.
     */
    @Test
    fun testRawVolumeMustNotEqualTrust() {
        val now = System.currentTimeMillis()

        // Pattern A: 10 reports from the SAME reporter token
        val reportsA = (1..10).map { i ->
            MicroReport(
                anonymousReporterToken = "single_spammer",
                category = ReportCategory.POOR_LIGHTING,
                latitude = 19.0760,
                longitude = 72.8777,
                timestamp = now + (i * 1000L) // 1 second intervals
            )
        }
        val patternA = createBasePattern(
            contributingReportIds = reportsA.map { it.reportId }
        )

        // Pattern B: 2 reports from 2 INDEPENDENT reporter tokens with 15 min separation
        val reportB1 = MicroReport(
            anonymousReporterToken = "reporter_alice",
            category = ReportCategory.POOR_LIGHTING,
            latitude = 19.0760,
            longitude = 72.8777,
            timestamp = now
        )
        val reportB2 = MicroReport(
            anonymousReporterToken = "reporter_bob",
            category = ReportCategory.POOR_LIGHTING,
            latitude = 19.0762,
            longitude = 72.8778,
            timestamp = now + (15 * 60 * 1000L) // 15 mins later
        )
        val reportsB = listOf(reportB1, reportB2)
        val patternB = createBasePattern(
            contributingReportIds = reportsB.map { it.reportId }
        )

        val resultA = evaluator.evaluate(patternA, reportsA)
        val resultB = evaluator.evaluate(patternB, reportsB)

        // Pattern A MUST NOT be trusted / emerging simply due to having 10 reports
        assertFalse("Pattern A with single reporter must not become emerging", resultA.isEmerging)
        assertEquals(PatternState.PATTERN_CANDIDATE, resultA.resultingState)
        assertTrue("Pattern A duplicate or flood penalty must be triggered", resultA.duplicateReportCount > 0 || resultA.floodedReportCount > 0)

        // Pattern B with independent evidence reaches PATTERN_EMERGING
        assertTrue("Pattern B with independent reporters must be emerging", resultB.isEmerging)
        assertEquals(PatternState.PATTERN_EMERGING, resultB.resultingState)
        assertTrue(
            "Pattern B trust score (${resultB.trustScore}) must exceed Pattern A (${resultA.trustScore})",
            resultB.trustScore > resultA.trustScore
        )
    }

    /**
     * Test 2 — Reporter diversity.
     * Case A: {A, A, A} -> 1 unique reporter
     * Case B: {A, B, C} -> 3 unique reporters
     */
    @Test
    fun testReporterDiversity() {
        val now = System.currentTimeMillis()
        val rA1 = MicroReport(anonymousReporterToken = "token_A", category = ReportCategory.HARASSMENT, latitude = 19.0760, longitude = 72.8777, timestamp = now)
        val rA2 = MicroReport(anonymousReporterToken = "token_A", category = ReportCategory.HARASSMENT, latitude = 19.0762, longitude = 72.8779, timestamp = now + 6 * 60 * 1000L)
        val rA3 = MicroReport(anonymousReporterToken = "token_A", category = ReportCategory.HARASSMENT, latitude = 19.0761, longitude = 72.8778, timestamp = now + 12 * 60 * 1000L)
        val reportsA = listOf(rA1, rA2, rA3)
        val patternA = createBasePattern(category = ReportCategory.HARASSMENT, contributingReportIds = reportsA.map { it.reportId })

        val rB1 = MicroReport(anonymousReporterToken = "token_A", category = ReportCategory.HARASSMENT, latitude = 19.0760, longitude = 72.8777, timestamp = now)
        val rB2 = MicroReport(anonymousReporterToken = "token_B", category = ReportCategory.HARASSMENT, latitude = 19.0762, longitude = 72.8779, timestamp = now + 6 * 60 * 1000L)
        val rB3 = MicroReport(anonymousReporterToken = "token_C", category = ReportCategory.HARASSMENT, latitude = 19.0761, longitude = 72.8778, timestamp = now + 12 * 60 * 1000L)
        val reportsB = listOf(rB1, rB2, rB3)
        val patternB = createBasePattern(category = ReportCategory.HARASSMENT, contributingReportIds = reportsB.map { it.reportId })

        val resultA = evaluator.evaluate(patternA, reportsA)
        val resultB = evaluator.evaluate(patternB, reportsB)

        assertEquals(1, resultA.uniqueReporterCount)
        assertEquals(3, resultB.uniqueReporterCount)
        assertTrue(resultB.diversityScore > resultA.diversityScore)
        assertTrue(resultB.trustScore > resultA.trustScore)
        assertFalse(resultA.isEmerging)
        assertTrue(resultB.isEmerging)
    }

    /**
     * Test 3 — Duplicate flooding.
     * Exact duplicates from same reporter within duplicate window are filtered and penalized.
     */
    @Test
    fun testDuplicateFlooding() {
        val now = System.currentTimeMillis()
        val original = MicroReport(anonymousReporterToken = "rep_1", category = ReportCategory.POOR_LIGHTING, latitude = 19.0760, longitude = 72.8777, timestamp = now)
        val dup1 = MicroReport(anonymousReporterToken = "rep_1", category = ReportCategory.POOR_LIGHTING, latitude = 19.0760, longitude = 72.8777, timestamp = now + 30 * 1000L) // 30s later
        val dup2 = MicroReport(anonymousReporterToken = "rep_1", category = ReportCategory.POOR_LIGHTING, latitude = 19.0760, longitude = 72.8777, timestamp = now + 60 * 1000L) // 60s later
        val reports = listOf(original, dup1, dup2)

        val pattern = createBasePattern(contributingReportIds = reports.map { it.reportId })
        val result = evaluator.evaluate(pattern, reports)

        assertEquals(2, result.duplicateReportCount)
        assertTrue("Duplicate penalty must be greater than zero", result.duplicatePenalty > 0.0f)
        assertFalse(result.isEmerging)
        assertEquals(PatternState.PATTERN_CANDIDATE, result.resultingState)
    }

    /**
     * Test 4 — Temporal independence.
     * Reports clustered within seconds vs distributed across the temporal window.
     */
    @Test
    fun testTemporalIndependence() {
        val now = System.currentTimeMillis()

        // Near-simultaneous reports (2 seconds apart)
        val rClustered1 = MicroReport(anonymousReporterToken = "user_1", category = ReportCategory.FEELING_FOLLOWED, latitude = 19.0760, longitude = 72.8777, timestamp = now)
        val rClustered2 = MicroReport(anonymousReporterToken = "user_2", category = ReportCategory.FEELING_FOLLOWED, latitude = 19.0762, longitude = 72.8778, timestamp = now + 2000L)
        val clusteredReports = listOf(rClustered1, rClustered2)
        val clusteredPattern = createBasePattern(category = ReportCategory.FEELING_FOLLOWED, contributingReportIds = clusteredReports.map { it.reportId })

        // Well-separated reports (12 minutes apart)
        val rSpaced1 = MicroReport(anonymousReporterToken = "user_1", category = ReportCategory.FEELING_FOLLOWED, latitude = 19.0760, longitude = 72.8777, timestamp = now)
        val rSpaced2 = MicroReport(anonymousReporterToken = "user_2", category = ReportCategory.FEELING_FOLLOWED, latitude = 19.0762, longitude = 72.8778, timestamp = now + 12 * 60 * 1000L)
        val spacedReports = listOf(rSpaced1, rSpaced2)
        val spacedPattern = createBasePattern(category = ReportCategory.FEELING_FOLLOWED, contributingReportIds = spacedReports.map { it.reportId })

        val clusteredResult = evaluator.evaluate(clusteredPattern, clusteredReports)
        val spacedResult = evaluator.evaluate(spacedPattern, spacedReports)

        assertTrue(
            "Spaced reports must have higher temporal independence score than clustered reports",
            spacedResult.temporalIndependenceScore > clusteredResult.temporalIndependenceScore
        )
    }

    /**
     * Test 5 — Spatial consistency.
     * Reports consistent with candidate pattern vs far outside pattern radius.
     */
    @Test
    fun testSpatialConsistency() {
        val now = System.currentTimeMillis()

        // Consistent: within 50m of pattern center (19.0760, 72.8777)
        val rNear1 = MicroReport(anonymousReporterToken = "u1", category = ReportCategory.UNSAFE_GATHERING, latitude = 19.0761, longitude = 72.8777, timestamp = now)
        val rNear2 = MicroReport(anonymousReporterToken = "u2", category = ReportCategory.UNSAFE_GATHERING, latitude = 19.0760, longitude = 72.8779, timestamp = now + 10 * 60 * 1000L)
        val nearReports = listOf(rNear1, rNear2)
        val nearPattern = createBasePattern(category = ReportCategory.UNSAFE_GATHERING, contributingReportIds = nearReports.map { it.reportId })

        // Distant: 1km away from center
        val rFar1 = MicroReport(anonymousReporterToken = "u1", category = ReportCategory.UNSAFE_GATHERING, latitude = 19.0760, longitude = 72.8777, timestamp = now)
        val rFar2 = MicroReport(anonymousReporterToken = "u2", category = ReportCategory.UNSAFE_GATHERING, latitude = 19.0850, longitude = 72.8870, timestamp = now + 10 * 60 * 1000L)
        val farReports = listOf(rFar1, rFar2)
        val farPattern = createBasePattern(category = ReportCategory.UNSAFE_GATHERING, contributingReportIds = farReports.map { it.reportId })

        val nearResult = evaluator.evaluate(nearPattern, nearReports)
        val farResult = evaluator.evaluate(farPattern, farReports)

        assertTrue(
            "Near reports must have higher spatial consistency score than far reports",
            nearResult.spatialConsistencyScore > farResult.spatialConsistencyScore
        )
    }

    /**
     * Test 6 — Rate/flood resistance.
     * Rapid sequence of non-duplicate reports from same reporter/session within flood window.
     */
    @Test
    fun testRateAndFloodResistance() {
        val now = System.currentTimeMillis()
        // 5 reports from same reporter at different locations (so not distance-duplicates), but in a burst
        val burstReports = (1..5).map { i ->
            MicroReport(
                anonymousReporterToken = "flooder_1",
                category = ReportCategory.SUSPICIOUS_ACTIVITY,
                latitude = 19.0760 + (i * 0.001), // approx 110m apart
                longitude = 72.8777,
                timestamp = now + (i * 30 * 1000L) // every 30 seconds
            )
        }
        val pattern = createBasePattern(category = ReportCategory.SUSPICIOUS_ACTIVITY, contributingReportIds = burstReports.map { it.reportId })
        val result = evaluator.evaluate(pattern, burstReports)

        assertTrue("Flooded report count must be greater than zero", result.floodedReportCount > 0)
        assertTrue("Flood penalty must be applied", result.floodPenalty > 0.0f)
        assertFalse(result.isEmerging)
        assertEquals(PatternState.PATTERN_CANDIDATE, result.resultingState)
    }

    /**
     * Test 7 — Missing location safely handled.
     * Reports without coordinates do not crash trust evaluation and do not fabricate coordinates.
     */
    @Test
    fun testMissingLocationHandledSafely() {
        val now = System.currentTimeMillis()
        val r1 = MicroReport(anonymousReporterToken = "u1", category = ReportCategory.POOR_LIGHTING, latitude = null, longitude = null, approximateArea = "Sector 4", timestamp = now)
        val r2 = MicroReport(anonymousReporterToken = "u2", category = ReportCategory.POOR_LIGHTING, latitude = null, longitude = null, approximateArea = "Sector 4", timestamp = now + 15 * 60 * 1000L)
        val reports = listOf(r1, r2)
        val pattern = createBasePattern(contributingReportIds = reports.map { it.reportId })

        // Must execute cleanly without exception
        val result = evaluator.evaluate(pattern, reports)
        assertNotNull(result)
        assertEquals(2, result.uniqueReporterCount)
        assertEquals(0, result.duplicateReportCount)
        assertTrue(result.spatialConsistencyScore >= 0.0f)
    }

    /**
     * Test 8 — Determinism.
     * Running evaluation multiple times with identical inputs yields exactly identical results.
     */
    @Test
    fun testDeterminism() {
        val now = System.currentTimeMillis()
        val r1 = MicroReport(anonymousReporterToken = "rep_a", category = ReportCategory.POOR_LIGHTING, latitude = 19.0760, longitude = 72.8777, timestamp = now)
        val r2 = MicroReport(anonymousReporterToken = "rep_b", category = ReportCategory.POOR_LIGHTING, latitude = 19.0762, longitude = 72.8779, timestamp = now + 10 * 60 * 1000L)
        val reports = listOf(r1, r2)
        val pattern = createBasePattern(contributingReportIds = reports.map { it.reportId })

        val run1 = evaluator.evaluate(pattern, reports)
        val run2 = evaluator.evaluate(pattern, reports)
        val run3 = evaluator.evaluate(pattern, reports)

        assertEquals(run1.trustScore, run2.trustScore, 0.0001f)
        assertEquals(run2.trustScore, run3.trustScore, 0.0001f)
        assertEquals(run1.resultingState, run2.resultingState)
        assertEquals(run1.isEmerging, run2.isEmerging)
        assertEquals(run1.uniqueReporterCount, run2.uniqueReporterCount)
        assertEquals(run1.diversityScore, run2.diversityScore, 0.0001f)
        assertEquals(run1.reasons, run2.reasons)
    }

    /**
     * Test 9 — Input-order independence.
     * Shuffling report order produces identical evaluation results.
     */
    @Test
    fun testInputOrderIndependence() {
        val now = System.currentTimeMillis()
        val r1 = MicroReport(reportId = UUID.fromString("00000000-0000-0000-0000-000000000001"), anonymousReporterToken = "tok_1", category = ReportCategory.HARASSMENT, latitude = 19.0760, longitude = 72.8777, timestamp = now)
        val r2 = MicroReport(reportId = UUID.fromString("00000000-0000-0000-0000-000000000002"), anonymousReporterToken = "tok_2", category = ReportCategory.HARASSMENT, latitude = 19.0762, longitude = 72.8778, timestamp = now + 8 * 60 * 1000L)
        val r3 = MicroReport(reportId = UUID.fromString("00000000-0000-0000-0000-000000000003"), anonymousReporterToken = "tok_3", category = ReportCategory.HARASSMENT, latitude = 19.0761, longitude = 72.8779, timestamp = now + 15 * 60 * 1000L)

        val pattern = createBasePattern(category = ReportCategory.HARASSMENT, contributingReportIds = listOf(r1.reportId, r2.reportId, r3.reportId))

        val eval1 = evaluator.evaluate(pattern, listOf(r1, r2, r3))
        val eval2 = evaluator.evaluate(pattern, listOf(r3, r1, r2))
        val eval3 = evaluator.evaluate(pattern, listOf(r2, r3, r1))

        assertEquals(eval1.trustScore, eval2.trustScore, 0.0001f)
        assertEquals(eval1.trustScore, eval3.trustScore, 0.0001f)
        assertEquals(eval1.resultingState, eval2.resultingState)
        assertEquals(eval1.isEmerging, eval2.isEmerging)
        assertEquals(eval1.diversityScore, eval2.diversityScore, 0.0001f)
    }

    /**
     * Test 10 — Empty and single-report candidates.
     * Safely handles edge cases without crashes or erroneous trust elevation.
     */
    @Test
    fun testEmptyAndSingleReportCandidates() {
        val emptyPattern = createBasePattern()
        val emptyResult = evaluator.evaluate(emptyPattern, emptyList())
        assertEquals(0.0f, emptyResult.trustScore, 0.0f)
        assertFalse(emptyResult.isEmerging)
        assertEquals(PatternState.PATTERN_CANDIDATE, emptyResult.resultingState)

        val singleReport = MicroReport(
            anonymousReporterToken = "lonely_user",
            category = ReportCategory.POOR_LIGHTING,
            latitude = 19.0760,
            longitude = 72.8777
        )
        val singlePattern = createBasePattern(contributingReportIds = listOf(singleReport.reportId))
        val singleResult = evaluator.evaluate(singlePattern, listOf(singleReport))
        assertEquals(0.0f, singleResult.trustScore, 0.0f)
        assertFalse(singleResult.isEmerging)
        assertEquals(PatternState.PATTERN_CANDIDATE, singleResult.resultingState)
    }

    /**
     * Test 11 — State boundary protection.
     * Resulting state MUST NEVER be ALERT_ACTIVE. That is strictly Phase D.
     */
    @Test
    fun testStateBoundaryNeverTransitionsToAlertActive() {
        val now = System.currentTimeMillis()
        val r1 = MicroReport(anonymousReporterToken = "u1", category = ReportCategory.POOR_LIGHTING, latitude = 19.0760, longitude = 72.8777, timestamp = now)
        val r2 = MicroReport(anonymousReporterToken = "u2", category = ReportCategory.POOR_LIGHTING, latitude = 19.0762, longitude = 72.8779, timestamp = now + 15 * 60 * 1000L)
        val reports = listOf(r1, r2)
        val pattern = createBasePattern(contributingReportIds = reports.map { it.reportId })

        val evaluatedPattern = evaluator.evaluatePattern(pattern, reports)
        assertNotEquals(PatternState.ALERT_ACTIVE, evaluatedPattern.state)
        assertTrue(evaluatedPattern.state == PatternState.PATTERN_EMERGING || evaluatedPattern.state == PatternState.PATTERN_CANDIDATE)
    }

    /**
     * Case A — Many reports, one reporter.
     * 20 reports from the exact same anonymous reporter token in the same area.
     * Invariant: Raw report count alone MUST NOT cause confidence escalation or alert.
     */
    @Test
    fun testCaseA_TwentyReportsSingleReporterNeverEscalates() {
        val now = System.currentTimeMillis()
        val reports = (1..20).map { i ->
            MicroReport(
                anonymousReporterToken = "sybil_flooder_token",
                category = ReportCategory.HARASSMENT,
                latitude = 19.0760,
                longitude = 72.8777,
                timestamp = now + (i * 2000L) // 2 second intervals
            )
        }
        val pattern = createBasePattern(
            category = ReportCategory.HARASSMENT,
            contributingReportIds = reports.map { it.reportId }
        )

        val result = evaluator.evaluate(pattern, reports)

        assertFalse("20 reports from single reporter MUST NOT become emerging", result.isEmerging)
        assertEquals(PatternState.PATTERN_CANDIDATE, result.resultingState)
        assertEquals(1, result.uniqueReporterCount)
        assertEquals(20, result.totalReportCount)
        assertTrue("Duplicate reports must be detected", result.duplicateReportCount > 0)
        assertTrue("Duplicate penalty must be applied", result.duplicatePenalty > 0.0f)
        assertTrue("Diversity score must be capped low", result.diversityScore <= 0.15f)
        assertNotEquals(PatternState.ALERT_ACTIVE, result.resultingState)
    }

    /**
     * Case B — Multiple independent reporters (3–5 reports).
     * 5 reports from 5 distinct reporter tokens, separated temporally and spatially consistent.
     */
    @Test
    fun testCaseB_FiveIndependentReportersEscalateToEmerging() {
        val now = System.currentTimeMillis()
        val reports = (1..5).map { i ->
            MicroReport(
                anonymousReporterToken = "independent_reporter_$i",
                category = ReportCategory.FEELING_FOLLOWED,
                latitude = 19.0760 + (i * 0.0001),
                longitude = 72.8777 + (i * 0.0001),
                timestamp = now + (i * 3 * 60 * 1000L) // every 3 minutes
            )
        }
        val pattern = createBasePattern(
            category = ReportCategory.FEELING_FOLLOWED,
            contributingReportIds = reports.map { it.reportId }
        )

        val result = evaluator.evaluate(pattern, reports)

        assertTrue("5 independent reports must promote to emerging", result.isEmerging)
        assertEquals(PatternState.PATTERN_EMERGING, result.resultingState)
        assertEquals(5, result.uniqueReporterCount)
        assertEquals(0, result.duplicateReportCount)
        assertEquals(0, result.floodedReportCount)
        assertTrue("Trust score must cross emerging threshold", result.trustScore >= 0.60f)
        assertNotEquals("Must not produce Phase D alert", PatternState.ALERT_ACTIVE, result.resultingState)
    }

    /**
     * Case C — Duplicate submissions & idempotency.
     * Repeated identical submissions from same reporter/session.
     */
    @Test
    fun testCaseC_DuplicateSubmissionsIdempotencyAndNoInflation() {
        val now = System.currentTimeMillis()
        val r1 = MicroReport(anonymousReporterToken = "rep_duper", category = ReportCategory.POOR_LIGHTING, latitude = 19.0760, longitude = 72.8777, timestamp = now)
        val dupes = (1..4).map { i ->
            MicroReport(
                anonymousReporterToken = "rep_duper",
                category = ReportCategory.POOR_LIGHTING,
                latitude = 19.0760,
                longitude = 72.8777,
                timestamp = now + (i * 10000L) // within 40 seconds
            )
        }
        val allReports = listOf(r1) + dupes
        val pattern = createBasePattern(contributingReportIds = allReports.map { it.reportId })

        val run1 = evaluator.evaluate(pattern, allReports)
        val run2 = evaluator.evaluate(pattern, allReports)

        // Idempotency
        assertEquals(run1.trustScore, run2.trustScore, 0.0001f)
        assertEquals(run1.resultingState, run2.resultingState)
        assertEquals(run1.duplicateReportCount, run2.duplicateReportCount)
        assertEquals(4, run1.duplicateReportCount)

        // No inflation
        assertFalse(run1.isEmerging)
        assertEquals(PatternState.PATTERN_CANDIDATE, run1.resultingState)
        assertTrue(run1.trustScore < 0.30f)
    }

    /**
     * Case D — Temporal independence rules.
     * Reports clustered within seconds vs distributed across the 10-minute window.
     */
    @Test
    fun testCaseD_TemporalIndependenceRules() {
        val now = System.currentTimeMillis()
        val reportsBurst = (1..3).map { i ->
            MicroReport(
                anonymousReporterToken = "token_$i",
                category = ReportCategory.SUSPICIOUS_ACTIVITY,
                latitude = 19.0760,
                longitude = 72.8777,
                timestamp = now + (i * 500L) // within 1.5 seconds
            )
        }
        val patternBurst = createBasePattern(category = ReportCategory.SUSPICIOUS_ACTIVITY, contributingReportIds = reportsBurst.map { it.reportId })

        val reportsDistributed = (1..3).map { i ->
            MicroReport(
                anonymousReporterToken = "token_$i",
                category = ReportCategory.SUSPICIOUS_ACTIVITY,
                latitude = 19.0760,
                longitude = 72.8777,
                timestamp = now + (i * 4 * 60 * 1000L) // 4 min intervals (total 12 mins)
            )
        }
        val patternDistributed = createBasePattern(category = ReportCategory.SUSPICIOUS_ACTIVITY, contributingReportIds = reportsDistributed.map { it.reportId })

        val resultBurst = evaluator.evaluate(patternBurst, reportsBurst)
        val resultDistributed = evaluator.evaluate(patternDistributed, reportsDistributed)

        assertEquals("Burst reports (< 10s) must receive minimum temporal baseline (0.10)", 0.10f, resultBurst.temporalIndependenceScore, 0.001f)
        assertTrue(
            "Distributed reports must achieve higher temporal independence score than burst",
            resultDistributed.temporalIndependenceScore > resultBurst.temporalIndependenceScore
        )
        assertTrue("Distributed reports achieve high temporal score", resultDistributed.temporalIndependenceScore >= 0.80f)
    }

    /**
     * Case E — Spatial consistency rules.
     * Geographically discordant reports vs spatially coherent cluster.
     */
    @Test
    fun testCaseE_SpatialConsistencyRules() {
        val now = System.currentTimeMillis()
        val centerLat = 19.0760
        val centerLng = 72.8777

        // Consistent cluster within 50 meters
        val coherentReports = listOf(
            MicroReport(anonymousReporterToken = "t1", category = ReportCategory.UNSAFE_GATHERING, latitude = centerLat, longitude = centerLng, timestamp = now),
            MicroReport(anonymousReporterToken = "t2", category = ReportCategory.UNSAFE_GATHERING, latitude = centerLat + 0.0002, longitude = centerLng + 0.0002, timestamp = now + 10 * 60 * 1000L)
        )
        val coherentPattern = createBasePattern(category = ReportCategory.UNSAFE_GATHERING, centerLat = centerLat, centerLng = centerLng, radiusMeters = 200.0, contributingReportIds = coherentReports.map { it.reportId })

        // Discordant reports: 5 km away
        val discordantReports = listOf(
            MicroReport(anonymousReporterToken = "t1", category = ReportCategory.UNSAFE_GATHERING, latitude = centerLat, longitude = centerLng, timestamp = now),
            MicroReport(anonymousReporterToken = "t2", category = ReportCategory.UNSAFE_GATHERING, latitude = centerLat + 0.05, longitude = centerLng + 0.05, timestamp = now + 10 * 60 * 1000L)
        )
        val discordantPattern = createBasePattern(category = ReportCategory.UNSAFE_GATHERING, centerLat = centerLat, centerLng = centerLng, radiusMeters = 200.0, contributingReportIds = discordantReports.map { it.reportId })

        val coherentResult = evaluator.evaluate(coherentPattern, coherentReports)
        val discordantResult = evaluator.evaluate(discordantPattern, discordantReports)

        assertTrue(
            "Coherent spatial cluster must score significantly higher than discordant reports",
            coherentResult.spatialConsistencyScore > discordantResult.spatialConsistencyScore + 0.40f
        )
        assertTrue("Coherent cluster spatial score must be >= 0.80", coherentResult.spatialConsistencyScore >= 0.80f)
        assertTrue("Discordant spatial score must be <= 0.50", discordantResult.spatialConsistencyScore <= 0.50f)
    }

    /**
     * Case F — Mixed-quality evidence trade-offs.
     */
    @Test
    fun testCaseF_MixedQualityEvidenceTradeoffs() {
        val now = System.currentTimeMillis()

        // 1. High count / Low diversity (15 reports, 1 token)
        val highCountLowDivReports = (1..15).map { i ->
            MicroReport(anonymousReporterToken = "lone_wolf", category = ReportCategory.POOR_LIGHTING, latitude = 19.0760, longitude = 72.8777, timestamp = now + i * 5000L)
        }
        val highCountLowDivPattern = createBasePattern(contributingReportIds = highCountLowDivReports.map { it.reportId })
        val res1 = evaluator.evaluate(highCountLowDivPattern, highCountLowDivReports)

        assertFalse("High count with 1 reporter must NEVER be emerging", res1.isEmerging)
        assertEquals(PatternState.PATTERN_CANDIDATE, res1.resultingState)
        assertTrue(res1.trustScore < 0.35f)

        // 2. Low count / High diversity (2 reports, 2 tokens, 12 mins apart, consistent location)
        val lowCountHighDivReports = listOf(
            MicroReport(anonymousReporterToken = "citizen_a", category = ReportCategory.POOR_LIGHTING, latitude = 19.0760, longitude = 72.8777, timestamp = now),
            MicroReport(anonymousReporterToken = "citizen_b", category = ReportCategory.POOR_LIGHTING, latitude = 19.0762, longitude = 72.8779, timestamp = now + 12 * 60 * 1000L)
        )
        val lowCountHighDivPattern = createBasePattern(contributingReportIds = lowCountHighDivReports.map { it.reportId })
        val res2 = evaluator.evaluate(lowCountHighDivPattern, lowCountHighDivReports)

        assertTrue("Low count with high diversity and temporal separation must be emerging", res2.isEmerging)
        assertEquals(PatternState.PATTERN_EMERGING, res2.resultingState)
        assertTrue(res2.trustScore >= 0.60f)

        // Verifying that Low count / High diversity achieves higher trust than High count / Low diversity!
        assertTrue(
            "Low count / high diversity (${res2.trustScore}) must beat high count / low diversity (${res1.trustScore})",
            res2.trustScore > res1.trustScore
        )
    }

    /**
     * Event and state ordering verification.
     * Preserves: report.created -> stored -> validated -> pattern.candidate_detected -> trust_evaluated -> pattern.emerging
     * STRICTLY PREVENTS transition to alert.generated / ALERT_ACTIVE.
     */
    @Test
    fun testEventAndStateOrdering_CandidateToEmergingNeverAlertActive() {
        val now = System.currentTimeMillis()
        val r1 = MicroReport(anonymousReporterToken = "tok_x", category = ReportCategory.HARASSMENT, latitude = 19.0760, longitude = 72.8777, timestamp = now)
        val r2 = MicroReport(anonymousReporterToken = "tok_y", category = ReportCategory.HARASSMENT, latitude = 19.0762, longitude = 72.8778, timestamp = now + 15 * 60 * 1000L)
        val reports = listOf(r1, r2)
        val candidate = createBasePattern(category = ReportCategory.HARASSMENT, contributingReportIds = reports.map { it.reportId })

        assertEquals("Input must start in PATTERN_CANDIDATE", PatternState.PATTERN_CANDIDATE, candidate.state)

        val evaluated = evaluator.evaluatePattern(candidate, reports)

        assertEquals("Evaluated pattern preserves patternId", candidate.patternId, evaluated.patternId)
        assertEquals("Evaluated pattern preserves category", candidate.category, evaluated.category)
        assertEquals("Evaluated pattern preserves coordinates", candidate.centerLatitude, evaluated.centerLatitude, 0.00001)
        assertEquals("Evaluated pattern state must be PATTERN_EMERGING", PatternState.PATTERN_EMERGING, evaluated.state)
        assertNotEquals("Evaluated pattern state MUST NOT be ALERT_ACTIVE", PatternState.ALERT_ACTIVE, evaluated.state)
        assertTrue("Evaluated pattern trustScore must be updated", evaluated.trustScore > 0.0f)
    }
}
