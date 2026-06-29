package io.blueeye.core.location

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Build
import android.os.Bundle
import android.os.Looper
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeoutOrNull
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.resume

/**
 * Location provider for scan telemetry.
 *
 * The scan pipeline needs a fresh position when possible, but BLE results can arrive very quickly.
 * This provider requests an active fix when the cached fix is stale, then reuses that fresh fix for
 * a short window to avoid blocking every scan row.
 */
@Singleton
open class LocationProvider @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    private val locationManager: LocationManager? =
        context.getSystemService(Context.LOCATION_SERVICE) as? LocationManager

    @Volatile
    private var cachedLocation: Location? = null

    private val readGate = LocationReadGate(PROVIDER_READ_THROTTLE_MS)

    /**
     * Get the last known location.
     */
    @SuppressLint("MissingPermission")
    open fun getLastLocation(): Location? {
        val bestLocation = when {
            !hasLocationPermission() -> cachedLocation?.takeIf(::isLastKnownLocationFresh)
            !readGate.shouldReadProviders() -> cachedLocation?.takeIf(::isLastKnownLocationFresh)
            else -> readLastKnownProviderLocation()
        }

        if (bestLocation != null && bestLocation != cachedLocation) {
            cachedLocation = bestLocation
        }

        return bestLocation
    }

    @SuppressLint("MissingPermission")
    private fun readLastKnownProviderLocation(): Location? =
        try {
            val gps = locationManager?.getLastKnownLocation(LocationManager.GPS_PROVIDER)
            val net = locationManager?.getLastKnownLocation(LocationManager.NETWORK_PROVIDER)
            val fused =
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    locationManager?.getLastKnownLocation(LocationManager.FUSED_PROVIDER)
                } else {
                    null
                }

            when {
                gps != null && isLastKnownLocationFresh(gps) -> gps
                net != null && isLastKnownLocationFresh(net) -> net
                fused != null && isLastKnownLocationFresh(fused) -> fused
                else -> cachedLocation
            }
        } catch (@Suppress("TooGenericExceptionCaught", "SwallowedException") e: Exception) {
            cachedLocation
        }

    private fun isLastKnownLocationFresh(location: Location): Boolean {
        val ageMs = System.currentTimeMillis() - location.time
        return ageMs in 0 until LAST_KNOWN_FRESHNESS_THRESHOLD_MS
    }

    private fun isActiveFixFresh(location: Location): Boolean {
        val ageMs = System.currentTimeMillis() - location.time
        return ageMs in 0 until ACTIVE_FIX_REUSE_MS
    }

    open fun getCurrentCoordinates(): Triple<Double?, Double?, Float?>? {
        val loc = getLastLocation() ?: return null
        return Triple(loc.latitude, loc.longitude, loc.accuracy)
    }

    open suspend fun getFreshCoordinates(): Triple<Double?, Double?, Float?>? {
        cachedLocation?.takeIf(::isActiveFixFresh)?.let { location ->
            return location.toCoordinates()
        }

        val location =
            activeFixMutex.withLock {
                cachedLocation?.takeIf(::isActiveFixFresh)
                    ?: requestActiveLocation()
                    ?: getLastLocation()
            }

        return location?.toCoordinates()
    }

    @SuppressLint("MissingPermission")
    private suspend fun requestActiveLocation(): Location? {
        val manager = locationManager
        val providers = manager?.let(::enabledProviders).orEmpty()
        val location =
            if (manager == null || !hasLocationPermission() || providers.isEmpty()) {
                null
            } else {
                awaitActiveLocation(manager, providers)
            }

        if (location != null) {
            cachedLocation = location
        }
        return location
    }

    @SuppressLint("MissingPermission")
    private suspend fun awaitActiveLocation(
        manager: LocationManager,
        providers: List<String>,
    ): Location? =
        withTimeoutOrNull(ACTIVE_FIX_TIMEOUT_MS) {
            suspendCancellableCoroutine { continuation ->
                val listener =
                    object : LocationListener {
                        override fun onLocationChanged(location: Location) {
                            cachedLocation = location
                            manager.removeUpdates(this)
                            if (continuation.isActive) {
                                continuation.resume(location)
                            }
                        }

                        override fun onProviderDisabled(provider: String) = Unit

                        override fun onProviderEnabled(provider: String) = Unit

                        @Deprecated("Deprecated in Android framework")
                        override fun onStatusChanged(
                            provider: String?,
                            status: Int,
                            extras: Bundle?,
                        ) = Unit
                    }

                val registered =
                    providers.count { provider ->
                        runCatching {
                            manager.requestLocationUpdates(
                                provider,
                                LOCATION_MIN_TIME_MS,
                                LOCATION_MIN_DISTANCE_M,
                                listener,
                                Looper.getMainLooper(),
                            )
                        }.isSuccess
                    }

                if (registered == 0 && continuation.isActive) {
                    continuation.resume(null)
                }

                continuation.invokeOnCancellation {
                    manager.removeUpdates(listener)
                }
            }
        }

    private fun enabledProviders(manager: LocationManager): List<String> =
        buildList {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                add(LocationManager.FUSED_PROVIDER)
            }
            add(LocationManager.GPS_PROVIDER)
            add(LocationManager.NETWORK_PROVIDER)
        }
            .filter { provider ->
                runCatching { manager.isProviderEnabled(provider) }.getOrDefault(false)
            }

    private fun hasLocationPermission(): Boolean =
        context.checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED ||
            context.checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED

    private val activeFixMutex = Mutex()

    companion object {
        private const val LAST_KNOWN_FRESHNESS_THRESHOLD_MS = 5 * 60 * 1000
        private const val ACTIVE_FIX_REUSE_MS = 10_000L
        private const val ACTIVE_FIX_TIMEOUT_MS = 2_000L
        private const val PROVIDER_READ_THROTTLE_MS = 2_000L
        private const val LOCATION_MIN_TIME_MS = 0L
        private const val LOCATION_MIN_DISTANCE_M = 0f
    }
}

private fun Location.toCoordinates(): Triple<Double?, Double?, Float?> =
    Triple(latitude, longitude, accuracy)
