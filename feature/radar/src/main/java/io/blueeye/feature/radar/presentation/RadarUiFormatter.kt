package io.blueeye.feature.radar.presentation

import io.blueeye.core.model.Device
import io.blueeye.core.model.DeviceType
import io.blueeye.core.model.TrackingStatus
import io.blueeye.core.ui.utils.DeviceUiUtils
import kotlin.math.max
import kotlin.math.min

@Suppress("MagicNumber", "CyclomaticComplexMethod", "MaxLineLength")
object RadarUiFormatter {
    fun formatVendorAndType(device: Device): String {
        val vendor = RadarIdentityUiFormatter.knownVendorName(device)
        val type =
            DeviceUiUtils.mapDeviceTypeToString(device.deviceType)
                .takeIf { device.deviceType != DeviceType.UNKNOWN }
        return listOfNotNull(vendor, type).joinToString(separator = " • ")
    }

    fun formatSignalInfo(device: Device): RadarUiSignalInfo {
        val minRssi = -100
        val maxRssi = -40
        val progress = ((device.rssi - minRssi).toFloat() * 100 / (maxRssi - minRssi)).toInt()

        // RSSI is only signal quality, not a safety or risk verdict.
        val color =
            when {
                device.rssi > -60 -> RadarUiColorToken.PRIMARY
                device.rssi > -80 -> RadarUiColorToken.SECONDARY
                else -> RadarUiColorToken.OUTLINE
            }

        val (techBadge, techBadgeColor) = getTechBadgeAndColor(device)
        val distStr = DeviceUiUtils.calculateDistance(device.txPower, device.rssi)
        val timeDiff = System.currentTimeMillis() - device.lastSeenAt
        val timeStr = DeviceUiUtils.formatTimeDiff(timeDiff)

        return RadarUiSignalInfo(
            rssi = device.rssi,
            rssiText = "${device.rssi} dBm",
            signalColor = color,
            signalProgress = max(0, min(100, progress)),
            distanceText = distStr,
            techBadge = techBadge,
            techBadgeColor = techBadgeColor,
            timeSinceSeen = timeStr,
        )
    }

    fun formatStatusInfo(
        device: Device,
        isNew: Boolean,
    ): RadarUiStatusInfo {
        if (isNew) {
            return RadarUiStatusInfo(
                text = "NEW",
                textColor = RadarUiColorToken.WHITE,
                backgroundTint = RadarUiColorToken.PRIMARY,
                isWarning = false,
                cardBackgroundColor = null,
            )
        }

        val (textColor, bgTint) =
            when (device.trackingStatus) {
                TrackingStatus.SAFE -> RadarUiColorToken.SAFE to RadarUiColorToken.SAFE_CONTAINER
                TrackingStatus.SUSPICIOUS -> RadarUiColorToken.SUSPICIOUS to RadarUiColorToken.SUSPICIOUS_CONTAINER
                TrackingStatus.DANGEROUS -> RadarUiColorToken.SUSPICIOUS to RadarUiColorToken.SUSPICIOUS_CONTAINER
            }

        return RadarUiStatusInfo(
            text = device.trackingStatus.displayText,
            textColor = textColor,
            backgroundTint = bgTint,
            isWarning = device.trackingStatus != TrackingStatus.SAFE,
            cardBackgroundColor = null,
        )
    }

    fun formatIcons(device: Device): RadarUiIcons =
        RadarUiIcons(
            mainIconRes = DeviceUiUtils.getIconForType(device.deviceType),
            isConnectable = device.isConnectable == true,
        )

    private fun getTechBadgeAndColor(device: Device): Pair<String, RadarUiColorToken> =
        when {
            device.technology.contains("Ext", true) || device.technology.contains("Phy", true) ->
                "BLE 5" to RadarUiColorToken.PRIMARY
            device.technology.contains("Classic", true) -> "CLASSIC" to RadarUiColorToken.SECONDARY
            else -> "BLE" to RadarUiColorToken.PRIMARY
        }

    private val TrackingStatus.displayText: String
        get() =
            when (this) {
                TrackingStatus.SAFE -> "SAFE"
                TrackingStatus.SUSPICIOUS -> "SUSPICIOUS"
                TrackingStatus.DANGEROUS -> "REVIEW"
            }
}
