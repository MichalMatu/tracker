package io.blueeye.core.scanner.rfcomm.handlers

import android.annotation.SuppressLint
import android.bluetooth.BluetoothDevice
import android.util.Log
import io.blueeye.core.scanner.rfcomm.RfcommDeviceHandler
import io.blueeye.core.scanner.rfcomm.RfcommProbeResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import java.io.InputStream
import java.io.OutputStream
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

/**
 * RFCOMM handler for Bose headphones (QC35, QC45, NC700, etc.)
 *
 * Protocol reverse-engineered from:
 * https://blog.davidv.dev/posts/reverse-engineering-the-bose-qc35-bluetooth-protocol/
 */
@Singleton
class BoseRfcommHandler @Inject constructor() : RfcommDeviceHandler {
    @SuppressLint("MissingPermission")
    override fun canHandle(
        device: BluetoothDevice,
        deviceName: String?,
    ): Boolean {
        val name = deviceName ?: device.name
        if (name != null && name.lowercase().contains("bose")) {
            return true
        }

        val mac = device.address.uppercase()
        return BOSE_OUIS.any { mac.startsWith(it) }
    }

    companion object {
        private const val TAG = "BoseRfcommHandler"
        private const val BUFFER_SIZE = 1024
        private const val READ_SLEEP_MS = 10L
        private const val STATUS_DELAY_MS = 50L

        private val SPP_UUID = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB")

        private val BOSE_OUIS = listOf(
            "BC:87:FA", "00:0C:8A", "04:52:C7", "08:DF:1F", "2C:41:A1",
            "30:CF:00", "A0:14:3D", "A8:1B:6A", "AC:D1:B8", "C8:7B:23"
        )

        val CMD_CONNECT = byteArrayOf(0x00, 0x01, 0x01, 0x00)
        val CMD_GET_DEVICE_STATUS = byteArrayOf(0x01, 0x01, 0x05, 0x00)
        val CMD_GET_BATTERY = byteArrayOf(0x02, 0x02, 0x01, 0x00)
    }

    override val handlerType: String = "Bose"
    override val serviceUuid: UUID = SPP_UUID
    override val priority: Int = 10
    override val responseDelayMs: Long = 50

    override fun getInitialCommands(): List<ByteArray> = listOf(CMD_CONNECT)

    @SuppressLint("MissingPermission")
    override suspend fun probe(
        device: BluetoothDevice,
        inputStream: InputStream,
        outputStream: OutputStream,
        timeoutMs: Long,
    ): RfcommProbeResult = withContext(Dispatchers.IO) {
        val mac = device.address
        val state = BoseProtocolParser.BoseState()
        val rawResponses = StringBuilder()

        try {
            delay(responseDelayMs)
            val connectResponse = readAvailable(inputStream)
            rawResponses.append("CONNECT: ${connectResponse.toHexString()}\n")

            outputStream.write(CMD_GET_DEVICE_STATUS)
            outputStream.flush()
            delay(STATUS_DELAY_MS)

            val statusResponse = readAvailable(inputStream)
            rawResponses.append("STATUS: ${statusResponse.toHexString()}\n")
            BoseProtocolParser.parseStatusMessages(statusResponse, state)

            outputStream.write(CMD_GET_BATTERY)
            outputStream.flush()
            delay(responseDelayMs)

            val batteryResponse = readAvailable(inputStream)
            BoseProtocolParser.parseBatteryMessages(batteryResponse, state, rawResponses)

            RfcommProbeResult(
                macAddress = mac,
                deviceName = state.deviceName ?: device.name,
                batteryLevel = state.batteryLevel,
                deviceStatus = state.buildStatusMap(),
                handlerType = handlerType,
                success = true,
                rawResponse = rawResponses.toString(),
            )
        } catch (@Suppress("TooGenericExceptionCaught") e: Exception) {
            Log.e(TAG, "Error probing Bose device", e)
            RfcommProbeResult.error(mac, handlerType, e.message ?: "Unknown error")
        }
    }

    private fun readAvailable(input: InputStream, maxWaitMs: Long = 500): ByteArray {
        val buffer = ByteArray(BUFFER_SIZE)
        val result = mutableListOf<Byte>()
        val startTime = System.currentTimeMillis()
        while (System.currentTimeMillis() - startTime < maxWaitMs) {
            if (input.available() > 0) {
                val read = input.read(buffer, 0, minOf(buffer.size, input.available()))
                if (read > 0) result.addAll(buffer.take(read).toList())
            } else if (result.isNotEmpty()) {
                break
            } else {
                Thread.sleep(READ_SLEEP_MS)
            }
        }
        return result.toByteArray()
    }

    private fun ByteArray.toHexString(): String = joinToString(" ") { "%02X".format(it) }
}
