package org.sahara.services.mesh.models

import java.util.UUID

enum class MeshPacketType {
    DISTRESS_ALERT,
    APPROXIMATE_LOCATION,
    EVIDENCE_HASH,
    DELIVERY_RECEIPT,
    /** SheGuard Phase E: rising-pattern early-warning alert relay payload. */
    SHEGUARD_ALERT
}

data class MeshPacket(
    val packetId: String = UUID.randomUUID().toString(),
    val incidentId: String,
    val packetType: MeshPacketType,
    val createdAt: Long = System.currentTimeMillis(),
    val hopCount: Int = 0,
    val maxHops: Int = 12,
    val senderIntegrityMetadata: String,
    val payloadHash: String,
    val payloadText: String
)
