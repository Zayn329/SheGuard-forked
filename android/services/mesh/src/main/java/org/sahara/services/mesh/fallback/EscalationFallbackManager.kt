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
    val referenceCode: String,
    val latitude: Double? = null,
    val longitude: Double? = null
) {
    fun formatSmsMessage(): String {
        val locPart = if (!locationText.isNullOrBlank()) {
            val ageInfo = if (locationAgeSeconds != null) " (${locationAgeSeconds}s ago)" else ""
            "\nLoc: $locationText$ageInfo"
        } else ""
        return "[SAHARA EMERGENCY ALERT]\nRef: $referenceCode\nIncident: ${incidentId.take(8)}$locPart\nIntegrity: ${evidenceIntegrityHash.take(8)}\nTime: $timestamp"
    }
}

/**
 * Plain-language SMS for the person receiving the alert (no hashes / IDs).
 * [formatSmsMessage] stays as the technical form used for the mesh relay payload.
 */
fun EmergencyAlertPayload.formatHumanSmsMessage(): String {
    val time = java.text.SimpleDateFormat("h:mm a, dd MMM", java.util.Locale.getDefault())
        .format(java.util.Date(timestamp))
    val locPart = when {
        latitude != null && longitude != null -> {
            val coords = String.format(java.util.Locale.US, "%.5f,%.5f", latitude, longitude)
            val ageNote = if (locationAgeSeconds != null && locationAgeSeconds > 120L) {
                " (last known, ${locationAgeSeconds / 60} min ago)"
            } else ""
            " Location: ${coords.replace(",", ", ")}$ageNote. Map: https://maps.google.com/?q=$coords"
        }
        !locationText.isNullOrBlank() -> " Last known location: $locationText."
        else -> ""
    }
    return "EMERGENCY ALERT from SheGuard: The person who added you as a trusted contact " +
            "may be in danger and needs help.$locPart " +
            "Please call or check on them right away. " +
            "If you cannot reach them, call the police on 112. " +
            "Sent automatically at $time. Ref: $referenceCode"
}

interface SmsProvider {
    fun sendSms(phoneNumber: String, message: String): SmsDeliveryStatus
}

class SystemSmsProvider(private val context: android.content.Context? = null) : SmsProvider {
    override fun sendSms(phoneNumber: String, message: String): SmsDeliveryStatus {
        if (context != null &&
            androidx.core.content.ContextCompat.checkSelfPermission(
                context, android.Manifest.permission.SEND_SMS
            ) != android.content.pm.PackageManager.PERMISSION_GRANTED
        ) {
            return SmsDeliveryStatus.FAILED("SEND_SMS permission not granted")
        }
        return try {
            @Suppress("DEPRECATION")
            val smsManager: android.telephony.SmsManager =
                if (context != null && android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S) {
                    context.getSystemService(android.telephony.SmsManager::class.java)
                } else {
                    android.telephony.SmsManager.getDefault()
                }
            val parts = smsManager.divideMessage(message)
            if (parts.size > 1) {
                smsManager.sendMultipartTextMessage(phoneNumber, null, parts, null, null)
            } else {
                smsManager.sendTextMessage(phoneNumber, null, message, null, null)
            }
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
        val smsText = alertPayload.formatHumanSmsMessage()

        for (contact in smsContacts) {
            val status = smsProvider.sendSms(contact.phoneNumber!!, smsText)
            deliveryResults[contact.displayName] = status
        }

        return deliveryResults
    }
}