package io.blueeye.core.data.db.projection

import io.blueeye.core.model.DeviceCalibrationLabel
import io.blueeye.core.model.DeviceType
import io.blueeye.core.model.TrackingStatus

/**
 * Room projection for the compact Radar path.
 *
 * Keep this list aligned with RadarDeviceSummary plus the persisted inputs required to derive the
 * section-decision evidence flags without constructing the complete Device evidence model.
 */
data class RadarDeviceProjection(
    val fingerprint: String,
    val lastMacAddress: String?,
    val technology: String,
    val lastDeviceName: String?,
    val deviceType: DeviceType,
    val vendorName: String?,
    val manufacturerId: Int?,
    val predictedModel: String?,
    val trackingStatus: TrackingStatus,
    val followingScore: Float,
    val isSafeBeacon: Boolean,
    val isInWatchlist: Boolean,
    val userAlias: String?,
    val isIgnoredForTracking: Boolean,
    val calibrationLabel: DeviceCalibrationLabel,
    val lastRssi: Int,
    val firstSeenAt: Long,
    val lastSeenAt: Long,
    val txPower: Int?,
    val isConnectable: Boolean?,
    val classOfDevice: Int?,
    val beaconType: String?,
    val connectionStatus: String,
    val gattServices: String?,
    val lastRawData: String?,
)
