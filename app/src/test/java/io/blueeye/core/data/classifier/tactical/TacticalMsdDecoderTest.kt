package io.blueeye.core.data.classifier.tactical

import io.blueeye.core.data.classifier.vendor.tactical.TacticalUuids
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class TacticalMsdDecoderTest {
    @Test
    fun `decode Axon Sidearm IDLE state`() {
        // Simulated Axon packet: Company ID 0x034D, Device Type 0x02 (Sidearm), Status 0x00 (Idle)
        // Format: [CompanyID_LSB, CompanyID_MSB, DeviceType, Status, ...SerialHash...]
        val msdPayload =
            byteArrayOf(
                0x4D, 0x03, // Company ID: 0x034D (Axon) - Little Endian
                0x02, // Device Type: 0x02 = Sidearm
                0x00, // Status: 0x00 = IDLE (Holstered)
                0x12, 0x34, 0x56, 0x78, // Serial Hash
                0x64 // Battery: 100%
            )

        val result = TacticalMsdDecoder.decode(msdPayload)

        assertNotNull("Should decode Axon packet", result)
        assertEquals(TacticalUuids.AXON_COMPANY_ID, result?.companyId)
        assertEquals("Axon Enterprise", result?.companyName)
        assertEquals(TacticalMsdDecoder.DeviceType.AXON_SIDEARM, result?.deviceType)
        assertEquals(TacticalMsdDecoder.Status.IDLE, result?.status)
        assertEquals(0x00, result?.rawStatusByte)
        assertEquals("12345678", result?.serialHash)
        assertEquals(100, result?.batteryLevel)
    }

    @Test
    fun `decode Axon Sidearm ALARM state (weapon drawn)`() {
        // Axon packet with ALARM state (high bit set in status)
        val msdPayload =
            byteArrayOf(
                0x4D, 0x03, // Company ID: 0x034D (Axon)
                0x02, // Device Type: 0x02 = Sidearm
                0xFF.toByte(), // Status: 0xFF = ALARM (Unholstered)
                0xAA.toByte(), 0x00.toByte(), 0xCC.toByte(), 0xDD.toByte(), // Serial
                0x50 // Battery: 80%
            )

        val result = TacticalMsdDecoder.decode(msdPayload)

        assertNotNull(result)
        assertEquals(TacticalMsdDecoder.DeviceType.AXON_SIDEARM, result?.deviceType)
        assertEquals(TacticalMsdDecoder.Status.ALARM, result?.status)
        assertEquals(0xFF, result?.rawStatusByte)
    }

    @Test
    fun `decode Axon Sidearm ALARM via high bit in device type`() {
        // Some implementations set high bit (0x80) in device type byte during alarm
        val msdPayload =
            byteArrayOf(
                0x4D,
                0x03, // Company ID: 0x034D (Axon)
                0x82.toByte(), // Device Type: 0x82 = 0x02 | 0x80 (Sidearm + Event flag)
                0x01, // Status: 0x01 (could be event counter)
                0x11,
                0x22,
                0x33,
                0x44
            )

        val result = TacticalMsdDecoder.decode(msdPayload)

        assertNotNull(result)
        assertEquals(TacticalMsdDecoder.DeviceType.AXON_SIDEARM, result?.deviceType)
        assertEquals(TacticalMsdDecoder.Status.ALARM, result?.status) // High bit detected
    }

    @Test
    fun `decode Motorola V300 STANDBY state`() {
        // Motorola V300 packet from patent: FF 08 00 03 00 1D 25 A6 31 0B 00 01 00
        // (We receive without the 0xFF prefix, just the payload)
        val msdPayload =
            byteArrayOf(
                0x08, 0x00, // Company ID: 0x0008 (Motorola) - Little Endian
                0x03, // Packet Type: 0x03 = Status Update
                0x00, // Reserved/Version
                0x1D, 0x25, 0xA6.toByte(), 0x31, // Serial Hash
                0x0B, 0x00, // Length/Code
                0x01 // Status: 0x01 = STANDBY (Bit 0 = 0, not recording)
            )

        val result = TacticalMsdDecoder.decode(msdPayload)

        assertNotNull("Should decode Motorola packet", result)
        assertEquals(TacticalUuids.MOTOROLA_COMPANY_ID, result?.companyId)
        assertEquals("Motorola Solutions", result?.companyName)
        assertEquals(TacticalMsdDecoder.DeviceType.MOTOROLA_V300, result?.deviceType)
        // Bit 0 is set (0x01), so recording is ON -> ALARM
        assertEquals(TacticalMsdDecoder.Status.ALARM, result?.status)
    }

    @Test
    fun `decode Motorola V300 RECORDING state with Holster trigger`() {
        // Status byte 0x05 = 00000101 (Bit 0: Recording ON, Bit 2: Holster Triggered)
        val msdPayload =
            byteArrayOf(
                0x08, 0x00, // Company ID
                0x03, // Packet Type
                0x00, // Reserved
                0xAA.toByte(), 0xBB.toByte(), 0xCC.toByte(), 0xDD.toByte(), // Serial
                0x0B, 0x00, // Length
                0x05 // Status: Recording + Holster
            )

        val result = TacticalMsdDecoder.decode(msdPayload)

        assertNotNull(result)
        assertEquals(TacticalMsdDecoder.Status.ALARM, result?.status)
        assertEquals(0x05, result?.rawStatusByte)
    }

    @Test
    fun `decode Motorola V300 ignores non-status packets`() {
        // Packet type 0x01 (not 0x03) should be ignored
        val msdPayload =
            byteArrayOf(
                0x08, 0x00, // Company ID
                0x01, // Packet Type: 0x01 = NOT Status Update
                0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00
            )

        val result = TacticalMsdDecoder.decode(msdPayload)
        assertNull("Should ignore non-status packets", result)
    }

    @Test
    fun `decode ignores Cambridge Silicon Radio company id`() {
        val msdPayload =
            byteArrayOf(
                0x0A, 0x00, // Company ID: 0x000A (Cambridge Silicon Radio), not Motorola
                0x03,
                0x00,
                0x12,
                0x34,
                0x56,
                0x78,
                0x00,
                0x00,
                0x01
            )

        val result = TacticalMsdDecoder.decode(msdPayload)

        assertNull("Should not decode CSR payload as Motorola tactical data", result)
    }

    @Test
    fun `decode Radetec Smart Slide ammo count`() {
        // Radetec device identified by name, ammo count at end of payload
        val msdPayload = ByteArray(30) { 0x00 }
        msdPayload[28] = 0x0F // 15 rounds

        val result = TacticalMsdDecoder.decodeRadetec(msdPayload, "SmartSlide")

        assertNotNull("Should decode Radetec", result)
        assertEquals(TacticalMsdDecoder.DeviceType.RADETEC_SLIDE, result?.deviceType)
        assertEquals(15, result?.ammoCount)
        assertEquals(TacticalMsdDecoder.Status.IDLE, result?.status) // 15 > 3, not low
    }

    @Test
    fun `decode Radetec Smart Slide LOW AMMO state`() {
        val msdPayload = ByteArray(30) { 0x00 }
        msdPayload[28] = 0x02 // 2 rounds remaining

        val result = TacticalMsdDecoder.decodeRadetec(msdPayload, "RISC Pro")

        assertNotNull(result)
        assertEquals(2, result?.ammoCount)
        assertEquals(TacticalMsdDecoder.Status.LOW_AMMO, result?.status)
    }

    @Test
    fun `isAlarmState quick check for Axon ALARM`() {
        val alarmPayload = byteArrayOf(0x4D, 0x03, 0x02, 0xFF.toByte())
        val idlePayload = byteArrayOf(0x4D, 0x03, 0x02, 0x00)

        assertTrue("Alarm payload should trigger", TacticalMsdDecoder.isAlarmState(alarmPayload))
        assertTrue("Idle payload should NOT trigger", !TacticalMsdDecoder.isAlarmState(idlePayload))
    }

    @Test
    fun `decode returns null for unknown Company ID`() {
        val unknownPayload = byteArrayOf(0xAA.toByte(), 0xBB.toByte(), 0x01, 0x02, 0x03)
        val result = TacticalMsdDecoder.decode(unknownPayload)
        assertNull("Should return null for unknown Company ID", result)
    }
}
