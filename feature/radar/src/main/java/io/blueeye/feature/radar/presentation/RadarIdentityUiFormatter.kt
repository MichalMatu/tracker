package io.blueeye.feature.radar.presentation

import io.blueeye.core.model.Device
import io.blueeye.core.model.DeviceType

object RadarIdentityUiFormatter {
    fun displayName(device: Device): String =
        device.userAlias?.takeIf { it.isMeaningfulIdentityValue() }
            ?: device.name?.takeIf { it.isMeaningfulIdentityValue() }
            ?: device.predictedModel?.takeIf { it.isMeaningfulIdentityValue() }
            ?: knownVendorName(device)?.let { "$it Device" }
            ?: fallbackDisplayName(device)

    fun hasIdentitySignal(device: Device): Boolean =
        device.deviceType != DeviceType.UNKNOWN ||
            device.name.isMeaningfulIdentityValue() ||
            device.userAlias.isMeaningfulIdentityValue() ||
            device.predictedModel.isMeaningfulIdentityValue() ||
            device.vendorName.isKnownVendorName()

    fun knownVendorName(device: Device): String? = device.vendorName?.takeIf { it.isKnownVendorName() }

    private fun fallbackDisplayName(device: Device): String =
        if (device.technology.contains(BLE_TECHNOLOGY, ignoreCase = true)) {
            "Unknown BLE device"
        } else {
            "Unknown Bluetooth device"
        }

    private fun String?.isMeaningfulIdentityValue(): Boolean {
        val normalized = normalizeIdentityValue() ?: return false
        return normalized !in GENERIC_IDENTITY_VALUES &&
            !normalized.startsWith(UNKNOWN_PREFIX)
    }

    private fun String?.isKnownVendorName(): Boolean {
        val normalized = normalizeIdentityValue() ?: return false
        return normalized !in GENERIC_VENDOR_VALUES &&
            !normalized.startsWith(UNKNOWN_PREFIX)
    }

    private fun String?.normalizeIdentityValue(): String? =
        this
            ?.trim()
            ?.lowercase()
            ?.takeIf { it.isNotBlank() }

    private const val UNKNOWN_PREFIX = "unknown "
    private const val BLE_TECHNOLOGY = "ble"

    private val GENERIC_IDENTITY_VALUES =
        setOf(
            "unknown",
            "unknown device",
            "ble device",
            "le device",
            "bluetooth device",
            "device",
            "n/a",
            "na",
            "null",
        )

    private val GENERIC_VENDOR_VALUES =
        setOf(
            "unknown",
            "unknown vendor",
            "n/a",
            "na",
            "null",
        )
}
