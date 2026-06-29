package io.blueeye.core.data.mapper

import io.blueeye.core.data.db.entity.DeviceEntity
import io.blueeye.core.data.evidence.DeviceEvidenceFactory
import io.blueeye.core.model.Device

fun DeviceEntity.toDomain(): Device {
    return Device(
        fingerprint = fingerprint,
        macAddress = lastMacAddress ?: "UNKNOWN",
        macAddressType = macAddressType,
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
        userNotes = userNotes,
        alertSound = alertSound,
        alertVibration = alertVibration,
        isTrackingEnabled = isTrackingEnabled,
        isIgnoredForTracking = isIgnoredForTracking,
        calibrationLabel = calibrationLabel,
        identityCarryoverVerdict = identityCarryoverVerdict,
        firstSeenAt = firstSeenAt,
        lastSeenAt = lastSeenAt,
        rssi = normalizedDomainRssi,
        encounterCount = encounterCount,
        sensorData = sensorData,
        txPower = txPower,
        isConnectable = isConnectable,
        primaryPhy = primaryPhy,
        secondaryPhy = secondaryPhy,
        advertisingIntervalMs = advertisingIntervalMs,
        beaconType = beaconType,
        connectionStatus = connectionStatus,
        connectionAttempts = connectionAttempts,
        lastProbeTimestamp = lastProbeTimestamp,
        modelNumber = modelNumber,
        serialNumber = serialNumber,
        firmwareRevision = firmwareRevision,
        hardwareRevision = hardwareRevision,
        softwareRevision = softwareRevision,
        manufacturerName = manufacturerName,
        batteryLevel = batteryLevel,
        gattServices = gattServices,
        characteristicData = characteristicData,
        probeError = probeError,
        lastRawData = lastRawData,
        evidence = DeviceEvidenceFactory.build(this)
    )
}

fun List<DeviceEntity>.toDomain(): List<Device> = map { it.toDomain() }

private val DeviceEntity.normalizedDomainRssi: Int
    get() =
        if (hasLegacyRfcommFallbackRssi()) {
            RssiNormalization.UNAVAILABLE_RSSI
        } else {
            lastRssi
        }

private fun DeviceEntity.hasLegacyRfcommFallbackRssi(): Boolean =
    lastRssi == RssiNormalization.LEGACY_RFCOMM_FALLBACK_RSSI &&
        technology == "CLASSIC" &&
        classOfDevice == null &&
        connectionStatus in RssiNormalization.LEGACY_RFCOMM_FALLBACK_STATUSES

private object RssiNormalization {
    const val LEGACY_RFCOMM_FALLBACK_RSSI = -50
    const val UNAVAILABLE_RSSI = -100

    val LEGACY_RFCOMM_FALLBACK_STATUSES =
        setOf(
            "PROBING",
            "RFCOMM_FAIL",
        )
}
