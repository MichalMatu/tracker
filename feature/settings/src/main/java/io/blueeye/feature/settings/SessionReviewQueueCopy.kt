package io.blueeye.feature.settings

internal object SessionReviewQueueCopy {
    const val FOLLOW_ME_ALERT =
        "Compare score, RSSI trend, and route context in Details before marking a verdict."
    const val IDENTITY_CARRYOVER =
        "Verify merged history in Details before trusting identity continuity."
    const val SUSPICIOUS =
        "Compare evidence in Details, then choose Suspicious, False Positive, or Known Safe."
    const val STRENGTHENING_RSSI =
        "Compare with movement context; RSSI alone is not proof."
    const val WATCHLIST_RETURN =
        "Confirm the return alert was useful; edit Watchlist or mark Known Safe if noisy."
    const val PUBLIC_SAFETY_ALERT =
        "Treat as classification evidence only; mark Known Safe or False Positive " +
            "only when repeated benign context is clear."
    const val WATCHLIST =
        "Confirm this device still needs return alerts."
    const val PUBLIC_SAFETY =
        "Treat as evidence only; mark Known Safe or False Positive only when repeated benign context is clear."
    const val UNKNOWN_NOISE =
        "Mark repeated expected noise as False Positive or Known Safe."
}
