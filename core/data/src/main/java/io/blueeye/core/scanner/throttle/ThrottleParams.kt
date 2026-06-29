package io.blueeye.core.scanner.throttle

/**
 * Parameters for determining if a device update should be throttled.
 */
data class ThrottleParams(
    val mac: String,
    val currentRssi: Int,
    val hasNewName: Boolean = false,
    val hasNewType: Boolean = false,
    val isPriorityDevice: Boolean = false,
    val now: Long = System.currentTimeMillis()
)
