package io.blueeye.feature.radar.presentation

import io.blueeye.core.domain.bluetooth.MacAddressAnalyzer
import io.blueeye.core.model.Device
import io.blueeye.core.model.DeviceCalibrationLabel
import io.blueeye.core.model.DeviceType
import io.blueeye.core.model.IdentityCarryoverVerdict
import io.blueeye.core.model.MacAddressType
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
            timeSinceSeen = timeStr
        )
    }

    fun formatStatusInfo(
        device: Device,
        isNew: Boolean
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
            cardBackgroundColor = null
        )
    }

    fun formatConnectionInfo(device: Device): RadarUiConnectionInfo {
        return when (device.connectionStatus) {
            "PROBED" -> RadarUiConnectionInfo(true, "LINKED", RadarUiColorToken.SAFE)
            "RFCOMM_OK" -> RadarUiConnectionInfo(true, "RFCOMM", RadarUiColorToken.SAFE)
            "RFCOMM_FAIL" -> RadarUiConnectionInfo(true, "RFCOMM ERR", RadarUiColorToken.SUSPICIOUS)
            "FAILED" -> RadarUiConnectionInfo(true, "RETRY ${device.connectionAttempts}", RadarUiColorToken.SUSPICIOUS)
            "FAILED_PERMANENT" -> RadarUiConnectionInfo(true, "FAILED", RadarUiColorToken.DANGEROUS)
            "PROBING" -> RadarUiConnectionInfo(true, "PROBING", RadarUiColorToken.SUSPICIOUS)
            else -> {
                val (text, color) =
                    when {
                        device.technology == "CLASSIC" -> "CLASSIC" to RadarUiColorToken.GRAY
                        device.isConnectable == false -> "BROADCAST" to RadarUiColorToken.GRAY
                        device.rssi < -90 -> "WEAK" to RadarUiColorToken.GRAY
                        device.connectionStatus == "NONE" -> "PENDING" to RadarUiColorToken.PRIMARY
                        else -> null to null
                    }

                if (text != null && color != null) {
                    RadarUiConnectionInfo(true, text, color)
                } else {
                    RadarUiConnectionInfo(false, "", RadarUiColorToken.TRANSPARENT)
                }
            }
        }
    }

    fun formatIcons(device: Device): RadarUiIcons {
        return RadarUiIcons(
            mainIconRes = DeviceUiUtils.getIconForType(device.deviceType),
            isConnectable = device.isConnectable == true
        )
    }

    fun formatBadges(device: Device): RadarBadgeInfo {
        val (techBadge, techColor) = getTechBadgeAndColor(device)

        val privacyBadge =
            if (device.macAddressType == MacAddressType.PUBLIC) {
                "PUBLIC"
            } else {
                MacAddressAnalyzer.getPrivacyLabel(device.macAddress).uppercase()
            }

        val isWatchlistPaused = device.isInWatchlist && !device.isTrackingEnabled
        val watchlistBadge = "ALERTS PAUSED".takeIf { isWatchlistPaused }
        val watchlistColor =
            if (isWatchlistPaused) {
                RadarUiColorToken.WARNING
            } else {
                RadarUiColorToken.GRAY
            }
        val (statusBadge, statusColor) = getStatusBadgeAndColor(device)
        val (calibrationBadge, calibrationColor) = getCalibrationBadgeAndColor(device)

        // Delegate string parsing to SensorDataParser
        val parsedData = RadarSensorDataParser.parse(device)

        return RadarBadgeInfo(
            techBadge = techBadge,
            techColor = techColor,
            privacyBadge = privacyBadge,
            watchlistBadge = watchlistBadge,
            watchlistColor = watchlistColor,
            statusBadge = statusBadge,
            statusColor = statusColor,
            calibrationBadge = calibrationBadge,
            calibrationColor = calibrationColor,
            batteryText = parsedData.batteryText,
            temperatureText = parsedData.temperatureText,
            humidityText = parsedData.humidityText,
            voltageText = parsedData.voltageText,
            extraText = parsedData.extraText
        )
    }

    fun formatSensorDataString(device: Device): String? {
        // This logic extracted from old mapToUi mapSensorData
        return RadarSensorDataParser.formatFullSensorString(device)
    }

    private fun getTechBadgeAndColor(device: Device): Pair<String, RadarUiColorToken> {
        return when {
            device.technology.contains("Ext", true) || device.technology.contains("Phy", true) -> "BLE 5" to RadarUiColorToken.PRIMARY
            device.technology.contains("Classic", true) -> "CLASSIC" to RadarUiColorToken.SECONDARY
            else -> "BLE" to RadarUiColorToken.PRIMARY
        }
    }

    private fun getStatusBadgeAndColor(device: Device): Pair<String?, RadarUiColorToken> {
        return when (device.connectionStatus) {
            "PROBED" -> "LINKED" to RadarUiColorToken.SAFE
            "RFCOMM_OK" -> "RFCOMM" to RadarUiColorToken.SAFE
            "RFCOMM_FAIL" -> "RF ERR" to RadarUiColorToken.SUSPICIOUS
            "FAILED" -> "RETRY" to RadarUiColorToken.SUSPICIOUS
            "FAILED_PERMANENT" -> "FAILED" to RadarUiColorToken.DANGEROUS
            "PROBING" -> "PROBING" to RadarUiColorToken.SUSPICIOUS
            "NONE" -> "PENDING" to RadarUiColorToken.PRIMARY
            else ->
                when {
                    device.technology == "CLASSIC" -> null to RadarUiColorToken.GRAY
                    device.isConnectable == false -> "BROADCAST" to RadarUiColorToken.GRAY
                    device.rssi < -90 -> "WEAK" to RadarUiColorToken.GRAY
                    else -> null to RadarUiColorToken.GRAY
                }
        }
    }

    private fun getCalibrationBadgeAndColor(device: Device): Pair<String?, RadarUiColorToken> {
        val calibrationBadge =
            when (device.calibrationLabel) {
                DeviceCalibrationLabel.TRUE_POSITIVE -> "TRUE POSITIVE" to RadarUiColorToken.SUSPICIOUS
                DeviceCalibrationLabel.FALSE_POSITIVE -> "FALSE POSITIVE" to RadarUiColorToken.SAFE
                DeviceCalibrationLabel.KNOWN_SAFE -> "KNOWN SAFE" to RadarUiColorToken.SAFE
                DeviceCalibrationLabel.SUSPICIOUS -> "USER SUSPICIOUS" to RadarUiColorToken.SUSPICIOUS
                DeviceCalibrationLabel.UNKNOWN -> null
            }
        return calibrationBadge ?: when (device.identityCarryoverVerdict) {
            IdentityCarryoverVerdict.UNREVIEWED -> null to RadarUiColorToken.GRAY
            IdentityCarryoverVerdict.CONFIRMED_SAME_DEVICE -> "SAME DEVICE" to RadarUiColorToken.SAFE
            IdentityCarryoverVerdict.FALSE_MATCH -> "FALSE MATCH" to RadarUiColorToken.WARNING
            IdentityCarryoverVerdict.INCONCLUSIVE -> "CARRYOVER ?" to RadarUiColorToken.WARNING
        }
    }

    private val TrackingStatus.displayText: String
        get() =
            when (this) {
                TrackingStatus.SAFE -> "SAFE"
                TrackingStatus.SUSPICIOUS -> "SUSPICIOUS"
                TrackingStatus.DANGEROUS -> "REVIEW"
            }
}
