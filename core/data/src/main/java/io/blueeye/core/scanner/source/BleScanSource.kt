package io.blueeye.core.scanner.source

import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.os.Build
import android.util.Log
import javax.inject.Inject

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
            else -> startWithEnabledAdapter(activeAdapter, macFilter, onResult, onError)
        }
    }

    @SuppressLint("MissingPermission")
    private fun startWithEnabledAdapter(
        activeAdapter: BluetoothAdapter,
        macFilter: String?,
        onResult: (ScanResult) -> Unit,
        onError: (Int) -> Unit,
    ): Boolean {
        // Debug Hardware Capabilities
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
            startScanner(scanner, macFilter, onResult, onError)
        }
    }

    @SuppressLint("MissingPermission")
    private fun startScanner(
        scanner: android.bluetooth.le.BluetoothLeScanner,
        macFilter: String?,
        onResult: (ScanResult) -> Unit,
        onError: (Int) -> Unit,
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
                .setReportDelay(0) // Ensure immediate reporting
                .apply {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                        setLegacy(false)
                        setPhy(ScanSettings.PHY_LE_ALL_SUPPORTED)
                    }
                }
                .build()

        val filters =
            if (macFilter != null) {
                val filter =
                    android.bluetooth.le.ScanFilter.Builder()
                        .setDeviceAddress(macFilter)
                        .build()
                listOf(filter)
            } else {
                null
            }

        return try {
            scanner.startScan(filters, settings, callback)
            Log.i("BleScanSource", "BLE Scan started (Filter: ${macFilter ?: "None"})")
            true
        } catch (e: RuntimeException) {
            if (scanCallback === callback) {
                scanCallback = null
            }
            Log.e("BleScanSource", "BLE Scan start failed", e)
            throw e
        }
    }

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
}
