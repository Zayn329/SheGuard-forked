package org.sahara.services.mesh.util

import android.util.Log

/**
 * Safe logger for SheGuard Mesh components.
 * Emits to Android Logcat using the unified "SheGuardMesh" tag,
 * with safe fallback for JVM unit tests where android.util.Log is unmocked.
 */
object MeshLogger {
    const val TAG = "SheGuardMesh"

    fun i(msg: String, tag: String = TAG) {
        try {
            Log.i(tag, msg)
        } catch (_: Throwable) {
            println("[$tag] [INFO] $msg")
        }
    }

    fun d(msg: String, tag: String = TAG) {
        try {
            Log.d(tag, msg)
        } catch (_: Throwable) {
            println("[$tag] [DEBUG] $msg")
        }
    }

    fun w(msg: String, tag: String = TAG) {
        try {
            Log.w(tag, msg)
        } catch (_: Throwable) {
            println("[$tag] [WARN] $msg")
        }
    }

    fun e(msg: String, tr: Throwable? = null, tag: String = TAG) {
        try {
            if (tr != null) Log.e(tag, msg, tr) else Log.e(tag, msg)
        } catch (_: Throwable) {
            System.err.println("[$tag] [ERROR] $msg ${tr?.message ?: ""}")
        }
    }
}
