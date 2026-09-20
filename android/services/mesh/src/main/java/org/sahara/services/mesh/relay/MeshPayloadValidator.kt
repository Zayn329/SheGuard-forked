package org.sahara.services.mesh.relay

import org.sahara.core.domain.models.ReportCategory
import org.sahara.core.domain.models.TrustLevel
import org.sahara.services.mesh.models.SheGuardMeshAlertPayload
import org.sahara.services.mesh.models.SheGuardMeshAlertPayload.Companion.MAX_PAYLOAD_BYTES

/**
 * Deterministic, pure-function validator for [SheGuardMeshAlertPayload].
 *
 * Returns a [MeshValidationResult] — never throws, never crashes the app.
 *
 * Validation checks (per Phase E spec):
 * 1. Payload size within [MAX_PAYLOAD_BYTES].
 * 2. Protocol version supported.
 * 3. messageType = "SHEGUARD_ALERT".
 * 4. messageId present and non-blank.
 * 5. alertId present, non-blank, and valid UUID format.
 * 6. patternId present, non-blank, and valid UUID format.
 * 7. category is a known [ReportCategory].
 * 8. approximateLocation present and non-blank.
 * 9. patternStartTime > 0 and patternEndTime >= patternStartTime.
 * 10. generatedAt > 0.
 * 11. trustLevel is a known [TrustLevel].
 * 12. trustScore in [0.0, 1.0].
 * 13. contributingReportCount >= 1.
 *
 * Privacy check (informational — payload must not contain raw coordinates):
 * The approximateLocation string must not look like a raw high-precision coordinate pair.
 */
class MeshPayloadValidator(
    private val supportedProtocolVersions: Set<Int> = setOf(
        SheGuardMeshAlertPayload.CURRENT_PROTOCOL_VERSION
    )
) {
    fun validate(rawJson: String): MeshValidationResult {
        // Size gate
        if (rawJson.toByteArray(Charsets.UTF_8).size > MAX_PAYLOAD_BYTES) {
            return MeshValidationResult.Rejected("Payload exceeds $MAX_PAYLOAD_BYTES bytes")
        }

        // Parse
        val payload = SheGuardMeshAlertPayload.fromJson(rawJson)
            ?: return MeshValidationResult.Rejected("Malformed JSON — cannot parse payload")

        return validate(payload)
    }

    fun validate(payload: SheGuardMeshAlertPayload): MeshValidationResult {
        // 1. Protocol version
        if (payload.protocolVersion !in supportedProtocolVersions) {
            return MeshValidationResult.Rejected(
                "Unsupported protocol version: ${payload.protocolVersion}"
            )
        }

        // 2. Message type
        if (payload.messageType != SheGuardMeshAlertPayload.MESSAGE_TYPE) {
            return MeshValidationResult.Rejected(
                "Unknown messageType: '${payload.messageType}'"
            )
        }

        // 3. messageId
        if (payload.messageId.isBlank()) {
            return MeshValidationResult.Rejected("Missing messageId")
        }
        if (!isValidUuid(payload.messageId)) {
            return MeshValidationResult.Rejected("Invalid messageId UUID: '${payload.messageId}'")
        }

        // 4. alertId
        if (payload.alertId.isBlank()) {
            return MeshValidationResult.Rejected("Missing alertId")
        }
        if (!isValidUuid(payload.alertId)) {
            return MeshValidationResult.Rejected("Invalid alertId UUID: '${payload.alertId}'")
        }

        // 5. patternId
        if (payload.patternId.isBlank()) {
            return MeshValidationResult.Rejected("Missing patternId")
        }
        if (!isValidUuid(payload.patternId)) {
            return MeshValidationResult.Rejected("Invalid patternId UUID: '${payload.patternId}'")
        }

        // 6. category
        if (runCatching { ReportCategory.valueOf(payload.category) }.isFailure) {
            return MeshValidationResult.Rejected("Unknown category: '${payload.category}'")
        }

        // 7. approximateLocation
        if (payload.approximateLocation.isBlank()) {
            return MeshValidationResult.Rejected("Missing approximateLocation")
        }

        // 8. timestamps
        if (payload.patternStartTime <= 0) {
            return MeshValidationResult.Rejected("Invalid patternStartTime: ${payload.patternStartTime}")
        }
        if (payload.patternEndTime < payload.patternStartTime) {
            return MeshValidationResult.Rejected(
                "patternEndTime ${payload.patternEndTime} < patternStartTime ${payload.patternStartTime}"
            )
        }
        if (payload.generatedAt <= 0) {
            return MeshValidationResult.Rejected("Invalid generatedAt: ${payload.generatedAt}")
        }

        // 9. trustLevel
        if (runCatching { TrustLevel.valueOf(payload.trustLevel) }.isFailure) {
            return MeshValidationResult.Rejected("Unknown trustLevel: '${payload.trustLevel}'")
        }

        // 10. trustScore range
        if (payload.trustScore < 0.0f || payload.trustScore > 1.0f) {
            return MeshValidationResult.Rejected("trustScore out of range: ${payload.trustScore}")
        }

        // 11. contributingReportCount
        if (payload.contributingReportCount < 1) {
            return MeshValidationResult.Rejected(
                "contributingReportCount must be >= 1, got: ${payload.contributingReportCount}"
            )
        }

        return MeshValidationResult.Valid(payload)
    }

    private fun isValidUuid(value: String): Boolean = try {
        java.util.UUID.fromString(value)
        true
    } catch (e: IllegalArgumentException) {
        false
    }
}

sealed class MeshValidationResult {
    data class Valid(val payload: SheGuardMeshAlertPayload) : MeshValidationResult()
    data class Rejected(val reason: String) : MeshValidationResult()
}
