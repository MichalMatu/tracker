package io.blueeye.core.data.classifier.vendor

/**
 * Centralized registry of Bluetooth SIG Manufacturer IDs. Source:
 * https://www.bluetooth.com/specifications/assigned-numbers/
 */
object ManufacturerIds {
    // === BIG TECH ===
    const val APPLE = 0x004C
    const val MICROSOFT = 0x0006
    const val GOOGLE = 0x00E0
    const val SAMSUNG = 0x0075
    const val AMAZON = 0x0171

    // === TRACKERS ===
    const val TILE = 0x0049

    // === PUBLIC SAFETY / INDUSTRIAL ===
    const val MOTOROLA = 0x0008
    const val ZEBRA = 0x00A5
    const val ZEBRA_SYMBOL = 0x0045
    const val HONEYWELL = 0x0153

    // === PROFESSIONAL / PUBLIC SAFETY ===
    // Many specialized devices use common chipsets with custom protocols.
    // These IDs are less common and only useful as supporting context.
    const val SIG_SAUER = 0x02E5 // BDX scopes/rangefinders (unconfirmed)
    const val L3HARRIS = 0x03B4 // Tactical radios (placeholder)
    const val SEPURA = 0x0455 // TETRA radios (placeholder)

    // === SMART HOME ===
    const val PHILIPS_HUE = 0x010B // Signify
    const val TUYA = 0x07D0
    const val XIAOMI = 0x038F
    const val IKEA = 0x0386

    // === AUDIO ===
    const val BOSE = 0x009E
    const val SONY = 0x012D
    const val JBL = 0x0087 // Harman

    // === AUTOMOTIVE ===
    const val CONTINENTAL = 0x0066
    const val BOSCH = 0x0083

    // === WEARABLES ===
    const val GARMIN = 0x0087
    const val FITBIT = 0x017C

    // === MEDICAL ===
    const val DEXCOM = 0x00D0
    const val ABBOTT = 0x0207
    const val MEDTRONIC = 0x0216
    const val OMRON = 0x020A

    // === DRONES ===
    const val DJI = 0x2795 // Remote ID broadcasts

    // === CAMERAS ===
    const val GOPRO = 0x00B5
    const val CANON = 0x00F0
    const val NIKON = 0x00A4

    // === PRINTERS ===
    const val HP = 0x0057
    const val EPSON = 0x027E
    const val BROTHER = 0x01D7

    // === GAMING ===
    const val NINTENDO = 0x0247
    const val VALVE = 0x028E // Steam Deck

    // === OTHER ===
    const val LOGITECH = 0x0102
    const val RAZER = 0x1532
    const val SENNHEISER = 0x0116
    const val JABRA = 0x0067 // GN Audio
    const val PLANTRONICS = 0x003F
}
