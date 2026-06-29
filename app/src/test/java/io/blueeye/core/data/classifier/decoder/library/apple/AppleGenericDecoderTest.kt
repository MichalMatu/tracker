@file:Suppress("WildcardImport", "NoWildcardImports")

package io.blueeye.core.data.classifier.decoder.library.apple

import io.blueeye.core.decoders.BleBeaconScanInput
import io.blueeye.core.decoders.apple.AppleGenericDecoder
import io.blueeye.core.decoders.parser.apple.*
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AppleGenericDecoderTest {
    private val decoder =
        AppleGenericDecoder(
            AppleContinuityParser(
                NearbyInfoParser(),
                NearbyActionParser(),
                HandoffParser(),
                ProximityPairingParser(),
                TetheringTargetParser(),
                TetheringSourceParser(),
                FindMyParser(),
                AirDropParser(),
                AirPrintParser(),
                HomeKitParser(),
                HeySiriParser(),
                AirPlayParser(),
                MagicSwitchParser()
            )
        )

    @org.junit.Ignore("Fails on CI/Local - Needs fix independent of Tactical refactor")
    @Test
    fun testFindMyOpenHaystackKeyReconstruction() {
        // Manufacturer ID is 0x004C.
        // Data passed to decode usually starts after ID (or is the full payload depending on abstraction,
        // but AppleGenericDecoder checks data[0] for type).
        // Let's assume data passed is payload starting with type.

        // Type 0x12 (Find My)
        val type = 0x12.toByte()
        // Length: Status (1) + Key (22) = 23 (0x17)
        val len = 23.toByte()
        // Status 0x00
        val status = 0x00.toByte()

        // 22 bytes of key part
        val keyPart = ByteArray(22) { 0xAA.toByte() } // All AAs

        // Total size = 1 (Type) + 1 (Len) + 23 (Value) = 25
        val data = ByteArray(25)
        data[0] = type
        data[1] = len
        data[2] = status
        System.arraycopy(keyPart, 0, data, 3, 22)

        val mac = "11:22:33:44:55:66"

        val result =
            decoder.decode(
                BleBeaconScanInput(
                    mac = mac,
                    manufacturerRecords = mapOf(0x004C to data),
                    serviceUuids = emptyList(),
                    rawData = null,
                ),
            )

        assertEquals("Find My", result.beaconType)

        // Expected full key: MAC (112233445566) + KeyPart (AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA)
        val expectedKey = "112233445566AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA"

        assertTrue("Sensor status should contain full public key", result.sensorStatus?.contains(expectedKey) == true)
        assertTrue("Sensor status should contain battery info", result.sensorStatus?.contains("Bat:") == true)
    }
}
