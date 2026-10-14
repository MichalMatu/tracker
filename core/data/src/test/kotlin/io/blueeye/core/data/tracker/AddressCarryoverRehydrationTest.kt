package io.blueeye.core.data.tracker

import io.blueeye.core.data.db.entity.DeviceEntity
import io.blueeye.core.data.tracker.strategy.DeviceCorrelationStrategy
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AddressCarryoverRehydrationTest {
    @Test
    fun `restart never uses active GATT services as passive identity evidence`() {
        val restoredMac = "11:22:33:44:55:66"
        val existing = DeviceEntity(
            fingerprint = "restored-identity",
            lastMacAddress = restoredMac,
            lastDeviceName = "Named Accessory",
            firstSeenAt = 1_000L,
            lastSeenAt = 2_000L,
            lastRawData = "020106",
            gattServices = "180A:[2A24,2A29];180F:[2A19]",
        )
        val tracker = AddressCarryoverTracker(DeviceCorrelationStrategy())
        tracker.rehydrateFromDatabase(listOf(existing))

        val restored = tracker.getTrackedTargets().single()
        assertEquals("restored-identity", restored.targetId)
        assertEquals(0, restored.lastPayloadHash)
        assertNull(restored.lastPayload)
        assertTrue(restored.lastServiceUuids.isEmpty())
        assertEquals("restored-identity", tracker.getTargetByMac(restoredMac)?.targetId)
    }
}
