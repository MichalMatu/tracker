package io.blueeye.core.decoders.parser.apple

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test

class AppleContinuityParserTest {

    private val parser =
        AppleContinuityParser(
            nearbyInfoParser = NearbyInfoParser(),
            nearbyActionParser = NearbyActionParser(),
            handoffParser = HandoffParser(),
            proximityPairingParser = ProximityPairingParser(),
            tetheringTargetParser = TetheringTargetParser(),
            tetheringSourceParser = TetheringSourceParser(),
            findMyParser = FindMyParser(),
            airDropParser = AirDropParser(),
            airPrintParser = AirPrintParser(),
            homeKitParser = HomeKitParser(),
            heySiriParser = HeySiriParser(),
            airPlayParser = AirPlayParser(),
            magicSwitchParser = MagicSwitchParser(),
        )

    @Test
    fun parse_shouldDecodeNearbyAction_wifiJoinHashPrefix() {
        // TLV: [type=0x0F][len=3][value=0xAA 0xBB 0xCC]
        val mfg = byteArrayOf(0x0F, 0x03, 0xAA.toByte(), 0xBB.toByte(), 0xCC.toByte())

        val result = parser.parse(mfg)
        assertNotNull(result)
        assertEquals("Apple Device (Wi‑Fi Join)", result?.deviceModel)
        assertEquals(3, result?.wifiSsidHashPrefix?.size)
        assertEquals(0xAA.toByte(), result?.wifiSsidHashPrefix?.get(0))
    }

    @Test
    fun parse_shouldDecodeHandoff_iv() {
        // TLV: [type=0x0C][len=2][value=0x12 0x34]
        val mfg = byteArrayOf(0x0C, 0x02, 0x12, 0x34)

        val result = parser.parse(mfg)
        assertNotNull(result)
        assertEquals("Apple Handoff", result?.deviceModel)
        assertEquals(0x1234, result?.handoffIv)
    }

    @Test
    fun parse_shouldDecodeProximityPairing_subtype() {
        // TLV: [type=0x07][len=1][value=0x0B] -> AirPods Pro
        val mfg = byteArrayOf(0x07, 0x01, 0x0B)

        val result = parser.parse(mfg)
        assertNotNull(result)
        assertEquals("AirPods Pro", result?.deviceModel)
        assertEquals(0x0B, result?.proximitySubtype)
    }

    @Test
    fun parse_shouldDecodeNearbyInfo_actionAndFlags() {
        // status byte: lower nibble deviceType=1 (iPhone), upper nibble action=0x0B (Active User)
        // flags byte: 0b0101 -> primary device + airdrop receiving (per FuriousMAC low nibble)
        val mfg = byteArrayOf(0x10, 0x02, 0xB1.toByte(), 0x05)

        val result = parser.parse(mfg)
        assertNotNull(result)
        assertEquals("iPhone", result?.deviceModel)
        assertEquals(0x0B, result?.nearbyActionCode)
        assertEquals("Active User", result?.nearbyActionDescription)
        assertEquals(0x05, result?.nearbyStatusFlags)
    }

    @Test
    fun parse_shouldDecodeTetheringTarget_wifiSettings() {
        // TLV: [type=0x0D][len=2][value=0x01 0x02]
        val mfg = byteArrayOf(0x0D, 0x02, 0x01, 0x02)

        val result = parser.parse(mfg)
        assertNotNull(result)
        assertEquals("Apple Wi‑Fi Settings", result?.deviceModel)
        assertEquals(0x0D, result?.tetheringType)
        assertEquals(2, result?.tetheringPayload?.size)
    }

    @Test
    fun parse_shouldDecodeTetheringSource_instantHotspot() {
        // TLV: [type=0x0E][len=1][value=0x7F]
        val mfg = byteArrayOf(0x0E, 0x01, 0x7F)

        val result = parser.parse(mfg)
        assertNotNull(result)
        assertEquals("Apple Instant Hotspot", result?.deviceModel)
        assertEquals(0x0E, result?.tetheringType)
        assertEquals(1, result?.tetheringPayload?.size)
        assertEquals(0x7F.toByte(), result?.tetheringPayload?.get(0))
    }

    @Test
    fun parse_shouldDecodeAirDropMode_everyoneWhenZeros() {
        // TLV: [type=0x05][len=8][value=8x00]
        val mfg = byteArrayOf(0x05, 0x08, 0, 0, 0, 0, 0, 0, 0, 0)

        val result = parser.parse(mfg)
        assertNotNull(result)
        assertEquals("Apple Device (AirDrop)", result?.deviceModel)
        assertEquals("Everyone", result?.airDropMode)
        assertEquals(8, result?.airDropHash?.size)
    }
}
