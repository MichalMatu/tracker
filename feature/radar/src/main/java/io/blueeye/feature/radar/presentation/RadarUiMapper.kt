package io.blueeye.feature.radar.presentation

import io.blueeye.core.model.Device

/**
 * Maps domain [Device] objects to UI-ready [RadarUiItem]s.
 * Handles only fields that the compact Radar card and sectioning pipeline still consume.
 */
object RadarUiMapper {
    fun mapToUi(
        device: Device,
        isNew: Boolean,
        activeProbeMac: String?,
    ): RadarUiItem {
        val isProbing =
            activeProbeMac != null &&
                (
                    device.macAddress.equals(activeProbeMac, ignoreCase = true) ||
                        device.fingerprint.equals(activeProbeMac, ignoreCase = true)
                )

        return RadarUiItem(
            device = device,
            fingerprint = device.fingerprint,
            displayName = RadarIdentityUiFormatter.displayName(device),
            vendorAndType = RadarUiFormatter.formatVendorAndType(device),
            signalInfo = RadarUiFormatter.formatSignalInfo(device),
            statusInfo = RadarUiFormatter.formatStatusInfo(device, isNew),
            icons = RadarUiFormatter.formatIcons(device),
            isNew = isNew,
            isInWatchlist = device.isInWatchlist,
            isIgnored = device.isIgnoredForTracking,
            nameColor =
                when {
                    device.isIgnoredForTracking -> RadarUiColorToken.SAFE
                    (System.currentTimeMillis() - device.firstSeenAt) > 180_000 -> RadarUiColorToken.SAFE
                    else -> RadarUiColorToken.PRIMARY
                },
            isProbing = isProbing,
        )
    }
}
