package io.blueeye.core.data.tracker.fingerprint

object FingerprintDefinitions {

    // --- Google Fast Pair ---
    const val FAST_PAIR_SERVICE_UUID_SHORT = "FE2C"

    val FAST_PAIR_MODELS = mapOf(
        "92BBBD" to "Pixel Buds",
        "000006" to "Google Pixel Buds",
        "821F66" to "JBL Flip 6",
        "F52494" to "JBL Buds Pro",
        "718FA4" to "JBL Live 300TWS",
        "D446A7" to "Sony XM5",
        "CD8256" to "Bose NC 700",
        "0000F0" to "Bose QuietComfort 35 II",
        "0E30C3" to "Razer Hammerhead TWS",
        "0003F0" to "LG HBS-835S"
    )

    // --- Apple ---
    const val APPLE_MANUFACTURER_ID = 0x004C

    val APPLE_MODELS = mapOf(
        "07190702" to "Airpods",
        "0719070e" to "Airpods Pro",
        "0719070a" to "Airpods Max",
        "0719070f" to "Airpods 2",
        "07190713" to "Airpods 3",
        "07190714" to "Airpods Pro 2",
        "04042a" to "Apple TV/Setup"
    )

    // --- Ecosystem IDs ---
    const val SAMSUNG_MANUFACTURER_ID = 0x0075
    const val PARTICLE_MANUFACTURER_ID = 0x0663
    const val MICROSOFT_MANUFACTURER_ID = 0x0006
    const val AMAZON_MANUFACTURER_ID = 0x0171

    // --- Service UUIDs ---
    const val TILE_SERVICE_UUID = "FEED"
    const val ALEXA_AMA_SERVICE_UUID = "FE03"
    const val EXPOSURE_NOTIFICATION_SERVICE_UUID = "FD6F"

    // --- Eddystone ---
    const val EDDYSTONE_SERVICE_UUID = "FEAA"
    const val EDDYSTONE_FRAME_UID = 0x00
    const val EDDYSTONE_FRAME_URL = 0x10
    const val EDDYSTONE_FRAME_TLM = 0x20
    const val EDDYSTONE_FRAME_EID = 0x30

    // --- AltBeacon ---
    const val ALTBEACON_PREAMBLE = 0xBEAC

    // --- 128-bit UUID Suffixes (Little Endian on wire) ---
    // Fitbit: 6e7d-4601-bda2-bffaa68956ba -> LE Suffix: ba5689aaffb2dab101467d6e
    const val FITBIT_LE_SUFFIX = "ba5689aaffb2dab101467d6e"

    // Tesla: 00000211-b2d1-43f0-9b88-960cebf8b91e -> LE Suffix: 1eb9f8eb0c96889bf043d1b2
    const val TESLA_LE_SUFFIX = "1eb9f8eb0c96889bf043d1b2"

    // Chipolo: 451085d6-f833-4f77-83d4-4f9438894ed5 -> LE Suffix: d54e8938944fd483774f33f8
    const val CHIPOLO_LE_SUFFIX = "d54e8938944fd483774f33f8"

    // TI SensorTag: f000aa80-0451-4000-b000-000000000000 -> LE Suffix: 00b00040510480aa00f0
    const val TI_SENSORTAG_LE_SUFFIX = "00b00040510480aa00f0"
}
