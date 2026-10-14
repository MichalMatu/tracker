package io.blueeye.core.connectivity.manager

/** Bluetooth Battery Level (0x2A19) is a single unsigned octet in the range 0..100. */
internal object GattBatteryLevelDecoder {
    private const val UINT8_MASK = 0xFF
    private const val MAX_PERCENT = 100

    fun parse(value: ByteArray): Int? {
        if (value.size != 1) return null
        return (value[0].toInt() and UINT8_MASK).takeIf { it <= MAX_PERCENT }
    }
}
