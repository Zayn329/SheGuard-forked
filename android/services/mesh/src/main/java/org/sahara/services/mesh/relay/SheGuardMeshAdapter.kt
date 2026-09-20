package org.sahara.services.mesh.relay

import org.sahara.core.domain.models.ReportCategory
import org.sahara.core.domain.models.RisingPatternAlert
import org.sahara.core.domain.models.TrustLevel
import org.sahara.core.domain.repository.AlertRepository
import org.sahara.services.mesh.models.MeshPacket
import org.sahara.services.mesh.models.MeshPacketType
import org.sahara.services.mesh.models.SheGuardMeshAlertPayload
import java.security.MessageDigest
import java.text.SimpleDateFormat
import java.util.Collections
import java.util.Date
import java.util.Locale
import java.util.UUID

/**
 * SheGuard Mesh Adapter — Phase E.
 *
 * Bridges the SheGuard domain layer ([RisingPatternAlert], [AlertRepository])
 * with the mesh relay infrastructure ([NearbyConnectionsMeshRelay], [MeshPacket]).
 *
 * ARCHITECTURAL BOUNDARIES (non-negotiable):
 * - Local operation is the fundamental guarantee. Mesh failure or unavailability
 *   MUST NOT break local reporting, detection, trust evaluation, or alert generation.
 * - Mesh transports ALREADY-GENERATED SheGuard alerts. A received alert is NEVER
 *   treated as raw input to increment trust scores or trigger pattern detection.
 * - Deduplication is enforced via [MeshDeduplicationCache]. Duplicate messages
 *   are ignored and never persisted more than once.
 * - Hop limits prevent indefinite circulation in mesh loops (max 12 hops by default).
 * - Location privacy is preserved: payload carries only coarsened ~1km location.
 * - Store-and-forward: alerts generated while mesh is unavailable are queued
 *   and can be drained when mesh connectivity becomes available.
 */
class SheGuardMeshAdapter(
    val meshRelay: NearbyConnectionsMeshRelay = NearbyConnectionsMeshRelay(),
    val validator: MeshPayloadValidator = MeshPayloadValidator(),
    val alertRepository: AlertRepository? = null,
    initialStatus: MeshStatus = MeshStatus.AVAILABLE
) {
    @Volatile
    private var meshStatus: MeshStatus = initialStatus

    // Store-and-forward outbound queue for offline resilience
    private val outboundQueue = Collections.synchronizedList(mutableListOf<MeshPacket>())

    fun getMeshStatus(): MeshStatus = meshStatus

    fun setMeshStatus(status: MeshStatus) {
        meshStatus = status
        if (status == MeshStatus.AVAILABLE) {
            drainOutboundQueue()
        }
    }

    /**
     * Serializes an alert into a [SheGuardMeshAlertPayload] and wraps it in a [MeshPacket].
     * Preserves coarse location, includes no PII, and signs with SHA-256 payload hash.
     */
    fun createPacketForAlert(
        alert: RisingPatternAlert,
        patternStartTime: Long = alert.createdAt,
        patternEndTime: Long = alert.createdAt,
        contributingReportCount: Int = 2,
        senderMetadata: String = "sheguard_node",
        maxHops: Int = 12
    ): MeshPacket {
        val payload = SheGuardMeshAlertPayload(
            protocolVersion = SheGuardMeshAlertPayload.CURRENT_PROTOCOL_VERSION,
            messageId = alert.alertId.toString(),
            messageType = SheGuardMeshAlertPayload.MESSAGE_TYPE,
            alertId = alert.alertId.toString(),
            patternId = alert.patternId.toString(),
            category = alert.category.name,
            approximateLocation = alert.approximateLocation,
            patternStartTime = patternStartTime,
            patternEndTime = patternEndTime,
            generatedAt = alert.createdAt,
            trustLevel = alert.trustLevel.name,
            trustScore = alert.trustScore,
            contributingReportCount = contributingReportCount,
            disclaimer = alert.disclaimer
        )

        val json = payload.toJson()
        val hash = computeSha256(json)

        return MeshPacket(
            packetId = alert.alertId.toString(),
            incidentId = alert.patternId.toString(),
            packetType = MeshPacketType.SHEGUARD_ALERT,
            createdAt = alert.createdAt,
            hopCount = 0,
            maxHops = maxHops,
            senderIntegrityMetadata = senderMetadata,
            payloadHash = hash,
            payloadText = json
        )
    }

    /**
     * Queues an alert for relay. If mesh is currently available, processes it
     * immediately through the relay; otherwise keeps it in the store-and-forward queue.
     */
    fun queueAlertForRelay(
        alert: RisingPatternAlert,
        patternStartTime: Long = alert.createdAt,
        patternEndTime: Long = alert.createdAt,
        contributingReportCount: Int = 2,
        senderMetadata: String = "sheguard_node",
        maxHops: Int = 12
    ): MeshPacket {
        val packet = createPacketForAlert(
            alert = alert,
            patternStartTime = patternStartTime,
            patternEndTime = patternEndTime,
            contributingReportCount = contributingReportCount,
            senderMetadata = senderMetadata,
            maxHops = maxHops
        )

        outboundQueue.add(packet)

        if (meshStatus == MeshStatus.AVAILABLE) {
            meshRelay.processIncomingPacket(packet)
        }

        return packet
    }

    /**
     * Drains the store-and-forward queue when mesh becomes available.
     */
    fun drainOutboundQueue(): List<MeshRelayResult> {
        val results = mutableListOf<MeshRelayResult>()
        synchronized(outboundQueue) {
            val iterator = outboundQueue.iterator()
            while (iterator.hasNext()) {
                val packet = iterator.next()
                val result = meshRelay.processIncomingPacket(packet)
                results.add(result)
                iterator.remove()
            }
        }
        return results
    }

    fun getOutboundQueueSize(): Int = outboundQueue.size

    /**
     * Handles an incoming packet received from a peer device over mesh.
     *
     * Pipeline:
     * 1. Check packet type == SHEGUARD_ALERT.
     * 2. Validate payload structure & invariants (never crashes on malformed data).
     * 3. Process via relay (deduplication check & hop limit enforcement).
     * 4. If accepted: persist locally as a relayed alert ([RisingPatternAlert.isRelayed] = true).
     */
    suspend fun handleIncomingPacket(packet: MeshPacket): SheGuardMeshProcessResult {
        if (packet.packetType != MeshPacketType.SHEGUARD_ALERT) {
            return SheGuardMeshProcessResult.UnrecognizedType(packet.packetType.name)
        }

        // 1. Validation gate
        val validationResult = validator.validate(packet.payloadText)
        if (validationResult is MeshValidationResult.Rejected) {
            return SheGuardMeshProcessResult.Rejected(validationResult.reason)
        }
        val validPayload = (validationResult as MeshValidationResult.Valid).payload

        // 2. Relay deduplication & hop limit gate
        val relayResult = meshRelay.processIncomingPacket(packet)
        return when (relayResult) {
            is MeshRelayResult.DUPLICATE_IGNORED -> {
                SheGuardMeshProcessResult.DuplicateIgnored(packet.packetId)
            }
            is MeshRelayResult.HOP_LIMIT_EXCEEDED -> {
                SheGuardMeshProcessResult.HopLimitExceeded(packet.packetId)
            }
            is MeshRelayResult.ACCEPTED_FOR_RELAY -> {
                val alert = payloadToAlert(validPayload)
                alertRepository?.saveAlert(alert)
                SheGuardMeshProcessResult.AcceptedAndPersisted(
                    payload = validPayload,
                    relayedPacket = relayResult.forwardedPacket
                )
            }
        }
    }

    /**
     * Converts a validated mesh payload into a local [RisingPatternAlert]
     * with `isRelayed = true`.
     */
    fun payloadToAlert(payload: SheGuardMeshAlertPayload): RisingPatternAlert {
        return RisingPatternAlert(
            alertId = UUID.fromString(payload.alertId),
            patternId = UUID.fromString(payload.patternId),
            category = ReportCategory.valueOf(payload.category),
            approximateLocation = payload.approximateLocation,
            timeWindow = formatTimeWindow(payload.patternStartTime, payload.patternEndTime),
            trustLevel = TrustLevel.valueOf(payload.trustLevel),
            trustScore = payload.trustScore,
            createdAt = payload.generatedAt,
            disclaimer = payload.disclaimer,
            isRelayed = true
        )
    }

    private fun formatTimeWindow(start: Long, end: Long): String {
        val fmt = SimpleDateFormat("HH:mm", Locale.getDefault())
        return "${fmt.format(Date(start))}–${fmt.format(Date(end))}"
    }

    private fun computeSha256(input: String): String {
        val digest = MessageDigest.getInstance("SHA-256").digest(input.toByteArray(Charsets.UTF_8))
        return digest.joinToString("") { "%02x".format(it) }
    }
}

enum class MeshStatus {
    AVAILABLE,
    UNAVAILABLE,
    DISCONNECTED
}

sealed class SheGuardMeshProcessResult {
    data class AcceptedAndPersisted(
        val payload: SheGuardMeshAlertPayload,
        val relayedPacket: MeshPacket?
    ) : SheGuardMeshProcessResult()

    data class DuplicateIgnored(val packetId: String) : SheGuardMeshProcessResult()
    data class HopLimitExceeded(val packetId: String) : SheGuardMeshProcessResult()
    data class Rejected(val reason: String) : SheGuardMeshProcessResult()
    data class UnrecognizedType(val type: String) : SheGuardMeshProcessResult()
}
