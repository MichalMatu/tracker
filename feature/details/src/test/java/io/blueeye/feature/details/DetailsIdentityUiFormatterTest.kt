package io.blueeye.feature.details

import io.blueeye.core.model.Device
import io.blueeye.core.model.DeviceType
import io.blueeye.core.model.MacAddressType
import io.blueeye.core.model.TrackingStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DetailsIdentityUiFormatterTest {
    @Test
    fun `public address is available as secondary identity context`() {
        val items = DetailsUiFormatter.formatIdentity(device(macAddressType = MacAddressType.PUBLIC))

        assertEquals("Acme", items.value("Vendor"))
        assertEquals("Model 1", items.value("Model"))
        assertEquals("AA:BB:CC:DD:EE:FF (public)", items.value("Address context"))
    }

    @Test
    fun `random address is described without exposing raw mac as identity`() {
        val items = DetailsUiFormatter.formatIdentity(device(macAddressType = MacAddressType.RANDOM))

        assertEquals("Random / rotating address", items.value("Address context"))
        assertFalse(items.any { (_, value) -> value.contains("AA:BB:CC:DD:EE:FF") })
    }

    @Test
    fun `predicted model is used when probed model is unavailable`() {
        val items =
            DetailsUiFormatter.formatIdentity(
                device(macAddressType = MacAddressType.UNKNOWN).copy(
                    modelNumber = null,
                    predictedModel = "Predicted Tag",
                ),
            )

        assertEquals("Predicted Tag", items.value("Model"))
        assertEquals("Address type unknown", items.value("Address context"))
        assertTrue(items.any { (label, _) -> label == "Technology" })
    }

    private fun List<Pair<String, String>>.value(label: String): String {
        val match = first { (itemLabel, _) -> itemLabel == label }
        return match.second
    }

    private fun device(macAddressType: MacAddressType): Device =
        Device(
            fingerprint = "stable-device-id",
            macAddress = "AA:BB:CC:DD:EE:FF",
            macAddressType = macAddressType,
            technology = "BLE",
            name = "Sample",
            deviceType = DeviceType.UNKNOWN,
            vendorName = "Acme",
            predictedModel = "Predicted Model",
            trackingStatus = TrackingStatus.SAFE,
            followingScore = 0f,
            isSafeBeacon = false,
            isInWatchlist = false,
            userAlias = null,
            userNotes = null,
            alertSound = false,
            alertVibration = false,
            firstSeenAt = 1L,
            lastSeenAt = 2L,
            encounterCount = 1,
            modelNumber = "Model 1",
        )
}
