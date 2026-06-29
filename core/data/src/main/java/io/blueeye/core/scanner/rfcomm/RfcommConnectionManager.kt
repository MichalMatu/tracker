package io.blueeye.core.scanner.rfcomm

import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothSocket
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/**
 * Manages RFCOMM/SPP connections to Bluetooth Classic devices.
 *
 * This class handles:
 * - Finding appropriate handlers for devices
 * - Creating and managing socket connections
 * - Coordinating between handlers and sockets
 * - Thread-safe concurrent access
 *
 * Usage:
 * ```
 * val result = connectionManager.probeDevice(device)
 * if (result?.success == true) {
 *     println("Battery: ${result.batteryLevel}%")
 * }
 * ```
 */
@Singleton
class RfcommConnectionManager
@Inject
constructor(
    private val adapter: BluetoothAdapter?,
    private val handlers: Set<@JvmSuppressWildcards RfcommDeviceHandler>,
) {
    init {
        Log.e(TAG, "Initialized with ${handlers.size} handlers: ${handlers.map { it.handlerType }}")
    }

    companion object {
        private const val TAG = "RfcommConnectionManager"
        private const val DEFAULT_TIMEOUT_MS = 10000L
        private const val CONNECT_RETRY_COUNT = 1
        private const val RETRY_DELAY_MS = 500L
    }

    /**
     * Finds and probes a Bluetooth device using the appropriate handler.
     *
     * @param device The device to probe
     * @param deviceName Optional device name (uses device.name if null)
     * @param timeoutMs Maximum time for the entire operation
     * @return Probe result or null if no handler supports this device
     */
    @SuppressLint("MissingPermission")
    suspend fun probeDevice(
        device: BluetoothDevice,
        deviceName: String? = null,
        timeoutMs: Long = DEFAULT_TIMEOUT_MS,
    ): RfcommProbeResult? =
        withContext(Dispatchers.IO) {
            val name = deviceName ?: device.name

            // Find appropriate handler
            val handler = findHandler(device, name)
            if (handler == null) {
                Log.v(TAG, "No RFCOMM handler for device: $name (${device.address})")
                return@withContext null
            }

            Log.i(TAG, "Probing ${device.address} with ${handler.handlerType} handler")

            try {
                withTimeout(timeoutMs) { connectAndProbe(device, handler) }
            } catch (@Suppress("SwallowedException") e: TimeoutCancellationException) {
                Log.w(TAG, "Timeout probing ${device.address}")
                RfcommProbeResult.error(
                    mac = device.address,
                    handlerType = handler.handlerType,
                    message = "Connection timeout after ${timeoutMs}ms",
                )
            } catch (@Suppress("TooGenericExceptionCaught") e: Exception) {
                Log.e(TAG, "Error probing ${device.address}", e)
                RfcommProbeResult.error(
                    mac = device.address,
                    handlerType = handler.handlerType,
                    message = e.message ?: "Unknown error",
                )
            }
        }

    /** Gets all bonded (paired) devices that have a compatible handler. */
    @SuppressLint("MissingPermission")
    fun getSupportedBondedDevices(): List<Pair<BluetoothDevice, RfcommDeviceHandler>> {
        val bondedDevices = adapter?.bondedDevices ?: return emptyList()

        Log.d(TAG, "Bonded devices count: ${bondedDevices.size}")
        bondedDevices.forEach { device ->
            val name = device.name ?: "Unknown"
            val hasHandler = hasHandler(device)
            if (hasHandler) {
                Log.i(TAG, "🟢 Supported Bonded Device: $name (${device.address})")
            } else {
                Log.d(TAG, "⚪ Unsupported Bonded Device: $name (${device.address})")
            }
        }

        return bondedDevices.mapNotNull { device ->
            findHandler(device, device.name)?.let { handler -> device to handler }
        }
    }

    /**
     * Probes all bonded devices that have compatible handlers.
     *
     * @param timeoutPerDeviceMs Timeout for each device probe
     * @return List of probe results (including failures)
     */
    suspend fun probeAllBondedDevices(timeoutPerDeviceMs: Long = DEFAULT_TIMEOUT_MS): List<RfcommProbeResult> {
        val supportedDevices = getSupportedBondedDevices()

        Log.i(TAG, "Found ${supportedDevices.size} supported bonded devices")

        return supportedDevices.mapNotNull { (device, _) ->
            probeDevice(device, timeoutMs = timeoutPerDeviceMs)
        }
    }

    /** Checks if any handler can handle the given device. */
    @SuppressLint("MissingPermission")
    fun hasHandler(device: BluetoothDevice): Boolean {
        return findHandler(device, device.name) != null
    }

    // ==================== PRIVATE ====================

    @SuppressLint("MissingPermission")
    private fun findHandler(
        device: BluetoothDevice,
        name: String?,
    ): RfcommDeviceHandler? {
        return handlers.filter { it.canHandle(device, name) }.maxByOrNull { it.priority }
    }

    @SuppressLint("MissingPermission")
    private suspend fun connectAndProbe(
        device: BluetoothDevice,
        handler: RfcommDeviceHandler,
    ): RfcommProbeResult {
        var lastException: Exception? = null

        for (attempt in 1..CONNECT_RETRY_COUNT) {
            try {
                return tryConnectAndProbe(device, handler, attempt)
            } catch (e: IOException) {
                Log.w(TAG, "Connection attempt $attempt failed: ${e.message}")
                lastException = e
                if (attempt < CONNECT_RETRY_COUNT) {
                    kotlinx.coroutines.delay(RETRY_DELAY_MS)
                }
            }
        }

        return RfcommProbeResult.error(
            mac = device.address,
            handlerType = handler.handlerType,
            message = "Failed after $CONNECT_RETRY_COUNT attempts: ${lastException?.message}",
        )
    }

    @SuppressLint("MissingPermission")
    private suspend fun tryConnectAndProbe(
        device: BluetoothDevice,
        handler: RfcommDeviceHandler,
        attempt: Int
    ): RfcommProbeResult {
        var socket: BluetoothSocket? = null
        try {
            socket = createSocket(device, handler.serviceUuid)
            adapter?.cancelDiscovery()

            Log.d(TAG, "Connecting to ${device.address} (attempt $attempt)...")
            socket.connectSuspending()
            Log.d(TAG, "Connected to ${device.address}")

            sendInitialHandshake(socket, handler)

            return handler.probe(
                device = device,
                inputStream = socket.inputStream,
                outputStream = socket.outputStream,
            )
        } finally {
            socket?.safeClose()
        }
    }

    /**
     * Connects to the socket in a cancellable way.
     * The standard socket.connect() is blocking and does not respect coroutine cancellation.
     * This wrapper launches the connection on a separate thread (Dispatcher.IO) and ensuring
     * that if the coroutine is cancelled (e.g. timeout), the socket is closed immediately,
     * which unblocks the connection attempt.
     */
    @SuppressLint("MissingPermission")
    private suspend fun BluetoothSocket.connectSuspending() =
        suspendCancellableCoroutine { continuation ->
            val verificationJob = GlobalScope.launch(Dispatchers.IO) {
                try {
                    this@connectSuspending.connect()
                    if (continuation.isActive) continuation.resume(Unit)
                } catch (t: Throwable) {
                    if (continuation.isActive) continuation.resumeWithException(t)
                }
            }

            continuation.invokeOnCancellation {
                // Determine if we need to close to interrupt
                // Closing the socket will cause connect() to throw IOException
                safeClose()
                // optional: verificationJob.cancel() - though it's blocked in connect()
            }
        }

    private suspend fun sendInitialHandshake(
        socket: BluetoothSocket,
        handler: RfcommDeviceHandler
    ) {
        val initialCommands = handler.getInitialCommands()
        if (initialCommands.isEmpty()) return

        for (cmd in initialCommands) {
            socket.outputStream.write(cmd)
            socket.outputStream.flush()
            kotlinx.coroutines.delay(handler.responseDelayMs)
        }
    }

    @SuppressLint("MissingPermission")
    private fun createSocket(
        device: BluetoothDevice,
        uuid: java.util.UUID,
    ): BluetoothSocket {
        return try {
            // Standard secure RFCOMM socket
            device.createRfcommSocketToServiceRecord(uuid)
        } catch (e: IOException) {
            // Fallback: Try insecure socket (some devices require this)
            Log.w(TAG, "Secure socket failed, trying insecure: ${e.message}")
            device.createInsecureRfcommSocketToServiceRecord(uuid)
        }
    }

    private fun BluetoothSocket.safeClose() {
        try {
            this.close()
        } catch (e: IOException) {
            Log.w(TAG, "Error closing socket: ${e.message}")
        }
    }
}
