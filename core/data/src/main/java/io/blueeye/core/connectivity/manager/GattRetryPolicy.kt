package io.blueeye.core.connectivity.manager

/** Retry a transient Android GATT connection error once, within the existing probe timeout. */
internal object GattRetryPolicy {
    const val RETRY_BACKOFF_MS = 900L

    private const val GATT_GENERIC_ERROR = 133
    private const val GATT_STATUS_147 = 147
    private const val MAX_RETRIES = 1
    private const val MIN_REMAINING_MS = 2_000L

    fun shouldRetry(
        status: Int?,
        retriesStarted: Int,
        hadConnectionProgress: Boolean,
        elapsedMs: Long,
    ): Boolean =
        !hadConnectionProgress &&
            retriesStarted < MAX_RETRIES &&
            status in setOf(GATT_GENERIC_ERROR, GATT_STATUS_147) &&
            elapsedMs in 0 until AutoActiveProbePolicy.PROBE_TIMEOUT_MS - MIN_REMAINING_MS
}
