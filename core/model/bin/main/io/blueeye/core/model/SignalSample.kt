package io.blueeye.core.model

/**
 * Domenowy model próbki sygnału (RSSI).
 */
data class SignalSample(
    val timestamp: Long,
    val rssi: Int
)
