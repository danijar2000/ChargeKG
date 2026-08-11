package com.chargekg.app.util

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Build
import android.os.Looper
import androidx.core.content.ContextCompat
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume

/**
 * Разовое определение места. Подписки нет намеренно: приложение открывают на
 * минуту, а постоянный запрос координат сажал бы батарею без пользы.
 *
 * play-services-location не используется: он тянет Google Play Services, а
 * APK должен ставиться на любой телефон.
 */
suspend fun getLocationOnce(context: Context): Location? {
    if (!hasLocationPermission(context)) return null
    val manager = ContextCompat.getSystemService(context, LocationManager::class.java)
        ?: return null

    val provider = when {
        manager.isProviderEnabled(LocationManager.GPS_PROVIDER) -> LocationManager.GPS_PROVIDER
        manager.isProviderEnabled(LocationManager.NETWORK_PROVIDER) -> LocationManager.NETWORK_PROVIDER
        else -> return lastKnown(manager)
    }

    return try {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            suspendCancellableCoroutine { cont ->
                val signal = android.os.CancellationSignal()
                cont.invokeOnCancellation { signal.cancel() }
                manager.getCurrentLocation(
                    provider,
                    signal,
                    context.mainExecutor,
                ) { location -> cont.resume(location ?: lastKnown(manager)) }
            }
        } else {
            suspendCancellableCoroutine { cont ->
                val listener = object : LocationListener {
                    override fun onLocationChanged(location: Location) {
                        manager.removeUpdates(this)
                        if (cont.isActive) cont.resume(location)
                    }

                    // На API 26–28 эти методы абстрактные: без переопределения
                    // приложение падает при выключении провайдера во время запроса.
                    override fun onProviderDisabled(provider: String) = Unit
                    override fun onProviderEnabled(provider: String) = Unit

                    @Deprecated("Требуется на API < 29")
                    override fun onStatusChanged(p: String?, s: Int, e: android.os.Bundle?) = Unit
                }
                cont.invokeOnCancellation { manager.removeUpdates(listener) }
                @Suppress("DEPRECATION")
                manager.requestSingleUpdate(provider, listener, Looper.getMainLooper())
            }
        }
    } catch (_: SecurityException) {
        null
    }
}

private fun lastKnown(manager: LocationManager): Location? = try {
    listOf(LocationManager.GPS_PROVIDER, LocationManager.NETWORK_PROVIDER)
        .mapNotNull { manager.getLastKnownLocation(it) }
        .maxByOrNull { it.time }
} catch (_: SecurityException) {
    null
}

fun hasLocationPermission(context: Context): Boolean =
    ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) ==
        PackageManager.PERMISSION_GRANTED ||
        ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) ==
        PackageManager.PERMISSION_GRANTED
