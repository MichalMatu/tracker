package io.blueeye.core.scanner.analysis.parsers

object BleClassOfDeviceParser {
    private const val MASK_BYTE = 0xFF
    private const val SHIFT_8 = 8
    private const val SHIFT_16 = 16
    private const val MASK_MAJOR_DEVICE = 0x1F
    private const val MIN_DATA_LENGTH = 3

    // Major Device Classes
    private const val CLASS_COMPUTER = 0x01
    private const val CLASS_PHONE = 0x02
    private const val CLASS_NETWORK = 0x03
    private const val CLASS_AUDIO_VIDEO = 0x04
    private const val CLASS_PERIPHERAL = 0x05
    private const val CLASS_IMAGING = 0x06
    private const val CLASS_WEARABLE = 0x07
    private const val CLASS_TOY = 0x08
    private const val CLASS_HEALTH = 0x09

    fun parse(data: ByteArray): String {
        if (data.size < MIN_DATA_LENGTH) return "Invalid"
        val cod = ((data[2].toInt() and MASK_BYTE) shl SHIFT_16) or
            ((data[1].toInt() and MASK_BYTE) shl SHIFT_8) or
            (data[0].toInt() and MASK_BYTE)

        val majorDevice = (cod shr SHIFT_8) and MASK_MAJOR_DEVICE

        val deviceClass = when (majorDevice) {
            CLASS_COMPUTER -> "Computer"
            CLASS_PHONE -> "Phone"
            CLASS_NETWORK -> "LAN/Network"
            CLASS_AUDIO_VIDEO -> "Audio/Video"
            CLASS_PERIPHERAL -> "Peripheral"
            CLASS_IMAGING -> "Imaging"
            CLASS_WEARABLE -> "Wearable"
            CLASS_TOY -> "Toy"
            CLASS_HEALTH -> "Health"
            else -> "Unknown"
        }
        return "0x%06X (%s)".format(cod, deviceClass)
    }
}
