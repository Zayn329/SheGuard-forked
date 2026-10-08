package org.sahara.app

import android.app.Application
import org.sahara.app.sync.ReportUploader
import org.sahara.core.data.sync.ReportSyncRegistry

class SaharaApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        // Registered at process start so it also works when the foreground service
        // is restarted by the system (START_STICKY) with no activity running.
        ReportSyncRegistry.handler = { repo -> ReportUploader.syncPending(repo) }
    }
}