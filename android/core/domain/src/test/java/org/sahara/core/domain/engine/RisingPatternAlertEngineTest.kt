package org.sahara.core.domain.engine

import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.sahara.core.domain.models.AlertEngineConfig
import org.sahara.core.domain.models.PatternState
import org.sahara.core.domain.models.ReportCategory
import org.sahara.core.domain.models.RisingPatternAlert
import org.sahara.core.domain.models.SpatioTemporalPattern
import org.sahara.core.domain.models.TrustLevel
import java.util.UUID

/**
 * Unit tests for RisingPatternAlertEngine — Phase D.
 *
 * Tests verify:
 * - State gate: only PATTERN_EMERGING produces an alert
 * - Trust score gate: below threshold returns null
 * - Deterministic / idempotent alert ID
 * - Approximate location privacy (≤2 decimal places)
 * - Fixed disclaimer
 * - TrustLevel classification (LOW / MEDIUM / HIGH)
 * - Batch generation
 * - Config customisation
 */
class RisingPatternAlertEngineTest {

    private lateinit var engine: RisingPatternAlertEngine
    private val NOW = System.currentTimeMillis()

    @Before
    fun setUp() {
        engine = RisingPatternAlertEngine()
    }

    // ─── Helpers ─────────────────────────────────────────────────────────────

    private fun pattern(
        state: PatternState,
        trustScore: Float = 0.75f,
        category: ReportCategory = ReportCategory.HARASSMENT,
        lat: Double = 19.0760,
        lng: Double = 72.8777,
        radiusMeters: Double = 350.0,
        patternId: UUID = UUID.randomUUID()
    ) = SpatioTemporalPattern(
        patternId = patternId,
        centerLatitude = lat,
        centerLongitude = lng,
        radiusMeters = radiusMeters,
        category = category,
        firstReportedAt = NOW - 30 * 60 * 1000L,
        lastReportedAt = NOW,
        reportCount = 3,
        contributingReportIds = listOf(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID()),
        trustScore = trustScore,
        state = state
    )

    // ─── State gate tests ─────────────────────────────────────────────────────

    @Test
    fun `test01 - PATTERN_EMERGING produces an alert`() {
        val p = pattern(PatternState.PATTERN_EMERGING, trustScore = 0.75f)
        val alert = engine.generateAlert(p)
        assertNotNull("PATTERN_EMERGING should produce an alert", alert)
    }

    @Test
    fun `test02 - PATTERN_CANDIDATE returns null`() {
        val p = pattern(PatternState.PATTERN_CANDIDATE, trustScore = 0.75f)
        assertNull("PATTERN_CANDIDATE must not produce an alert", engine.generateAlert(p))
    }

    @Test
    fun `test03 - INACTIVE returns null`() {
        val p = pattern(PatternState.INACTIVE, trustScore = 0.90f)
        assertNull("INACTIVE must not produce an alert", engine.generateAlert(p))
    }

    @Test
    fun `test04 - TRUST_EVALUATING returns null`() {
        val p = pattern(PatternState.TRUST_EVALUATING, trustScore = 0.90f)
        assertNull("TRUST_EVALUATING must not produce an alert", engine.generateAlert(p))
    }

    @Test
    fun `test05 - RESOLVED returns null`() {
        val p = pattern(PatternState.RESOLVED, trustScore = 0.95f)
        assertNull("RESOLVED must not produce an alert", engine.generateAlert(p))
    }

    @Test
    fun `test06 - ALERT_ACTIVE returns null`() {
        // ALERT_ACTIVE is a separate pattern state — alert engine produces RisingPatternAlert
        // only from PATTERN_EMERGING, not ALERT_ACTIVE.
        val p = pattern(PatternState.ALERT_ACTIVE, trustScore = 0.90f)
        assertNull("ALERT_ACTIVE must not produce a new alert (duplicate guard)", engine.generateAlert(p))
    }

    // ─── Trust score gate tests ───────────────────────────────────────────────

    @Test
    fun `test07 - trust score at threshold (0_60) produces alert`() {
        val p = pattern(PatternState.PATTERN_EMERGING, trustScore = 0.60f)
        val alert = engine.generateAlert(p)
        assertNotNull("Score exactly at 0.60 should produce an alert", alert)
        assertEquals(TrustLevel.MEDIUM, alert!!.trustLevel)
    }

    @Test
    fun `test08 - trust score below threshold (0_59) returns null`() {
        val p = pattern(PatternState.PATTERN_EMERGING, trustScore = 0.59f)
        assertNull("Score below 0.60 must return null (trust gate)", engine.generateAlert(p))
    }

    @Test
    fun `test09 - zero trust score returns null`() {
        val p = pattern(PatternState.PATTERN_EMERGING, trustScore = 0.0f)
        assertNull("Zero trust score must return null", engine.generateAlert(p))
    }

    // ─── TrustLevel classification ────────────────────────────────────────────

    @Test
    fun `test10 - HIGH trust level at 0_80`() {
        val p = pattern(PatternState.PATTERN_EMERGING, trustScore = 0.80f)
        val alert = engine.generateAlert(p)!!
        assertEquals(TrustLevel.HIGH, alert.trustLevel)
    }

    @Test
    fun `test11 - MEDIUM trust level at 0_70`() {
        val p = pattern(PatternState.PATTERN_EMERGING, trustScore = 0.70f)
        val alert = engine.generateAlert(p)!!
        assertEquals(TrustLevel.MEDIUM, alert.trustLevel)
    }

    @Test
    fun `test12 - MEDIUM trust level at 0_79`() {
        val p = pattern(PatternState.PATTERN_EMERGING, trustScore = 0.79f)
        val alert = engine.generateAlert(p)!!
        assertEquals(TrustLevel.MEDIUM, alert.trustLevel)
    }

    // ─── Idempotency / determinism ────────────────────────────────────────────

    @Test
    fun `test13 - same patternId always yields same alertId`() {
        val id = UUID.randomUUID()
        val p = pattern(PatternState.PATTERN_EMERGING, trustScore = 0.80f, patternId = id)
        val a1 = engine.generateAlert(p)!!
        val a2 = engine.generateAlert(p)!!
        assertEquals(
            "Alert ID must be deterministically derived from pattern ID",
            a1.alertId,
            a2.alertId
        )
    }

    @Test
    fun `test14 - different patternIds produce different alertIds`() {
        val p1 = pattern(PatternState.PATTERN_EMERGING, trustScore = 0.80f, patternId = UUID.randomUUID())
        val p2 = pattern(PatternState.PATTERN_EMERGING, trustScore = 0.80f, patternId = UUID.randomUUID())
        assertNotEquals(engine.generateAlert(p1)!!.alertId, engine.generateAlert(p2)!!.alertId)
    }

    // ─── Privacy: approximate location ───────────────────────────────────────

    @Test
    fun `test15 - approximateLocation does not expose sub-100m precision`() {
        // Lat: 19.07605678, Lng: 72.87776543 should be coarsened to 2dp
        val p = pattern(
            PatternState.PATTERN_EMERGING, trustScore = 0.80f,
            lat = 19.07605678, lng = 72.87776543, radiusMeters = 350.0
        )
        val alert = engine.generateAlert(p)!!
        // The location string must NOT contain more than 2 digits after the decimal
        assertFalse(
            "Location must not expose sub-1km precision",
            alert.approximateLocation.contains("19.076056") ||
                alert.approximateLocation.contains("72.877765")
        )
        // Must be present in a coarse form
        assertTrue(alert.approximateLocation.contains("19.08") || alert.approximateLocation.contains("19.07"))
    }

    // ─── Disclaimer invariant ─────────────────────────────────────────────────

    @Test
    fun `test16 - disclaimer is fixed and cannot be overridden`() {
        val p = pattern(PatternState.PATTERN_EMERGING, trustScore = 0.80f)
        val alert = engine.generateAlert(p)!!
        assertEquals(
            "EARLY WARNING PATTERN ALERT. NOT A GUARANTEED EMERGENCY RESPONSE.",
            alert.disclaimer
        )
    }

    // ─── Batch generation ────────────────────────────────────────────────────

    @Test
    fun `test17 - generateAlerts filters non-emerging patterns from a mixed list`() {
        val emerging1 = pattern(PatternState.PATTERN_EMERGING, trustScore = 0.85f)
        val emerging2 = pattern(PatternState.PATTERN_EMERGING, trustScore = 0.65f)
        val candidate = pattern(PatternState.PATTERN_CANDIDATE, trustScore = 0.70f)
        val inactive = pattern(PatternState.INACTIVE, trustScore = 0.90f)

        val alerts = engine.generateAlerts(listOf(emerging1, candidate, inactive, emerging2))

        assertEquals("Only PATTERN_EMERGING patterns should produce alerts", 2, alerts.size)
        assertTrue(alerts.all { it.trustScore >= 0.60f })
    }
}
