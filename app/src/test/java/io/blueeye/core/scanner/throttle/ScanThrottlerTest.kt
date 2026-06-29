package io.blueeye.core.scanner.throttle

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class ScanThrottlerTest {
    private lateinit var throttler: ScanThrottler

    @Before
    fun setup() {
        throttler = ScanThrottler()
    }

    @Test
    fun `shouldUpdateDevice respects throttling interval`() {
        val mac = "AA:BB:CC:11:22:33"
        val rssi = -60

        // T0 = 1000
        assertTrue(
            "First call should pass",
            throttler.shouldUpdateDevice(ThrottleParams(mac = mac, currentRssi = rssi, now = 1000L))
        )

        // T1 = 1500 (Diff 500 < 1000)
        assertFalse(
            "Too soon call should fail",
            throttler.shouldUpdateDevice(ThrottleParams(mac = mac, currentRssi = rssi, now = 1500L))
        )

        // T2 = 2200 (Diff 1200 > 1000 from T0)
        assertTrue(
            "Call after delay should pass",
            throttler.shouldUpdateDevice(ThrottleParams(mac = mac, currentRssi = rssi, now = 2200L))
        )
    }

    @Test
    fun `shouldUpdateDevice allows critical updates immediately`() {
        val mac = "AA:BB:CC:11:22:33"
        val rssi = -60

        // T0 = 1000
        throttler.shouldUpdateDevice(ThrottleParams(mac = mac, currentRssi = rssi, now = 1000L))

        // T1 = 1050 (Diff 50 < 1000) - but name changed
        assertTrue(
            "Name change should bypass throttle",
            throttler.shouldUpdateDevice(
                ThrottleParams(mac = mac, currentRssi = rssi, hasNewName = true, now = 1050L)
            )
        )
    }

    @Test
    fun `shouldUpdateDevice handles significant RSSI change`() {
        val mac = "AA:BB:CC:11:22:33"
        // T0: RSSI -80
        throttler.shouldUpdateDevice(ThrottleParams(mac = mac, currentRssi = -80, now = 1000L))

        // T1: 1100. RSSI -75 (Delta 5 < 10) -> Throttle
        assertFalse(
            "Small RSSI change should fail",
            throttler.shouldUpdateDevice(ThrottleParams(mac = mac, currentRssi = -75, now = 1100L))
        )

        // T2: 1200. RSSI -50 (Delta 30 > 10 vs last recorded -80)
        // Since we skipped update at -75, the "lastRssi" in throttler map is still -80.
        assertTrue(
            "Big RSSI change should pass",
            throttler.shouldUpdateDevice(ThrottleParams(mac = mac, currentRssi = -50, now = 1200L))
        )
    }

    @Test
    fun `shouldUpdateDevice respects priority throttle limit`() {
        val mac = "PRIORITY:DEVICE"
        val rssi = -60

        // T0 = 1000. Priority = true.
        assertTrue(
            throttler.shouldUpdateDevice(
                ThrottleParams(mac = mac, currentRssi = rssi, isPriorityDevice = true, now = 1000L)
            )
        )

        // T1 = 1150 (Diff 150 < 1000 but > 100).
        // If priority, should PASS (Tactical limit is 100ms).
        assertTrue(
            "Priority device should pass 100ms throttle",
            throttler.shouldUpdateDevice(
                ThrottleParams(mac = mac, currentRssi = rssi, isPriorityDevice = true, now = 1150L)
            )
        )

        // T2: 1200 (Diff 50 < 100 from last update at 1150). Should FAIL even for priority.
        assertFalse(
            "Too fast even for priority",
            throttler.shouldUpdateDevice(
                ThrottleParams(mac = mac, currentRssi = rssi, isPriorityDevice = true, now = 1200L)
            )
        )
    }

    @Test
    fun `shouldWriteSample throttle separately`() {
        val mac = "AA:BB:CC:11:22:33"

        assertTrue(throttler.shouldWriteSample(mac, now = 1000L))
        assertFalse(throttler.shouldWriteSample(mac, now = 1500L)) // 2000ms limit
        assertTrue(throttler.shouldWriteSample(mac, now = 3100L))
    }
}
