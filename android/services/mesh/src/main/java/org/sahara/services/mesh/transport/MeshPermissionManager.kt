package org.sahara.services.mesh.transport

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.content.ContextCompat
import org.sahara.services.mesh.util.MeshLogger

object MeshPermissionManager {
    fun requiredPermissions(): Array<String> = buildList {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            add(Manifest.permission.BLUETOOTH_SCAN)
            add(Manifest.permission.BLUETOOTH_CONNECT)
            add(Manifest.permission.BLUETOOTH_ADVERTISE)
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            add(Manifest.permission.NEARBY_WIFI_DEVICES)
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            add(Manifest.permission.ACCESS_FINE_LOCATION)
        } else {
            add(Manifest.permission.ACCESS_COARSE_LOCATION)
        }
    }.distinct().toTypedArray()

    fun missingPermissions(context: Context): Array<String> {
        val missing = requiredPermissions()
            .filter { ContextCompat.checkSelfPermission(context, it) != PackageManager.PERMISSION_GRANTED }
            .toTypedArray()

        if (missing.isEmpty()) {
            MeshLogger.i("PERMISSIONS_GRANTED: All required Nearby mesh permissions are granted.")
        } else {
            MeshLogger.w("PERMISSIONS_MISSING: Missing mesh permissions: ${missing.joinToString(", ")}")
        }
        return missing
    }

    fun hasAllPermissions(context: Context): Boolean = missingPermissions(context).isEmpty()
}
