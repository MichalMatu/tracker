package io.blueeye.core.data.repository.handler.ble

import io.blueeye.core.data.db.entity.DeviceEntity
import io.blueeye.core.model.DetectionEvidence
import io.blueeye.core.model.DeviceType
import io.blueeye.core.model.MacAddressType
import io.blueeye.core.model.SensorData
import io.blueeye.core.scanner.model.BleScanResultData

/**
 * Context object that carries all scan data through the processing pipeline.
 *
 * This is a mutable container that gets enriched by each processing step:
 * 1. MacAddressResolver - resolves vendor, MAC type, and carryover
 * 2. DeviceEnricher - decodes sensors, vendors, tactical data
 * 3. ScanResultClassifier - determines device type and beacon
 * 4. DevicePersister - saves to database
 */
data class ScanDataContext(
    // ========== INPUT (immutable from scan) ==========
    val mac: String,
    val rssi: Int,
    val timestamp: Long,
    val technology: String,
    val name: String?,
    val manufacturerId: Int?,
    val manufacturerData: ByteArray?,
    val manufacturerDataById: Map<Int, ByteArray> = emptyMap(),
    val serviceUuids: List<String>,
    val serviceDataByUuid: Map<String, ByteArray> = emptyMap(),
    val appearance: Int?,
    val txPower: Int?,
    val isConnectable: Boolean,
    val primaryPhy: Int?,
    val secondaryPhy: Int?,
    val rawData: ByteArray?,

    // ========== COMPUTED (enriched by pipeline) ==========

    /** Device fingerprint - may differ from MAC due to carryover tracking */
    var fingerprint: String = mac,

    /** Sanitized device name */
    var sanitizedName: String? = null,

    /** Resolved vendor name from OUI or Company ID */
    var vendorName: String? = null,

    /** MAC address type (Public, Random, RPA) */
    var macAddressType: MacAddressType = MacAddressType.UNKNOWN,

    /** Whether this MAC was correlated to an existing device (carryover) */
    var isCarryover: Boolean = false,

    /** Number of correlated MAC rotations observed for this physical target */
    var macChangeCount: Int = 0,

    /** Matcher reason code for the last MAC carryover correlation */
    var carryoverReasonCode: String? = null,

    /** Matcher confidence for the last MAC carryover correlation */
    var carryoverConfidence: Float = 0f,

    /** Compact feature summary used by the MAC carryover matcher */
    var carryoverFeatures: String? = null,

    /** Whether this scan result is provisional (too weak to persist) */
    var isProvisional: Boolean = false,

    /** Existing device entity from database (if found) */
    var existingDevice: DeviceEntity? = null,

    /** Classified device type */
    var deviceType: DeviceType = DeviceType.UNKNOWN,

    /** Tracking Status (Safe, Suspicious, Dangerous) */
    var trackingStatus: io.blueeye.core.model.TrackingStatus = io.blueeye.core.model.TrackingStatus.SAFE,

    /** Follow-Me Score (0.0 - 100.0) */
    var followingScore: Float = 0f,

    /** User-facing reason for the current Follow-Me score */
    var followMeExplanation: String? = null,

    /** Structured Follow-Me score components */
    var followMeDurationScore: Int = 0,
    var followMeRssiStabilityScore: Int = 0,
    var followMeDeviceTypeScore: Int = 0,
    var followMeMacBehaviorScore: Int = 0,
    var followMeEncounterScore: Int = 0,
    var followMeUserMoved: Boolean? = null,
    var followMeBaselineDevice: Boolean? = null,

    /** Vendor-specific device type (may override scan type) */
    var vendorDeviceType: DeviceType = DeviceType.UNKNOWN,

    /** Detected beacon type (iBeacon, Eddystone, etc.) */
    var beaconType: String? = null,

    // Probe Results
    var probeManufacturer: String? = null,
    var probeModel: String? = null,
    var probeSerial: String? = null,
    var probeFirmware: String? = null,
    var probeError: String? = null,


    /** Decoded sensor data (temperature, humidity, etc.) */
    var sensorData: SensorData? = null,

    /** Vendor-specific model name */
    var vendorModel: String? = null,

    /** Whether this scan is consistent with professional/public-safety equipment */
    var isTactical: Boolean = false,

    /** Tactical category description */
    var tacticalCategory: String? = null,

    /** Public-safety/professional evidence emitted during tactical classification */
    var tacticalEvidence: List<DetectionEvidence> = emptyList(),

    /** Follow-Me alert evidence emitted by alert decision logic and stored after persistence */
    var followMeAlertEvidence: DetectionEvidence? = null,

    /** Precise fingerprint model from known device database */
    var fingerprintModel: String? = null,

    /** Calculated advertising interval (ms) */
    var advertisingInterval: Long? = null,

    /** Raw data as hex string for persistence */
    var rawDataHex: String? = null,

    /** Validated RSSI value */
    var validRssi: Int = rssi,

    /** Original scan data source (if available) */
    val scanData: BleScanResultData? = null
) {
    companion object {
        private const val RSSI_MIN = -120
        private const val RSSI_MAX = -1
        private const val RSSI_DEFAULT = -100
        private const val ASCII_PRINTABLE_MIN = 32
        private const val ASCII_PRINTABLE_MAX = 126

        /**
         * Creates a ScanDataContext from scan parameters.
         */
        fun fromScan(data: BleScanResultData): ScanDataContext {
            return ScanDataContext(
                mac = data.mac,
                rssi = data.rssi,
                timestamp = data.timestamp,
                technology = data.technology,
                name = data.name,
                manufacturerId = data.manufacturerId,
                manufacturerData = data.manufacturerData,
                manufacturerDataById = data.manufacturerDataById,
                serviceUuids = data.serviceUuids,
                serviceDataByUuid = data.serviceDataByUuid,
                appearance = data.appearance,
                txPower = data.txPower,
                isConnectable = data.isConnectable,
                primaryPhy = data.primaryPhy,
                secondaryPhy = data.secondaryPhy,
                rawData = data.rawData,
                scanData = data,
            ).apply {
                // Pre-compute common values
                sanitizedName = sanitizeName(data.name)
                rawDataHex = data.rawData?.joinToString("") { "%02X".format(it) }
                validRssi = if (data.rssi in RSSI_MIN..RSSI_MAX) data.rssi else RSSI_DEFAULT
            }
        }

        private fun sanitizeName(name: String?): String? {
            if (name.isNullOrBlank()) return null
            val clean = name.filter { it.code in ASCII_PRINTABLE_MIN..ASCII_PRINTABLE_MAX }
            return if (clean.length < 2) null else clean
        }
    }

    // ByteArray doesn't implement equals/hashCode correctly by default
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false
        other as ScanDataContext
        return mac == other.mac && timestamp == other.timestamp
    }

    override fun hashCode(): Int {
        var result = mac.hashCode()
        result = 31 * result + timestamp.hashCode()
        return result
    }

    fun manufacturerRecords(): Map<Int, ByteArray> {
        if (manufacturerDataById.isNotEmpty()) return manufacturerDataById
        val id = manufacturerId
        val data = manufacturerData
        return if (id != null && data != null) mapOf(id to data) else emptyMap()
    }

    fun serviceDataRecords(): Map<String, ByteArray> = serviceDataByUuid
}
