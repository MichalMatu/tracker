package io.blueeye.core.data.tracker

import io.blueeye.core.data.tracker.model.CarryoverMatchReason
import io.blueeye.core.data.tracker.strategy.DeviceCorrelationStrategy
import io.blueeye.core.scanner.model.BleScanResultData
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AppleShadowCarryoverGuardTest {
    @Test
    fun `generic apple shadows stay separate even when sequential and payload matches`() {
        val tracker = AddressCarryoverTracker(DeviceCorrelationStrategy())
        val now = System.currentTimeMillis()
        val first = appleScan("61:11:11:11:11:11", "Find My", -45, now)
        val second = appleScan("62:22:22:22:22:22", "Find My", -46, now + 3_000L)

        val firstResult = tracker.processScan(first, first.name)
        val secondResult = tracker.processScan(second, second.name)

        assertTrue(firstResult.isNewTarget)
        assertTrue(secondResult.isNewTarget)
        assertFalse(secondResult.isCarryover)
        assertNotEquals(firstResult.targetId, secondResult.targetId)
    }

    @Test
    fun `specific apple identity and shadow may carry over sequentially with payload corroboration`() {
        val tracker = AddressCarryoverTracker(DeviceCorrelationStrategy())
        val now = System.currentTimeMillis()
        val primary = appleScan("63:11:11:11:11:11", "MacBook Air", -45, now)
        val shadow = appleScan("64:22:22:22:22:22", "Find My", -46, now + 3_000L)

        val primaryResult = tracker.processScan(primary, primary.name)
        val shadowResult = tracker.processScan(shadow, shadow.name)

        assertTrue(primaryResult.isNewTarget)
        assertTrue(shadowResult.isCarryover)
        assertEquals(primaryResult.targetId, shadowResult.targetId)
        assertEquals(CarryoverMatchReason.APPLE_SHADOW, shadowResult.matchEvidence?.reasonCode)
    }

    private fun appleScan(
        mac: String,
        name: String,
        rssi: Int,
        timestamp: Long,
    ): BleScanResultData {
        val rawData =
            byteArrayOf(
                0x02,
                0x01,
                0x1A,
                0x0A,
                0xFF.toByte(),
                0x4C,
                0x00,
                0x10,
                0x06,
                0x02,
                0x1D,
                0x33,
                0x34,
                0x55,
            )
        return BleScanResultData(
            mac = mac,
            rssi = rssi,
            timestamp = timestamp,
            technology = "BLE",
            name = name,
            manufacturerId = 76,
            manufacturerData = rawData,
            serviceUuids = emptyList(),
            appearance = null,
            txPower = null,
            isConnectable = true,
            primaryPhy = 1,
            secondaryPhy = 0,
            rawData = rawData,
        )
    }
}
