package io.blueeye.core.scanner.source

import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanFilter
import android.bluetooth.le.ScanRecord
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.os.Build
import android.os.ParcelUuid
import android.util.Log
import javax.inject.Inject

enum class PassiveBleScanMode {
    BROAD,
    BACKGROUND_FILTERED,
}

class BleScanSource
@Inject
constructor(private val adapter: BluetoothAdapter?) {
    private var scanCallback: ScanCallback? = null

    @SuppressLint("MissingPermission")
    @Synchronized
    fun start(
        macFilter: String? = null,
        onResult: (ScanResult) -> Unit,
        onError: (Int) -> Unit,
        passiveMode: PassiveBleScanMode = PassiveBleScanMode.BROAD,
    ): Boolean {
        val activeAdapter = adapter
        return when {
            activeAdapter == null || !activeAdapter.isEnabled -> {
                Log.e("BleScanSource", "Bluetooth disabled or not available")
                false
            }
            scanCallback != null -> {
                Log.w("BleScanSource", "BLE Scanning already active")
                true
            }
            else -> startWithEnabledAdapter(activeAdapter, macFilter, onResult, onError, passiveMode)
        }
    }

    @SuppressLint("MissingPermission")
    private fun startWithEnabledAdapter(
        activeAdapter: BluetoothAdapter,
        macFilter: String?,
        onResult: (ScanResult) -> Unit,
        onError: (Int) -> Unit,
        passiveMode: PassiveBleScanMode,
    ): Boolean {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val isLeCoded = activeAdapter.isLeCodedPhySupported
            val isLeExt = activeAdapter.isLeExtendedAdvertisingSupported
            Log.i("BleScanSource", "HW Support: LE_CODED=$isLeCoded, LE_EXT_ADV=$isLeExt")
        }

        val scanner = activeAdapter.bluetoothLeScanner
        return if (scanner == null) {
            Log.e("BleScanSource", "Bluetooth LE scanner unavailable")
            false
        } else {
            startScanner(scanner, macFilter, onResult, onError, passiveMode)
        }
    }

    @SuppressLint("MissingPermission")
    private fun startScanner(
        scanner: android.bluetooth.le.BluetoothLeScanner,
        macFilter: String?,
        onResult: (ScanResult) -> Unit,
        onError: (Int) -> Unit,
        passiveMode: PassiveBleScanMode,
    ): Boolean {
        val callback =
            object : ScanCallback() {
                override fun onScanResult(callbackType: Int, result: ScanResult?) {
                    result?.let { onResult(it) }
                }

                override fun onBatchScanResults(results: MutableList<ScanResult>?) {
                    results?.forEach { onResult(it) }
                }

                override fun onScanFailed(errorCode: Int) {
                    Log.e("BleScanSource", "BLE Scan failed: $errorCode")
                    onError(errorCode)
                }
            }
        scanCallback = callback

        val settings =
            ScanSettings.Builder()
                .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY)
                .setReportDelay(0)
                .apply {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                        setLegacy(false)
                        setPhy(ScanSettings.PHY_LE_ALL_SUPPORTED)
                    }
                }
                .build()

        val filters = buildScanFilters(macFilter, passiveMode)

        return try {
            scanner.startScan(filters, settings, callback)
            Log.i(
                "BleScanSource",
                "BLE Scan started (mac=${macFilter ?: "any"}, mode=$passiveMode, filters=${filters?.size ?: 0})",
            )
            true
        } catch (e: RuntimeException) {
            if (scanCallback === callback) {
                scanCallback = null
            }
            Log.e("BleScanSource", "BLE Scan start failed", e)
            throw e
        }
    }

    private fun buildScanFilters(
        macFilter: String?,
        passiveMode: PassiveBleScanMode,
    ): List<ScanFilter>? =
        when {
            macFilter != null ->
                listOf(
                    ScanFilter.Builder()
                        .setDeviceAddress(macFilter)
                        .build(),
                )
            passiveMode == PassiveBleScanMode.BROAD -> null
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU -> buildAdvertisingTypeFilters()
            else -> buildLegacyBackgroundFilters()
        }

    /**
     * API 33+ can filter on the presence of an advertising-data type without knowing its payload.
     * The OR-list deliberately covers the broad structures Tracker consumes while remaining a
     * genuine filtered scan that Android may continue when the display is off.
     */
    private fun buildAdvertisingTypeFilters(): List<ScanFilter> =
        listOf(
            ScanRecord.DATA_TYPE_FLAGS,
            ScanRecord.DATA_TYPE_MANUFACTURER_SPECIFIC_DATA,
            ScanRecord.DATA_TYPE_SERVICE_DATA_16_BIT,
            ScanRecord.DATA_TYPE_SERVICE_UUIDS_16_BIT_PARTIAL,
            ScanRecord.DATA_TYPE_SERVICE_UUIDS_16_BIT_COMPLETE,
            ScanRecord.DATA_TYPE_SERVICE_UUIDS_128_BIT_PARTIAL,
            ScanRecord.DATA_TYPE_SERVICE_UUIDS_128_BIT_COMPLETE,
        ).map { advertisingDataType ->
            ScanFilter.Builder()
                .setAdvertisingDataType(advertisingDataType)
                .build()
        }

    /**
     * Older Android releases lack advertising-data-type filters. Keep their background fallback
     * intentionally tracker-focused while foreground scans remain fully broad.
     */
    private fun buildLegacyBackgroundFilters(): List<ScanFilter> =
        listOf(
            ScanFilter.Builder().setManufacturerData(APPLE_MANUFACTURER_ID, byteArrayOf()).build(),
            ScanFilter.Builder().setManufacturerData(SAMSUNG_MANUFACTURER_ID, byteArrayOf()).build(),
            ScanFilter.Builder().setServiceUuid(parcelUuid16(TILE_SERVICE_UUID)).build(),
            ScanFilter.Builder().setServiceData(parcelUuid16(SMARTTAG_SERVICE_UUID), byteArrayOf()).build(),
            ScanFilter.Builder().setServiceData(parcelUuid16(EDDYSTONE_SERVICE_UUID), byteArrayOf()).build(),
            ScanFilter.Builder().setServiceData(parcelUuid16(GOOGLE_FAST_PAIR_SERVICE_UUID), byteArrayOf()).build(),
            ScanFilter.Builder().setServiceUuid(parcelUuid16(CHIPOLO_SERVICE_UUID)).build(),
        )

    private fun parcelUuid16(shortUuid: String): ParcelUuid =
        ParcelUuid.fromString("0000$shortUuid-0000-1000-8000-00805f9b34fb")

    @SuppressLint("MissingPermission")
    @Synchronized
    fun stop() {
        val callback = scanCallback ?: return

        if (adapter?.isEnabled == true) {
            adapter.bluetoothLeScanner?.stopScan(callback)
            scanCallback = null
            Log.i("BleScanSource", "BLE Scan stopped")
        } else {
            scanCallback = null
            Log.i("BleScanSource", "BLE Scan state cleared")
        }
    }

    @Synchronized
    fun isScanning(): Boolean = scanCallback != null

    private companion object {
        const val APPLE_MANUFACTURER_ID = 0x004C
        const val SAMSUNG_MANUFACTURER_ID = 0x0075
        const val TILE_SERVICE_UUID = "feed"
        const val SMARTTAG_SERVICE_UUID = "fd5a"
        const val EDDYSTONE_SERVICE_UUID = "feaa"
        const val GOOGLE_FAST_PAIR_SERVICE_UUID = "fe2c"
        const val CHIPOLO_SERVICE_UUID = "fe33"
    }
}
