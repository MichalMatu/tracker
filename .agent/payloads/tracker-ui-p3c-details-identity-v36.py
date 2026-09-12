from pathlib import Path

screen_path = Path('feature/details/src/main/java/io/blueeye/feature/details/DetailsScreen.kt')
formatter_path = Path('feature/details/src/main/java/io/blueeye/feature/details/DetailsUiFormatter.kt')
test_path = Path('feature/details/src/test/java/io/blueeye/feature/details/DetailsIdentityUiFormatterTest.kt')

screen = screen_path.read_text()
old_identity = '''                InfoSection("Identity",
                    listOf(
                        "Vendor" to (dev.vendorName ?: "Unknown"),
                        "Technology" to dev.technology,
                        "Type" to dev.deviceType.name
                    )
                )
'''
new_identity = '''                InfoSection(
                    title = "Identity",
                    items = DetailsUiFormatter.formatIdentity(dev),
                )
'''
assert old_identity in screen
screen = screen.replace(old_identity, new_identity, 1)

old_rssi = '''                Text(
                    text = "${device.rssi} dBm",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.primary,
                    maxLines = 1,
                )
                DetailsDecisionSummaryText(summary)
'''
new_rssi = '''                Text(
                    text = "${device.rssi} dBm",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.primary,
                    maxLines = 1,
                )
                Text(
                    text = "Last seen ${DetailsUiFormatter.formatFriendlyTimestamp(device.lastSeenAt)}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                DetailsDecisionSummaryText(summary)
'''
assert old_rssi in screen
screen = screen.replace(old_rssi, new_rssi, 1)
screen_path.write_text(screen)

formatter = formatter_path.read_text()
assert 'import io.blueeye.core.model.Device\n' not in formatter
formatter = formatter.replace(
    'package io.blueeye.feature.details\n\n',
    'package io.blueeye.feature.details\n\nimport io.blueeye.core.model.Device\nimport io.blueeye.core.model.MacAddressType\n',
    1,
)
insert_before = '''    fun formatPhy(
'''
identity_code = '''    fun formatIdentity(device: Device): List<Pair<String, String>> =
        buildList {
            add("Vendor" to (device.vendorName ?: device.manufacturerName ?: "Unknown"))
            (device.modelNumber ?: device.predictedModel)
                ?.takeIf(String::isNotBlank)
                ?.let { model -> add("Model" to model) }
            add("Type" to device.deviceType.name)
            add("Technology" to device.technology)
            add("Address context" to formatAddressContext(device))
        }

    private fun formatAddressContext(device: Device): String =
        when (device.macAddressType) {
            MacAddressType.PUBLIC -> "${device.macAddress} (public)"
            MacAddressType.RANDOM -> "Random / rotating address"
            MacAddressType.UNKNOWN -> "Address type unknown"
        }

'''
assert insert_before in formatter
formatter = formatter.replace(insert_before, identity_code + insert_before, 1)
formatter_path.write_text(formatter)

assert not test_path.exists()
test_path.write_text('''package io.blueeye.feature.details

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

    private fun List<Pair<String, String>>.value(label: String): String =
        first { (itemLabel, _) -> itemLabel == label }.second

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
''')
