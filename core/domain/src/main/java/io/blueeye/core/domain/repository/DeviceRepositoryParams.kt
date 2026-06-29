package io.blueeye.core.domain.repository

import io.blueeye.core.model.DeviceType

/** Configuration for device updates (alias, notes, etc.) */
data class DeviceConfig(
    val alias: String?,
    val notes: String?,
    val isSafe: Boolean,
    val alertSound: Boolean,
    val alertVibration: Boolean,
    val isTrackingEnabled: Boolean = true
)

/** Full scan result data for BLE devices */
data class ScanResultParams(
    val mac: String,
    val rssi: Int,
    val timestamp: Long,
    val technology: String,
    val name: String? = null,
    val manufacturerId: Int? = null,
    val manufacturerData: ByteArray? = null,
    val manufacturerDataById: Map<Int, ByteArray> = emptyMap(),
    val serviceUuids: List<String> = emptyList(),
    val serviceDataByUuid: Map<String, ByteArray> = emptyMap(),
    val appearance: Int? = null,
    val txPower: Int? = null,
    val isConnectable: Boolean = false,
    val primaryPhy: Int? = null,
    val secondaryPhy: Int? = null,
    val rawData: ByteArray? = null
)

/** Data from GATT probing (Active Recon) */
data class RepoProbeParams(
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
    val charData: String? = null,
    val error: String? = null
)

/** Low-level scan update data */
data class RepoScanParams(
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
    val rawData: String?
)
