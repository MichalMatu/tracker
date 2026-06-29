package io.blueeye.core.connectivity.manager

internal data class AutoActiveProbeCandidate(
    val enabled: Boolean,
    val isConnectable: Boolean,
    val mac: String,
    val connectionStatus: String?,
    val lastProbeTimestamp: Long,
    val lastQueuedAt: Long?,
    val now: Long,
)

internal sealed interface AutoActiveProbeDecision {
    object Queue : AutoActiveProbeDecision
    object Disabled : AutoActiveProbeDecision
    object NotConnectable : AutoActiveProbeDecision
    object InvalidAddress : AutoActiveProbeDecision
    object RecentlyQueued : AutoActiveProbeDecision
    object RecentlyProbed : AutoActiveProbeDecision
}

internal object AutoActiveProbePolicy {
    const val PROBE_COOLDOWN_MS = 15 * 60 * 1000L
    const val QUEUE_COOLDOWN_MS = 60 * 1000L
    const val PROBE_TIMEOUT_MS = 12 * 1000L

    private val macAddressPattern = Regex("^[0-9A-Fa-f]{2}(:[0-9A-Fa-f]{2}){5}$")
    private val recentProbeStatuses =
        setOf(
            "PROBING",
            "CONNECTED",
            "PROBED",
            "FAILED",
            "RFCOMM_FAIL",
        )

    fun evaluate(candidate: AutoActiveProbeCandidate): AutoActiveProbeDecision =
        when {
            !candidate.enabled -> AutoActiveProbeDecision.Disabled
            !candidate.isConnectable -> AutoActiveProbeDecision.NotConnectable
            !macAddressPattern.matches(candidate.mac) -> AutoActiveProbeDecision.InvalidAddress
            isRecent(candidate.lastQueuedAt, candidate.now, QUEUE_COOLDOWN_MS) -> {
                AutoActiveProbeDecision.RecentlyQueued
            }
            isRecentlyProbed(candidate) -> AutoActiveProbeDecision.RecentlyProbed
            else -> AutoActiveProbeDecision.Queue
        }

    private fun isRecentlyProbed(candidate: AutoActiveProbeCandidate): Boolean {
        val status = candidate.connectionStatus ?: return false
        return status in recentProbeStatuses &&
            isRecent(candidate.lastProbeTimestamp, candidate.now, PROBE_COOLDOWN_MS)
    }

    private fun isRecent(
        timestamp: Long?,
        now: Long,
        windowMs: Long,
    ): Boolean =
        timestamp != null &&
            timestamp > 0L &&
            (timestamp >= now || now - timestamp < windowMs)
}
