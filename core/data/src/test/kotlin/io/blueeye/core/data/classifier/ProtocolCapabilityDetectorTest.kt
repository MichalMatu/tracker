package io.blueeye.core.data.classifier

import io.blueeye.core.model.ProtocolCapability
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ProtocolCapabilityDetectorTest {
    @Test
    fun `Sony headphones retain Find Hub capability without changing physical type`() {
        val capabilities = ProtocolCapabilityDetector.fromAdvertisement(
            serviceUuids = listOf("feaa", "fcb2"),
            serviceDataByUuid = mapOf(
                "0000feaa-0000-1000-8000-00805f9b34fb" to
                    (byteArrayOf(0x40) + ByteArray(20) { 0x01 } + byteArrayOf(0x01)),
            ),
        )

        assertTrue(capabilities.contains(ProtocolCapability.FIND_HUB))
        assertTrue(capabilities.contains(ProtocolCapability.DULT))
        assertFalse(capabilities.contains(ProtocolCapability.EDDYSTONE))
    }

    @Test
    fun `256 bit Find Hub FEAA frame is recognized`() {
        val capabilities = ProtocolCapabilityDetector.fromAdvertisement(
            serviceUuids = listOf("feaa"),
            serviceDataByUuid = mapOf(
                "feaa" to (byteArrayOf(0x41) + ByteArray(32) { 0x01 } + byteArrayOf(0x01)),
            ),
        )
        assertTrue(capabilities.contains(ProtocolCapability.FIND_HUB))
    }

    @Test
    fun `FEAA alone does not imply Eddystone or Find Hub`() {
        assertEquals(
            emptySet<ProtocolCapability>(),
            ProtocolCapabilityDetector.fromAdvertisement(listOf("feaa"), emptyMap()),
        )
    }

    @Test
    fun `persisted service and validated beacon label reconstruct distinct capabilities`() {
        val capabilities = ProtocolCapabilityDetector.fromPersisted(
            beaconType = "Google Find Hub",
            gattServices = "0000fcb2-0000-1000-8000-00805f9b34fb,fe2c",
        )
        assertTrue(capabilities.contains(ProtocolCapability.FIND_HUB))
        assertTrue(capabilities.contains(ProtocolCapability.DULT))
        assertTrue(capabilities.contains(ProtocolCapability.FAST_PAIR))
    }
}
