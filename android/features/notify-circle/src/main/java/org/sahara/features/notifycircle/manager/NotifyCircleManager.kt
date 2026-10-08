package org.sahara.features.notifycircle.manager

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import org.sahara.core.domain.models.AuditEvent
import org.sahara.core.domain.models.AuditResult
import org.sahara.core.domain.models.NotifyContact
import org.sahara.core.domain.repository.AuditRepository
import org.sahara.core.domain.repository.ContactRepository
import org.sahara.services.mesh.fallback.EmergencyAlertPayload
import org.sahara.services.mesh.fallback.EscalationFallbackManager
import org.sahara.services.mesh.fallback.SmsDeliveryStatus
import java.util.UUID

enum class ContactDeliveryState {
    PENDING,
    DELIVERED,
    FAILED,
    ACKNOWLEDGED
}

data class ContactDeliveryRecord(
    val contactId: UUID,
    val contactName: String,
    val state: ContactDeliveryState,
    val transportUsed: String,
    val updatedAt: Long = System.currentTimeMillis()
)

class NotifyCircleManager(
    private val contactRepository: ContactRepository,
    private val auditRepository: AuditRepository,
    private val fallbackManager: EscalationFallbackManager
) {

    private val deliveryRecords = mutableMapOf<UUID, ContactDeliveryRecord>()

    fun getContacts(): Flow<List<NotifyContact>> = contactRepository.getContacts()

    suspend fun addContact(contact: NotifyContact): Boolean {
        val currentContacts = contactRepository.getContacts().first()
        if (currentContacts.size >= MAX_CONTACTS) {
            return false // Configurable limit: max 5 contacts
        }
        contactRepository.saveContact(contact)
        auditRepository.recordAudit(
            AuditEvent(
                component = "NOTIFY_CIRCLE",
                action = "ADD_CONTACT",
                result = AuditResult.SUCCESS,
                metadataJson = "{\"displayName\":\"${contact.displayName}\"}"
            )
        )
        return true
    }

    suspend fun removeContact(contactId: UUID) {
        contactRepository.deleteContact(contactId)
        auditRepository.recordAudit(
            AuditEvent(
                component = "NOTIFY_CIRCLE",
                action = "REMOVE_CONTACT",
                result = AuditResult.SUCCESS,
                metadataJson = "{\"contactId\":\"$contactId\"}"
            )
        )
    }

    suspend fun dispatchAlert(
        incidentId: UUID,
        locationText: String? = null,
        locationAgeSeconds: Long? = null,
        evidenceHash: String,
        referenceCode: String,
        latitude: Double? = null,
        longitude: Double? = null
    ): List<ContactDeliveryRecord> {
        val contacts = contactRepository.getContacts().first()
        if (contacts.isEmpty()) {
            auditRepository.recordAudit(
                AuditEvent(
                    component = "NOTIFY_CIRCLE",
                    action = "DISPATCH_ALERT_SKIPPED_NO_CONTACTS",
                    result = AuditResult.SUCCESS,
                    incidentId = incidentId
                )
            )
            return emptyList()
        }

        val results = mutableListOf<ContactDeliveryRecord>()

        for (contact in contacts) {
            if (!contact.notificationPermission) {
                continue
            }

            val sanitizedLocation = if (contact.locationPermission) locationText else null
            val sanitizedAge = if (contact.locationPermission) locationAgeSeconds else null

            val payload = EmergencyAlertPayload(
                incidentId = incidentId.toString(),
                timestamp = System.currentTimeMillis(),
                locationText = sanitizedLocation,
                locationAgeSeconds = sanitizedAge,
                evidenceIntegrityHash = evidenceHash,
                referenceCode = referenceCode,
                latitude = if (contact.locationPermission) latitude else null,
                longitude = if (contact.locationPermission) longitude else null
            )

            val meshPacket = org.sahara.services.mesh.models.MeshPacket(
                packetId = "mesh_${UUID.randomUUID().toString().take(8)}",
                incidentId = incidentId.toString(),
                packetType = org.sahara.services.mesh.models.MeshPacketType.DISTRESS_ALERT,
                createdAt = System.currentTimeMillis(),
                hopCount = 0,
                maxHops = 12,
                senderIntegrityMetadata = "Android/SaharaMeshNode",
                payloadHash = evidenceHash,
                payloadText = payload.formatSmsMessage()
            )

            val escalationResult = fallbackManager.executeEscalation(
                alertPayload = payload,
                contacts = listOf(contact),
                meshPacket = meshPacket
            )

            val smsStatus = escalationResult[contact.displayName]
            val record = if (smsStatus is SmsDeliveryStatus.ACCEPTED_BY_TRANSPORT || smsStatus is SmsDeliveryStatus.SIMULATED_DEMO) {
                ContactDeliveryRecord(
                    contactId = contact.contactId,
                    contactName = contact.displayName,
                    state = ContactDeliveryState.DELIVERED,
                    transportUsed = if (smsStatus is SmsDeliveryStatus.SIMULATED_DEMO) "MOCK_SMS" else "SMS"
                )
            } else {
                ContactDeliveryRecord(
                    contactId = contact.contactId,
                    contactName = contact.displayName,
                    state = ContactDeliveryState.FAILED,
                    transportUsed = "SMS"
                )
            }

            deliveryRecords[contact.contactId] = record
            results.add(record)

            auditRepository.recordAudit(
                AuditEvent(
                    component = "NOTIFY_CIRCLE",
                    action = "DISPATCH_ALERT_CONTACT",
                    result = if (record.state == ContactDeliveryState.DELIVERED) AuditResult.SUCCESS else AuditResult.FAILURE,
                    incidentId = incidentId,
                    metadataJson = "{\"contactId\":\"${contact.contactId}\",\"state\":\"${record.state}\"}"
                )
            )
        }

        return results
    }

    suspend fun recordAcknowledgement(contactId: UUID) {
        val existing = deliveryRecords[contactId]
        if (existing != null) {
            val updated = existing.copy(
                state = ContactDeliveryState.ACKNOWLEDGED,
                updatedAt = System.currentTimeMillis()
            )
            deliveryRecords[contactId] = updated
            auditRepository.recordAudit(
                AuditEvent(
                    component = "NOTIFY_CIRCLE",
                    action = "RECORD_ACKNOWLEDGEMENT",
                    result = AuditResult.SUCCESS,
                    metadataJson = "{\"contactId\":\"$contactId\"}"
                )
            )
        }
    }

    fun getDeliveryRecords(): List<ContactDeliveryRecord> = deliveryRecords.values.toList()

    companion object {
        const val MAX_CONTACTS = 5
    }
}