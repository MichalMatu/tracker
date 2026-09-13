package io.blueeye.core.data.tracker.alert

import io.blueeye.core.model.TrackingStatus
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Pure decision engine for Follow-Me alerts.
 *
 * No state, no side effects - just decision logic.
 * All inputs are explicit parameters.
 */
@Singleton
class AlertDecisionEngine @Inject constructor() {

    /**
     * Determine if an alert should be triggered for a device.
     *
     * Decision matrix:
     * 1. Device is ignored → NO alert (user whitelist)
     * 2. User hasn't moved → NO movement-pattern alert
     * 3. Device was seen before movement (zastane) → NO alert (baseline device)
     * 4. A suspicious or dangerous movement pattern can alert regardless of device type.
     *    Tracker identity is supporting evidence, not a prerequisite: a phone, watch or headset can
     *    also reveal that a person is repeatedly moving with the user.
     */
    fun shouldAlert(
        isIgnored: Boolean,
        userHasMoved: Boolean,
        isZastane: Boolean,
        trackingStatus: TrackingStatus,
    ): Boolean =
        !isIgnored &&
            userHasMoved &&
            !isZastane &&
            (
                trackingStatus == TrackingStatus.DANGEROUS ||
                    trackingStatus == TrackingStatus.SUSPICIOUS
            )

    /**
     * Get a human-readable explanation for why an alert was/wasn't triggered.
     */
    fun getDecisionExplanation(
        isIgnored: Boolean,
        isKnownTracker: Boolean,
        userHasMoved: Boolean,
        isZastane: Boolean,
        trackingStatus: TrackingStatus,
    ): String {
        return when {
            isIgnored -> "Device is manually ignored"
            !userHasMoved -> "User hasn't moved - alerts disabled"
            isZastane -> "Device was present before movement (baseline)"
            trackingStatus == TrackingStatus.DANGEROUS -> "High follow-me score needs evidence review"
            trackingStatus == TrackingStatus.SUSPICIOUS -> "Possible follow-me pattern needs evidence review"
            isKnownTracker -> "Known tracker evidence present without movement-pattern alert"
            else -> "Low risk - no alert needed"
        }
    }
}
