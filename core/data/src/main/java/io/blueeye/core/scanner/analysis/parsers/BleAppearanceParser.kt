package io.blueeye.core.scanner.analysis.parsers

object BleAppearanceParser {
    private const val MASK_BYTE = 0xFF
    private const val SHIFT_8 = 8

    // Generic Categories (masked)
    private const val MASK_CATEGORY = 0xFFC0
    private const val CAT_PHONE = 0x0040
    private const val CAT_COMPUTER = 0x0080
    private const val CAT_WATCH = 0x00C0
    private const val CAT_TAG = 0x0200
    private const val CAT_HID = 0x03C0
    private const val CAT_GLUCOMETER = 0x0440
    private const val CAT_WALKING = 0x0480
    private const val CAT_CYCLING = 0x04C0

    // Specific Appearances
    private const val APP_SPORTS_WATCH = 193
    private const val APP_KEYBOARD = 961
    private const val APP_MOUSE = 962
    private const val APP_JOYSTICK = 963
    private const val APP_GAMEPAD = 964
    private const val APP_APPLE_WATCH = 5184

    fun parse(data: ByteArray): String {
        if (data.size < 2) return "Invalid"
        val valInt = (data[0].toInt() and MASK_BYTE) or (data[1].toInt() and MASK_BYTE shl SHIFT_8)

        val specific = getSpecificName(valInt) ?: getCategoryName(valInt) ?: "Unknown"

        return "0x%04X ($specific)".format(valInt)
    }

    private fun getCategoryName(valInt: Int): String? = when (valInt and MASK_CATEGORY) {
        CAT_PHONE -> "Phone"
        CAT_COMPUTER -> "Computer"
        CAT_WATCH -> "Watch"
        CAT_TAG -> "Tag"
        CAT_HID -> "HID (Human Interface Device)"
        CAT_GLUCOMETER -> "Glucometer"
        CAT_WALKING -> "Walking Sensor"
        CAT_CYCLING -> "Cycling"
        else -> null
    }

    private fun getSpecificName(valInt: Int): String? = when (valInt) {
        APP_SPORTS_WATCH -> "Sports Watch"
        APP_KEYBOARD -> "Keyboard"
        APP_MOUSE -> "Mouse"
        APP_JOYSTICK -> "Joystick"
        APP_GAMEPAD -> "Gamepad"
        APP_APPLE_WATCH -> "Apple Watch"
        else -> null
    }
}
