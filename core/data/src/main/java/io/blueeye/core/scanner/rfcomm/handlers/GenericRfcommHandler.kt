package io.blueeye.core.scanner.rfcomm.handlers

import android.annotation.SuppressLint
import android.bluetooth.BluetoothDevice
import io.blueeye.core.scanner.rfcomm.RfcommDeviceHandler
import io.blueeye.core.scanner.rfcomm.RfcommProbeResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.InputStream
import java.io.OutputStream
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

/**
 * A fallback handler that matches ANY device.
 * Used to attempt a generic connection for fingerprinting purposes.
 *
 * It will likely fail for most devices, but the failure reason (e.g. "Connection Refused")
 * provides valuable information about the device's state.
 */
@Singleton
class GenericRfcommHandler @Inject constructor() : RfcommDeviceHandler {

    companion object {
        // Standard Serial Port Profile UUID
        private val SPP_UUID = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB")
    }

    override val handlerType: String = "Generic"
    override val serviceUuid: UUID = SPP_UUID
    
    // Lowest priority to ensure specific handlers (like Bose) are chosen first
    override val priority: Int = -1 
    override val responseDelayMs: Long = 100

    @SuppressLint("MissingPermission")
    override fun canHandle(device: BluetoothDevice, deviceName: String?): Boolean {
        // Match EVERYTHING that doesn't have a specific handler
        return true
    }

    @SuppressLint("MissingPermission")
    override suspend fun probe(
        device: BluetoothDevice,
        inputStream: InputStream,
        outputStream: OutputStream,
        timeoutMs: Long
    ): RfcommProbeResult = withContext(Dispatchers.IO) {
        // If we reached here, the socket connected successfully!
        // This is rare for random devices, but possible.
        
        RfcommProbeResult(
            macAddress = device.address,
            deviceName = device.name,
            batteryLevel = null,
            deviceStatus = mapOf("Connection" to "Success (Generic SPP)"),
            handlerType = handlerType,
            success = true,
            rawResponse = "Connected via Generic SPP",
            firmwareVersion = null,
            serialNumber = null
        )
    }
}
