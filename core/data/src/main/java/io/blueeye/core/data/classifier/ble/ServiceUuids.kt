package io.blueeye.core.data.classifier.ble

/**
 * Constants for BLE Service UUID classification. Separates UUID definitions from classification
 * logic.
 */
object ServiceUuids {
    // ========== STANDARD UUIDS (16-bit) ==========

    // Health & Fitness
    const val UUID_HEART_RATE = "180d"
    const val UUID_BLOOD_PRESSURE = "1810"
    const val UUID_HEALTH_THERMOMETER = "1809"
    const val UUID_GLUCOSE = "1808"
    const val UUID_BODY_COMPOSITION = "181b"
    const val UUID_WEIGHT_SCALE = "181d"
    const val UUID_FITNESS_MACHINE = "1826"
    const val UUID_RUNNING_SPEED_CADENCE = "1814"
    const val UUID_CYCLING_SPEED_CADENCE = "1816"
    const val UUID_CYCLING_POWER = "1818"

    // Device Info
    const val UUID_DEVICE_INFORMATION = "180a"
    const val UUID_BATTERY_SERVICE = "180f"

    // Audio (LE Audio)
    const val UUID_AUDIO_INPUT_CONTROL = "1843"
    const val UUID_VOLUME_CONTROL = "1844"
    const val UUID_MEDIA_CONTROL = "1848"

    // HID
    const val UUID_HUMAN_INTERFACE_DEVICE = "1812"

    // Proximity / Find
    const val UUID_IMMEDIATE_ALERT = "1802"
    const val UUID_LINK_LOSS = "1803"
    const val UUID_TX_POWER = "1804"
    const val UUID_FIND_ME = "1805"

    // ========== PROPRIETARY UUIDS ==========

    /** Tile Tracker - 0000FEED-... */
    const val UUID_TILE = "feed"

    /** Google Eddystone Beacon - 0000FEAA-... */
    const val UUID_EDDYSTONE = "feaa"

    /** Exposure Notification (COVID-19) - 0000FD6F-... */
    const val UUID_EXPOSURE_NOTIFICATION = "fd6f"

    /** Apple Media Service */
    const val UUID_APPLE_MEDIA = "89d3502b-0f36-433a-8ef4-c502ad55f8dc"

    /** Apple Notification Center */
    const val UUID_APPLE_NOTIFICATION = "7905f431-b5ce-4e99-a40f-4b1e122d00d0"

    /** Samsung SmartTag */
    const val UUID_SAMSUNG_SMARTTAG = "fd5a"

    /** Nordic UART Service */
    const val UUID_NORDIC_UART = "6e400001-b5a3-f393-e0a9-e50e24dcca9e"

    /** Xiaomi Mi Band */
    const val UUID_XIAOMI_MI_BAND = "fee0"

    /** Fitbit */
    const val UUID_FITBIT = "adabfb00-6e7d-4601-bda2-bffaa68956ba"

    /** Google Fast Pair */
    const val UUID_GOOGLE_FAST_PAIR = "fe2c"

    /** Amazon AMA (Alexa Mobile Accessory) */
    const val UUID_AMAZON_AMA = "fe03"

    /** Google Nearby (Chromecast Settings) */
    const val UUID_GOOGLE_NEARBY = "fea0"

    /** Bose Proprietary */
    const val UUID_BOSE = "febe"

    /** Chipolo Tracker */
    const val UUID_CHIPOLO = "fe33"

    /** Chipolo (classic app mode) proprietary 128-bit service UUID */
    const val UUID_CHIPOLO_CLASSIC = "451085d6f8334f7783d44f9438894ed5"

    /** Tesla Phone Key (vehicle) proprietary 128-bit service UUID */
    const val UUID_TESLA_PHONE_KEY = "00000211b2d143f09b88960cebf8b91e"

    /** Meater+ proprietary 128-bit service UUID */
    const val UUID_MEATER_PLUS = "a75cc7fcc956488fac2a2dbc08b63a04"

    // ========== UUID CATEGORY LISTS ==========

    val FITNESS_UUIDS =
        listOf(
            UUID_HEART_RATE,
            UUID_RUNNING_SPEED_CADENCE,
            UUID_CYCLING_SPEED_CADENCE,
            UUID_CYCLING_POWER,
            UUID_FITNESS_MACHINE,
            UUID_XIAOMI_MI_BAND,
        )

    val HEALTH_UUIDS =
        listOf(
            UUID_BLOOD_PRESSURE,
            UUID_GLUCOSE,
            UUID_HEALTH_THERMOMETER,
            UUID_BODY_COMPOSITION,
            UUID_WEIGHT_SCALE,
        )

    val AUDIO_UUIDS = listOf(UUID_AUDIO_INPUT_CONTROL, UUID_VOLUME_CONTROL, UUID_MEDIA_CONTROL)
}
