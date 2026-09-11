package io.blueeye.core.data.tracker.session

import android.util.Log
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.max

private data class DeviceMovementState(
    val firstSeenAt: Long,
    var lastSeenAt: Long,
    var lastSightingWasMoving: Boolean,
    var observedWhileMovingMs: Long,
    var movingEncounterCount: Int,
)

/**
 * Manages Follow-Me tracking session state.
 *
 * Responsibilities:
 * - Track when devices were first seen in THIS session (not from DB)
 * - Detect user movement to enable/disable alerts
 * - Maintain "zastane" (baseline) device list
 * - Accumulate only continuous device observation time during recent confirmed movement
 *
 * This class is stateful but has a clear contract:
 * - Call [resetSession] when scanning starts
 * - Call [updateMovement] with each location update
 * - Call [recordDeviceSighting] for each logical device observation
 * - Query [isDeviceZastane], [hasUserMoved], [isUserMoving] as needed
 */
@Singleton
class FollowMeSessionManager @Inject constructor() {

    companion object {
        private const val TAG = "FollowMeSession"
        private const val MOVEMENT_THRESHOLD_METERS = 50.0

        /**
         * Recent movement must stay shorter than the 5-minute Follow-Me duration threshold so a
         * stop after a walk cannot create duration risk by itself.
         */
        private const val RECENT_MOVEMENT_WINDOW_MS = 120_000L

        /**
         * A device that disappears longer than this is not continuously observed for duration
         * scoring. A later sighting may continue the session total, but the missing interval is
         * never counted as observed time.
         */
        private const val MAX_CONTIGUOUS_OBSERVATION_GAP_MS = 30_000L
    }

    // Session timing
    @Volatile
    private var sessionStartTime: Long = System.currentTimeMillis()

    // Device fingerprint -> movement-scoped observation state.
    private val deviceMovementStates = ConcurrentHashMap<String, DeviceMovementState>()

    // Devices seen BEFORE user started moving (baseline - assumed safe)
    private val zastaneDevices = java.util.Collections.newSetFromMap(ConcurrentHashMap<String, Boolean>())

    // User movement tracking
    @Volatile
    private var startLocationLat: Double? = null

    @Volatile
    private var startLocationLon: Double? = null

    @Volatile
    private var startLocationAccuracyM: Double? = null

    @Volatile
    private var movementAnchorLat: Double? = null

    @Volatile
    private var movementAnchorLon: Double? = null

    @Volatile
    private var movementAnchorAccuracyM: Double? = null

    @Volatile
    private var userHasMoved: Boolean = false

    @Volatile
    private var lastConfirmedMovementAt: Long = 0L

    /**
     * Reset all session state. Call when scanning session starts/restarts.
     */
    @Synchronized
    fun resetSession() {
        sessionStartTime = System.currentTimeMillis()
        deviceMovementStates.clear()
        zastaneDevices.clear()
        userHasMoved = false
        startLocationLat = null
        startLocationLon = null
        startLocationAccuracyM = null
        movementAnchorLat = null
        movementAnchorLon = null
        movementAnchorAccuracyM = null
        lastConfirmedMovementAt = 0L
        Log.i(TAG, "Session reset - all tracking state cleared")
    }

    /**
     * Update movement state from the latest location.
     *
     * [hasUserMoved] remains the session-level historical latch used for baseline semantics.
     * [isUserMoving] is a recent-motion signal used by Follow-Me scoring.
     *
     * Location accuracy is included in the threshold: displacement must exceed both the fixed
     * 50-m floor and the combined uncertainty radii of the compared fixes. This prevents a coarse
     * location fix from latching movement purely from GPS jitter.
     */
    @Synchronized
    fun updateMovement(
        currentLat: Double?,
        currentLon: Double?,
        currentAccuracyM: Float? = null,
        now: Long = System.currentTimeMillis(),
    ): Boolean {
        if (currentLat == null || currentLon == null) return userHasMoved

        val accuracyM = currentAccuracyM.normalizedAccuracy()

        if (startLocationLat == null || startLocationLon == null) {
            startLocationLat = currentLat
            startLocationLon = currentLon
            startLocationAccuracyM = accuracyM
            movementAnchorLat = currentLat
            movementAnchorLon = currentLon
            movementAnchorAccuracyM = accuracyM
            return false
        }

        if (!userHasMoved) {
            val distanceFromStart =
                calculateDistance(
                    startLocationLat!!,
                    startLocationLon!!,
                    currentLat,
                    currentLon,
                )
            val requiredDistance =
                requiredMovementDistance(
                    startLocationAccuracyM,
                    accuracyM,
                )
            if (distanceFromStart >= requiredDistance) {
                userHasMoved = true
                confirmMovement(currentLat, currentLon, accuracyM, now)
                Log.i(
                    TAG,
                    "User movement confirmed at ${distanceFromStart.toInt()}m " +
                        "(required=${requiredDistance.toInt()}m)",
                )
            }
            return userHasMoved
        }

        val anchorLat = movementAnchorLat
        val anchorLon = movementAnchorLon
        if (anchorLat != null && anchorLon != null) {
            val distanceFromAnchor =
                calculateDistance(
                    anchorLat,
                    anchorLon,
                    currentLat,
                    currentLon,
                )
            val requiredDistance =
                requiredMovementDistance(
                    movementAnchorAccuracyM,
                    accuracyM,
                )
            if (distanceFromAnchor >= requiredDistance) {
                confirmMovement(currentLat, currentLon, accuracyM, now)
            }
        }

        return userHasMoved
    }

    private fun confirmMovement(
        lat: Double,
        lon: Double,
        accuracyM: Double?,
        now: Long,
    ) {
        movementAnchorLat = lat
        movementAnchorLon = lon
        movementAnchorAccuracyM = accuracyM
        lastConfirmedMovementAt = now
    }

    /**
     * Record a logical-device sighting and return its first-seen time in this session.
     *
     * Duration is accumulated only between two consecutive sightings that both happened while
     * recent movement was confirmed, and only across short observation gaps. Wall-clock time while
     * a device is absent or while the user is stationary is never counted.
     */
    @Synchronized
    fun recordDeviceSighting(
        fingerprint: String,
        now: Long = System.currentTimeMillis(),
    ): Long {
        val movingNow = isUserMoving(now)
        val state = deviceMovementStates[fingerprint]

        val updatedState =
            if (state == null) {
                DeviceMovementState(
                    firstSeenAt = now,
                    lastSeenAt = now,
                    lastSightingWasMoving = movingNow,
                    observedWhileMovingMs = 0L,
                    movingEncounterCount = if (movingNow) 1 else 0,
                )
            } else {
                val gapMs = now - state.lastSeenAt
                if (movingNow) {
                    state.movingEncounterCount += 1
                    if (
                        state.lastSightingWasMoving &&
                        gapMs in 1..MAX_CONTIGUOUS_OBSERVATION_GAP_MS
                    ) {
                        state.observedWhileMovingMs += gapMs
                    }
                }
                if (now > state.lastSeenAt) {
                    state.lastSeenAt = now
                    state.lastSightingWasMoving = movingNow
                }
                state
            }

        deviceMovementStates[fingerprint] = updatedState

        // Mark baseline only after the session has a movement reference point.
        if (hasMovementReference() && !userHasMoved) {
            zastaneDevices.add(fingerprint)
        }

        return updatedState.firstSeenAt
    }

    /** Continuous observation time accumulated only during recent confirmed movement. */
    fun getObservedWhileMovingDurationMs(fingerprint: String): Long =
        deviceMovementStates[fingerprint]?.observedWhileMovingMs ?: 0L

    /** Number of sightings received while recent confirmed movement was active. */
    fun getMovingEncounterCount(fingerprint: String): Int =
        deviceMovementStates[fingerprint]?.movingEncounterCount ?: 0

    /**
     * Check if device was seen before user started moving (baseline device).
     */
    fun isDeviceZastane(fingerprint: String): Boolean = zastaneDevices.contains(fingerprint)

    /**
     * Session-level historical movement latch. Keep this for baseline semantics only.
     */
    fun hasUserMoved(): Boolean = userHasMoved

    /**
     * True only while a confirmed movement step is recent enough to support Follow-Me scoring.
     */
    fun isUserMoving(now: Long = System.currentTimeMillis()): Boolean {
        val lastMovementAt = lastConfirmedMovementAt
        if (!userHasMoved || lastMovementAt <= 0L) return false
        val ageMs = now - lastMovementAt
        return ageMs in 0..RECENT_MOVEMENT_WINDOW_MS
    }

    /**
     * Check if location has provided a reference point for movement/baseline decisions.
     */
    fun hasMovementReference(): Boolean = startLocationLat != null && startLocationLon != null

    /**
     * Get session start time.
     */
    fun getSessionStartTime(): Long = sessionStartTime

    private fun requiredMovementDistance(
        firstAccuracyM: Double?,
        secondAccuracyM: Double?,
    ): Double {
        val uncertaintyM = (firstAccuracyM ?: 0.0) + (secondAccuracyM ?: 0.0)
        return max(MOVEMENT_THRESHOLD_METERS, uncertaintyM)
    }

    /**
     * Haversine formula for distance in meters.
     */
    private fun calculateDistance(
        lat1: Double,
        lon1: Double,
        lat2: Double,
        lon2: Double,
    ): Double {
        val earthRadiusM = 6_371_000.0
        val dLat = Math.toRadians(lat2 - lat1)
        val dLon = Math.toRadians(lon2 - lon1)
        val a =
            kotlin.math.sin(dLat / 2) * kotlin.math.sin(dLat / 2) +
                kotlin.math.cos(Math.toRadians(lat1)) *
                kotlin.math.cos(Math.toRadians(lat2)) *
                kotlin.math.sin(dLon / 2) *
                kotlin.math.sin(dLon / 2)
        val c = 2 * kotlin.math.atan2(kotlin.math.sqrt(a), kotlin.math.sqrt(1 - a))
        return earthRadiusM * c
    }
}

private fun Float?.normalizedAccuracy(): Double? =
    this
        ?.takeIf { it.isFinite() && it > 0f }
        ?.toDouble()
