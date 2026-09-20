package org.sahara.core.data.repository

import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.sahara.core.domain.models.MicroReport
import org.sahara.core.domain.models.ReportCategory
import org.sahara.core.domain.models.SyncStatus
import java.util.UUID

class SheGuardReportUnitTest {

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

        // Save report offline
        repository.saveReport(report)

        // Retrieve by ID
        val retrieved = repository.getReportById(reportId)
        assertNotNull(retrieved)
        assertEquals(token, retrieved?.anonymousReporterToken)
        assertEquals(ReportCategory.POOR_LIGHTING, retrieved?.category)
        assertEquals(19.0760, retrieved?.latitude!!, 0.0001)
        assertEquals("Broken streetlights near bus stop", retrieved?.contextDescription)
        assertEquals(SyncStatus.LOCAL, retrieved?.syncStatus)

        // Retrieve list
        val allReports = repository.getAllReports().first()
        assertEquals(1, allReports.size)
        assertEquals(reportId, allReports[0].reportId)
    }

    @Test
    fun testMicroReportCreationWithoutLocationDegradesGracefully() = runBlocking {
        val fakeDao = FakeMicroReportDao()
        val repository = MicroReportRepositoryImpl(fakeDao)

        val report = MicroReport(
            anonymousReporterToken = "anon_token_no_loc",
            category = ReportCategory.HARASSMENT,
            latitude = null,
            longitude = null,
            approximateArea = "Unknown Area",
            contextDescription = "Verbal harassment",
            syncStatus = SyncStatus.LOCAL
        )

        repository.saveReport(report)

        val retrieved = repository.getReportById(report.reportId)
        assertNotNull(retrieved)
        assertNull(retrieved?.latitude)
        assertNull(retrieved?.longitude)
        assertEquals("Unknown Area", retrieved?.approximateArea)
    }
}
