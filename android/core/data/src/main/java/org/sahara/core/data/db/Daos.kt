package org.sahara.core.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface MicroReportDao {
    @Query("SELECT * FROM micro_reports WHERE reportId = :id")
    suspend fun getReportById(id: String): MicroReportEntity?

    @Query("SELECT * FROM micro_reports ORDER BY timestamp DESC")
    fun getAllReports(): Flow<List<MicroReportEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertReport(report: MicroReportEntity)
}

@Dao
interface SpatioTemporalPatternDao {
    @Query("SELECT * FROM spatio_temporal_patterns WHERE patternId = :id")
    suspend fun getPatternById(id: String): SpatioTemporalPatternEntity?

    @Query("SELECT * FROM spatio_temporal_patterns ORDER BY lastReportedAt DESC")
    fun getAllPatterns(): Flow<List<SpatioTemporalPatternEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPattern(pattern: SpatioTemporalPatternEntity)

    @Query("DELETE FROM spatio_temporal_patterns")
    suspend fun clearPatterns()
}

@Dao
interface IncidentDao {
    @Query("SELECT * FROM incidents WHERE incidentId = :id")
    suspend fun getIncidentById(id: String): IncidentEntity?

    @Query("SELECT * FROM incidents ORDER BY createdAt DESC")
    fun getAllIncidents(): Flow<List<IncidentEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertIncident(incident: IncidentEntity)

    @Query("UPDATE incidents SET state = :state, sealedAt = :sealedAt, finalMerkleRoot = :merkleRoot WHERE incidentId = :id")
    suspend fun updateIncidentState(id: String, state: String, sealedAt: Long?, merkleRoot: String?)
}

@Dao
interface EvidenceDao {
    @Query("SELECT * FROM evidence_entries WHERE incidentId = :incidentId ORDER BY createdAt ASC")
    fun getEvidenceForIncident(incidentId: String): Flow<List<EvidenceEntryEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertEvidence(evidence: EvidenceEntryEntity)
}

@Dao
interface DetectionEventDao {
    @Query("SELECT * FROM detection_events ORDER BY occurredAt DESC")
    fun getRecentDetectionEvents(): Flow<List<DetectionEventEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertDetectionEvent(event: DetectionEventEntity)
}

@Dao
interface NotifyContactDao {
    @Query("SELECT * FROM notify_contacts")
    fun getContacts(): Flow<List<NotifyContactEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertContact(contact: NotifyContactEntity)

    @Query("DELETE FROM notify_contacts WHERE contactId = :id")
    suspend fun deleteContact(id: String)
}

@Dao
interface AuditEventDao {
    @Query("SELECT * FROM audit_events ORDER BY occurredAt DESC")
    fun getAuditLogs(): Flow<List<AuditEventEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAudit(audit: AuditEventEntity)
}
