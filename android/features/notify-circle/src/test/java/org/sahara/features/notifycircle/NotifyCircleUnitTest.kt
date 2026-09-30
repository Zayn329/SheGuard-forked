package org.sahara.features.notifycircle

import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.sahara.core.data.repository.AuditRepositoryImpl
import org.sahara.core.data.repository.ContactRepositoryImpl
import org.sahara.core.domain.models.ContactType
import org.sahara.core.domain.models.NotifyContact
import org.sahara.core.testing.fakes.FakeAuditEventDao
import org.sahara.core.testing.fakes.FakeNotifyContactDao
import org.sahara.features.notifycircle.manager.ContactDeliveryState
import org.sahara.features.notifycircle.manager.NotifyCircleManager
import org.sahara.services.mesh.fallback.DemoMockSmsProvider
import org.sahara.services.mesh.fallback.EscalationFallbackManager
import org.sahara.services.mesh.relay.NearbyConnectionsMeshRelay
import java.util.UUID

class NotifyCircleUnitTest {

    private lateinit var contactRepository: ContactRepositoryImpl
    private lateinit var auditRepository: AuditRepositoryImpl
    private lateinit var fallbackManager: EscalationFallbackManager
    private lateinit var circleManager: NotifyCircleManager

    @Before
    fun setup() {
        val fakeContactDao = FakeNotifyContactDao()
        val fakeAuditDao = FakeAuditEventDao()
        contactRepository = ContactRepositoryImpl(fakeContactDao)
        auditRepository = AuditRepositoryImpl(fakeAuditDao)

        val meshRelay = NearbyConnectionsMeshRelay()
        val mockSmsProvider = DemoMockSmsProvider()
        fallbackManager = EscalationFallbackManager(meshRelay, mockSmsProvider, isDebug = true)

        circleManager = NotifyCircleManager(contactRepository, auditRepository, fallbackManager)
    }

    @Test
    fun testMaxContactsLimitEnforced() = runBlocking {
        for (i in 1..5) {
            val success = circleManager.addContact(
                NotifyContact(displayName = "Contact $i", type = ContactType.SMS_ONLY, phoneNumber = "+123456780$i")
            )
            assertTrue("Should allow adding contact $i up to max 5", success)
        }

        val sixthAdd = circleManager.addContact(
            NotifyContact(displayName = "Contact 6", type = ContactType.SMS_ONLY, phoneNumber = "+1234567806")
        )
        assertFalse("Sixth contact must be rejected due to max 5 limit", sixthAdd)

        val contacts = circleManager.getContacts().first()
        assertEquals(5, contacts.size)
    }

    @Test
    fun testIndependentContactDispatchAndLocationPermission() = runBlocking {
        val contactWithLoc = NotifyContact(
            displayName = "Alice",
            type = ContactType.SMS_ONLY,
            phoneNumber = "+111111",
            locationPermission = true
        )
        val contactNoLoc = NotifyContact(
            displayName = "Bob",
            type = ContactType.SMS_ONLY,
            phoneNumber = "+222222",
            locationPermission = false
        )

        circleManager.addContact(contactWithLoc)
        circleManager.addContact(contactNoLoc)

        val incidentId = UUID.randomUUID()
        val records = circleManager.dispatchAlert(
            incidentId = incidentId,
            locationText = "19.0760, 72.8777",
            locationAgeSeconds = 5L,
            evidenceHash = "hash123",
            referenceCode = "REF123"
        )

        assertEquals(2, records.size)
        assertTrue(records.all { it.state == ContactDeliveryState.DELIVERED })

        // Acknowledge Alice's alert
        circleManager.recordAcknowledgement(contactWithLoc.contactId)
        val updatedRecords = circleManager.getDeliveryRecords()
        val aliceRecord = updatedRecords.find { it.contactId == contactWithLoc.contactId }
        assertEquals(ContactDeliveryState.ACKNOWLEDGED, aliceRecord?.state)
    }

    @Test
    fun testOnIncidentActivatedTriggersDispatchAlert() = runBlocking {
        val contact = NotifyContact(
            displayName = "Emergency Contact",
            type = ContactType.SMS_ONLY,
            phoneNumber = "+99999999"
        )
        circleManager.addContact(contact)

        val incidentId = UUID.randomUUID()
        var dispatchCalled = false

        val onIncidentActivated: (UUID) -> Unit = { id ->
            runBlocking {
                val records = circleManager.dispatchAlert(
                    incidentId = id,
                    locationText = "Location A",
                    locationAgeSeconds = 0,
                    evidenceHash = "hash_test",
                    referenceCode = "REF_TEST"
                )
                dispatchCalled = records.isNotEmpty()
            }
        }

        onIncidentActivated(incidentId)

        assertTrue("onIncidentActivated must trigger NotifyCircleManager dispatchAlert", dispatchCalled)
        assertEquals(1, circleManager.getDeliveryRecords().size)
    }
}
