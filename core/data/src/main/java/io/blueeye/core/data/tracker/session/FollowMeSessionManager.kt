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
    var lastSightingContinuedMovingWindow: Boolean,
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

    private val deviceMovementStates = ConcurrentHashMap<String, DeviceMovementState>()
    private val zastaneDevices = java.util.Collections.newSetFromMap(ConcurrentHashMap<String, Boolean>())

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

    @Synchronized
    fun resetSession() {
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
     * [hasUserMoved] remains the session-level historical latch used for baseline semantics.
     * [isUserMoving] is a recent-motion signal used by Follow-Me scoring.
     *
     * Displacement must exceed both the fixed 50-m floor and the combined uncertainty radii of
     * the compared fixes so coarse GPS jitter cannot latch movement by itself.
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
        } else if (!userHasMoved) {
            val distanceFromStart =
                calculateDistance(
                    startLocationLat!!,
                    startLocationLon!!,
                    currentLat,
                    currentLon,
                )
            val requiredDistance = requiredMovementDistance(startLocationAccuracyM, accuracyM)
            if (distanceFromStart >= requiredDistance) {
                userHasMoved = true
                movementAnchorLat = currentLat
                movementAnchorLon = currentLon
                movementAnchorAccuracyM = accuracyM
                lastConfirmedMovementAt = now
                Log.i(
                    TAG,
                    "User movement confirmed at ${distanceFromStart.toInt()}m " +
                        "(required=${requiredDistance.toInt()}m)",
                )
            }
        } else {
            val anchorLat = movementAnchorLat
            val anchorLon = movementAnchorLon
            if (anchorLat != null && anchorLon != null) {
                val distanceFromAnchor = calculateDistance(anchorLat, anchorLon, currentLat, currentLon)
                val requiredDistance = requiredMovementDistance(movementAnchorAccuracyM, accuracyM)
                if (distanceFromAnchor >= requiredDistance) {
                    movementAnchorLat = currentLat
                    movementAnchorLon = currentLon
                    movementAnchorAccuracyM = accuracyM
                    lastConfirmedMovementAt = now
                }
            }
        }

        return userHasMoved
    }

    /**
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
                    lastSightingContinuedMovingWindow = false,
                    observedWhileMovingMs = 0L,
                    movingEncounterCount = if (movingNow) 1 else 0,
                )
            } else {
                val gapMs = now - state.lastSeenAt
                val continuesMovingWindow =
                    movingNow &&
                        state.lastSightingWasMoving &&
                        gapMs in 1..MAX_CONTIGUOUS_OBSERVATION_GAP_MS

                if (movingNow) {
                    state.movingEncounterCount += 1
                }
                if (continuesMovingWindow) {
                    state.observedWhileMovingMs += gapMs
                }
                state.lastSightingContinuedMovingWindow = continuesMovingWindow
                if (now > state.lastSeenAt) {
                    state.lastSeenAt = now
                    state.lastSightingWasMoving = movingNow
                }
                state
            }

        deviceMovementStates[fingerprint] = updatedState

        if (hasMovementReference() && !userHasMoved) {
            zastaneDevices.add(fingerprint)
        }

        return updatedState.firstSeenAt
    }

    fun getObservedWhileMovingDurationMs(fingerprint: String): Long =
        deviceMovementStates[fingerprint]?.observedWhileMovingMs ?: 0L

    fun getMovingEncounterCount(fingerprint: String): Int =
        deviceMovementStates[fingerprint]?.movingEncounterCount ?: 0

    /** False on the first moving sighting and after a stationary period or observation gap. */
    fun isMovingObservationContinuous(fingerprint: String): Boolean =
        deviceMovementStates[fingerprint]?.lastSightingContinuedMovingWindow == true

    fun isDeviceZastane(fingerprint: String): Boolean = zastaneDevices.contains(fingerprint)

    /** Session-level historical movement latch. Keep this for baseline semantics only. */
    fun hasUserMoved(): Boolean = userHasMoved

    /** True only while a confirmed movement step is recent enough to support Follow-Me scoring. */
    fun isUserMoving(now: Long = System.currentTimeMillis()): Boolean {
        val lastMovementAt = lastConfirmedMovementAt
        if (!userHasMoved || lastMovementAt <= 0L) return false
        val ageMs = now - lastMovementAt
        return ageMs in 0..RECENT_MOVEMENT_WINDOW_MS
    }

    fun hasMovementReference(): Boolean = startLocationLat != null && startLocationLon != null
}

private const val EarthRadiusMeters = 6_371_000.0
private const val MovementThresholdMeters = 50.0

private fun requiredMovementDistance(
    firstAccuracyM: Double?,
    secondAccuracyM: Double?,
): Double {
    val uncertaintyM = (firstAccuracyM ?: 0.0) + (secondAccuracyM ?: 0.0)
    return max(MovementThresholdMeters, uncertaintyM)
}

private fun calculateDistance(
    lat1: Double,
    lon1: Double,
    lat2: Double,
    lon2: Double,
): Double {
    val dLat = Math.toRadians(lat2 - lat1)
    val dLon = Math.toRadians(lon2 - lon1)
    val a =
        kotlin.math.sin(dLat / 2) * kotlin.math.sin(dLat / 2) +
            kotlin.math.cos(Math.toRadians(lat1)) *
            kotlin.math.cos(Math.toRadians(lat2)) *
            kotlin.math.sin(dLon / 2) *
            kotlin.math.sin(dLon / 2)
    val c = 2 * kotlin.math.atan2(kotlin.math.sqrt(a), kotlin.math.sqrt(1 - a))
    return EarthRadiusMeters * c
}

private fun Float?.normalizedAccuracy(): Double? =
    this
        ?.takeIf { it.isFinite() && it > 0f }
        ?.toDouble()
