package io.blueeye.core.data.mapper

import io.blueeye.core.data.db.entity.DeviceEntity
import io.blueeye.core.data.db.projection.RadarDeviceProjection
import io.blueeye.core.data.evidence.DeviceEvidenceFactory
import io.blueeye.core.model.RadarDeviceSummary

fun RadarDeviceProjection.toRadarDomain(): RadarDeviceSummary {
    val evidenceDevice = toEvidenceDeviceEntity()

    return RadarDeviceSummary(
        fingerprint = fingerprint,
        macAddress = lastMacAddress ?: "UNKNOWN",
        technology = technology,
        name = lastDeviceName,
        deviceType = deviceType,
        vendorName = vendorName,
        predictedModel = predictedModel,
        trackingStatus = trackingStatus,
        followingScore = followingScore,
        isSafeBeacon = isSafeBeacon,
        isInWatchlist = isInWatchlist,
        userAlias = userAlias,
        isIgnoredForTracking = isIgnoredForTracking,
        calibrationLabel = calibrationLabel,
        firstSeenAt = firstSeenAt,
        lastSeenAt = lastSeenAt,
        rssi = normalizedRadarRssi,
        txPower = txPower,
        isConnectable = isConnectable,
        evidenceSignals = DeviceEvidenceFactory.buildRadarSignals(evidenceDevice),
    )
}

fun List<RadarDeviceProjection>.toRadarDomain(): List<RadarDeviceSummary> = map { it.toRadarDomain() }

private fun RadarDeviceProjection.toEvidenceDeviceEntity(): DeviceEntity =
    DeviceEntity(
        fingerprint = fingerprint,
        lastMacAddress = lastMacAddress,
        technology = technology,
        lastDeviceName = lastDeviceName,
        deviceType = deviceType,
        vendorName = vendorName,
        manufacturerId = manufacturerId,
        predictedModel = predictedModel,
        classOfDevice = classOfDevice,
        trackingStatus = trackingStatus,
        followingScore = followingScore,
        isInWatchlist = isInWatchlist,
        isSafeBeacon = isSafeBeacon,
        userAlias = userAlias,
        isIgnoredForTracking = isIgnoredForTracking,
        calibrationLabel = calibrationLabel,
        lastRssi = lastRssi,
        firstSeenAt = firstSeenAt,
        lastSeenAt = lastSeenAt,
        txPower = txPower,
        isConnectable = isConnectable,
        beaconType = beaconType,
        connectionStatus = connectionStatus,
        gattServices = gattServices,
        lastRawData = lastRawData,
    )

private val RadarDeviceProjection.normalizedRadarRssi: Int
    get() =
        if (hasLegacyRfcommFallbackRssi()) {
            RadarRssiNormalization.UNAVAILABLE_RSSI
        } else {
            lastRssi
        }

private fun RadarDeviceProjection.hasLegacyRfcommFallbackRssi(): Boolean =
    lastRssi == RadarRssiNormalization.LEGACY_RFCOMM_FALLBACK_RSSI &&
        technology == "CLASSIC" &&
        classOfDevice == null &&
        connectionStatus in RadarRssiNormalization.LEGACY_RFCOMM_FALLBACK_STATUSES

private object RadarRssiNormalization {
    const val LEGACY_RFCOMM_FALLBACK_RSSI = -50
    const val UNAVAILABLE_RSSI = -100

    val LEGACY_RFCOMM_FALLBACK_STATUSES =
        setOf(
            "PROBING",
            "RFCOMM_FAIL",
        )
}
