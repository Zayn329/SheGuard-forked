package org.sahara.app

import org.junit.Assert.*
import org.junit.Test
import org.sahara.app.location.LocationState
import org.sahara.app.location.SheGuardLocation
import org.sahara.core.domain.engine.SpatioTemporalPatternEngine
import org.sahara.core.domain.engine.TrustAndAntiGamingEvaluator
import org.sahara.core.domain.models.MicroReport
import org.sahara.core.domain.models.PatternState
import org.sahara.core.domain.models.ReportCategory
import org.sahara.core.domain.models.SyncStatus
import java.util.UUID

class SheGuardLocationUnitTest {

    @Test
    fun testAccuracyDescriptionBuckets() {
        val highAccuracy = SheGuardLocation(
            latitude = 19.0760,
            longitude = 72.8777,
            accuracy = 12.4f,
            readableAddress = "Marine Drive, Mumbai",
            isApproximateOnly = false
        )
        assertEquals("High accuracy (±12m)", highAccuracy.getAccuracyDescription())

        val streetLevel = SheGuardLocation(
            latitude = 19.0760,
            longitude = 72.8777,
            accuracy = 65.0f,
            readableAddress = "Linking Road, Bandra",
            isApproximateOnly = false
        )
        assertEquals("Street level (±65m)", streetLevel.getAccuracyDescription())

        val neighborhoodLevel = SheGuardLocation(
            latitude = 19.0760,
            longitude = 72.8777,
            accuracy = 350.0f,
            readableAddress = "Andheri East, Mumbai",
            isApproximateOnly = false
        )
        assertEquals("Neighborhood level (±350m)", neighborhoodLevel.getAccuracyDescription())

        val approximateOnly = SheGuardLocation(
            latitude = 19.0760,
            longitude = 72.8777,
            accuracy = 1200.0f,
            readableAddress = "19.0760° N, 72.8777° E",
            isApproximateOnly = true
        )
        assertEquals("Approximate area (±1200m)", approximateOnly.getAccuracyDescription())
        assertTrue(approximateOnly.isApproximateOnly)

        val nullAccuracy = SheGuardLocation(
            latitude = 19.0760,
            longitude = 72.8777,
            accuracy = null,
            readableAddress = "19.0760° N, 72.8777° E",
            isApproximateOnly = false
        )
        assertEquals("Accuracy unknown", nullAccuracy.getAccuracyDescription())
    }

    @Test
    fun testTwoPhonesRetainIndependentCoordinates() {
        // Phone 1: Bandra West
        val phone1Location = SheGuardLocation(
            latitude = 19.0596,
            longitude = 72.8295,
            accuracy = 15.0f,
            readableAddress = "Bandra West, Mumbai",
            isApproximateOnly = false
        )
        val reportPhone1 = MicroReport(
            reportId = UUID.randomUUID(),
            anonymousReporterToken = "reporter_phone_1",
            category = ReportCategory.POOR_LIGHTING,
            latitude = phone1Location.latitude,
            longitude = phone1Location.longitude,
            approximateArea = phone1Location.readableAddress,
            accuracy = phone1Location.accuracy,
            syncStatus = SyncStatus.LOCAL
        )

        // Phone 2: Dadar West
        val phone2Location = SheGuardLocation(
            latitude = 19.0178,
            longitude = 72.8478,
            accuracy = 25.0f,
            readableAddress = "Dadar West, Mumbai",
            isApproximateOnly = false
        )
        val reportPhone2 = MicroReport(
            reportId = UUID.randomUUID(),
            anonymousReporterToken = "reporter_phone_2",
            category = ReportCategory.HARASSMENT,
            latitude = phone2Location.latitude,
            longitude = phone2Location.longitude,
            approximateArea = phone2Location.readableAddress,
            accuracy = phone2Location.accuracy,
            syncStatus = SyncStatus.MESH_QUEUED
        )

        // Verify distinct coordinates, areas, and accuracies are preserved
        assertNotEquals(reportPhone1.latitude, reportPhone2.latitude)
        assertNotEquals(reportPhone1.longitude, reportPhone2.longitude)
        assertNotEquals(reportPhone1.approximateArea, reportPhone2.approximateArea)
        assertEquals(19.0596, reportPhone1.latitude!!, 0.0001)
        assertEquals(19.0178, reportPhone2.latitude!!, 0.0001)
        assertEquals(15.0f, reportPhone1.accuracy!!, 0.01f)
        assertEquals(25.0f, reportPhone2.accuracy!!, 0.01f)
    }

    @Test
    fun testSpatioTemporalClusteringWithLiveCoordinates() {
        val patternEngine = SpatioTemporalPatternEngine()
        val trustEvaluator = TrustAndAntiGamingEvaluator()

        val now = System.currentTimeMillis()

        // Two independent reporters in close proximity (~100m apart) in Bandra
        val r1 = MicroReport(
            anonymousReporterToken = "user_alpha_123",
            category = ReportCategory.POOR_LIGHTING,
            latitude = 19.0596,
            longitude = 72.8295,
            approximateArea = "Hill Road, Bandra",
            timestamp = now - 1000 * 60 * 10,
            accuracy = 10f
        )
        val r2 = MicroReport(
            anonymousReporterToken = "user_beta_456",
            category = ReportCategory.POOR_LIGHTING,
            latitude = 19.0601,
            longitude = 72.8298,
            approximateArea = "Hill Road, Bandra",
            timestamp = now,
            accuracy = 18f
        )

        val candidates = patternEngine.detectCandidatePatterns(listOf(r1, r2))
        assertEquals(1, candidates.size)
        assertEquals(ReportCategory.POOR_LIGHTING, candidates[0].category)
        assertEquals(2, candidates[0].reportCount)

        val evaluatedResult = trustEvaluator.evaluate(candidates[0], listOf(r1, r2))
        assertTrue(evaluatedResult.isEmerging)
        assertEquals(PatternState.PATTERN_EMERGING, evaluatedResult.resultingState)
        assertTrue(evaluatedResult.trustScore >= 0.60f)

        val evaluatedPattern = trustEvaluator.evaluatePattern(candidates[0], listOf(r1, r2))
        assertEquals(PatternState.PATTERN_EMERGING, evaluatedPattern.state)
        assertTrue(evaluatedPattern.trustScore >= 0.60f)
    }

    @Test
    fun testLocationStateTransitions() {
        val idle = LocationState.Idle
        val fetching = LocationState.Fetching
        val permRequired = LocationState.PermissionRequired
        val error = LocationState.Error("GPS signal unavailable", isGpsDisabled = false)
        val success = LocationState.Success(
            SheGuardLocation(19.0760, 72.8777, 10f, "Mumbai Central", false)
        )

        assertNotNull(idle)
        assertNotNull(fetching)
        assertNotNull(permRequired)
        assertEquals("GPS signal unavailable", error.message)
        assertFalse(error.isGpsDisabled)
        assertEquals("Mumbai Central", success.location.readableAddress)
    }
}
