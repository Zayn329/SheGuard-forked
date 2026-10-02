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

    @Test
    fun testTrustEvaluatedEmergingPatternPersistenceAndRetrieval() = runBlocking {
        val fakeDao = FakePatternDao()
        val repository = PatternRepositoryImpl(fakeDao)

        val patternId = UUID.randomUUID()
        val r1 = UUID.randomUUID()
        val r2 = UUID.randomUUID()

        val emergingPattern = SpatioTemporalPattern(
            patternId = patternId,
            centerLatitude = 19.0760,
            centerLongitude = 72.8777,
            radiusMeters = 200.0,
            category = ReportCategory.HARASSMENT,
            firstReportedAt = System.currentTimeMillis() - 15 * 60 * 1000L,
            lastReportedAt = System.currentTimeMillis(),
            reportCount = 2,
            contributingReportIds = listOf(r1, r2),
            trustScore = 0.82f,
            state = PatternState.PATTERN_EMERGING
        )

        repository.savePattern(emergingPattern)

        val retrieved = repository.getPatternById(patternId)
        assertNotNull(retrieved)
        assertEquals(patternId, retrieved!!.patternId)
        assertEquals(0.82f, retrieved.trustScore, 0.001f)
        assertEquals(PatternState.PATTERN_EMERGING, retrieved.state)
        assertEquals(2, retrieved.contributingReportIds.size)
    }

    // ─── Phase D: Alert persistence tests ────────────────────────────────────

    private class FakeAlertDao : org.sahara.core.data.db.RisingPatternAlertDao {
        private val alerts = mutableMapOf<String, org.sahara.core.data.db.RisingPatternAlertEntity>()

        override suspend fun getAlertById(id: String) = alerts[id]

        override fun getAllAlerts(): kotlinx.coroutines.flow.Flow<List<org.sahara.core.data.db.RisingPatternAlertEntity>> =
            kotlinx.coroutines.flow.flowOf(alerts.values.toList().sortedByDescending { it.createdAt })

        override suspend fun insertAlert(alert: org.sahara.core.data.db.RisingPatternAlertEntity) {
            alerts[alert.alertId] = alert
        }

        override suspend fun clearAlerts() {
            alerts.clear()
        }
    }

    @Test
    fun testAlertPersistenceAndRetrieval() = runBlocking {
        val fakeDao = FakeAlertDao()
        val repository = AlertRepositoryImpl(fakeDao)

        val alertId = UUID.randomUUID()
        val patternId = UUID.randomUUID()
        val alert = org.sahara.core.domain.models.RisingPatternAlert(
            alertId = alertId,
            patternId = patternId,
            category = ReportCategory.HARASSMENT,
            approximateLocation = "approx. 19.07°N 72.87°E within ~400m",
            timeWindow = "18:00–18:30",
            trustLevel = org.sahara.core.domain.models.TrustLevel.HIGH,
            trustScore = 0.85f,
            createdAt = System.currentTimeMillis(),
            disclaimer = "EARLY WARNING PATTERN ALERT. NOT A GUARANTEED EMERGENCY RESPONSE."
        )

        repository.saveAlert(alert)

        val retrieved = repository.getAlertById(alertId)
        assertNotNull(retrieved)
        assertEquals(alertId, retrieved!!.alertId)
        assertEquals(patternId, retrieved.patternId)
        assertEquals(ReportCategory.HARASSMENT, retrieved.category)
        assertEquals(org.sahara.core.domain.models.TrustLevel.HIGH, retrieved.trustLevel)
        assertEquals(0.85f, retrieved.trustScore, 0.001f)
        assertEquals(
            "EARLY WARNING PATTERN ALERT. NOT A GUARANTEED EMERGENCY RESPONSE.",
            retrieved.disclaimer
        )

        val all = repository.getAllAlerts().first()
        assertEquals(1, all.size)
    }

    @Test
    fun testAlertClearRemovesAll() = runBlocking {
        val fakeDao = FakeAlertDao()
        val repository = AlertRepositoryImpl(fakeDao)

        repeat(3) {
            repository.saveAlert(
                org.sahara.core.domain.models.RisingPatternAlert(
                    alertId = UUID.randomUUID(),
                    patternId = UUID.randomUUID(),
                    category = ReportCategory.POOR_LIGHTING,
                    approximateLocation = "approx. 19.07°N 72.87°E within ~300m",
                    timeWindow = "20:00–20:10",
                    trustLevel = org.sahara.core.domain.models.TrustLevel.MEDIUM,
                    trustScore = 0.70f,
                    createdAt = System.currentTimeMillis()
                )
            )
        }
        val before = repository.getAllAlerts().first()
        assertEquals(3, before.size)

        repository.clearAlerts()
        val after = repository.getAllAlerts().first()
        assertTrue("clearAlerts must remove all alerts", after.isEmpty())
    }
}
