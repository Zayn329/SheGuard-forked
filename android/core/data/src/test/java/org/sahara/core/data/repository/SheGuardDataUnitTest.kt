package org.sahara.core.data.repository

import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.sahara.core.domain.models.MicroReport
import org.sahara.core.domain.models.PatternState
import org.sahara.core.domain.models.ReportCategory
import org.sahara.core.domain.models.SpatioTemporalPattern
import org.sahara.core.domain.models.SyncStatus
import java.util.UUID

class SheGuardDataUnitTest {

    private class FakeMicroReportDao : org.sahara.core.data.db.MicroReportDao {
        private val reports = mutableMapOf<String, org.sahara.core.data.db.MicroReportEntity>()

        override suspend fun getReportById(id: String): org.sahara.core.data.db.MicroReportEntity? {
            return reports[id]
        }

        override fun getAllReports(): kotlinx.coroutines.flow.Flow<List<org.sahara.core.data.db.MicroReportEntity>> {
            return kotlinx.coroutines.flow.flowOf(reports.values.toList().sortedByDescending { it.timestamp })
        }

        override suspend fun insertReport(report: org.sahara.core.data.db.MicroReportEntity) {
            reports[report.reportId] = report
        }
    }

    private class FakePatternDao : org.sahara.core.data.db.SpatioTemporalPatternDao {
        private val patterns = mutableMapOf<String, org.sahara.core.data.db.SpatioTemporalPatternEntity>()

        override suspend fun getPatternById(id: String): org.sahara.core.data.db.SpatioTemporalPatternEntity? {
            return patterns[id]
        }

        override fun getAllPatterns(): kotlinx.coroutines.flow.Flow<List<org.sahara.core.data.db.SpatioTemporalPatternEntity>> {
            return kotlinx.coroutines.flow.flowOf(patterns.values.toList().sortedByDescending { it.lastReportedAt })
        }

        override suspend fun insertPattern(pattern: org.sahara.core.data.db.SpatioTemporalPatternEntity) {
            patterns[pattern.patternId] = pattern
        }

        override suspend fun clearPatterns() {
            patterns.clear()
        }
    }

    @Test
    fun testMicroReportCreationAndOfflinePersistence() = runBlocking {
        val fakeDao = FakeMicroReportDao()
        val repository = MicroReportRepositoryImpl(fakeDao)

        val reportId = UUID.randomUUID()
        val token = "anon_session_token_123"
        val report = MicroReport(
            reportId = reportId,
            anonymousReporterToken = token,
            category = ReportCategory.POOR_LIGHTING,
            latitude = 19.0760,
            longitude = 72.8777,
            approximateArea = "Mumbai Central",
            contextDescription = "Broken streetlights near bus stop",
            syncStatus = SyncStatus.LOCAL
        )

        repository.saveReport(report)

        val retrieved = repository.getReportById(reportId)
        assertNotNull(retrieved)
        assertEquals(token, retrieved!!.anonymousReporterToken)
        assertEquals(ReportCategory.POOR_LIGHTING, retrieved.category)
        assertEquals(19.0760, retrieved.latitude!!, 0.0001)
        assertEquals("Broken streetlights near bus stop", retrieved.contextDescription)
        assertEquals(SyncStatus.LOCAL, retrieved.syncStatus)

        val allReports = repository.getAllReports().first()
        assertEquals(1, allReports.size)
        assertEquals(reportId, allReports[0].reportId)
    }

    @Test
    fun testPatternPersistenceAndRetrieval() = runBlocking {
        val fakeDao = FakePatternDao()
        val repository = PatternRepositoryImpl(fakeDao)

        val patternId = UUID.randomUUID()
        val reportId1 = UUID.randomUUID()
        val reportId2 = UUID.randomUUID()

        val pattern = SpatioTemporalPattern(
            patternId = patternId,
            centerLatitude = 19.0760,
            centerLongitude = 72.8777,
            radiusMeters = 250.0,
            category = ReportCategory.POOR_LIGHTING,
            firstReportedAt = System.currentTimeMillis() - 10000,
            lastReportedAt = System.currentTimeMillis(),
            reportCount = 2,
            contributingReportIds = listOf(reportId1, reportId2),
            trustScore = 0.0f,
            state = PatternState.PATTERN_CANDIDATE
        )

        repository.savePattern(pattern)

        val retrieved = repository.getPatternById(patternId)
        assertNotNull(retrieved)
        assertEquals(patternId, retrieved!!.patternId)
        assertEquals(19.0760, retrieved.centerLatitude, 0.0001)
        assertEquals(250.0, retrieved.radiusMeters, 0.0001)
        assertEquals(ReportCategory.POOR_LIGHTING, retrieved.category)
        assertEquals(2, retrieved.reportCount)
        assertEquals(2, retrieved.contributingReportIds.size)
        assertEquals(PatternState.PATTERN_CANDIDATE, retrieved.state)

        val allPatterns = repository.getAllPatterns().first()
        assertEquals(1, allPatterns.size)
        assertEquals(patternId, allPatterns[0].patternId)

        repository.clearPatterns()
        val emptyPatterns = repository.getAllPatterns().first()
        assertTrue(emptyPatterns.isEmpty())
    }
}
