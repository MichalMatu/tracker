package io.blueeye.core.location

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LocationReadGateTest {

    @Test
    fun `allows the first provider read`() {
        val gate = LocationReadGate(minIntervalMs = INTERVAL_MS) { NOW }

        assertTrue(gate.shouldReadProviders())
    }

    @Test
    fun `suppresses provider reads until interval elapses`() {
        var now = NOW
        val gate = LocationReadGate(minIntervalMs = INTERVAL_MS) { now }

        assertTrue(gate.shouldReadProviders())

        now += INTERVAL_MS - 1
        assertFalse(gate.shouldReadProviders())

        now += 1
        assertTrue(gate.shouldReadProviders())
    }

    @Test
    fun `allows provider read when clock moves backwards`() {
        var now = NOW
        val gate = LocationReadGate(minIntervalMs = INTERVAL_MS) { now }

        assertTrue(gate.shouldReadProviders())

        now -= 1
        assertTrue(gate.shouldReadProviders())
    }

    private companion object {
        private const val NOW = 1_789_000_000_000L
        private const val INTERVAL_MS = 2_000L
    }
}
