package com.taplink.app.platform

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationManager
import android.os.CancellationSignal
import androidx.core.content.ContextCompat
import androidx.core.location.LocationManagerCompat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.coroutines.resume
import kotlin.math.round

sealed interface LocationResult {
    data class Approximate(val latitude: Double, val longitude: Double, val accuracyMeters: Float) : LocationResult
    data class Manual(val district: String, val reason: String) : LocationResult
}

object CoarseLocationHelper {
    const val PERMISSION = Manifest.permission.ACCESS_COARSE_LOCATION

    fun hasPermission(context: Context): Boolean =
        ContextCompat.checkSelfPermission(context, PERMISSION) == PackageManager.PERMISSION_GRANTED

    /** One foreground fix. Cancelling the calling coroutine also cancels the Android location request. */
    @SuppressLint("MissingPermission") // Checked by hasPermission; revocation races are caught below.
    suspend fun locate(context: Context, manualDistrict: String): LocationResult = withContext(Dispatchers.Main.immediate) {
        val app = context.applicationContext
        val district = manualDistrict.trim().ifEmpty { "Sai Kung" }
        fun manual(reason: String) = LocationResult.Manual(district, reason)
        if (!hasPermission(app)) return@withContext manual("Location permission is off; showing your selected district.")
        val manager = app.getSystemService(LocationManager::class.java)
            ?: return@withContext manual("Location is unavailable; showing your selected district.")
        try {
            if (!LocationManagerCompat.isLocationEnabled(manager)) {
                return@withContext manual("Device location is off; showing your selected district.")
            }
            // Network provider works with approximate permission and avoids a GPS/fine-location dependency.
            if (!manager.isProviderEnabled(LocationManager.NETWORK_PROVIDER)) {
                return@withContext manual("Approximate location is unavailable; showing your selected district.")
            }
            val fix = withTimeoutOrNull(10_000L) {
                suspendCancellableCoroutine<Location?> { continuation ->
                    val cancellation = CancellationSignal()
                    continuation.invokeOnCancellation { cancellation.cancel() }
                    LocationManagerCompat.getCurrentLocation(
                        manager, LocationManager.NETWORK_PROVIDER, cancellation, ContextCompat.getMainExecutor(app),
                    ) { location -> if (continuation.isActive) continuation.resume(location) }
                }
            }
            if (fix == null || !fix.latitude.isFinite() || !fix.longitude.isFinite() ||
                fix.latitude !in -90.0..90.0 || fix.longitude !in -180.0..180.0) {
                manual("No current location fix; showing your selected district.")
            } else {
                // Keep output coarse even if the host app previously received fine permission.
                LocationResult.Approximate(
                    round(fix.latitude * 100) / 100, round(fix.longitude * 100) / 100,
                    if (fix.accuracy.isFinite()) fix.accuracy.coerceAtLeast(1_000f) else 1_000f,
                )
            }
        } catch (_: SecurityException) {
            manual("Location permission changed; showing your selected district.")
        } catch (_: IllegalArgumentException) {
            manual("Approximate location is unavailable; showing your selected district.")
        }
    }
}
