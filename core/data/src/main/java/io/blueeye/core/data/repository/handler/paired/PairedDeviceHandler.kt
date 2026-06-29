package io.blueeye.core.data.repository.handler.paired

import android.annotation.SuppressLint
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager
import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import io.blueeye.core.data.classifier.DeviceClassifier
import io.blueeye.core.data.classifier.chipset.ChipsetIdentifier
import io.blueeye.core.data.db.dao.DeviceDao
import io.blueeye.core.data.repository.VendorRepository
import io.blueeye.core.model.DeviceType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Handler responsible for syncing bonded (paired) devices to the database.
 * It allows tracking paired devices even if they are currently connected and not advertising.
 */
@Singleton
class PairedDeviceHandler
@Inject
constructor(
    private val deviceDao: DeviceDao,
    private val vendorRepository: VendorRepository,
    private val persister: PairedDevicePersister,
    @ApplicationContext private val context: Context,
    private val deviceClassifier: DeviceClassifier,
) {
    @SuppressLint("MissingPermission")
    suspend fun syncPairedDevices() {
        withContext(Dispatchers.IO) {
            val bluetoothManager =
                context.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager
            val adapter = bluetoothManager?.adapter ?: return@withContext

            // Permission check should be handled by the caller (ScannerService/ViewModel)
            val bondedDevices = adapter.bondedDevices ?: emptySet()

            for (device in bondedDevices) {
                processPairedDevice(device)
            }
        }
    }

    @SuppressLint("MissingPermission")
    private suspend fun processPairedDevice(device: BluetoothDevice) {
        val mac = device.address
        val name = device.name
        val type = device.type // Classic, LE, Dual
        val deviceClass = device.bluetoothClass?.deviceClass

        // Create Context
        val ctx = PairedScanDataContext.fromDevice(mac, name, type, deviceClass)

        // Vendor resolution
        ctx.vendorName = vendorRepository.getVendorName(mac, null)

        // Classification
        ctx.deviceType = if (deviceClass != null) {
            deviceClassifier.classifyByCoD(deviceClass)
        } else {
            DeviceType.UNKNOWN
        }

        // Load existing
        ctx.existingDevice = deviceDao.getByFingerprint(ctx.fingerprint)

        // Enrich with Chipset Info
        ctx.chipsetInfo = ChipsetIdentifier.getChipFamily(mac)

        // Enrich with Battery Level
        ctx.batteryLevel = getBatteryLevel(device)

        // Map Technology
        ctx.technology = mapTech(type)

        // Persist
        persister.persist(ctx)
    }

    private fun mapTech(type: Int): String {
        return when (type) {
            BluetoothDevice.DEVICE_TYPE_CLASSIC -> "BR/EDR"
            BluetoothDevice.DEVICE_TYPE_LE -> "BLE"
            BluetoothDevice.DEVICE_TYPE_DUAL -> "DUAL"
            else -> "UNKNOWN"
        }
    }

    @SuppressLint("PrivateApi")
    private fun getBatteryLevel(device: BluetoothDevice): Int? {
        return try {
            val method = device.javaClass.getMethod("getBatteryLevel")
            val level = method.invoke(device) as Int
            if (level >= 0) level else null
        } catch (@Suppress("TooGenericExceptionCaught", "SwallowedException") e: Exception) {
            null
        }
    }
}
