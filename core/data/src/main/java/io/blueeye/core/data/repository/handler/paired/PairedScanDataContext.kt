package io.blueeye.core.data.repository.handler.paired

import io.blueeye.core.data.db.entity.DeviceEntity
import io.blueeye.core.model.DeviceType

/**
 * Context object for Paired Device processing.
 */
data class PairedScanDataContext(
    // ========== INPUT (immutable from device) ==========
    val mac: String,
    val name: String?,
    val type: Int, // BluetoothDevice.DEVICE_TYPE_*
    val deviceClass: Int?,
    val timestamp: Long = System.currentTimeMillis(),

    // ========== COMPUTED (enriched by pipeline) ==========

    /** Fingerprint is always MAC for paired devices */
    val fingerprint: String = mac,

    /** Resolved vendor name from OUI */
    var vendorName: String? = null,

    /** Classified device type */
    var deviceType: DeviceType = DeviceType.UNKNOWN,

    /** Existing device entity from database (if found) */
    var existingDevice: DeviceEntity? = null,

    /** Battery level if available (e.g. via reflection) */
    var batteryLevel: Int? = null,

    /** Chipset info derived from MAC */
    var chipsetInfo: String? = null,

    /** Technology string (BLE, CLASSIC, DUAL) */
    var technology: String = "UNKNOWN",
) {
    companion object {
        fun fromDevice(
            mac: String,
            name: String?,
            type: Int,
            deviceClass: Int?
        ): PairedScanDataContext {
            return PairedScanDataContext(
                mac = mac,
                name = name,
                type = type,
                deviceClass = deviceClass
            )
        }
    }
}
