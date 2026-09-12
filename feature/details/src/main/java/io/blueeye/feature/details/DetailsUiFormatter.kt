package io.blueeye.feature.details

import io.blueeye.core.model.Device
import io.blueeye.core.model.MacAddressType
import java.text.SimpleDateFormat
import java.util.Locale

/**
 * Handles formatting of timestamps and Bluetooth PHY values for the UI.
 */
@Suppress("MagicNumber")
object DetailsUiFormatter {
    fun formatFriendlyTimestamp(timestamp: Long): String {
        val now = java.util.Calendar.getInstance()
        val time = java.util.Calendar.getInstance()
        time.timeInMillis = timestamp

        val formatTime = SimpleDateFormat("HH:mm:ss", Locale.getDefault())
        val formatDate = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())

        return if (now.get(java.util.Calendar.YEAR) == time.get(java.util.Calendar.YEAR) &&
            now.get(java.util.Calendar.DAY_OF_YEAR) == time.get(java.util.Calendar.DAY_OF_YEAR)
        ) {
            "Today, ${formatTime.format(time.time)}"
        } else if (now.get(java.util.Calendar.YEAR) == time.get(java.util.Calendar.YEAR) &&
            now.get(java.util.Calendar.DAY_OF_YEAR) - 1 == time.get(java.util.Calendar.DAY_OF_YEAR)
        ) {
            "Yesterday, ${formatTime.format(time.time)}"
        } else {
            "${formatDate.format(time.time)} ${formatTime.format(time.time)}"
        }
    }

    fun formatIdentity(device: Device): List<Pair<String, String>> =
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

    fun formatPhy(
        primary: Int?,
        secondary: Int?,
    ): String {
        if (primary == null) return "Legacy (1M)"

        val p = phyToString(primary)
        return if (secondary != null && secondary != 0) {
            "$p / ${phyToString(secondary)}"
        } else {
            p
        }
    }

    private fun phyToString(phy: Int): String {
        return when (phy) {
            1 -> "LE 1M" // BluetoothDevice.PHY_LE_1M
            2 -> "LE 2M" // BluetoothDevice.PHY_LE_2M
            3 -> "LE Coded" // BluetoothDevice.PHY_LE_CODED
            else -> "Unk ($phy)"
        }
    }
}
