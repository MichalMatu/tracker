package io.blueeye.feature.settings

import io.blueeye.core.model.Device
import io.blueeye.core.model.DeviceType

internal fun Device.hasExportIdentitySignal(): Boolean =
    deviceType != DeviceType.UNKNOWN ||
        name.isMeaningfulIdentityValue() ||
        userAlias.isMeaningfulIdentityValue() ||
        predictedModel.isMeaningfulIdentityValue() ||
        vendorName.isKnownVendorName()

private fun String?.isMeaningfulIdentityValue(): Boolean {
    val normalized = normalizeIdentityValue() ?: return false
    return normalized !in genericIdentityValues &&
        !normalized.startsWith(SessionExportIdentityConstants.UNKNOWN_PREFIX)
}

private fun String?.isKnownVendorName(): Boolean {
    val normalized = normalizeIdentityValue() ?: return false
    return normalized !in genericVendorValues &&
        !normalized.startsWith(SessionExportIdentityConstants.UNKNOWN_PREFIX)
}

private fun String?.normalizeIdentityValue(): String? =
    this
        ?.trim()
        ?.lowercase()
        ?.takeIf { it.isNotBlank() }

private object SessionExportIdentityConstants {
    const val UNKNOWN_PREFIX = "unknown "
}

private val genericIdentityValues =
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

private val genericVendorValues =
    setOf(
        "unknown",
        "unknown vendor",
        "n/a",
        "na",
        "null",
    )
