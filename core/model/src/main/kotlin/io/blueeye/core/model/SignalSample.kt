package io.blueeye.core.model

/**
 * Domenowy model próbki sygnału (RSSI).
 */
data class SignalSample(
    val timestamp: Long,
    val rssi: Int,
    val deviceFingerprint: String = "",
    val observedMac: String? = null,
    val technology: String? = null,
    val deviceName: String? = null,
    val deviceType: String? = null,
    val vendorName: String? = null,
    val latitude: Double? = null,
    val longitude: Double? = null,
    val locationAccuracy: Float? = null,
    val manufacturerId: Int? = null,
    val manufacturerDataHex: String? = null,
    val manufacturerDataByIdHex: String? = null,
    val serviceUuids: String? = null,
    val serviceDataByUuidHex: String? = null,
    val appearance: Int? = null,
    val txPower: Int? = null,
    val isConnectable: Boolean? = null,
    val primaryPhy: Int? = null,
    val secondaryPhy: Int? = null,
    val advertisingIntervalMs: Long? = null,
    val beaconType: String? = null,
    val rawDataHex: String? = null,
    val sensorData: String? = null,
    val classOfDevice: Int? = null,
    val trackingStatus: String? = null,
    val followingScore: Float? = null,
    val isTactical: Boolean? = null,
    val tacticalCategory: String? = null,
    val probeError: String? = null,
)
