package org.sahara.core.domain.repository

import kotlinx.coroutines.flow.Flow
import org.sahara.core.domain.models.AuditEvent
import org.sahara.core.domain.models.DetectionEvent
import org.sahara.core.domain.models.EvidenceEntry
import org.sahara.core.domain.models.Incident
import org.sahara.core.domain.models.IncidentState
import org.sahara.core.domain.models.MicroReport
import org.sahara.core.domain.models.NotifyContact
import org.sahara.core.domain.models.RisingPatternAlert
import org.sahara.core.domain.models.SpatioTemporalPattern
import java.util.UUID

/** Maximum number of micro-reports that may be saved on the device at one time. */
const val MAX_SAVED_REPORTS = 3

interface MicroReportRepository {
    /**
     * Saves the report only if the persisted report count is below [MAX_SAVED_REPORTS]
     * (or the report already exists and is simply being updated).
     * Returns true if saved, false if the limit was reached. Never deletes existing reports.
     */
    suspend fun saveReport(report: MicroReport): Boolean
    fun getAllReports(): Flow<List<MicroReport>>
    suspend fun getReportById(id: UUID): MicroReport?
    suspend fun getReportCount(): Int
    suspend fun deleteReport(id: UUID)
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

interface AlertRepository {
    fun getAllAlerts(): Flow<List<RisingPatternAlert>>
    suspend fun saveAlert(alert: RisingPatternAlert)
    suspend fun getAlertById(id: UUID): RisingPatternAlert?
    suspend fun clearAlerts()
}