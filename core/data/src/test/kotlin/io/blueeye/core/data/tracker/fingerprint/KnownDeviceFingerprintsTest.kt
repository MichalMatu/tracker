package io.blueeye.core.data.tracker.fingerprint

import org.junit.Assert.assertEquals
import org.junit.Test

class KnownDeviceFingerprintsTest {

    @Test
    fun `identify matches Fast Pair model from full Android service UUID`() {
        val result =
            KnownDeviceFingerprints.identify(
                serviceDataMap = mapOf(FAST_PAIR_UUID to SONY_XM5_FAST_PAIR_PAYLOAD),
                manufacturerSpecificData = emptyMap(),
            )

        assertEquals("Sony XM5", result)
    }

    @Test
    fun `identify matches Eddystone frame from full Android service UUID`() {
        val result =
            KnownDeviceFingerprints.identify(
                serviceDataMap = mapOf(EDDYSTONE_UUID to byteArrayOf(0x00)),
                manufacturerSpecificData = emptyMap(),
            )

        assertEquals("Eddystone Beacon (UID)", result)
    }

    @Test
    fun `identify matches Tile from full Android service UUID`() {
        val result =
            KnownDeviceFingerprints.identify(
                serviceDataMap = mapOf(TILE_UUID to byteArrayOf()),
                manufacturerSpecificData = emptyMap(),
            )

        assertEquals("Tile Device", result)
    }

    private companion object {
        private const val FAST_PAIR_UUID = "0000fe2c-0000-1000-8000-00805f9b34fb"
        private const val EDDYSTONE_UUID = "0000feaa-0000-1000-8000-00805f9b34fb"
        private const val TILE_UUID = "0000feed-0000-1000-8000-00805f9b34fb"
        private val SONY_XM5_FAST_PAIR_PAYLOAD = byteArrayOf(0xD4.toByte(), 0x46, 0xA7.toByte())
    }
}
