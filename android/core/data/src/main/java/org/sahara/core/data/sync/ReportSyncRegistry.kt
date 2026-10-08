package org.sahara.core.data.sync

import org.sahara.core.domain.repository.MicroReportRepository

/**
 * Lets modules that cannot depend on :android:app (e.g. features:incident) trigger
 * report upload. The app module registers the real implementation at process start.
 */
object ReportSyncRegistry {
    @Volatile
    var handler: (suspend (MicroReportRepository) -> Unit)? = null
}