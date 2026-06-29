package io.blueeye.core.data.mapper

import io.blueeye.core.data.db.entity.SignalSampleEntity
import io.blueeye.core.model.SignalSample

fun SignalSampleEntity.toDomain(): SignalSample {
    return SignalSample(
        timestamp = timestamp,
        rssi = rssi,
        deviceFingerprint = deviceFingerprint,
        observedMac = observedMac,
        technology = technology,
        deviceName = deviceName,
        deviceType = deviceType,
        vendorName = vendorName,
        latitude = latitude,
        longitude = longitude,
        locationAccuracy = locationAccuracy,
        manufacturerId = manufacturerId,
        manufacturerDataHex = manufacturerDataHex,
        manufacturerDataByIdHex = manufacturerDataByIdHex,
        serviceUuids = serviceUuids,
        serviceDataByUuidHex = serviceDataByUuidHex,
        appearance = appearance,
        txPower = txPower,
        isConnectable = isConnectable,
        primaryPhy = primaryPhy,
        secondaryPhy = secondaryPhy,
        advertisingIntervalMs = advertisingIntervalMs,
        beaconType = beaconType,
        rawDataHex = rawDataHex,
        sensorData = sensorData,
        classOfDevice = classOfDevice,
        trackingStatus = trackingStatus,
        followingScore = followingScore,
        isTactical = isTactical,
        tacticalCategory = tacticalCategory,
        probeError = probeError,
    )
}

fun List<SignalSampleEntity>.toDomain(): List<SignalSample> = map { it.toDomain() }
