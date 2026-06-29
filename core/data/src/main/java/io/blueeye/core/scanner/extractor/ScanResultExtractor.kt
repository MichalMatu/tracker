package io.blueeye.core.scanner.extractor

import android.annotation.SuppressLint
import android.bluetooth.le.ScanResult
import android.os.Build
import android.os.ParcelUuid
import android.util.SparseArray
import io.blueeye.core.decoders.parser.generic.ScanRecordParser
import javax.inject.Inject
import javax.inject.Singleton

/** Data class containing all extracted BLE scan data. */
data class ExtractedScanData(
    val mac: String,
    val rssi: Int,
    val name: String?,
    val manufacturerId: Int?,
    val manufacturerData: ByteArray?,
    val manufacturerDataById: Map<Int, ByteArray>,
    val serviceUuids: List<String>,
    val serviceDataByUuid: Map<String, ByteArray>,
    val appearance: Int?,
    val technology: String,
    val txPower: Int?,
    val isConnectable: Boolean,
    val primaryPhy: Int?,
    val secondaryPhy: Int?,
    val rawData: ByteArray?,
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false
        other as ExtractedScanData
        return mac == other.mac && rssi == other.rssi
    }

    override fun hashCode(): Int {
        return mac.hashCode() * 31 + rssi
    }
}

/**
 * Extracts relevant data from Android BLE ScanResult objects. Handles API level differences for
 * PHY, TxPower, and Connectable status.
 */
@Singleton
class ScanResultExtractor
@Inject
constructor(private val scanRecordParser: ScanRecordParser) {
    /** Extracts all relevant data from a ScanResult. */
    @SuppressLint("MissingPermission")
    fun extract(result: ScanResult): ExtractedScanData {
        val device = result.device
        val rssi = result.rssi
        val record = result.scanRecord

        val name = record?.deviceName ?: device.name

        val manufacturerData = record?.manufacturerSpecificData
        val manufacturerDataById = manufacturerData.toMap()
        val manufacturerId =
            manufacturerDataById.keys.firstOrNull()
        val manufacturerBytes = manufacturerId?.let(manufacturerDataById::get)

        // Service UUIDs
        val advertisedServiceUuids = record?.serviceUuids?.map { it.uuid.toString() } ?: emptyList()
        val serviceDataByUuid = record?.serviceData.toServiceDataByUuid()
        val serviceDataUuids = serviceDataByUuid.keys.toList()
        val serviceUuids = (advertisedServiceUuids + serviceDataUuids).distinct()

        // BLE Appearance (AD Type 0x19)
        val appearance = scanRecordParser.extractAppearance(record?.bytes)

        // Technology detection
        val technology =
            scanRecordParser.determineTechnology(
                isLegacy =
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    result.isLegacy
                } else {
                    true
                },
                primaryPhy =
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    result.primaryPhy
                } else {
                    0
                },
            )

        // TxPower extraction
        val txPower = extractTxPower(result, record?.txPowerLevel)

        // Connectable status
        val isConnectable =
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                result.isConnectable
            } else {
                true // Fallback for older APIs
            }

        // PHY values (API 26+)
        val primaryPhy =
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                result.primaryPhy
            } else {
                null
            }

        val secondaryPhy =
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                result.secondaryPhy
            } else {
                null
            }

        return ExtractedScanData(
            mac = device.address,
            rssi = rssi,
            name = name,
            manufacturerId = manufacturerId,
            manufacturerData = manufacturerBytes,
            manufacturerDataById = manufacturerDataById,
            serviceUuids = serviceUuids,
            serviceDataByUuid = serviceDataByUuid,
            appearance = appearance,
            technology = technology,
            txPower = txPower,
            isConnectable = isConnectable,
            primaryPhy = primaryPhy,
            secondaryPhy = secondaryPhy,
            rawData = record?.bytes,
        )
    }

    private fun SparseArray<ByteArray>?.toMap(): Map<Int, ByteArray> {
        if (this == null || size() == 0) return emptyMap()

        return buildMap {
            repeat(size()) { index ->
                put(keyAt(index), valueAt(index))
            }
        }
    }

    private fun Map<ParcelUuid, ByteArray>?.toServiceDataByUuid(): Map<String, ByteArray> {
        if (isNullOrEmpty()) return emptyMap()

        return entries.associate { (uuid, data) ->
            uuid.uuid.toString() to data
        }
    }

    private fun extractTxPower(
        result: ScanResult,
        recordTxPower: Int?,
    ): Int? {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            if (result.txPower != android.bluetooth.le.ScanResult.TX_POWER_NOT_PRESENT) {
                result.txPower
            } else {
                recordTxPower
            }
        } else {
            recordTxPower
        }
    }
}
