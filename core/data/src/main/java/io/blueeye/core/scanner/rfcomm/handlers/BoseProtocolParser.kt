package io.blueeye.core.scanner.rfcomm.handlers

object BoseProtocolParser {
    private const val HEADER_SIZE = 3
    private const val MSG_MIN_SIZE = 4
    private const val PAYLOAD_OFFSET = 4
    private const val LENGTH_BYTE_INDEX = 3
    private const val MASK_BYTE = 0xFF
    private const val BATTERY_MAX = 100

    val HEADER_NAME = byteArrayOf(0x01, 0x02, 0x03)
    val HEADER_AUTO_OFF = byteArrayOf(0x01, 0x04, 0x03)
    val HEADER_BATTERY = byteArrayOf(0x02, 0x02, 0x03)
    val HEADER_NOISE_LEVEL = byteArrayOf(0x01, 0x06, 0x03)
    val HEADER_NOISE_LEVEL_ULTRA = byteArrayOf(0x01, 0x05, 0x03)
    val HEADER_LANGUAGE = byteArrayOf(0x01, 0x03, 0x03)

    const val NOISE_LEVEL_OFF = 0x00
    const val NOISE_LEVEL_HIGH = 0x01
    const val NOISE_LEVEL_WIND = 0x02
    const val NOISE_LEVEL_LOW = 0x03

    val AUTO_OFF_VALUES = mapOf(
        0x00 to "Never",
        0x14 to "20 min",
        0x3C to "60 min",
        0xB4 to "180 min"
    )

    data class BoseState(
        var batteryLevel: Int? = null,
        var noiseLevel: String? = null,
        var autoOff: String? = null,
        var deviceName: String? = null,
        var language: String? = null
    ) {
        fun buildStatusMap(): Map<String, String> {
            val statusMap = mutableMapOf<String, String>()
            noiseLevel?.let { statusMap["NC"] = it }
            autoOff?.let { statusMap["AutoOff"] = it }
            language?.let { statusMap["Lang"] = it }
            return statusMap
        }
    }

    fun parseStatusMessages(data: ByteArray, state: BoseState) {
        parseMessages(data) { header, payload ->
            when {
                header.contentEquals(HEADER_NAME) -> handleNameMessage(payload, state)
                header.contentEquals(HEADER_NOISE_LEVEL) ||
                    header.contentEquals(HEADER_NOISE_LEVEL_ULTRA) -> handleNoiseLevel(payload, state)
                header.contentEquals(HEADER_AUTO_OFF) -> handleAutoOff(payload, state)
                header.contentEquals(HEADER_LANGUAGE) -> handleLanguage(payload, state)
            }
        }
    }

    fun parseBatteryMessages(data: ByteArray, state: BoseState, raw: StringBuilder) {
        parseMessages(data) { header, payload ->
            if (header.contentEquals(HEADER_BATTERY) && payload.isNotEmpty()) {
                val level = payload[0].toInt() and MASK_BYTE
                if (level in 0..BATTERY_MAX) {
                    state.batteryLevel = level
                }
            }
            raw.append("BAT_RESP: ${header.toHexString()} -> ${payload.toHexString()}\n")
        }
    }

    private fun handleNameMessage(payload: ByteArray, state: BoseState) {
        if (payload.size > 1) {
            state.deviceName = payload.drop(1)
                .takeWhile { it != 0x00.toByte() }
                .toByteArray()
                .toString(Charsets.UTF_8)
        }
    }

    private fun handleNoiseLevel(payload: ByteArray, state: BoseState) {
        if (payload.isNotEmpty()) {
            val levelByte = payload.last().toInt() and MASK_BYTE
            state.noiseLevel = when (levelByte) {
                NOISE_LEVEL_OFF -> "OFF"
                NOISE_LEVEL_HIGH -> "MAX"
                NOISE_LEVEL_LOW -> "ON"
                NOISE_LEVEL_WIND -> "WIND"
                else -> "LVL:$levelByte"
            }
        }
    }

    private fun handleAutoOff(payload: ByteArray, state: BoseState) {
        if (payload.size >= 2) {
            val value = payload[1].toInt() and MASK_BYTE
            state.autoOff = AUTO_OFF_VALUES[value] ?: "$value min"
        }
    }

    private fun handleLanguage(payload: ByteArray, state: BoseState) {
        if (payload.size >= 2) {
            state.language = "0x${"%02X".format(payload[1])}"
        }
    }

    private fun parseMessages(
        data: ByteArray,
        onMessage: (header: ByteArray, payload: ByteArray) -> Unit,
    ) {
        var i = 0
        while (i <= data.size - MSG_MIN_SIZE) {
            val header = data.copyOfRange(i, i + HEADER_SIZE)
            val length = data[i + LENGTH_BYTE_INDEX].toInt() and MASK_BYTE
            if (i + PAYLOAD_OFFSET + length > data.size) break
            val payload = data.copyOfRange(i + PAYLOAD_OFFSET, i + PAYLOAD_OFFSET + length)
            onMessage(header, payload)
            i += PAYLOAD_OFFSET + length
        }
    }

    private fun ByteArray.toHexString(): String = joinToString(" ") { "%02X".format(it) }
}
