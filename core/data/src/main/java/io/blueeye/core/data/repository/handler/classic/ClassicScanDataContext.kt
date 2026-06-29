package io.blueeye.core.data.repository.handler.classic

import io.blueeye.core.data.db.entity.DeviceEntity
import io.blueeye.core.model.DeviceType

/**
 * Context object for Classic Bluetooth scan data processing.
 */
data class ClassicScanDataContext(
    // ========== INPUT (immutable from scan) ==========
    val mac: String,
    val name: String?,
    val rssi: Int,
    val classOfDevice: Int?,
    val timestamp: Long = System.currentTimeMillis(),

    // ========== COMPUTED (enriched by pipeline) ==========

    /** Fingerprint is always MAC for Classic BT, but mutable for merge fixes */
    var fingerprint: String = mac,

    /** Resolved vendor name from OUI */
    var vendorName: String? = null,

    /** Classified device type */
    var deviceType: DeviceType = DeviceType.UNKNOWN,

    /** Existing device entity from database (if found) */
    var existingDevice: DeviceEntity? = null,

    /** Validated RSSI value */
    var validRssi: Int = rssi,

    /** Scan interval (ms) */
    var advertisingInterval: Long? = null,
    
    /** Found Service UUIDs (via SDP or cache) */
    var serviceUuids: List<String> = emptyList(),

    /** True only when Android provided a measured RSSI value. */
    val hasMeasuredRssi: Boolean = rssi in RSSI_MIN..RSSI_MAX,
) {
    companion object {
        const val RSSI_MIN = -120
        const val RSSI_MAX = -1
        const val RSSI_DEFAULT = -100
        const val RSSI_UNAVAILABLE = Int.MIN_VALUE

        fun fromScan(
            mac: String,
            name: String?,
            rssi: Int,
            classOfDevice: Int?,
            serviceUuids: List<String> = emptyList(),
        ): ClassicScanDataContext {
            return ClassicScanDataContext(
                mac = mac,
                name = name,
                rssi = rssi,
                classOfDevice = classOfDevice,
            ).apply {
                validRssi = if (hasMeasuredRssi) rssi else RSSI_DEFAULT
                this.serviceUuids = serviceUuids
            }
        }
    }
}
