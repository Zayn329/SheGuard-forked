package org.sahara.app

import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.sahara.core.data.repository.AuditRepositoryImpl
import org.sahara.core.data.repository.ContactRepositoryImpl
import org.sahara.core.data.repository.EvidenceRepositoryImpl
import org.sahara.core.data.repository.IncidentRepositoryImpl
import org.sahara.core.domain.models.ContactType
import org.sahara.core.domain.models.IncidentState
import org.sahara.core.domain.models.NotifyContact
import org.sahara.core.security.crypto.AesGcmFileStorage
import org.sahara.core.security.crypto.KeyStorageManagerImpl
import org.sahara.core.testing.fakes.FakeAuditEventDao
import org.sahara.core.testing.fakes.FakeEvidenceDao
import org.sahara.core.testing.fakes.FakeIncidentDao
import org.sahara.core.testing.fakes.FakeNotifyContactDao
import org.sahara.features.incident.statemachine.IncidentStateMachine
import org.sahara.features.notifycircle.manager.ContactDeliveryState
import org.sahara.features.notifycircle.manager.NotifyCircleManager
import org.sahara.services.evidence.engine.EvidenceCaptureEngine
import org.sahara.services.evidence.preroll.AudioChunk
import org.sahara.services.evidence.preroll.BoundedAudioPreRollBuffer
import org.sahara.services.mesh.fallback.DemoMockSmsProvider
import org.sahara.services.mesh.fallback.EscalationFallbackManager
import org.sahara.services.mesh.relay.NearbyConnectionsMeshRelay
import java.io.File
import java.util.UUID

class EmergencyResponseIntegrationTest {

    private lateinit var tempDir: File
    private lateinit var incidentRepository: IncidentRepositoryImpl
    private lateinit var evidenceRepository: EvidenceRepositoryImpl
    private lateinit var auditRepository: AuditRepositoryImpl
    private lateinit var contactRepository: ContactRepositoryImpl
    private lateinit var stateMachine: IncidentStateMachine
    private lateinit var notifyCircleManager: NotifyCircleManager
    private lateinit var captureEngine: EvidenceCaptureEngine
    private lateinit var preRollBuffer: BoundedAudioPreRollBuffer

    @Before
    fun setup() = runBlocking {
        tempDir = File(System.getProperty("java.io.tmpdir"), "p1_integration_test_" + System.currentTimeMillis())
        tempDir.mkdirs()

        incidentRepository = IncidentRepositoryImpl(FakeIncidentDao())
        evidenceRepository = EvidenceRepositoryImpl(FakeEvidenceDao())
        auditRepository = AuditRepositoryImpl(FakeAuditEventDao())
        contactRepository = ContactRepositoryImpl(FakeNotifyContactDao())

        stateMachine = IncidentStateMachine(incidentRepository, auditRepository)
        preRollBuffer = BoundedAudioPreRollBuffer(10000L)

        val keyManager = KeyStorageManagerImpl()
        val gcmStorage = AesGcmFileStorage()
        captureEngine = EvidenceCaptureEngine(evidenceRepository, keyManager, gcmStorage, preRollBuffer, tempDir)

        val meshRelay = NearbyConnectionsMeshRelay()
        val smsProvider = DemoMockSmsProvider()
        val fallbackManager = EscalationFallbackManager(meshRelay, smsProvider, isDebug = true)
        notifyCircleManager = NotifyCircleManager(contactRepository, auditRepository, fallbackManager)

        // Add 2 circle contacts
        contactRepository.saveContact(
            NotifyContact(displayName = "Aisha", type = ContactType.SMS_ONLY, phoneNumber = "+91 9876543210", notificationPermission = true)
        )
        contactRepository.saveContact(
            NotifyContact(displayName = "Sara", type = ContactType.SMS_ONLY, phoneNumber = "+91 9876543211", notificationPermission = true)
        )

        // Connect State Machine -> Pre-roll flush & Emergency Circle Dispatch
        stateMachine.onIncidentActivated = { incident ->
            captureEngine.processBufferedPreRoll(incident.incidentId)
            val refCode = "SAHARA-${incident.incidentId.toString().take(6).uppercase()}"
            notifyCircleManager.dispatchAlert(
                incidentId = incident.incidentId,
                locationText = "Bandra West, Mumbai",
                locationAgeSeconds = 0,
                evidenceHash = "HASH_12345678",
                referenceCode = refCode
            )
        }
    }

    @Test
    fun testActiveIncidentTriggersNotificationDispatchAndEvidenceCapture() = runBlocking {
        preRollBuffer.offerChunk(AudioChunk("preroll_chunk_1", ShortArray(1600)))

        // Activate incident
        stateMachine.activateIncident("PANIC_BUTTON")

        val currentInc = stateMachine.currentIncident.value
        assertNotNull(currentInc)
        assertEquals(IncidentState.ACTIVE_INCIDENT, currentInc?.state)

        // Verify evidence capture ran
        val evidenceEntries = evidenceRepository.getEvidenceForIncident(currentInc!!.incidentId).first()
        assertEquals(1, evidenceEntries.size)

        // Verify notify circle delivery records
        val records = notifyCircleManager.getDeliveryRecords()
        assertEquals(2, records.size)
        assertTrue(records.all { it.state == ContactDeliveryState.DELIVERED })
        assertTrue(records.all { it.transportUsed == "MOCK_SMS" })

        // Verify audit log received DISPATCH_ALERT_CONTACT entries
        val auditLogs = auditRepository.getAuditLogs().first()
        assertTrue(auditLogs.any { it.action == "DISPATCH_ALERT_CONTACT" && it.component == "NOTIFY_CIRCLE" })
    }

    @Test
    fun testNotificationFailureDoesNotDisruptLocalIncidentState() = runBlocking {
        // Clear contacts so dispatch alert handles 0 contacts scenario
        val currentContacts = contactRepository.getContacts().first()
        for (c in currentContacts) {
            contactRepository.deleteContact(c.contactId)
        }

        stateMachine.activateIncident("MOTION_ANOMALY")

        val currentInc = stateMachine.currentIncident.value
        assertNotNull(currentInc)
        assertEquals(IncidentState.ACTIVE_INCIDENT, currentInc?.state)

        // Local state machine and incident persist successfully
        val saved = incidentRepository.getIncidentById(currentInc!!.incidentId)
        assertNotNull(saved)
        assertEquals(IncidentState.ACTIVE_INCIDENT, saved?.state)
    }

    @Test
    fun testContactPersistenceInRepository() = runBlocking {
        val initialContacts = contactRepository.getContacts().first()
        assertEquals(2, initialContacts.size)

        val newContact = NotifyContact(
            displayName = "Dr. Mehta",
            type = ContactType.SMS_ONLY,
            phoneNumber = "+91 9999999999"
        )
        contactRepository.saveContact(newContact)

        val updatedContacts = contactRepository.getContacts().first()
        assertEquals(3, updatedContacts.size)
        assertTrue(updatedContacts.any { it.displayName == "Dr. Mehta" })

        contactRepository.deleteContact(newContact.contactId)
        val finalContacts = contactRepository.getContacts().first()
        assertEquals(2, finalContacts.size)
    }

    @Test
    fun testIncomingMeshDistressPayloadTriggersRelayAndPersistence() = runBlocking {
        val meshRelay = NearbyConnectionsMeshRelay()
        val adapter = org.sahara.services.mesh.relay.SheGuardMeshAdapter(meshRelay = meshRelay)

        val distressPacket = org.sahara.services.mesh.models.MeshPacket(
            incidentId = UUID.randomUUID().toString(),
            packetType = org.sahara.services.mesh.models.MeshPacketType.DISTRESS_ALERT,
            senderIntegrityMetadata = "peer_node",
            payloadHash = "hash123",
            payloadText = "HELP"
        )

        val wireBytes = org.sahara.services.mesh.transport.MeshPacketWireCodec.encode(distressPacket)
        val processResult = adapter.handleIncomingWirePayload(wireBytes)

        assertTrue(processResult is org.sahara.services.mesh.relay.SheGuardMeshProcessResult.DistressRelayed)
        assertEquals(distressPacket.packetId, (processResult as org.sahara.services.mesh.relay.SheGuardMeshProcessResult.DistressRelayed).relayedPacket.packetId)
    }
}
