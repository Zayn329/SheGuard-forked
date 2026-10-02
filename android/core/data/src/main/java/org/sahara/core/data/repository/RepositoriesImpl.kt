package org.sahara.core.data.repository

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import org.sahara.core.data.db.AuditEventDao
import org.sahara.core.data.db.AuditEventEntity
import org.sahara.core.data.db.EvidenceDao
import org.sahara.core.data.db.EvidenceEntryEntity
import org.sahara.core.data.db.IncidentDao
import org.sahara.core.data.db.IncidentEntity
import org.sahara.core.data.db.MicroReportDao
import org.sahara.core.data.db.MicroReportEntity
import org.sahara.core.data.db.NotifyContactDao
import org.sahara.core.data.db.NotifyContactEntity
import org.sahara.core.data.db.SpatioTemporalPatternDao
import org.sahara.core.data.db.SpatioTemporalPatternEntity
import org.sahara.core.domain.models.AuditEvent
import org.sahara.core.domain.models.AuditResult
import org.sahara.core.domain.models.ContactType
import org.sahara.core.domain.models.EvidenceEntry
import org.sahara.core.domain.models.EvidenceType
import org.sahara.core.domain.models.Incident
import org.sahara.core.domain.models.IncidentState
import org.sahara.core.domain.models.MicroReport
import org.sahara.core.domain.models.NotifyContact
import org.sahara.core.domain.models.PatternState
import org.sahara.core.domain.models.ReportCategory
import org.sahara.core.domain.models.SpatioTemporalPattern
import org.sahara.core.domain.models.SyncStatus
import org.sahara.core.domain.repository.AuditRepository
import org.sahara.core.domain.repository.ContactRepository
import org.sahara.core.domain.repository.EvidenceRepository
import org.sahara.core.domain.repository.IncidentRepository
import org.sahara.core.domain.repository.MicroReportRepository
import org.sahara.core.domain.repository.PatternRepository
import java.util.UUID

class PatternRepositoryImpl(private val patternDao: SpatioTemporalPatternDao) : PatternRepository {
    override suspend fun savePattern(pattern: SpatioTemporalPattern) {
        patternDao.insertPattern(pattern.toEntity())
    }

    override fun getAllPatterns(): Flow<List<SpatioTemporalPattern>> {
        return patternDao.getAllPatterns().map { list -> list.map { it.toDomain() } }
    }

    override suspend fun getPatternById(id: UUID): SpatioTemporalPattern? {
        return patternDao.getPatternById(id.toString())?.toDomain()
    }

    override suspend fun clearPatterns() {
        patternDao.clearPatterns()
    }

    private fun SpatioTemporalPatternEntity.toDomain() = SpatioTemporalPattern(
        patternId = UUID.fromString(patternId),
        centerLatitude = centerLatitude,
        centerLongitude = centerLongitude,
        radiusMeters = radiusMeters,
        category = ReportCategory.valueOf(category),
        firstReportedAt = firstReportedAt,
        lastReportedAt = lastReportedAt,
        reportCount = reportCount,
        contributingReportIds = contributingReportIdsJson.removeSurrounding("[", "]")
            .split(",")
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .map { UUID.fromString(it) },
        trustScore = trustScore,
        state = PatternState.valueOf(state)
    )

    private fun SpatioTemporalPattern.toEntity() = SpatioTemporalPatternEntity(
        patternId = patternId.toString(),
        centerLatitude = centerLatitude,
        centerLongitude = centerLongitude,
        radiusMeters = radiusMeters,
        category = category.name,
        firstReportedAt = firstReportedAt,
        lastReportedAt = lastReportedAt,
        reportCount = reportCount,
        contributingReportIdsJson = "[${contributingReportIds.joinToString(",")}]",
        trustScore = trustScore,
        state = state.name
    )
}

class MicroReportRepositoryImpl(private val microReportDao: MicroReportDao) : MicroReportRepository {
    override suspend fun saveReport(report: MicroReport) {
        microReportDao.insertReport(report.toEntity())
    }

    override fun getAllReports(): Flow<List<MicroReport>> {
        return microReportDao.getAllReports().map { list -> list.map { it.toDomain() } }
    }

    override suspend fun getReportById(id: UUID): MicroReport? {
        return microReportDao.getReportById(id.toString())?.toDomain()
    }

    private fun MicroReportEntity.toDomain() = MicroReport(
        reportId = UUID.fromString(reportId),
        anonymousReporterToken = anonymousReporterToken,
        category = ReportCategory.valueOf(category),
        latitude = latitude,
        longitude = longitude,
        approximateArea = approximateArea,
        timestamp = timestamp,
        contextDescription = contextDescription,
        syncStatus = SyncStatus.valueOf(syncStatus)
    )

    private fun MicroReport.toEntity() = MicroReportEntity(
        reportId = reportId.toString(),
        anonymousReporterToken = anonymousReporterToken,
        category = category.name,
        latitude = latitude,
        longitude = longitude,
        approximateArea = approximateArea,
        timestamp = timestamp,
        contextDescription = contextDescription,
        syncStatus = syncStatus.name
    )
}

class IncidentRepositoryImpl(private val incidentDao: IncidentDao) : IncidentRepository {
    override suspend fun getIncidentById(id: UUID): Incident? {
        return incidentDao.getIncidentById(id.toString())?.toDomain()
    }

    override fun getAllIncidents(): Flow<List<Incident>> {
        return incidentDao.getAllIncidents().map { list -> list.map { it.toDomain() } }
    }

    override suspend fun saveIncident(incident: Incident) {
        incidentDao.insertIncident(incident.toEntity())
    }

    override suspend fun updateState(id: UUID, state: IncidentState, sealedAt: Long?, merkleRoot: String?) {
        incidentDao.updateIncidentState(id.toString(), state.name, sealedAt, merkleRoot)
    }

    private fun IncidentEntity.toDomain() = Incident(
        incidentId = UUID.fromString(incidentId),
        state = IncidentState.valueOf(state),
        createdAt = createdAt,
        activatedAt = activatedAt,
        sealedAt = sealedAt,
        triggerSources = triggerSourcesJson.removeSurrounding("[", "]").split(",").map { it.trim() }.filter { it.isNotEmpty() },
        configurationSnapshotJson = configurationSnapshotJson,
        finalMerkleRoot = finalMerkleRoot
    )

    private fun Incident.toEntity() = IncidentEntity(
        incidentId = incidentId.toString(),
        state = state.name,
        createdAt = createdAt,
        activatedAt = activatedAt,
        sealedAt = sealedAt,
        triggerSourcesJson = "[${triggerSources.joinToString(",")}]",
        configurationSnapshotJson = configurationSnapshotJson,
        finalMerkleRoot = finalMerkleRoot
    )
}

class EvidenceRepositoryImpl(private val evidenceDao: EvidenceDao) : EvidenceRepository {
    override fun getEvidenceForIncident(incidentId: UUID): Flow<List<EvidenceEntry>> {
        return evidenceDao.getEvidenceForIncident(incidentId.toString()).map { list -> list.map { it.toDomain() } }
    }

    override suspend fun saveEvidence(evidence: EvidenceEntry) {
        evidenceDao.insertEvidence(evidence.toEntity())
    }

    private fun EvidenceEntryEntity.toDomain() = EvidenceEntry(
        evidenceId = UUID.fromString(evidenceId),
        incidentId = UUID.fromString(incidentId),
        type = EvidenceType.valueOf(type),
        createdAt = createdAt,
        encryptedPath = encryptedPath,
        sha256 = sha256,
        signatureReference = signatureReference,
        chunkIndex = chunkIndex
    )

    private fun EvidenceEntry.toEntity() = EvidenceEntryEntity(
        evidenceId = evidenceId.toString(),
        incidentId = incidentId.toString(),
        type = type.name,
        createdAt = createdAt,
        encryptedPath = encryptedPath,
        sha256 = sha256,
        signatureReference = signatureReference,
        chunkIndex = chunkIndex
    )
}

class AuditRepositoryImpl(private val auditDao: AuditEventDao) : AuditRepository {
    override fun getAuditLogs(): Flow<List<AuditEvent>> {
        return auditDao.getAuditLogs().map { list -> list.map { it.toDomain() } }
    }

    override suspend fun recordAudit(auditEvent: AuditEvent) {
        auditDao.insertAudit(auditEvent.toEntity())
    }

    private fun AuditEventEntity.toDomain() = AuditEvent(
        auditId = UUID.fromString(auditId),
        occurredAt = occurredAt,
        component = component,
        action = action,
        result = AuditResult.valueOf(result),
        incidentId = incidentId?.let { UUID.fromString(it) },
        metadataJson = metadataJson
    )

    private fun AuditEvent.toEntity() = AuditEventEntity(
        auditId = auditId.toString(),
        occurredAt = occurredAt,
        component = component,
        action = action,
        result = result.name,
        incidentId = incidentId?.toString(),
        metadataJson = metadataJson
    )
}

class ContactRepositoryImpl(private val contactDao: NotifyContactDao) : ContactRepository {
    override fun getContacts(): Flow<List<NotifyContact>> {
        return contactDao.getContacts().map { list -> list.map { it.toDomain() } }
    }

    override suspend fun saveContact(contact: NotifyContact) {
        contactDao.insertContact(contact.toEntity())
    }

    override suspend fun deleteContact(id: UUID) {
        contactDao.deleteContact(id.toString())
    }

    private fun NotifyContactEntity.toDomain() = NotifyContact(
        contactId = UUID.fromString(contactId),
        displayName = displayName,
        type = ContactType.valueOf(type),
        phoneNumber = phoneNumber,
        appUserId = appUserId?.let { UUID.fromString(it) },
        locationPermission = locationPermission,
        notificationPermission = notificationPermission
    )

    private fun NotifyContact.toEntity() = NotifyContactEntity(
        contactId = contactId.toString(),
        displayName = displayName,
        type = type.name,
        phoneNumber = phoneNumber,
        appUserId = appUserId?.toString(),
        locationPermission = locationPermission,
        notificationPermission = notificationPermission
    )
}

class AlertRepositoryImpl(
    private val alertDao: org.sahara.core.data.db.RisingPatternAlertDao
) : org.sahara.core.domain.repository.AlertRepository {

    override fun getAllAlerts(): kotlinx.coroutines.flow.Flow<List<org.sahara.core.domain.models.RisingPatternAlert>> {
        return alertDao.getAllAlerts().map { list -> list.map { it.toDomain() } }
    }

    override suspend fun saveAlert(alert: org.sahara.core.domain.models.RisingPatternAlert) {
        alertDao.insertAlert(alert.toEntity())
    }

    override suspend fun getAlertById(id: UUID): org.sahara.core.domain.models.RisingPatternAlert? {
        return alertDao.getAlertById(id.toString())?.toDomain()
    }

    override suspend fun clearAlerts() {
        alertDao.clearAlerts()
    }

    private fun org.sahara.core.data.db.RisingPatternAlertEntity.toDomain() =
        org.sahara.core.domain.models.RisingPatternAlert(
            alertId = UUID.fromString(alertId),
            patternId = UUID.fromString(patternId),
            category = ReportCategory.valueOf(category),
            approximateLocation = approximateLocation,
            timeWindow = timeWindow,
            trustLevel = org.sahara.core.domain.models.TrustLevel.valueOf(trustLevel),
            trustScore = trustScore,
            createdAt = createdAt,
            disclaimer = disclaimer,
            isRelayed = isRelayed
        )

    private fun org.sahara.core.domain.models.RisingPatternAlert.toEntity() =
        org.sahara.core.data.db.RisingPatternAlertEntity(
            alertId = alertId.toString(),
            patternId = patternId.toString(),
            category = category.name,
            approximateLocation = approximateLocation,
            timeWindow = timeWindow,
            trustLevel = trustLevel.name,
            trustScore = trustScore,
            createdAt = createdAt,
            disclaimer = disclaimer,
            isRelayed = isRelayed
        )
}
