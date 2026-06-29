package io.blueeye.core.data.db.dao

import io.blueeye.core.model.DeviceType

/**
 * Parameters for updating device probe data in [DeviceDao].
 */
data class DeviceProbeParams(
    val status: String,
    val attempts: Int,
    val timestamp: Long,
    val model: String?,
    val serial: String?,
    val firmware: String?,
    val hardware: String?,
    val software: String?,
    val manufacturer: String?,
    val battery: Int?,
    val services: String?,
    val charData: String?,
    val error: String?,
    val newDeviceType: DeviceType
)

/**
 * Parameters for updating device scan data in [DeviceDao].
 */
data class DeviceScanParams(
    val mac: String?,
    val timestamp: Long,
    val rssi: Int,
    val technology: String,
    val name: String?,
    val vendor: String?,
    val newType: DeviceType,
    val sensor: String?,
    val tx: Int?,
    val connectable: Boolean,
    val phy1: Int?,
    val phy2: Int?,
    val interval: Long?,
    val beacon: String?,
    val rawData: String?,
    val services: String?
)
