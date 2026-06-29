package io.blueeye.core.data.tracker.session

import android.util.Log
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Manages Follow-Me tracking session state.
 * 
 * Responsibilities:
 * - Track when devices were first seen in THIS session (not from DB)
 * - Detect user movement to enable/disable alerts
 * - Maintain "zastane" (baseline) device list
 * 
 * This class is stateful but has a clear contract:
 * - Call [resetSession] when scanning starts
 * - Call [updateMovement] with each location update
 * - Query [isDeviceZastane], [hasUserMoved], [getSessionFirstSeen] as needed
 */
@Singleton
class FollowMeSessionManager @Inject constructor() {

    companion object {
        private const val TAG = "FollowMeSession"
        private const val MOVEMENT_THRESHOLD_METERS = 50.0
    }

    // Session timing
    @Volatile
    private var sessionStartTime: Long = System.currentTimeMillis()

    // Device fingerprint → first seen timestamp IN THIS SESSION
    private val sessionFirstSeenMap = ConcurrentHashMap<String, Long>()

    // Devices seen BEFORE user started moving (baseline - assumed safe)
    private val zastaneDevices = java.util.Collections.newSetFromMap(ConcurrentHashMap<String, Boolean>())

    // User movement tracking
    @Volatile
    private var startLocationLat: Double? = null
    @Volatile
    private var startLocationLon: Double? = null
    @Volatile
    private var userHasMoved: Boolean = false

    /**
     * Reset all session state. Call when scanning session starts/restarts.
     */
    fun resetSession() {
        sessionStartTime = System.currentTimeMillis()
        sessionFirstSeenMap.clear()
        zastaneDevices.clear()
        userHasMoved = false
        startLocationLat = null
        startLocationLon = null
        Log.i(TAG, "Session reset - all tracking state cleared")
    }

    /**
     * Update user movement status based on current location.
     * @return true if user has moved beyond threshold, false otherwise
     */
    fun updateMovement(currentLat: Double?, currentLon: Double?): Boolean {
        if (currentLat == null || currentLon == null) return userHasMoved

        // Initialize start location if not set
        if (startLocationLat == null || startLocationLon == null) {
            startLocationLat = currentLat
            startLocationLon = currentLon
            return false
        }

        // Already moved? No need to recalculate
        if (userHasMoved) return true

        // Calculate distance
        val distance = calculateDistance(
            startLocationLat!!, startLocationLon!!,
            currentLat, currentLon
        )

        if (distance >= MOVEMENT_THRESHOLD_METERS) {
            userHasMoved = true
            Log.i(TAG, "User has moved ${distance.toInt()}m - enabling follow-me analysis")
        }

        return userHasMoved
    }

    /**
     * Record a device sighting. Returns the session-based first seen time.
     * Also marks device as "zastane" if user hasn't moved yet.
     */
    fun recordDeviceSighting(fingerprint: String): Long {
        val now = System.currentTimeMillis()
        val firstSeen = sessionFirstSeenMap.getOrPut(fingerprint) { now }

        // Mark baseline only after the session has a movement reference point.
        if (hasMovementReference() && !userHasMoved) {
            zastaneDevices.add(fingerprint)
        }

        return firstSeen
    }

    /**
     * Check if a device was seen before user started moving (baseline device).
     */
    fun isDeviceZastane(fingerprint: String): Boolean = zastaneDevices.contains(fingerprint)

    /**
     * Check if user has moved beyond the movement threshold.
     */
    fun hasUserMoved(): Boolean = userHasMoved

    /**
     * Check if location has provided a reference point for movement/baseline decisions.
     */
    fun hasMovementReference(): Boolean = startLocationLat != null && startLocationLon != null

    /**
     * Get session start time.
     */
    fun getSessionStartTime(): Long = sessionStartTime

    /**
     * Haversine formula for distance in meters.
     */
    private fun calculateDistance(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
        val R = 6371000.0 // Earth radius in meters
        val dLat = Math.toRadians(lat2 - lat1)
        val dLon = Math.toRadians(lon2 - lon1)
        val a = kotlin.math.sin(dLat / 2) * kotlin.math.sin(dLat / 2) +
                kotlin.math.cos(Math.toRadians(lat1)) * kotlin.math.cos(Math.toRadians(lat2)) *
                kotlin.math.sin(dLon / 2) * kotlin.math.sin(dLon / 2)
        val c = 2 * kotlin.math.atan2(kotlin.math.sqrt(a), kotlin.math.sqrt(1 - a))
        return R * c
    }
}
