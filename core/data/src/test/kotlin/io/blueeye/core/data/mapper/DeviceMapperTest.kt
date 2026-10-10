package io.blueeye.core.data.mapper

import io.blueeye.core.data.db.entity.DeviceEntity
import io.blueeye.core.model.IdentityCarryoverVerdict
import io.blueeye.core.scanner.extractor.normalizeBleTxPower
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class DeviceMapperTest {
    @Test
    fun `toDomain normalizes legacy RFCOMM fallback rssi`() {
        val device =
            deviceEntity(
                technology = "CLASSIC",
                classOfDevice = null,
                connectionStatus = "RFCOMM_FAIL",
                lastRssi = -50,
            ).toDomain()

        assertEquals(-100, device.rssi)
    }

    @Test
    fun `toDomain preserves measured minus fifty rssi`() {
        val device =
            deviceEntity(
                technology = "CLASSIC",
                classOfDevice = 0x240404,
                connectionStatus = "NONE",
                lastRssi = -50,
            ).toDomain()

        assertEquals(-50, device.rssi)
    }

    @Test
    fun `unavailable Android Tx power stays unknown`() {
        assertNull(normalizeBleTxPower(Int.MIN_VALUE, Int.MIN_VALUE))
        assertNull(normalizeBleTxPower(null, Int.MIN_VALUE))
        assertEquals(-55, normalizeBleTxPower(Int.MIN_VALUE, -55))
        assertEquals(-42, normalizeBleTxPower(-42, -55))
    }

    @Test
    fun `toDomain suppresses legacy Tx power not present sentinel`() {
        val device =
            deviceEntity(
                technology = "BLE",
                classOfDevice = null,
                connectionStatus = "NONE",
                lastRssi = -61,
            ).copy(txPower = Int.MIN_VALUE).toDomain()
        assertNull(device.txPower)
    }

    @Test
    fun `toDomain maps identity carryover verdict`() {
        val device =
            deviceEntity(
                technology = "BLE",
                classOfDevice = null,
                connectionStatus = "NONE",
                lastRssi = -61,
            ).copy(
                identityCarryoverVerdict = IdentityCarryoverVerdict.FALSE_MATCH,
            ).toDomain()

        assertEquals(IdentityCarryoverVerdict.FALSE_MATCH, device.identityCarryoverVerdict)
    }

    private fun deviceEntity(
        technology: String,
        classOfDevice: Int?,
        connectionStatus: String,
        lastRssi: Int,
    ): DeviceEntity =
        DeviceEntity(
            fingerprint = "classic-test",
            lastMacAddress = "00:11:22:33:44:55",
            technology = technology,
            lastDeviceName = null,
            classOfDevice = classOfDevice,
            connectionStatus = connectionStatus,
            lastRssi = lastRssi,
            firstSeenAt = 1L,
            lastSeenAt = 2L,
        )
}
