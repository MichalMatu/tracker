package io.blueeye.feature.settings

import io.blueeye.core.model.SignalSample

internal object SessionSignalSampleExportQuality {
    fun countGpsSamples(samples: List<SignalSample>): Int = samples.count { sample -> sample.hasGps() }

    fun countRawPayloadSamples(samples: List<SignalSample>): Int = samples.count(::hasRawPayloadData)

    fun countScanMetadataSamples(samples: List<SignalSample>): Int = samples.count(::hasScanMetadata)

    fun hasRawPayloadData(sample: SignalSample): Boolean =
        !sample.rawDataHex.isNullOrBlank() ||
            !sample.manufacturerDataHex.isNullOrBlank() ||
            !sample.manufacturerDataByIdHex.isNullOrBlank() ||
            !sample.serviceDataByUuidHex.isNullOrBlank()

    fun hasScanMetadata(sample: SignalSample): Boolean =
        hasRawPayloadData(sample) ||
            sample.metadataFields().any { value -> value != null }

    private fun SignalSample.hasGps(): Boolean = latitude != null && longitude != null

    private fun SignalSample.metadataFields(): List<Any?> =
        listOf(
            observedMac,
            technology,
            deviceName,
            deviceType,
            vendorName,
            manufacturerId,
            serviceUuids,
            appearance,
            txPower,
            isConnectable,
            primaryPhy,
            secondaryPhy,
            advertisingIntervalMs,
            beaconType,
            sensorData,
            classOfDevice,
            trackingStatus,
            followingScore,
            isTactical,
            tacticalCategory,
            probeError,
        )
}
