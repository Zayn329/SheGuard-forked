package org.sahara.services.mesh.fallback

import org.sahara.core.domain.models.NotifyContact
import org.sahara.services.mesh.models.MeshPacket
import org.sahara.services.mesh.relay.MeshRelayResult
import org.sahara.services.mesh.relay.NearbyConnectionsMeshRelay
import org.sahara.services.mesh.relay.SheGuardMeshAdapter

enum class DeliveryTransportType {
    MESH_NEARBY,
    LOCAL_STORAGE,
    DIRECT_SMS,
    BACKEND_SYNC
}

data class EmergencyAlertPayload(
    val incidentId: String,
    val timestamp: Long,
    val locationText: String? = null,
    val locationAgeSeconds: Long? = null,
    val evidenceIntegrityHash: String,
    val referenceCode: String
) {
    fun formatSmsMessage(): String {
        val locPart = if (!locationText.isNullOrBlank()) {
            val ageInfo = if (locationAgeSeconds != null) " (${locationAgeSeconds}s ago)" else ""
            "\nLoc: $locationText$ageInfo"
        } else ""
        return "[SAHARA EMERGENCY ALERT]\nRef: $referenceCode\nIncident: ${incidentId.take(8)}$locPart\nIntegrity: ${evidenceIntegrityHash.take(8)}\nTime: $timestamp"
    }
}

interface SmsProvider {
    fun sendSms(phoneNumber: String, message: String): SmsDeliveryStatus
}

class SystemSmsProvider : SmsProvider {
    override fun sendSms(phoneNumber: String, message: String): SmsDeliveryStatus {
        return try {
            val smsManager = android.telephony.SmsManager.getDefault()
            smsManager.sendTextMessage(phoneNumber, null, message, null, null)
            SmsDeliveryStatus.ACCEPTED_BY_TRANSPORT
        } catch (e: Throwable) {
            SmsDeliveryStatus.FAILED(e.message ?: "SMS Manager failed")
        }
    }
}

class DemoMockSmsProvider : SmsProvider {
    override fun sendSms(phoneNumber: String, message: String): SmsDeliveryStatus {
        return SmsDeliveryStatus.SIMULATED_DEMO("PASSED USING FALLBACK - Demo SMS sent to $phoneNumber")
    }
}

sealed class SmsDeliveryStatus {
    object ACCEPTED_BY_TRANSPORT : SmsDeliveryStatus()
    data class SIMULATED_DEMO(val message: String) : SmsDeliveryStatus()
    data class FAILED(val reason: String) : SmsDeliveryStatus()
}

class EscalationFallbackManager(
    private val meshRelay: NearbyConnectionsMeshRelay,
    private val smsProvider: SmsProvider,
    private val isDebug: Boolean = false,
    val meshAdapter: SheGuardMeshAdapter? = null
) {

    init {
        if (!isDebug && smsProvider is DemoMockSmsProvider) {
            throw IllegalStateException("DemoMockSmsProvider is strictly forbidden in production/release builds")
        }
    }

    companion object {
        fun createSmsProvider(isDebug: Boolean): SmsProvider {
            return if (isDebug) {
                DemoMockSmsProvider()
            } else {
                SystemSmsProvider()
            }
        }

        fun create(
            meshRelay: NearbyConnectionsMeshRelay,
            isDebug: Boolean,
            customSmsProvider: SmsProvider? = null,
            meshAdapter: SheGuardMeshAdapter? = null
        ): EscalationFallbackManager {
            val provider = if (!isDebug) {
                SystemSmsProvider()
            } else {
                customSmsProvider ?: DemoMockSmsProvider()
            }
            return EscalationFallbackManager(meshRelay, provider, isDebug = isDebug, meshAdapter = meshAdapter)
        }

        fun create(
            meshAdapter: SheGuardMeshAdapter,
            isDebug: Boolean,
            customSmsProvider: SmsProvider? = null
        ): EscalationFallbackManager {
            val provider = if (!isDebug) {
                SystemSmsProvider()
            } else {
                customSmsProvider ?: DemoMockSmsProvider()
            }
            return EscalationFallbackManager(meshAdapter.meshRelay, provider, isDebug = isDebug, meshAdapter = meshAdapter)
        }
    }

    fun executeEscalation(
        alertPayload: EmergencyAlertPayload,
        contacts: List<NotifyContact>,
        meshPacket: MeshPacket?
    ): Map<String, SmsDeliveryStatus> {
        val deliveryResults = mutableMapOf<String, SmsDeliveryStatus>()

        // 1. Mesh Transport Attempt
        if (meshPacket != null) {
            val meshResult = meshRelay.processIncomingPacket(meshPacket)
            if (meshResult is MeshRelayResult.ACCEPTED_FOR_RELAY) {
                meshAdapter?.queuePacketForRelay(meshPacket)
            }
        }

        // 2. Direct SMS Escalation Fallback for eligible SMS contacts
        val smsContacts = contacts.filter { !it.phoneNumber.isNullOrBlank() && it.notificationPermission }
        val smsText = alertPayload.formatSmsMessage()

        for (contact in smsContacts) {
            val status = smsProvider.sendSms(contact.phoneNumber!!, smsText)
            deliveryResults[contact.displayName] = status
        }

        return deliveryResults
    }
}
