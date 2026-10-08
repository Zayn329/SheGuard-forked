package org.sahara.app.location

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.location.Address
import android.location.Geocoder
import android.location.Location
import android.location.LocationManager
import android.os.Build
import androidx.core.content.ContextCompat
import com.google.android.gms.location.CurrentLocationRequest
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.android.gms.tasks.CancellationTokenSource
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.util.Locale
import kotlin.coroutines.resume
import kotlin.math.roundToInt

data class SheGuardLocation(
    val latitude: Double,
    val longitude: Double,
    val accuracy: Float?,
    val readableAddress: String,
    val isApproximateOnly: Boolean
) {
    fun getAccuracyDescription(): String {
        return when {
            accuracy == null -> "Accuracy unknown"
            accuracy <= 20f -> "High accuracy (±${accuracy.roundToInt()}m)"
            accuracy <= 100f -> "Street level (±${accuracy.roundToInt()}m)"
            accuracy <= 500f -> "Neighborhood level (±${accuracy.roundToInt()}m)"
            else -> "Approximate area (±${accuracy.roundToInt()}m)"
        }
    }
}

/** Raw GPS fix for emergency SMS: works fully offline (no address lookup needed). */
data class EmergencyFix(
    val latitude: Double,
    val longitude: Double,
    val ageSeconds: Long,
    val accuracyMeters: Float?
)

sealed class LocationState {
    object Idle : LocationState()
    object Fetching : LocationState()
    object PermissionRequired : LocationState()
    data class Success(val location: SheGuardLocation) : LocationState()
    data class Error(val message: String, val isGpsDisabled: Boolean = false) : LocationState()
}

class DeviceLocationManager(
    private val context: Context,
    private val fusedLocationClient: FusedLocationProviderClient = LocationServices.getFusedLocationProviderClient(context)
) {

    fun hasFineLocationPermission(): Boolean {
        return ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
    }

    fun hasCoarseLocationPermission(): Boolean {
        return ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.ACCESS_COARSE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
    }

    fun hasAnyLocationPermission(): Boolean {
        return hasFineLocationPermission() || hasCoarseLocationPermission()
    }

    fun isLocationServicesEnabled(): Boolean {
        val locationManager = context.getSystemService(Context.LOCATION_SERVICE) as? LocationManager ?: return false
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            locationManager.isLocationEnabled
        } else {
            locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER) ||
                    locationManager.isProviderEnabled(LocationManager.NETWORK_PROVIDER)
        }
    }

    suspend fun getCurrentDeviceLocation(timeoutMillis: Long = 10000L): LocationState {
        if (!hasAnyLocationPermission()) {
            return LocationState.PermissionRequired
        }

        if (!isLocationServicesEnabled()) {
            return LocationState.Error(
                message = "Location Services (GPS) are turned off in Android Settings.",
                isGpsDisabled = true
            )
        }

        val hasFine = hasFineLocationPermission()
        val priority = if (hasFine) {
            Priority.PRIORITY_HIGH_ACCURACY
        } else {
            Priority.PRIORITY_BALANCED_POWER_ACCURACY
        }

        // Each stage has its own budget. Previously one timeout wrapped everything, so a slow GPS cold
        // start (typical with internet off) cancelled the "last known location" fallbacks before they ran.
        val rawLocation = withTimeoutOrNull(timeoutMillis) { fetchFusedLocation(priority) }
            ?: (if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) withTimeoutOrNull(3000L) { fetchGpsProviderLocation() } else null)
            ?: withTimeoutOrNull(2000L) { fetchLastLocation() }
            ?: fetchSystemLocationManagerFallback()

        if (rawLocation == null) {
            return LocationState.Error(
                message = "Unable to acquire GPS signal. Ensure you are not in a shielded area or retry.",
                isGpsDisabled = false
            )
        }

        val readableAddress = resolveAddress(rawLocation.latitude, rawLocation.longitude)
        val sheGuardLocation = SheGuardLocation(
            latitude = rawLocation.latitude,
            longitude = rawLocation.longitude,
            accuracy = if (rawLocation.hasAccuracy()) rawLocation.accuracy else null,
            readableAddress = readableAddress,
            isApproximateOnly = !hasFine
        )

        return LocationState.Success(sheGuardLocation)
    }

    /**
     * Fast, offline-safe location for emergency alerts. Tries a fresh fix (GPS works without internet),
     * then falls back to the last known location, and never reverse-geocodes (that needs internet).
     */
    suspend fun getEmergencyFix(freshTimeoutMillis: Long = 4000L): EmergencyFix? {
        if (!hasAnyLocationPermission()) return null
        val priority = if (hasFineLocationPermission()) Priority.PRIORITY_HIGH_ACCURACY else Priority.PRIORITY_BALANCED_POWER_ACCURACY

        val raw: Location = (if (isLocationServicesEnabled()) {
            withTimeoutOrNull(freshTimeoutMillis) { fetchFusedLocation(priority) }
                ?: (if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) withTimeoutOrNull(2000L) { fetchGpsProviderLocation() } else null)
        } else null)
            ?: withTimeoutOrNull(2000L) { fetchLastLocation() }
            ?: fetchSystemLocationManagerFallback()
            ?: return null

        val ageSeconds = if (raw.time > 0L) ((System.currentTimeMillis() - raw.time) / 1000L).coerceAtLeast(0L) else 0L
        return EmergencyFix(
            latitude = raw.latitude,
            longitude = raw.longitude,
            ageSeconds = ageSeconds,
            accuracyMeters = if (raw.hasAccuracy()) raw.accuracy else null
        )
    }

    /** Direct GPS-chip fix (API 30+). Works without internet and without Google Play services. */
    @androidx.annotation.RequiresApi(Build.VERSION_CODES.R)
    private suspend fun fetchGpsProviderLocation(): Location? = suspendCancellableCoroutine { continuation ->
        try {
            val lm = context.getSystemService(Context.LOCATION_SERVICE) as? LocationManager
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R || lm == null ||
                !hasFineLocationPermission() || !lm.isProviderEnabled(LocationManager.GPS_PROVIDER)
            ) {
                continuation.resume(null)
                return@suspendCancellableCoroutine
            }
            val signal = android.os.CancellationSignal()
            continuation.invokeOnCancellation { signal.cancel() }
            lm.getCurrentLocation(
                LocationManager.GPS_PROVIDER,
                signal,
                ContextCompat.getMainExecutor(context)
            ) { location: Location? ->
                if (continuation.isActive) continuation.resume(location)
            }
        } catch (e: Exception) {
            if (continuation.isActive) continuation.resume(null)
        }
    }

    private suspend fun fetchFusedLocation(priority: Int): Location? = suspendCancellableCoroutine { continuation ->
        try {
            if (!hasAnyLocationPermission()) {
                continuation.resume(null)
                return@suspendCancellableCoroutine
            }

            val cts = CancellationTokenSource()
            continuation.invokeOnCancellation { cts.cancel() }

            val request = CurrentLocationRequest.Builder()
                .setPriority(priority)
                .setMaxUpdateAgeMillis(60000)
                .build()

            fusedLocationClient.getCurrentLocation(request, cts.token)
                .addOnSuccessListener { location: Location? ->
                    if (continuation.isActive) continuation.resume(location)
                }
                .addOnFailureListener {
                    if (continuation.isActive) continuation.resume(null)
                }
        } catch (e: SecurityException) {
            if (continuation.isActive) continuation.resume(null)
        } catch (e: Exception) {
            if (continuation.isActive) continuation.resume(null)
        }
    }

    private suspend fun fetchLastLocation(): Location? = suspendCancellableCoroutine { continuation ->
        try {
            if (!hasAnyLocationPermission()) {
                continuation.resume(null)
                return@suspendCancellableCoroutine
            }
            fusedLocationClient.lastLocation
                .addOnSuccessListener { location: Location? ->
                    if (continuation.isActive) continuation.resume(location)
                }
                .addOnFailureListener {
                    if (continuation.isActive) continuation.resume(null)
                }
        } catch (e: Exception) {
            if (continuation.isActive) continuation.resume(null)
        }
    }

    private fun fetchSystemLocationManagerFallback(): Location? {
        try {
            if (!hasAnyLocationPermission()) return null
            val locationManager = context.getSystemService(Context.LOCATION_SERVICE) as? LocationManager ?: return null
            val providers = locationManager.getProviders(true)
            var bestLocation: Location? = null
            for (provider in providers) {
                val l = locationManager.getLastKnownLocation(provider) ?: continue
                if (bestLocation == null || l.accuracy < bestLocation.accuracy) {
                    bestLocation = l
                }
            }
            return bestLocation
        } catch (e: Exception) {
            return null
        }
    }

    suspend fun resolveAddress(latitude: Double, longitude: Double): String {
        val fallbackCoordinates = String.format(Locale.US, "%.4f° N, %.4f° E", latitude, longitude)
        if (!Geocoder.isPresent()) return fallbackCoordinates

        // Geocoder is a blocking network call that can hang for a long time with no internet.
        // Run it unstructured and give up after 3s so callers are never stuck.
        val lookup = CoroutineScope(Dispatchers.IO).async {
            try {
                val geocoder = Geocoder(context, Locale.getDefault())
                @Suppress("DEPRECATION")
                val addresses: List<Address>? = geocoder.getFromLocation(latitude, longitude, 1)
                val address = addresses?.firstOrNull() ?: return@async null

                val parts = mutableListOf<String>()
                val thoroughfare = address.thoroughfare
                val subLocality = address.subLocality
                val locality = address.locality
                val subAdmin = address.subAdminArea
                if (!thoroughfare.isNullOrBlank()) parts.add(thoroughfare)
                if (!subLocality.isNullOrBlank() && subLocality != thoroughfare) parts.add(subLocality)
                if (!locality.isNullOrBlank()) parts.add(locality)
                else if (!subAdmin.isNullOrBlank()) parts.add(subAdmin)

                if (parts.isNotEmpty()) parts.joinToString(", ") else address.getAddressLine(0)
            } catch (e: Exception) {
                null
            }
        }
        return withTimeoutOrNull(3000L) { lookup.await() } ?: fallbackCoordinates
    }
}