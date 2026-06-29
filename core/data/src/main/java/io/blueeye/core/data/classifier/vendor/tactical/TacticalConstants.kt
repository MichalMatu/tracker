package io.blueeye.core.data.classifier.vendor.tactical

/**
 * Characteristic BLE Service UUIDs and Company IDs for professional/public-safety equipment.
 */
object TacticalUuids {
    /**
     * Motorola Solutions - Official Bluetooth SIG registered UUID.
     * Presence of 0xFD8E in Service UUIDs is a high-confidence signal
     * consistent with Motorola Solutions equipment.
     * Used for: V300/V700 cameras, APX radio integration, accessories.
     */
    const val MOTOROLA_SOLUTIONS_UUID = "0000fd8e-0000-1000-8000-00805f9b34fb"
    const val MOTOROLA_SOLUTIONS_SHORT = "fd8e"

    /** Meshtastic/ATAK-compatible mesh service. */
    const val MESHTASTIC_SERVICE = "6ba1b218-15a8-461f-9fa8-5dcae273eafd"

    /** Eddystone beacon (used by Yardarm holster sensors) */
    const val EDDYSTONE_SERVICE = "0000feaa-0000-1000-8000-00805f9b34fb"

    /** Yardarm proprietary (alternate) - FCC ID: 2AJ3810242 */
    const val YARDARM_SERVICE = "0000f3fe-0000-1000-8000-00805f9b34fb"

    /** Sig Sauer BDX (Applied Ballistics) */
    const val SIG_BDX_PATTERN = "BDX"

    /** Kestrel LiNK (Applied Ballistics) - Serial Port Service equivalent */
    const val KESTREL_LINK_SERVICE = "0000ffe0-0000-1000-8000-00805f9b34fb"

    // === PROFESSIONAL EQUIPMENT SIGNATURES ===
    /**
     * Harris XL-200P / MSA G1 Integration
     * Custom 128-bit UUID cited in technical analysis.
     * Example/Actual: "F5A1287E-227D-4C9E-AD2C-11D0FD6ED640"
     */
    const val HARRIS_MSA_G1_SERVICE = "f5a1287e-227d-4c9e-ad2c-11d0fd6ed640"

    /** Sepura STP8X / SC21 Serial Data Access (PEI emulation) */
    // Note: Specific UUID not fully disclosed, using pattern match logic in registry for now.

    /**
     * Short burst advertising interval observed in some connected accessories.
     * Devices may advertise every 20ms (+jitter) for roughly 30 seconds.
     */
    const val TACTICAL_BURST_INTERVAL_MS = 20L
    const val TACTICAL_BURST_WINDOW_MS = 30000L

    // === MANUFACTURER COMPANY IDS (Bluetooth SIG) ===
    // Source: Bluetooth SIG and known professional-equipment identifiers.
    const val AXON_COMPANY_ID = 0x034D // Axon/TASER - Body 3/4, Signal Sidearm (FCC: X4GS01834)
    const val YARDARM_COMPANY_ID = 0x025F // Yardarm Technologies - Motorola Holster Aware (FCC: 2AJ3810242)
    const val MOTOROLA_COMPANY_ID = 0x0008 // Motorola - V300/V700 camera payloads and accessories
    const val SAMSUNG_ELECTRONICS_ID = 0x0075 // Samsung - XCover 6 Pro, XCover FieldPro (DE/NL/UK)
    const val ZEBRA_TECHNOLOGIES_ID = 0x00AF // Zebra - TC77 rugged terminal family
    const val WURTH_ELEKTRONIK_ID = 0x031A // Würth - components in professional systems
    const val SIERRA_WIRELESS_ID = 0x0577 // Sierra Wireless - Vehicle routers MP70/MG90
    const val DRAEGER_ID = 0x02A5 // Dräger - PSS Merlin SCBA telemetry
    const val PANASONIC_ID = 0x0071 // Panasonic - Toughbook terminals (SG)
    const val REVEAL_MEDIA_ID = 0x0000 // Reveal Media - Uses iBeacon format (Apple 0x004C)
}

/**
 * Chipset OUI prefixes commonly found in professional equipment.
 */
object ChipsetOuis {
    /** Nordic Semiconductor - Used by Axon Signal, Invisio R30 (nRF52832/nRF52840) */
    val NORDIC = listOf("00:19:DA", "D4:CA:6E", "E7:E8:E9")

    /** Laird Connectivity - Common in Motorola/Axon modules */
    val LAIRD = listOf("00:60:77", "C0:EE:40")

    /** Silicon Labs (EFR32) - Used in some professional IoT devices */
    val SILABS = listOf("84:2E:14", "00:0B:57")

    /** Texas Instruments (CC2640/CC2652) - Common in industrial devices */
    val TI = listOf("00:17:E9", "90:59:AF", "98:7B:F3")

    // === VEHICLE ROUTER OUIs ===
    /** Sierra Wireless / Semtech - AirLink MP70, MG90 vehicle routers */
    val SIERRA_WIRELESS = listOf("00:14:3E", "00:1E:42", "00:A0:D5")

    /** Cradlepoint - IBR900, IBR1700 vehicle routers */
    val CRADLEPOINT = listOf("00:30:44")

    // === FIREFIGHTER EQUIPMENT OUIs ===
    /** Dräger Safety - PSS Merlin SCBA telemetry, gas detectors */
    val DRAEGER = listOf("00:1E:C0", "00:0D:6F")

    /** MSA Safety - SCBA, G1 masks with telemetry */
    val MSA = listOf("00:1B:C5")

    // === RUGGED TERMINALS ===
    /** Panasonic - Toughbook CF-series rugged terminals */
    val PANASONIC = listOf("00:80:45", "00:0B:97")

    /** Getac - F110, B360 rugged tablets */
    val GETAC = listOf("00:24:21")
}
