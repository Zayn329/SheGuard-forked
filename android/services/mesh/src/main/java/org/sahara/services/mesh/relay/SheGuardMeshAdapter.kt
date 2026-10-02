package org.sahara.services.mesh.relay

import org.sahara.core.domain.models.ReportCategory
import org.sahara.core.domain.models.RisingPatternAlert
import org.sahara.core.domain.models.TrustLevel
import org.sahara.core.domain.repository.AlertRepository
import org.sahara.services.mesh.models.MeshPacket
import org.sahara.services.mesh.models.MeshPacketType
import org.sahara.services.mesh.models.SheGuardMeshAlertPayload
import org.sahara.services.mesh.transport.MeshPacketWireCodec
import org.sahara.services.mesh.transport.MeshTransport
import org.sahara.services.mesh.transport.MeshTransportResult
import org.sahara.services.mesh.util.MeshLogger
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
    initialStatus: MeshStatus = MeshStatus.AVAILABLE,
    val transport: MeshTransport? = null
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
     * Queues any generic MeshPacket (such as DISTRESS_ALERT) for mesh relay.
     * Attempts immediate transmission if transport is connected, or adds to store-and-forward queue.
     */
    fun queuePacketForRelay(packet: MeshPacket): MeshPacket {
        outboundQueue.add(packet)
        MeshLogger.i("MESSAGE_QUEUED: packetId=${packet.packetId}, type=${packet.packetType}, queueSize=${outboundQueue.size}")

        if (transport != null) {
            sendToConnectedPeers(packet)
        } else if (meshStatus == MeshStatus.AVAILABLE) {
            meshRelay.processIncomingPacket(packet)
        }

        return packet
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

        return queuePacketForRelay(packet)
    }

    /**
     * Drains the store-and-forward queue when mesh becomes available.
     */
    fun drainOutboundQueue(): List<MeshRelayResult> {
        if (transport != null) {
            val results = mutableListOf<MeshRelayResult>()
            synchronized(outboundQueue) {
                outboundQueue.toList().forEach { packet ->
                    if (sendToConnectedPeers(packet) > 0) {
                        results.add(MeshRelayResult.ACCEPTED_FOR_RELAY(packet))
                    }
                }
            }
            return results
        }
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

    /** Sends a wire packet to every currently connected Nearby endpoint. */
    fun sendToConnectedPeers(packet: MeshPacket): Int {
        val currentTransport = transport ?: return 0
        val bytes = MeshPacketWireCodec.encode(packet)
        var sentCount = 0
        currentTransport.peers.value.forEach { peer ->
            if (currentTransport.send(peer.endpointId, bytes) is MeshTransportResult.Accepted) {
                sentCount++
            }
        }
        if (sentCount > 0) {
            MeshLogger.i("MESSAGE_SENT: packetId=${packet.packetId}, type=${packet.packetType}, sentPeers=$sentCount, totalPeers=${currentTransport.peers.value.size}")
            synchronized(outboundQueue) {
                outboundQueue.removeAll { it.packetId == packet.packetId }
            }
        } else {
            MeshLogger.w("MESSAGE_SEND_FAILED: packetId=${packet.packetId}, connectedPeersCount=${currentTransport.peers.value.size}, remainingInQueue=${outboundQueue.size}")
        }
        return sentCount
    }

    /** Decodes and validates a packet received from the real transport. */
    suspend fun handleIncomingWirePayload(bytes: ByteArray): SheGuardMeshProcessResult {
        val packet = MeshPacketWireCodec.decode(bytes)
        if (packet == null) {
            MeshLogger.w("MESSAGE_RECEIVE_FAILED: Malformed mesh packet envelope")
            return SheGuardMeshProcessResult.Rejected("Malformed mesh packet envelope")
        }
        return handleIncomingPacket(packet)
    }

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
        MeshLogger.i("MESSAGE_RECEIVED: packetId=${packet.packetId}, type=${packet.packetType}, hopCount=${packet.hopCount}")

        if (packet.packetType == MeshPacketType.DISTRESS_ALERT) {
            val relayResult = meshRelay.processIncomingPacket(packet)
            return when (relayResult) {
                is MeshRelayResult.DUPLICATE_IGNORED -> {
                    MeshLogger.i("DUPLICATE_IGNORED: Distress packetId=${packet.packetId}")
                    SheGuardMeshProcessResult.DuplicateIgnored(packet.packetId)
                }
                is MeshRelayResult.HOP_LIMIT_EXCEEDED -> {
                    MeshLogger.w("HOP_LIMIT_EXCEEDED: Distress packetId=${packet.packetId}, hopCount=${packet.hopCount}")
                    SheGuardMeshProcessResult.HopLimitExceeded(packet.packetId)
                }
                is MeshRelayResult.ACCEPTED_FOR_RELAY -> {
                    MeshLogger.i("MESSAGE_RELAYED: Distress alert packetId=${packet.packetId}")
                    if (transport != null) {
                        sendToConnectedPeers(relayResult.forwardedPacket)
                    }
                    SheGuardMeshProcessResult.DistressRelayed(relayResult.forwardedPacket)
                }
            }
        }

        if (packet.packetType != MeshPacketType.SHEGUARD_ALERT) {
            MeshLogger.w("MESSAGE_REJECTED: Unrecognized packetType='${packet.packetType.name}'")
            return SheGuardMeshProcessResult.UnrecognizedType(packet.packetType.name)
        }

        // 1. Validation gate
        val validationResult = validator.validate(packet.payloadText)
        if (validationResult is MeshValidationResult.Rejected) {
            MeshLogger.w("MESSAGE_REJECTED: Validation failed for packetId=${packet.packetId}, reason=${validationResult.reason}")
            return SheGuardMeshProcessResult.Rejected(validationResult.reason)
        }
        val validPayload = (validationResult as MeshValidationResult.Valid).payload

        if (!packet.payloadHash.equals(computeSha256(packet.payloadText), ignoreCase = true)) {
            MeshLogger.w("MESSAGE_REJECTED: Hash mismatch for packetId=${packet.packetId}")
            return SheGuardMeshProcessResult.Rejected("Payload integrity hash mismatch")
        }

        // 2. Relay deduplication & hop limit gate
        val relayResult = meshRelay.processIncomingPacket(packet)
        return when (relayResult) {
            is MeshRelayResult.DUPLICATE_IGNORED -> {
                MeshLogger.i("DUPLICATE_IGNORED: packetId=${packet.packetId}")
                SheGuardMeshProcessResult.DuplicateIgnored(packet.packetId)
            }
            is MeshRelayResult.HOP_LIMIT_EXCEEDED -> {
                MeshLogger.w("HOP_LIMIT_EXCEEDED: packetId=${packet.packetId}, hopCount=${packet.hopCount}")
                SheGuardMeshProcessResult.HopLimitExceeded(packet.packetId)
            }
            is MeshRelayResult.ACCEPTED_FOR_RELAY -> {
                val alert = payloadToAlert(validPayload)
                alertRepository?.saveAlert(alert)
                MeshLogger.i("MESSAGE_RELAYED: Successfully persisted relayed alert alertId=${alert.alertId}, category=${alert.category}, trustLevel=${alert.trustLevel}")
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

    data class DistressRelayed(val relayedPacket: MeshPacket) : SheGuardMeshProcessResult()
    data class DuplicateIgnored(val packetId: String) : SheGuardMeshProcessResult()
    data class HopLimitExceeded(val packetId: String) : SheGuardMeshProcessResult()
    data class Rejected(val reason: String) : SheGuardMeshProcessResult()
    data class UnrecognizedType(val type: String) : SheGuardMeshProcessResult()
}
