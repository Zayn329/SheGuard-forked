package org.sahara.core.domain.repository

import kotlinx.coroutines.flow.Flow
import org.sahara.core.domain.models.AuditEvent
import org.sahara.core.domain.models.DetectionEvent
import org.sahara.core.domain.models.EvidenceEntry
import org.sahara.core.domain.models.Incident
import org.sahara.core.domain.models.IncidentState
import org.sahara.core.domain.models.MicroReport
import org.sahara.core.domain.models.NotifyContact
import org.sahara.core.domain.models.SpatioTemporalPattern
import java.util.UUID

interface MicroReportRepository {
    suspend fun saveReport(report: MicroReport)
    fun getAllReports(): Flow<List<MicroReport>>
    suspend fun getReportById(id: UUID): MicroReport?
}

interface PatternRepository {
    suspend fun savePattern(pattern: SpatioTemporalPattern)
    fun getAllPatterns(): Flow<List<SpatioTemporalPattern>>
    suspend fun getPatternById(id: UUID): SpatioTemporalPattern?
    suspend fun clearPatterns()
}

interface IncidentRepository {
    suspend fun getIncidentById(id: UUID): Incident?
    fun getAllIncidents(): Flow<List<Incident>>
    suspend fun saveIncident(incident: Incident)
    suspend fun updateState(id: UUID, state: IncidentState, sealedAt: Long? = null, merkleRoot: String? = null)
}

interface EvidenceRepository {
    fun getEvidenceForIncident(incidentId: UUID): Flow<List<EvidenceEntry>>
    suspend fun saveEvidence(evidence: EvidenceEntry)
}

interface AuditRepository {
    fun getAuditLogs(): Flow<List<AuditEvent>>
    suspend fun recordAudit(auditEvent: AuditEvent)
}

interface ContactRepository {
    fun getContacts(): Flow<List<NotifyContact>>
    suspend fun saveContact(contact: NotifyContact)
    suspend fun deleteContact(id: UUID)
}
