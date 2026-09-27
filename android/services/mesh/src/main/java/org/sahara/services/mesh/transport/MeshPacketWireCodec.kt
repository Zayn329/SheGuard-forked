package org.sahara.services.mesh.transport

import org.sahara.services.mesh.models.MeshPacket
import org.sahara.services.mesh.models.MeshPacketType
import java.util.Base64

/** Small, bounded wire envelope for Nearby byte payloads. */
object MeshPacketWireCodec {
    private const val VERSION = "1"

    fun encode(packet: MeshPacket): ByteArray {
        val values = listOf(
            packet.packetId,
            packet.incidentId,
            packet.packetType.name,
            packet.createdAt.toString(),
            packet.hopCount.toString(),
            packet.maxHops.toString(),
            packet.senderIntegrityMetadata,
            packet.payloadHash,
            packet.payloadText
        ).joinToString("|") { encodePart(it) }
        return "$VERSION|$values".toByteArray(Charsets.UTF_8)
    }

    fun decode(bytes: ByteArray): MeshPacket? = runCatching {
        val parts = bytes.toString(Charsets.UTF_8).split('|')
        if (parts.size != 10 || parts[0] != VERSION) return null
        MeshPacket(
            packetId = decodePart(parts[1]),
            incidentId = decodePart(parts[2]),
            packetType = MeshPacketType.valueOf(decodePart(parts[3])),
            createdAt = decodePart(parts[4]).toLong(),
            hopCount = decodePart(parts[5]).toInt(),
            maxHops = decodePart(parts[6]).toInt(),
            senderIntegrityMetadata = decodePart(parts[7]),
            payloadHash = decodePart(parts[8]),
            payloadText = decodePart(parts[9])
        )
    }.getOrNull()

    private fun encodePart(value: String): String = Base64.getUrlEncoder()
        .withoutPadding()
        .encodeToString(value.toByteArray(Charsets.UTF_8))

    private fun decodePart(value: String): String = Base64.getUrlDecoder().decode(value).toString(Charsets.UTF_8)
}
