package org.sahara.app.sync

import android.content.Context
import kotlinx.coroutines.flow.first
import org.json.JSONArray
import org.json.JSONObject
import org.sahara.app.ui.SaharaApiClient
import org.sahara.core.domain.models.SyncStatus
import org.sahara.core.domain.repository.MicroReportRepository
import java.util.UUID

/**
 * Uploads anonymous micro-reports to the responder dashboard backend.
 * - Offline-safe: on any failure reports stay LOCAL and are retried next time.
 * - Privacy: coordinates rounded to 3 decimals (~110 m), address trimmed to locality + city,
 *   free-text contextDescription is NEVER uploaded, no login / user id is sent.
 */
object ReportUploader {
    private const val PREFS = "sheguard_prefs"
    private const val KEY_TOKEN = "reporter_token"

    /** Stable anonymous token so the server can count UNIQUE reporters. Use this in the reporting screen. */
    fun reporterToken(context: Context): String {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        return prefs.getString(KEY_TOKEN, null)
            ?: UUID.randomUUID().toString().take(12).also { prefs.edit().putString(KEY_TOKEN, it).apply() }
    }

    private fun round3(v: Double) = Math.round(v * 1000.0) / 1000.0

    // Geocoder gives "12, Street, Locality, City, State PIN, India" -> keep "Locality, City"
    private fun coarseArea(address: String): String =
        address.split(",").map { it.trim() }.filter { it.isNotEmpty() }
            .takeLast(4).take(2).joinToString(", ")

    /** Returns number of reports uploaded. Never throws. */
    suspend fun syncPending(repository: MicroReportRepository): Int {
        return try {
            val pending = repository.getAllReports().first()
                .filter { it.syncStatus != SyncStatus.SYNCED && it.latitude != null && it.longitude != null }
            if (pending.isEmpty()) return 0

            val arr = JSONArray()
            pending.forEach { r ->
                arr.put(JSONObject().apply {
                    put("report_id", r.reportId.toString())
                    put("reporter_token", r.anonymousReporterToken)
                    put("category", r.category.name)
                    put("latitude", round3(r.latitude!!))
                    put("longitude", round3(r.longitude!!))
                    put("approximate_area", coarseArea(r.approximateArea))
                    put("timestamp", r.timestamp)
                })
            }
            val body = JSONObject().put("reports", arr).toString()

            // bearerToken = null -> upload endpoint is open (no OTP needed)
            val resp = JSONObject(SaharaApiClient.postJson("/api/v1/reports/batch", body, bearerToken = null))
            val accepted = resp.getJSONArray("accepted_report_ids").let { a -> (0 until a.length()).map { a.getString(it) }.toSet() }

            var uploaded = 0
            pending.filter { it.reportId.toString() in accepted }.forEach {
                repository.saveReport(it.copy(syncStatus = SyncStatus.SYNCED)) // update, not a new insert
                uploaded++
            }
            uploaded
        } catch (e: Exception) {
            0 // offline / server down: keep LOCAL, retry on next call
        }
    }
}
