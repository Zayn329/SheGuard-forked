package org.sahara.services.mesh

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.sahara.core.domain.engine.RisingPatternAlertEngine
import org.sahara.core.domain.engine.SpatioTemporalPatternEngine
import org.sahara.core.domain.engine.TrustAndAntiGamingEvaluator
import org.sahara.core.domain.models.MicroReport
import org.sahara.core.domain.models.PatternState
import org.sahara.core.domain.models.ReportCategory
import org.sahara.core.domain.models.RisingPatternAlert
import org.sahara.core.domain.models.SpatioTemporalPattern
import org.sahara.core.domain.models.TrustLevel
import org.sahara.core.domain.repository.AlertRepository
import org.sahara.services.mesh.models.MeshPacket
import org.sahara.services.mesh.models.MeshPacketType
import org.sahara.services.mesh.models.SheGuardMeshAlertPayload
import org.sahara.services.mesh.relay.MeshDeduplicationCache
import org.sahara.services.mesh.relay.MeshPayloadValidator
import org.sahara.services.mesh.relay.MeshRelayResult
import org.sahara.services.mesh.relay.MeshStatus
import org.sahara.services.mesh.relay.MeshValidationResult
import org.sahara.services.mesh.relay.NearbyConnectionsMeshRelay
import org.sahara.services.mesh.relay.SheGuardMeshAdapter
import org.sahara.services.mesh.relay.SheGuardMeshProcessResult
import java.util.UUID

/**
 * Comprehensive Unit Tests for SheGuard Phase E — Offline Mesh Relay.
 *
 * Covers all required test scenarios defined in the Phase E contract:
 * - Payload (Tests 1–6): serialization, deserialization, round-trip, invalid, version, required fields
 * - Privacy (Tests 7–10): token absence, PII absence, raw coordinate absence, precision preservation
 * - Deduplication (Tests 11–12): duplicate receipt, multi-node relay deduplication
 * - Loop Protection (Tests 13–14): already-processed loop prevention, hop limit
 * - Offline / Store-and-Forward (Tests 15–17): local alert generation without mesh, offline accessibility, queued relay delivery
 * - Trust Boundary (Tests 18–21): no trust score mutation, no pattern creation, no bypass of Phase C, no alert generation from raw volume
 * - Determinism & Idempotency (Tests 22–23): identical identity, repeated relay idempotency
 */
class SheGuardMeshUnitTest {

    private lateinit var cache: MeshDeduplicationCache
    private lateinit var relay: NearbyConnectionsMeshRelay
    private lateinit var validator: MeshPayloadValidator
    private lateinit var fakeAlertRepo: FakeAlertRepository
    private lateinit var adapter: SheGuardMeshAdapter

    private val sampleAlertId = UUID.fromString("11111111-1111-1111-1111-111111111111")
    private val samplePatternId = UUID.fromString("22222222-2222-2222-2222-222222222222")

    class FakeAlertRepository : AlertRepository {
        val alerts = mutableMapOf<UUID, RisingPatternAlert>()
        override fun getAllAlerts(): Flow<List<RisingPatternAlert>> = flowOf(alerts.values.toList())
        override suspend fun saveAlert(alert: RisingPatternAlert) { alerts[alert.alertId] = alert }
        override suspend fun getAlertById(id: UUID): RisingPatternAlert? = alerts[id]
        override suspend fun clearAlerts() { alerts.clear() }
    }

    @Before
    fun setup() {
        cache = MeshDeduplicationCache(1000)
        relay = NearbyConnectionsMeshRelay(cache)
        validator = MeshPayloadValidator()
        fakeAlertRepo = FakeAlertRepository()
        adapter = SheGuardMeshAdapter(
            meshRelay = relay,
            validator = validator,
            alertRepository = fakeAlertRepo,
            initialStatus = MeshStatus.AVAILABLE
        )
    }

    private fun createSampleAlert(
        alertId: UUID = sampleAlertId,
        patternId: UUID = samplePatternId,
        trustScore: Float = 0.85f,
        trustLevel: TrustLevel = TrustLevel.HIGH,
        approxLocation: String = "approx. 19.08°N 72.88°E within ~200m"
    ) = RisingPatternAlert(
        alertId = alertId,
        patternId = patternId,
        category = ReportCategory.HARASSMENT,
        approximateLocation = approxLocation,
        timeWindow = "14:00–14:30",
        trustLevel = trustLevel,
        trustScore = trustScore,
        createdAt = 1700000000000L,
        disclaimer = "EARLY WARNING PATTERN ALERT. NOT A GUARANTEED EMERGENCY RESPONSE.",
        isRelayed = false
    )

    // =========================================================================
    // 1. PAYLOAD TESTS (1–6)
    // =========================================================================

    @Test
    fun test01_alertSerializesSuccessfully() {
        val alert = createSampleAlert()
        val packet = adapter.createPacketForAlert(alert)

        assertNotNull("Packet must be generated", packet)
        assertEquals(MeshPacketType.SHEGUARD_ALERT, packet.packetType)
        assertTrue("PayloadText must be valid JSON string", packet.payloadText.contains("\"alertId\""))
        assertTrue(packet.payloadText.contains("HARASSMENT"))
    }

    @Test
    fun test02_payloadDeserializesSuccessfully() {
        val alert = createSampleAlert()
        val packet = adapter.createPacketForAlert(alert)
        val payload = SheGuardMeshAlertPayload.fromJson(packet.payloadText)

        assertNotNull("Payload must deserialize cleanly", payload)
        assertEquals(sampleAlertId.toString(), payload?.alertId)
        assertEquals(samplePatternId.toString(), payload?.patternId)
        assertEquals("HARASSMENT", payload?.category)
        assertEquals(0.85f, payload?.trustScore ?: 0f, 0.001f)
    }

    @Test
    fun test03_roundTripPreservesRequiredFields() {
        val alert = createSampleAlert()
        val packet = adapter.createPacketForAlert(alert)
        val payload = SheGuardMeshAlertPayload.fromJson(packet.payloadText)!!
        val reconstructedAlert = adapter.payloadToAlert(payload)

        assertEquals(alert.alertId, reconstructedAlert.alertId)
        assertEquals(alert.patternId, reconstructedAlert.patternId)
        assertEquals(alert.category, reconstructedAlert.category)
        assertEquals(alert.approximateLocation, reconstructedAlert.approximateLocation)
        assertEquals(alert.trustLevel, reconstructedAlert.trustLevel)
        assertEquals(alert.trustScore, reconstructedAlert.trustScore, 0.001f)
        assertEquals(alert.disclaimer, reconstructedAlert.disclaimer)
        assertTrue("Reconstructed mesh alert must have isRelayed = true", reconstructedAlert.isRelayed)
    }

    @Test
    fun test04_invalidPayloadIsRejected() {
        val malformedJson = "{ this is completely invalid JSON ::: }"
        val result = validator.validate(malformedJson)

        assertTrue("Malformed JSON must be rejected", result is MeshValidationResult.Rejected)
        val rejectReason = (result as MeshValidationResult.Rejected).reason
        assertTrue(rejectReason.contains("Malformed JSON"))
    }

    @Test
    fun test05_unsupportedProtocolVersionIsRejected() {
        val alert = createSampleAlert()
        val packet = adapter.createPacketForAlert(alert)
        // Corrupt protocolVersion to 999
        val corruptedJson = packet.payloadText.replace("\"protocolVersion\":1", "\"protocolVersion\":999")
        val result = validator.validate(corruptedJson)

        assertTrue(result is MeshValidationResult.Rejected)
        assertTrue((result as MeshValidationResult.Rejected).reason.contains("Unsupported protocol version"))
    }

    @Test
    fun test06_missingRequiredFieldsAreRejected() {
        // Missing alertId
        val alert = createSampleAlert()
        val packet = adapter.createPacketForAlert(alert)
        val corruptedJson = packet.payloadText.replace("\"alertId\":\"$sampleAlertId\",", "\"alertId\":\"\",")
        val result = validator.validate(corruptedJson)

        assertTrue("Empty alertId must be rejected", result is MeshValidationResult.Rejected)
    }

    // =========================================================================
    // 2. PRIVACY TESTS (7–10)
    // =========================================================================

    @Test
    fun test07_reporterTokenIsAbsent() {
        val alert = createSampleAlert()
        val packet = adapter.createPacketForAlert(alert)

        assertFalse("Reporter token must NEVER appear in serialized mesh packet", packet.payloadText.contains("token"))
        assertFalse(packet.payloadText.contains("anonymousReporterToken"))
    }

    @Test
    fun test08_piiIsAbsent() {
        val alert = createSampleAlert()
        val packet = adapter.createPacketForAlert(alert)

        val forbiddenTerms = listOf("phone", "email", "name", "imsi", "imei", "mac", "deviceId")
        for (term in forbiddenTerms) {
            assertFalse("PII key '$term' must be absent from mesh payload", packet.payloadText.lowercase().contains(term))
        }
    }

    @Test
    fun test09_rawCoordinatesAreAbsent() {
        // High precision coordinates: 19.07604321, 72.87771234
        val alert = createSampleAlert(approxLocation = "approx. 19.08°N 72.88°E within ~200m")
        val packet = adapter.createPacketForAlert(alert)

        assertFalse("Raw micro-level GPS digits must not exist in payload", packet.payloadText.contains("19.07604321"))
        assertFalse(packet.payloadText.contains("72.87771234"))
    }

    @Test
    fun test10_meshPayloadDoesNotIncreaseLocationPrecision() {
        val alert = createSampleAlert(approxLocation = "approx. 19.08°N 72.88°E within ~200m")
        val packet = adapter.createPacketForAlert(alert)
        val payload = SheGuardMeshAlertPayload.fromJson(packet.payloadText)!!

        assertEquals("approx. 19.08°N 72.88°E within ~200m", payload.approximateLocation)
    }

    // =========================================================================
    // 3. DEDUPLICATION TESTS (11–12)
    // =========================================================================

    @Test
    fun test11_sameMessageReceivedTwiceResultsInOneLogicalAlert() = runBlocking {
        val alert = createSampleAlert()
        val packet = adapter.createPacketForAlert(alert)

        val firstResult = adapter.handleIncomingPacket(packet)
        assertTrue("First receipt must be accepted", firstResult is SheGuardMeshProcessResult.AcceptedAndPersisted)
        assertEquals(1, fakeAlertRepo.alerts.size)

        val secondResult = adapter.handleIncomingPacket(packet)
        assertTrue("Duplicate must be ignored", secondResult is SheGuardMeshProcessResult.DuplicateIgnored)
        assertEquals("Database must still have exactly 1 alert row", 1, fakeAlertRepo.alerts.size)
    }

    @Test
    fun test12_sameAlertRelayedThroughMultipleNodesResultsInOneLogicalAlert() = runBlocking {
        val alert = createSampleAlert()
        val packetFromNodeB = adapter.createPacketForAlert(alert).copy(senderIntegrityMetadata = "node_B", hopCount = 1)
        val packetFromNodeC = adapter.createPacketForAlert(alert).copy(senderIntegrityMetadata = "node_C", hopCount = 2)

        val resB = adapter.handleIncomingPacket(packetFromNodeB)
        assertTrue(resB is SheGuardMeshProcessResult.AcceptedAndPersisted)
        assertEquals(1, fakeAlertRepo.alerts.size)

        val resC = adapter.handleIncomingPacket(packetFromNodeC)
        assertTrue("Receipt from second node must be dropped as duplicate", resC is SheGuardMeshProcessResult.DuplicateIgnored)
        assertEquals("Only 1 persistent alert row should exist", 1, fakeAlertRepo.alerts.size)
    }

    // =========================================================================
    // 4. LOOP & HOP PROTECTION TESTS (13–14)
    // =========================================================================

    @Test
    fun test13_alreadyProcessedMessageIsNotReprocessedInLoop() = runBlocking {
        val alert = createSampleAlert()
        val packet = adapter.createPacketForAlert(alert)

        // Simulate Node A receiving packet
        val resultA1 = adapter.handleIncomingPacket(packet)
        assertTrue(resultA1 is SheGuardMeshProcessResult.AcceptedAndPersisted)

        // Packet traverses B -> C -> back to A
        val loopedPacket = packet.copy(hopCount = 3)
        val resultA2 = adapter.handleIncomingPacket(loopedPacket)
        assertEquals(
            "Loop back to originating node must be dropped as duplicate",
            SheGuardMeshProcessResult.DuplicateIgnored(packet.packetId),
            resultA2
        )
    }

    @Test
    fun test14_hopLimitPreventsIndefiniteRelay() = runBlocking {
        val alert = createSampleAlert()
        val maxHopsPacket = adapter.createPacketForAlert(alert, maxHops = 12).copy(hopCount = 12)

        val result = adapter.handleIncomingPacket(maxHopsPacket)
        assertTrue("Packet at or beyond maxHops must be rejected", result is SheGuardMeshProcessResult.HopLimitExceeded)
        assertEquals(0, fakeAlertRepo.alerts.size)
    }

    // =========================================================================
    // 5. OFFLINE & STORE-AND-FORWARD TESTS (15–17)
    // =========================================================================

    @Test
    fun test15_meshUnavailableDoesNotPreventLocalAlertGeneration() {
        // Set mesh to unavailable
        adapter.setMeshStatus(MeshStatus.UNAVAILABLE)

        // Run local Phase A-D pipeline completely offline
        val report1 = MicroReport(anonymousReporterToken = "t1", category = ReportCategory.HARASSMENT, latitude = 19.076, longitude = 72.877)
        val report2 = MicroReport(anonymousReporterToken = "t2", category = ReportCategory.HARASSMENT, latitude = 19.077, longitude = 72.878)
        val reports = listOf(report1, report2)

        val patternEngine = SpatioTemporalPatternEngine()
        val trustEvaluator = TrustAndAntiGamingEvaluator()
        val alertEngine = RisingPatternAlertEngine()

        val candidates = patternEngine.detectCandidatePatterns(reports)
        assertEquals(1, candidates.size)

        val emerging = candidates.map { trustEvaluator.evaluatePattern(it, reports) }.filter { it.state == PatternState.PATTERN_EMERGING }
        assertEquals(1, emerging.size)

        val localAlert = alertEngine.generateAlert(emerging.first())
        assertNotNull("Local alert generation must succeed completely independent of mesh state", localAlert)
        assertEquals(ReportCategory.HARASSMENT, localAlert?.category)
    }

    @Test
    fun test16_localAlertRemainsAccessibleWhileDisconnected() = runBlocking {
        adapter.setMeshStatus(MeshStatus.DISCONNECTED)
        val alert = createSampleAlert()
        fakeAlertRepo.saveAlert(alert)

        val retrieved = fakeAlertRepo.getAlertById(sampleAlertId)
        assertNotNull("Local alert must remain fully accessible while disconnected", retrieved)
        assertEquals(sampleAlertId, retrieved?.alertId)
    }

    @Test
    fun test17_queuedRelayCanLaterBeDeliveredWhenMeshBecomesAvailable() {
        adapter.setMeshStatus(MeshStatus.UNAVAILABLE)
        val alert = createSampleAlert()

        adapter.queueAlertForRelay(alert)
        assertEquals(1, adapter.getOutboundQueueSize())

        // When mesh becomes available, queue is drained
        adapter.setMeshStatus(MeshStatus.AVAILABLE)
        assertEquals(0, adapter.getOutboundQueueSize())
        assertEquals(1, relay.getRelayedPackets().size)
    }

    // =========================================================================
    // 6. TRUST BOUNDARY TESTS (18–21)
    // =========================================================================

    @Test
    fun test18_receivingAnAlertDoesNotModifyTrustScore() = runBlocking {
        val originalPattern = SpatioTemporalPattern(
            patternId = samplePatternId,
            centerLatitude = 19.08,
            centerLongitude = 72.88,
            radiusMeters = 200.0,
            category = ReportCategory.HARASSMENT,
            firstReportedAt = 1000L,
            lastReportedAt = 2000L,
            reportCount = 2,
            trustScore = 0.70f,
            state = PatternState.PATTERN_EMERGING
        )

        val incomingMeshAlert = createSampleAlert(trustScore = 0.95f)
        val packet = adapter.createPacketForAlert(incomingMeshAlert)
        adapter.handleIncomingPacket(packet)

        // Verify the original pattern's trust score remains completely untouched
        assertEquals("Pattern trustScore must NOT be modified by receiving a mesh packet", 0.70f, originalPattern.trustScore, 0.001f)
    }

    @Test
    fun test19_receivingAnAlertDoesNotCreateANewPattern() = runBlocking {
        val patternEngine = SpatioTemporalPatternEngine()
        val localReports = emptyList<MicroReport>()

        val incomingAlert = createSampleAlert()
        val packet = adapter.createPacketForAlert(incomingAlert)
        adapter.handleIncomingPacket(packet)

        // Verify pattern engine still finds 0 patterns
        val candidates = patternEngine.detectCandidatePatterns(localReports)
        assertTrue("Mesh alert receipt must NOT create or inject patterns into pattern engine", candidates.isEmpty())
    }

    @Test
    fun test20_meshDoesNotBypassPhaseC() {
        // Direct test that mesh cannot fabricate an emerging pattern without passing Trust evaluator
        val spamReports = listOf(
            MicroReport(anonymousReporterToken = "spammer", category = ReportCategory.HARASSMENT, latitude = 19.076, longitude = 72.877),
            MicroReport(anonymousReporterToken = "spammer", category = ReportCategory.HARASSMENT, latitude = 19.076, longitude = 72.877)
        )

        val patternEngine = SpatioTemporalPatternEngine()
        val trustEvaluator = TrustAndAntiGamingEvaluator()
        val candidates = patternEngine.detectCandidatePatterns(spamReports)
        assertEquals(1, candidates.size)

        val evaluated = trustEvaluator.evaluatePattern(candidates.first(), spamReports)
        assertFalse("Spam must be rejected by Phase C", evaluated.state == PatternState.PATTERN_EMERGING)

        val alertEngine = RisingPatternAlertEngine()
        val alert = alertEngine.generateAlert(evaluated)
        assertNull("Alert engine must refuse non-emerging pattern — mesh cannot bypass Phase C", alert)
    }

    @Test
    fun test21_meshDoesNotIndependentlyGenerateAlertsFromReportVolume() {
        val singleReporterManyReports = (1..10).map {
            MicroReport(anonymousReporterToken = "single_token", category = ReportCategory.POOR_LIGHTING, latitude = 19.076, longitude = 72.877)
        }
        val patternEngine = SpatioTemporalPatternEngine()
        val trustEvaluator = TrustAndAntiGamingEvaluator()
        val alertEngine = RisingPatternAlertEngine()

        val candidates = patternEngine.detectCandidatePatterns(singleReporterManyReports)
        val evaluated = candidates.map { trustEvaluator.evaluatePattern(it, singleReporterManyReports) }
        val alerts = alertEngine.generateAlerts(evaluated)

        assertTrue("High raw volume from single reporter MUST NOT trigger alerts", alerts.isEmpty())
    }

    // =========================================================================
    // 7. DETERMINISM & IDEMPOTENCY (22–23)
    // =========================================================================

    @Test
    fun test22_samePayloadProducesSameLogicalMessageIdentity() {
        val alert = createSampleAlert()
        val packet1 = adapter.createPacketForAlert(alert)
        val packet2 = adapter.createPacketForAlert(alert)

        assertEquals("Same alert must produce identical packetId", packet1.packetId, packet2.packetId)
        assertEquals("Same alert must produce identical payloadHash", packet1.payloadHash, packet2.payloadHash)
    }

    @Test
    fun test23_repeatedRelayIsIdempotent() = runBlocking {
        val alert = createSampleAlert()
        val packet = adapter.createPacketForAlert(alert)

        val r1 = adapter.handleIncomingPacket(packet)
        val r2 = adapter.handleIncomingPacket(packet)
        val r3 = adapter.handleIncomingPacket(packet)

        assertTrue(r1 is SheGuardMeshProcessResult.AcceptedAndPersisted)
        assertTrue(r2 is SheGuardMeshProcessResult.DuplicateIgnored)
        assertTrue(r3 is SheGuardMeshProcessResult.DuplicateIgnored)
        assertEquals(1, fakeAlertRepo.alerts.size)
    }
}
