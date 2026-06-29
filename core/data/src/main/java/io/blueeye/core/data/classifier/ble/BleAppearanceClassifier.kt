package io.blueeye.core.data.classifier.ble

import io.blueeye.core.model.DeviceType

/**
 * Classifies device based on BLE Appearance (GAP Assigned number). Ref:
 * https://specificationrefs.bluetooth.com/assigned-values/Appearance%20Values.pdf
 */
object BleAppearanceClassifier {
    // Appearance Category Ranges
    private const val CAT_PHONE_START = 0x0040
    private const val CAT_PHONE_END = 0x007F
    private const val CAT_COMPUTER_START = 0x0080
    private const val CAT_COMPUTER_END = 0x00BF
    private const val CAT_WATCH_START = 0x0180
    private const val CAT_WATCH_END = 0x01BF
    private const val CAT_TAG_START = 0x0200
    private const val CAT_TAG_END = 0x023F
    private const val CAT_HEART_RATE_START = 0x0340
    private const val CAT_HEART_RATE_END = 0x037F
    private const val CAT_BLOOD_PRESSURE_START = 0x0440
    private const val CAT_BLOOD_PRESSURE_END = 0x047F
    private const val CAT_MEDIA_START = 0x0880
    private const val CAT_MEDIA_END = 0x08BF

    // Specific Appearance Values
    private const val APP_GENERIC_TAG = 512
    private const val APP_KEY_FOB = 0x0201
    private const val APP_HEADSET = 0x0941
    private const val APP_HANDSFREE = 0x0942

    fun classify(appearance: Int?): DeviceType {
        if (appearance == null) return DeviceType.UNKNOWN

        return when (appearance) {
            in CAT_PHONE_START..CAT_PHONE_END -> DeviceType.PHONE
            in CAT_COMPUTER_START..CAT_COMPUTER_END -> DeviceType.LAPTOP
            in CAT_WATCH_START..CAT_WATCH_END -> DeviceType.WATCH
            in CAT_HEART_RATE_START..CAT_HEART_RATE_END -> DeviceType.WEARABLE
            in CAT_BLOOD_PRESSURE_START..CAT_BLOOD_PRESSURE_END -> DeviceType.WEARABLE
            in CAT_TAG_START..CAT_TAG_END -> DeviceType.TAG
            APP_GENERIC_TAG -> DeviceType.TAG
            APP_KEY_FOB -> DeviceType.TAG
            in CAT_MEDIA_START..CAT_MEDIA_END -> DeviceType.HEADPHONES
            APP_HEADSET -> DeviceType.HEADPHONES
            APP_HANDSFREE -> DeviceType.HEADPHONES
            else -> DeviceType.UNKNOWN
        }
    }

    fun isPotentialTracker(appearance: Int?): Boolean {
        if (appearance == null) return false
        return (appearance in CAT_TAG_START..CAT_TAG_END)
    }
}
