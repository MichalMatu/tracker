package io.blueeye.core.scanner.rfcomm

import android.bluetooth.BluetoothDevice

/**
 * Interface for device-specific RFCOMM protocol handlers.
 *
 * Each handler knows how to:
 * 1. Identify if it can handle a specific device (by name, UUID, or manufacturer)
 * 2. Communicate with the device using its proprietary protocol
 * 3. Parse responses into a standardized RfcommProbeResult
 *
 * Implementations should be stateless and thread-safe.
 */
interface RfcommDeviceHandler {
    /**
     * Unique identifier for this handler type. Used in RfcommProbeResult.handlerType and for
     * logging.
     */
    val handlerType: String

    /**
     * The UUID for the RFCOMM/SPP service to connect to. Most devices use the standard SPP UUID,
     * but some use proprietary ones.
     */
    val serviceUuid: java.util.UUID

    /**
     * Determines if this handler can communicate with the given device.
     *
     * @param device The Bluetooth device to check
     * @param deviceName The device name (may differ from device.name if cached)
     * @return true if this handler should be used for this device
     */
    fun canHandle(
        device: BluetoothDevice,
        deviceName: String?,
    ): Boolean

    /**
     * Priority of this handler (higher = tried first). Useful when multiple handlers might match
     * the same device.
     */
    val priority: Int
        get() = 0

    /**
     * Probes the device by sending commands and parsing responses.
     *
     * This method is called with an already-connected socket's streams. The handler should:
     * 1. Send necessary commands
     * 2. Read and parse responses
     * 3. Return structured data
     *
     * @param device The Bluetooth device being probed
     * @param inputStream The socket's input stream for reading responses
     * @param outputStream The socket's output stream for sending commands
     * @param timeoutMs Maximum time to wait for responses
     * @return Parsed probe result
     */
    suspend fun probe(
        device: BluetoothDevice,
        inputStream: java.io.InputStream,
        outputStream: java.io.OutputStream,
        timeoutMs: Long = 5000,
    ): RfcommProbeResult

    /**
     * Optional: Commands to send immediately after connection. Some devices require an initial
     * handshake or authentication.
     */
    fun getInitialCommands(): List<ByteArray> = emptyList()

    /**
     * Optional: Grace period after sending a command before reading response. Some slow devices
     * need extra time.
     */
    val responseDelayMs: Long
        get() = 100
}
