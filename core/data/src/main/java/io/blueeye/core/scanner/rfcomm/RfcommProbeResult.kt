package io.blueeye.core.scanner.rfcomm

/**
 * Result of an RFCOMM probe/read operation from a connected Bluetooth device.
 *
 * This is a generic container for data extracted via RFCOMM/SPP connection. Different device
 * handlers populate different fields based on their protocol.
 */
data class RfcommProbeResult(
    /** MAC address of the device */
    val macAddress: String,
    /** Device name (may be read from device or from bonded info) */
    val deviceName: String? = null,
    /** Battery level percentage (0-100), null if not supported/available */
    val batteryLevel: Int? = null,
    /** Firmware version string */
    val firmwareVersion: String? = null,
    /** Serial number */
    val serialNumber: String? = null,
    /** Device-specific status map (e.g., "noiseLevel" -> "HIGH", "autoOff" -> "60min") */
    val deviceStatus: Map<String, String> = emptyMap(),
    /** Handler type that produced this result (e.g., "Bose", "Sony", "OBD2") */
    val handlerType: String,
    /** Whether the probe was successful */
    val success: Boolean = true,
    /** Error message if probe failed */
    val errorMessage: String? = null,
    /** Timestamp of the probe */
    val timestamp: Long = System.currentTimeMillis(),
    /** Raw response bytes for debugging (hex encoded) */
    val rawResponse: String? = null,
) {
    companion object {
        fun error(
            mac: String,
            handlerType: String,
            message: String,
        ) = RfcommProbeResult(
            macAddress = mac,
            handlerType = handlerType,
            success = false,
            errorMessage = message,
        )
    }
}
