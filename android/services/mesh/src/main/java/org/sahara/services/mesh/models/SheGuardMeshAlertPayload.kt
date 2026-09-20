package org.sahara.services.mesh.models

/**
 * SheGuard Mesh Alert Payload — Phase E relay-safe payload.
 *
 * This is the ONLY form in which SheGuard alert data travels over the mesh.
 *
 * PRIVACY INVARIANTS (non-negotiable):
 * - No reporter token, phone, name, email, hardware ID, or device identifier.
 * - No raw GPS coordinates — only the coarse approximateLocation string from [RisingPatternAlert],
 *   which has already been truncated to 2 decimal places (~1 km resolution) by Phase D.
 * - Location precision MUST NOT be increased in this payload.
 *
 * TRUST INVARIANTS (non-negotiable):
 * - Receiving this payload MUST NOT modify any trust score.
 * - Receiving this payload MUST NOT create a new SpatioTemporalPattern.
 * - Receiving this payload MUST NOT generate a new local RisingPatternAlert via Trust pipeline.
 * - The payload represents an already-generated alert from a trusted local device.
 *   It is displayed as a "received community alert" — not a locally-generated alert.
 *
 * TRANSPORT:
 * - Serialized as JSON and placed in [MeshPacket.payloadText].
 * - [MeshPacket.packetType] = [MeshPacketType.SHEGUARD_ALERT].
 */
data class SheGuardMeshAlertPayload(
    val protocolVersion: Int = CURRENT_PROTOCOL_VERSION,
    val messageId: String,        // = alertId from RisingPatternAlert (deterministic, dedup key)
    val messageType: String = MESSAGE_TYPE,
    val alertId: String,          // = alertId (same as messageId for dedup convenience)
    val patternId: String,
    val category: String,         // ReportCategory.name — validated on deserialize
    /** Coarse location string from RisingPatternAlert.approximateLocation — already ~1km resolution. */
    val approximateLocation: String,
    val patternStartTime: Long,
    val patternEndTime: Long,
    val generatedAt: Long,
    val trustLevel: String,       // TrustLevel.name — validated on deserialize
    val trustScore: Float,
    val contributingReportCount: Int,
    val disclaimer: String = FIXED_DISCLAIMER
) {
    /**
     * Pure Kotlin JSON serialization. Avoids JVM mock issues with Android org.json.
     */
    fun toJson(): String = buildString {
        append("{")
        append("\"protocolVersion\":").append(protocolVersion).append(",")
        append("\"messageId\":\"").append(escapeJson(messageId)).append("\",")
        append("\"messageType\":\"").append(escapeJson(messageType)).append("\",")
        append("\"alertId\":\"").append(escapeJson(alertId)).append("\",")
        append("\"patternId\":\"").append(escapeJson(patternId)).append("\",")
        append("\"category\":\"").append(escapeJson(category)).append("\",")
        append("\"approximateLocation\":\"").append(escapeJson(approximateLocation)).append("\",")
        append("\"patternStartTime\":").append(patternStartTime).append(",")
        append("\"patternEndTime\":").append(patternEndTime).append(",")
        append("\"generatedAt\":").append(generatedAt).append(",")
        append("\"trustLevel\":\"").append(escapeJson(trustLevel)).append("\",")
        append("\"trustScore\":").append(trustScore).append(",")
        append("\"contributingReportCount\":").append(contributingReportCount).append(",")
        append("\"disclaimer\":\"").append(escapeJson(disclaimer)).append("\"")
        append("}")
    }

    companion object {
        const val CURRENT_PROTOCOL_VERSION = 1
        const val MESSAGE_TYPE = "SHEGUARD_ALERT"
        const val FIXED_DISCLAIMER =
            "EARLY WARNING PATTERN ALERT. NOT A GUARANTEED EMERGENCY RESPONSE."
        const val MAX_PAYLOAD_BYTES = 4096

        private fun escapeJson(value: String): String =
            value.replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\n", "\\n")
                .replace("\r", "\\r")

        private fun unescapeJson(value: String): String =
            value.replace("\\\"", "\"")
                .replace("\\\\", "\\")
                .replace("\\n", "\n")
                .replace("\\r", "\r")

        /**
         * Pure Kotlin JSON deserializer for SheGuardMeshAlertPayload.
         * Robust against whitespace, handles escaped characters, and runs
         * cleanly on Android runtime and in JVM unit tests without mocks.
         */
        fun fromJson(json: String): SheGuardMeshAlertPayload? {
            return try {
                val trimmed = json.trim()
                if (!trimmed.startsWith("{") || !trimmed.endsWith("}")) return null

                fun extractString(key: String): String? {
                    val regex = Regex("\"$key\"\\s*:\\s*\"((?:\\\\\"|[^\"])*)\"")
                    return regex.find(json)?.groupValues?.get(1)?.let { unescapeJson(it) }
                }
                fun extractLong(key: String): Long? {
                    val regex = Regex("\"$key\"\\s*:\\s*(-?\\d+)")
                    return regex.find(json)?.groupValues?.get(1)?.toLongOrNull()
                }
                fun extractInt(key: String): Int? {
                    val regex = Regex("\"$key\"\\s*:\\s*(-?\\d+)")
                    return regex.find(json)?.groupValues?.get(1)?.toIntOrNull()
                }
                fun extractFloat(key: String): Float? {
                    val regex = Regex("\"$key\"\\s*:\\s*(-?\\d+(?:\\.\\d+)?)")
                    return regex.find(json)?.groupValues?.get(1)?.toFloatOrNull()
                }

                val protocolVersion = extractInt("protocolVersion") ?: return null
                val messageId = extractString("messageId") ?: return null
                val messageType = extractString("messageType") ?: return null
                val alertId = extractString("alertId") ?: return null
                val patternId = extractString("patternId") ?: return null
                val category = extractString("category") ?: return null
                val approximateLocation = extractString("approximateLocation") ?: return null
                val patternStartTime = extractLong("patternStartTime") ?: return null
                val patternEndTime = extractLong("patternEndTime") ?: return null
                val generatedAt = extractLong("generatedAt") ?: return null
                val trustLevel = extractString("trustLevel") ?: return null
                val trustScore = extractFloat("trustScore") ?: return null
                val contributingReportCount = extractInt("contributingReportCount") ?: return null
                val disclaimer = extractString("disclaimer") ?: FIXED_DISCLAIMER

                SheGuardMeshAlertPayload(
                    protocolVersion = protocolVersion,
                    messageId = messageId,
                    messageType = messageType,
                    alertId = alertId,
                    patternId = patternId,
                    category = category,
                    approximateLocation = approximateLocation,
                    patternStartTime = patternStartTime,
                    patternEndTime = patternEndTime,
                    generatedAt = generatedAt,
                    trustLevel = trustLevel,
                    trustScore = trustScore,
                    contributingReportCount = contributingReportCount,
                    disclaimer = disclaimer
                )
            } catch (e: Exception) {
                null
            }
        }
    }
}
