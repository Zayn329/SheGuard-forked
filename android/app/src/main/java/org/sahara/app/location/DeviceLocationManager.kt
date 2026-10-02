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
import kotlinx.coroutines.Dispatchers
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

        val rawLocation = withTimeoutOrNull(timeoutMillis) {
            fetchFusedLocation(priority) ?: fetchLastLocation() ?: fetchSystemLocationManagerFallback()
        }

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

    suspend fun resolveAddress(latitude: Double, longitude: Double): String = withContext(Dispatchers.IO) {
        val fallbackCoordinates = String.format(Locale.US, "%.4f° N, %.4f° E", latitude, longitude)
        if (!Geocoder.isPresent()) {
            return@withContext fallbackCoordinates
        }

        try {
            val geocoder = Geocoder(context, Locale.getDefault())
            @Suppress("DEPRECATION")
            val addresses: List<Address>? = geocoder.getFromLocation(latitude, longitude, 1)
            val address = addresses?.firstOrNull() ?: return@withContext fallbackCoordinates

            // Format address hierarchically
            val thoroughfare = address.thoroughfare // Street name
            val subLocality = address.subLocality   // Area / Neighborhood
            val locality = address.locality         // City
            val subAdmin = address.subAdminArea     // District / Sub-region

            val parts = mutableListOf<String>()
            if (!thoroughfare.isNullOrBlank()) parts.add(thoroughfare)
            if (!subLocality.isNullOrBlank() && subLocality != thoroughfare) parts.add(subLocality)
            if (!locality.isNullOrBlank()) parts.add(locality)
            else if (!subAdmin.isNullOrBlank()) parts.add(subAdmin)

            if (parts.isNotEmpty()) {
                parts.joinToString(", ")
            } else {
                address.getAddressLine(0) ?: fallbackCoordinates
            }
        } catch (e: Exception) {
            // Reverse geocoding fails gracefully when offline or network unavailable
            fallbackCoordinates
        }
    }
}
