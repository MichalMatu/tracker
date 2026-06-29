package io.blueeye.core.scanner.source

import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothClass
import android.bluetooth.BluetoothDevice
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import android.os.ParcelUuid
import android.util.Log
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject

class ClassicScanSource
@Inject
constructor(
    @ApplicationContext private val context: Context,
    private val adapter: BluetoothAdapter?,
) {
    private var isScanning = false

    // Callback function: (Device, RSSI, DeviceClass?, UUIDs?) -> Unit
    private var onDeviceFound: ((BluetoothDevice, Int, Int?, List<ParcelUuid>?) -> Unit)? = null

    private val discoveryReceiver =
        object : BroadcastReceiver() {
            @SuppressLint("MissingPermission")
            override fun onReceive(
                context: Context?,
                intent: Intent?,
            ) {
                when (intent?.action) {
                    BluetoothDevice.ACTION_FOUND -> handleDeviceFound(intent)
                    BluetoothDevice.ACTION_UUID -> handleDeviceUuids(intent)
                    BluetoothAdapter.ACTION_DISCOVERY_FINISHED -> restartDiscoveryIfNeeded()
                }
            }
        }

    @SuppressLint("MissingPermission")
    fun start(callback: (BluetoothDevice, Int, Int?, List<ParcelUuid>?) -> Unit): Boolean {
        if (adapter == null || !adapter.isEnabled) {
            Log.e("ClassicScanSource", "Bluetooth disabled or not available")
            return false
        }

        if (isScanning) {
            Log.w("ClassicScanSource", "Classic Scanning already active")
            return true
        }

        onDeviceFound = callback
        isScanning = true

        val filter =
            IntentFilter().apply {
                addAction(BluetoothDevice.ACTION_FOUND)
                addAction(BluetoothDevice.ACTION_UUID)
                addAction(BluetoothAdapter.ACTION_DISCOVERY_FINISHED)
            }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            context.registerReceiver(discoveryReceiver, filter, Context.RECEIVER_EXPORTED)
        } else {
            @Suppress("DEPRECATION")
            context.registerReceiver(discoveryReceiver, filter)
        }

        val started = adapter.startDiscovery()
        if (!started) {
            isScanning = false
            onDeviceFound = null
            try {
                context.unregisterReceiver(discoveryReceiver)
            } catch (@Suppress("SwallowedException") e: IllegalArgumentException) {
                // Receiver was not registered.
            }
            Log.e("ClassicScanSource", "Classic Discovery failed to start")
            return false
        }

        Log.i("ClassicScanSource", "Classic Discovery started")
        return true
    }

    @SuppressLint("MissingPermission")
    fun stop() {
        if (!isScanning) return

        isScanning = false
        if (adapter?.isDiscovering == true) {
            adapter.cancelDiscovery()
        }

        try {
            context.unregisterReceiver(discoveryReceiver)
        } catch (@Suppress("SwallowedException") e: IllegalArgumentException) {
            // Ignore if not registered
        }
        onDeviceFound = null
        Log.i("ClassicScanSource", "Classic Discovery stopped")
    }

    @SuppressLint("MissingPermission")
    private fun triggerSdpIfGeneric(device: BluetoothDevice, name: String?) {
        val deviceName = name ?: device.name
        if (isGenericName(deviceName)) {
            // It's async and the OS handles caching/limiting often.
            val result = device.fetchUuidsWithSdp()
            Log.v(
                "ClassicScanSource",
                "Triggering SDP for generic device '$deviceName' (${device.address}): $result",
            )
        }
    }

    private fun isGenericName(name: String?): Boolean {
        if (name.isNullOrBlank()) return true
        val lower = name.lowercase()
        return lower in setOf(
            "find my",
            "apple inc",
            "apple device",
            "unknown",
            "ble device",
            "tracker",
            "le device",
            "smart beacon",
            "beacon",
            "headphones",
            "headset",
            "audio",
            "accessory",
            "samsung",
            "microsoft",
        ) || lower.startsWith("apple")
    }

    @SuppressLint("MissingPermission")
    private fun handleDeviceFound(intent: Intent) {
        val device = getBluetoothDevice(intent) ?: return
        val rssi =
            intent.getShortExtra(BluetoothDevice.EXTRA_RSSI, Short.MIN_VALUE)
                .toInt()
        val bluetoothClass = getBluetoothClass(intent)

        triggerSdpIfGeneric(device, device.name)
        onDeviceFound?.invoke(device, rssi, bluetoothClass?.deviceClass, null)
    }

    private fun handleDeviceUuids(intent: Intent) {
        val device = getBluetoothDevice(intent)
        val uuids = getParcelUuids(intent)

        if (device != null && !uuids.isNullOrEmpty()) {
            Log.v("ClassicScanSource", "Received UUIDs for ${device.address}: ${uuids.joinToString()}")
            onDeviceFound?.invoke(device, DEFAULT_UUID_RSSI, null, uuids)
        }
    }

    @SuppressLint("MissingPermission")
    private fun restartDiscoveryIfNeeded() {
        if (isScanning && adapter?.isEnabled == true) {
            Log.i("ClassicScanSource", "Restarting Classic Discovery loop...")
            adapter.startDiscovery()
        }
    }

    private fun getBluetoothDevice(intent: Intent): BluetoothDevice? {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            intent.getParcelableExtra(
                BluetoothDevice.EXTRA_DEVICE,
                BluetoothDevice::class.java,
            )
        } else {
            @Suppress("DEPRECATION")
            intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE)
        }
    }

    private fun getBluetoothClass(intent: Intent): BluetoothClass? {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            intent.getParcelableExtra(
                BluetoothDevice.EXTRA_CLASS,
                BluetoothClass::class.java,
            )
        } else {
            @Suppress("DEPRECATION")
            intent.getParcelableExtra(BluetoothDevice.EXTRA_CLASS)
        }
    }

    private fun getParcelUuids(intent: Intent): List<ParcelUuid>? {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            intent.getParcelableArrayExtra(
                BluetoothDevice.EXTRA_UUID,
                ParcelUuid::class.java,
            )?.toList()
        } else {
            @Suppress("DEPRECATION")
            intent.getParcelableArrayExtra(BluetoothDevice.EXTRA_UUID)?.map { it as ParcelUuid }
        }
    }

    private companion object {
        private const val DEFAULT_UUID_RSSI = Int.MIN_VALUE
    }
}
