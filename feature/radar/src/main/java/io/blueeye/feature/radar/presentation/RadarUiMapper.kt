package io.blueeye.feature.radar.presentation

import io.blueeye.core.model.Device

/**
 * Maps domain [Device] objects to UI-ready [RadarUiItem]s.
 * Handles all text formatting, icon selection, and color logic.
 */
object RadarUiMapper {
    fun mapToUi(
        device: Device,
        isNew: Boolean,
        activeProbeMac: String?
    ): RadarUiItem {
        val isProbing = activeProbeMac != null && (device.macAddress.equals(activeProbeMac, ignoreCase = true) || device.fingerprint.equals(activeProbeMac, ignoreCase = true))

        val item =
            RadarUiItem(
                device = device, // Keep reference for click handlers
                fingerprint = device.fingerprint,
                displayName = RadarIdentityUiFormatter.displayName(device),
                vendorAndType = RadarUiFormatter.formatVendorAndType(device),
                sensorData = RadarUiFormatter.formatSensorDataString(device),
                signalInfo = RadarUiFormatter.formatSignalInfo(device),
                statusInfo = RadarUiFormatter.formatStatusInfo(device, isNew),
                connectionInfo = RadarUiFormatter.formatConnectionInfo(device),
                icons = RadarUiFormatter.formatIcons(device),
                badges = RadarUiFormatter.formatBadges(device),
                isNew = isNew,
                isInWatchlist = device.isInWatchlist,
                isIgnored = device.isIgnoredForTracking,
                nameColor =
                    when {
                        device.isIgnoredForTracking -> RadarUiColorToken.SAFE // Ignored = Green (Safe)
                        (System.currentTimeMillis() - device.firstSeenAt) > 180_000 -> RadarUiColorToken.SAFE // Old = Green
                        else -> RadarUiColorToken.PRIMARY // New = Primary Blue
                    },
                isProbing = isProbing,
                evidenceInfo = RadarEvidenceUiFormatter.format(device.evidence)
            )

        // Override status if probing active (Immediate UI feedback)
        return if (isProbing) {
            item.copy(
                connectionInfo = RadarUiConnectionInfo(true, "PROBING", RadarUiColorToken.SUSPICIOUS),
                badges =
                    item.badges.copy(
                        statusBadge = "PROBING",
                        statusColor = RadarUiColorToken.SUSPICIOUS
                    )
            )
        } else {
            item
        }
    }
}
