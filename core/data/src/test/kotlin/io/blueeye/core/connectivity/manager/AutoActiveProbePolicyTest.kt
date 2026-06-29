package io.blueeye.core.connectivity.manager

import org.junit.Assert.assertSame
import org.junit.Test

class AutoActiveProbePolicyTest {
    @Test
    fun `evaluate blocks candidates when auto active probing is disabled`() {
        val decision =
            AutoActiveProbePolicy.evaluate(
                candidate(enabled = false)
            )

        assertSame(AutoActiveProbeDecision.Disabled, decision)
    }

    @Test
    fun `evaluate blocks non connectable devices`() {
        val decision =
            AutoActiveProbePolicy.evaluate(
                candidate(isConnectable = false)
            )

        assertSame(AutoActiveProbeDecision.NotConnectable, decision)
    }

    @Test
    fun `evaluate blocks invalid mac addresses`() {
        val decision =
            AutoActiveProbePolicy.evaluate(
                candidate(mac = "not-a-mac")
            )

        assertSame(AutoActiveProbeDecision.InvalidAddress, decision)
    }

    @Test
    fun `evaluate blocks recently queued devices`() {
        val decision =
            AutoActiveProbePolicy.evaluate(
                candidate(lastQueuedAt = NOW - AutoActiveProbePolicy.QUEUE_COOLDOWN_MS + 1L)
            )

        assertSame(AutoActiveProbeDecision.RecentlyQueued, decision)
    }

    @Test
    fun `evaluate blocks queued devices with future timestamps`() {
        val decision =
            AutoActiveProbePolicy.evaluate(
                candidate(lastQueuedAt = NOW + 1L)
            )

        assertSame(AutoActiveProbeDecision.RecentlyQueued, decision)
    }

    @Test
    fun `evaluate blocks recently probed devices`() {
        val decision =
            AutoActiveProbePolicy.evaluate(
                candidate(
                    connectionStatus = "CONNECTED",
                    lastProbeTimestamp = NOW - AutoActiveProbePolicy.PROBE_COOLDOWN_MS + 1L,
                )
            )

        assertSame(AutoActiveProbeDecision.RecentlyProbed, decision)
    }

    @Test
    fun `evaluate blocks recently failed devices`() {
        val decision =
            AutoActiveProbePolicy.evaluate(
                candidate(
                    connectionStatus = "FAILED",
                    lastProbeTimestamp = NOW - 1L,
                )
            )

        assertSame(AutoActiveProbeDecision.RecentlyProbed, decision)
    }

    @Test
    fun `evaluate blocks probed devices with future timestamps`() {
        val decision =
            AutoActiveProbePolicy.evaluate(
                candidate(
                    connectionStatus = "PROBED",
                    lastProbeTimestamp = NOW + 1L,
                )
            )

        assertSame(AutoActiveProbeDecision.RecentlyProbed, decision)
    }

    @Test
    fun `evaluate queues stale connectable devices`() {
        val decision =
            AutoActiveProbePolicy.evaluate(
                candidate(
                    connectionStatus = "CONNECTED",
                    lastProbeTimestamp = NOW - AutoActiveProbePolicy.PROBE_COOLDOWN_MS - 1L,
                )
            )

        assertSame(AutoActiveProbeDecision.Queue, decision)
    }

    @Test
    fun `evaluate queues fresh unknown connectable devices`() {
        val decision =
            AutoActiveProbePolicy.evaluate(
                candidate(
                    connectionStatus = null,
                    lastProbeTimestamp = 0L,
                )
            )

        assertSame(AutoActiveProbeDecision.Queue, decision)
    }

    @Suppress("LongParameterList")
    private fun candidate(
        enabled: Boolean = true,
        isConnectable: Boolean = true,
        mac: String = MAC,
        connectionStatus: String? = null,
        lastProbeTimestamp: Long = 0L,
        lastQueuedAt: Long? = null,
    ): AutoActiveProbeCandidate =
        AutoActiveProbeCandidate(
            enabled = enabled,
            isConnectable = isConnectable,
            mac = mac,
            connectionStatus = connectionStatus,
            lastProbeTimestamp = lastProbeTimestamp,
            lastQueuedAt = lastQueuedAt,
            now = NOW,
        )

    private companion object {
        private const val MAC = "AA:BB:CC:11:22:33"
        private const val NOW = 1_789_000_000_000L
    }
}
