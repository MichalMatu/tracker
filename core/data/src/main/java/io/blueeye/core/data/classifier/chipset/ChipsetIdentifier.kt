package io.blueeye.core.data.classifier.chipset

/**
 * Identifies BLE chipsets based on MAC OUI prefixes.
 *
 * Common BLE chip manufacturers and their OUI ranges:
 * - Texas Instruments (CC254x, CC26xx) - Very common in IoT/bulbs
 * - Nordic Semiconductor (nRF51, nRF52) - Popular for wearables/beacons
 * - Cypress/Infineon (PSoC BLE) - Industrial/automotive
 * - Dialog Semiconductor (DA145xx) - Audio/wearables
 * - Telink Semiconductor - Cheap IoT devices
 * - Espressif (ESP32) - WiFi+BLE combo modules
 * - Silicon Labs (EFR32) - Mesh/industrial
 *
 * Reference: Wireshark manuf.txt, Bluetooth SIG, Instructables RE guide
 */
object ChipsetIdentifier {
    /** Represents a BLE chipset with vendor and common use cases. */
    data class ChipsetInfo(val vendor: String, val chipFamily: String, val commonUses: String)

    // OUI prefixes to chipset mapping
    // Key: First 3 bytes of MAC as uppercase hex without colons (e.g., "0017E3")
    private val ouiToChipset =
        mapOf(
            // Texas Instruments (CC254x, CC2640, CC2652)
            "001237" to
                ChipsetInfo(
                    "Texas Instruments",
                    "CC254x/CC26xx",
                    "IoT, Smart Bulbs, Sensors",
                ),
            "00124B" to
                ChipsetInfo(
                    "Texas Instruments",
                    "CC254x/CC26xx",
                    "IoT, Smart Bulbs, Sensors",
                ),
            "0012D1" to
                ChipsetInfo("Texas Instruments", "CC254x/CC26xx", "IoT, Smart Home"),
            "0012D2" to
                ChipsetInfo("Texas Instruments", "CC254x/CC26xx", "IoT, Smart Home"),
            "001783" to ChipsetInfo("Texas Instruments", "CC254x", "Legacy BLE devices"),
            "0017E3" to ChipsetInfo("Texas Instruments", "CC254x", "BLE Beacons, Bulbs"),
            "0017E4" to ChipsetInfo("Texas Instruments", "CC254x", "BLE Beacons, Bulbs"),
            "0017E5" to ChipsetInfo("Texas Instruments", "CC254x", "BLE Beacons, Bulbs"),
            "0017E6" to ChipsetInfo("Texas Instruments", "CC254x", "BLE Beacons, Bulbs"),
            "0017E7" to ChipsetInfo("Texas Instruments", "CC254x", "BLE Beacons, Bulbs"),
            "0017E8" to ChipsetInfo("Texas Instruments", "CC254x", "BLE Beacons, Bulbs"),
            "0017E9" to ChipsetInfo("Texas Instruments", "CC254x", "BLE Beacons, Bulbs"),
            "0017EA" to ChipsetInfo("Texas Instruments", "CC254x", "BLE Beacons, Bulbs"),
            "0017EB" to ChipsetInfo("Texas Instruments", "CC254x", "BLE Beacons, Bulbs"),
            "0017EC" to ChipsetInfo("Texas Instruments", "CC254x", "BLE Beacons, Bulbs"),
            "00182F" to ChipsetInfo("Texas Instruments", "CC254x", "Smart Devices"),
            "001830" to ChipsetInfo("Texas Instruments", "CC254x", "Smart Devices"),
            "001831" to ChipsetInfo("Texas Instruments", "CC254x", "Smart Devices"),
            "001AB6" to ChipsetInfo("Texas Instruments", "CC254x/CC26xx", "Industrial IoT"),
            "0021BA" to ChipsetInfo("Texas Instruments", "CC254x/CC26xx", "IoT Sensors"),
            "8081F9" to ChipsetInfo("Texas Instruments", "CC26xx/CC13xx", "Mesh, Thread"),
            "98072D" to ChipsetInfo("Texas Instruments", "CC26xx", "Smart Home"),
            // Nordic Semiconductor (nRF51, nRF52, nRF53)
            "F8E5CF" to ChipsetInfo("Nordic Semiconductor", "nRF52", "Wearables, Beacons"),
            "DA1428" to ChipsetInfo("Nordic Semiconductor", "nRF52", "Fitness Trackers"),
            "E72F96" to ChipsetInfo("Nordic Semiconductor", "nRF52", "Smart Devices"),
            "C05774" to ChipsetInfo("Nordic Semiconductor", "nRF52", "Audio, Wearables"),
            "D4F057" to ChipsetInfo("Nordic Semiconductor", "nRF52", "Smart Home"),
            "F4CE36" to ChipsetInfo("Nordic Semiconductor", "nRF52", "IoT Sensors"),
            "E0E5CF" to ChipsetInfo("Nordic Semiconductor", "nRF52", "Beacons"),
            // Espressif (ESP32-BLE)
            "24A160" to ChipsetInfo("Espressif", "ESP32", "WiFi+BLE IoT"),
            "240AC4" to ChipsetInfo("Espressif", "ESP32", "WiFi+BLE IoT"),
            "3C71BF" to ChipsetInfo("Espressif", "ESP32", "WiFi+BLE IoT"),
            "8CAAB5" to ChipsetInfo("Espressif", "ESP32", "WiFi+BLE IoT"),
            "ACCE5E" to ChipsetInfo("Espressif", "ESP32", "WiFi+BLE IoT"),
            "B4E62D" to ChipsetInfo("Espressif", "ESP32", "WiFi+BLE IoT"),
            "404CCA" to ChipsetInfo("Espressif", "ESP32", "WiFi+BLE IoT"),
            "485519" to ChipsetInfo("Espressif", "ESP32-S3", "WiFi+BLE IoT"),
            "483FDA" to ChipsetInfo("Espressif", "ESP32-C3", "WiFi+BLE IoT"),
            "600194" to ChipsetInfo("Espressif", "ESP32", "WiFi+BLE IoT"),
            "68B6B3" to ChipsetInfo("Espressif", "ESP32", "WiFi+BLE IoT"),
            // Cypress/Infineon (PSoC BLE, CYW)
            "001EAB" to ChipsetInfo("Cypress/Infineon", "PSoC BLE", "Industrial BLE"),
            "002629" to ChipsetInfo("Cypress/Infineon", "CYW43xxx", "WiFi+BLE Combo"),
            // Dialog Semiconductor (DA1458x, DA1469x)
            "80EA23" to ChipsetInfo("Dialog/Renesas", "DA1458x", "Audio, Wearables"),
            "F80377" to ChipsetInfo("Dialog/Renesas", "DA1469x", "Hearables, IoT"),
            // Silicon Labs (EFR32)
            "001DFB" to ChipsetInfo("Silicon Labs", "EFR32", "Mesh, Zigbee/BLE"),
            "000B57" to ChipsetInfo("Silicon Labs", "EFR32", "Smart Home"),
            "0C4314" to ChipsetInfo("Silicon Labs", "EFR32", "Industrial IoT"),
            // Telink Semiconductor (TLSR825x, TLSR921x) - Very common in cheap devices
            "A4C138" to ChipsetInfo("Telink", "TLSR825x", "Cheap IoT, Bulbs"),
            "381F8D" to ChipsetInfo("Telink", "TLSR825x", "BLE Mesh, Smart Home"),
            // Realtek (RTL8762)
            "00E04C" to ChipsetInfo("Realtek", "RTL8762", "Audio, TWS Earbuds"),
            "9CE33F" to ChipsetInfo("Realtek", "RTL8762", "Audio Devices"),
            // Qualcomm/CSR (QCC, CSR1xxx)
            "001CF0" to ChipsetInfo("Qualcomm/CSR", "QCC30xx", "TWS Audio"),
            "001D4F" to ChipsetInfo("Qualcomm/CSR", "CSR1000", "Bluetooth Audio"),
            "0025BC" to ChipsetInfo("Qualcomm/CSR", "QCC5xxx", "Premium Audio"),
            // Mediatek (MT2502, MT2523)
            "F4F5DB" to ChipsetInfo("MediaTek", "MT25xx", "Wearables"),
            "B0F893" to ChipsetInfo("MediaTek", "MT2523", "Smartwatches"),
            // ST Microelectronics (BlueNRG)
            "001824" to ChipsetInfo("STMicroelectronics", "BlueNRG", "Industrial BLE"),
            "80E126" to ChipsetInfo("STMicroelectronics", "BlueNRG-2", "IoT Sensors"),
            // Actions Semiconductor (ATS28xx) - Common in cheap TWS
            "20C38F" to ChipsetInfo("Actions", "ATS28xx", "Budget TWS Earbuds"),
            "4419B6" to ChipsetInfo("Actions", "ATS28xx", "Budget Audio"),
            // BES (Bestechnic) - Common in TWS
            "10F0A0" to ChipsetInfo("Bestechnic", "BES2xxx", "TWS Earbuds"),
            "8C1AFC" to ChipsetInfo("Bestechnic", "BES2300", "Premium TWS"),
            // JL (Jieli) - Very common in budget devices
            "AC1232" to ChipsetInfo("Jieli", "AC69xx", "Budget Bluetooth Audio"),
            "E89C25" to ChipsetInfo("Jieli", "AC69xx", "Budget Speakers"),
            // === MAJOR DEVICE VENDORS (Implied Chipsets) ===
            // Apple (Uses Apple Silicon, W1, H1, or Broadcom/Cypress)
            "4098AD" to ChipsetInfo("Apple", "W1/H1/S-Series", "Apple Ecosystem"),
            "441793" to ChipsetInfo("Apple", "W1/H1/S-Series", "Apple Ecosystem"),
            "48437C" to ChipsetInfo("Apple", "W1/H1/S-Series", "Apple Ecosystem"),
            "D45763" to ChipsetInfo("Apple", "Apple Silicon", "MacBook/iPad"),
            "749B2B" to ChipsetInfo("Apple", "Apple Silicon", "iPad/iPhone"),
            "BC926B" to ChipsetInfo("Apple", "W1/H1", "AirPods/Beats"),
            // Bose (Often uses Qualcomm/CSR or proprietary)
            "BC87FA" to ChipsetInfo("Bose", "CSR/Qualcomm", "Premium Audio"),
            // Samsung (Uses Exynos, Cypress, or Broadcom)
            "8C71F8" to ChipsetInfo("Samsung", "Exynos/Cypress", "Galaxy Devices"),
            "A82BBD" to ChipsetInfo("Samsung", "Samsung Module", "Smart TV/Audio"),
            // Sony (Mediatek, Qualcomm, or Sony)
            "94DB56" to ChipsetInfo("Sony", "Sony/Mediatek", "Headphones"),
            "143FA6" to ChipsetInfo("Sony", "Sony/Mediatek", "Audio Devices"),
        )

    private const val OUI_LENGTH = 6

    /**
     * Identify chipset from MAC address.
     * @param mac MAC address in format "XX:XX:XX:XX:XX:XX" or "XXXXXXXXXXXX"
     * @return ChipsetInfo if identified, null otherwise
     */
    fun identify(mac: String): ChipsetInfo? {
        val cleanMac = mac.replace(":", "").uppercase()
        if (cleanMac.length < OUI_LENGTH) return null

        val oui = cleanMac.substring(0, OUI_LENGTH)
        return ouiToChipset[oui]
    }

    /** Get chipset vendor name (short form for UI). */
    fun getVendorName(mac: String): String? = identify(mac)?.vendor

    /** Get chip family (e.g., "nRF52", "CC254x"). */
    fun getChipFamily(mac: String): String? = identify(mac)?.chipFamily

    /** Get a human-readable chipset description. */
    fun getDescription(mac: String): String? {
        val info = identify(mac) ?: return null
        return "${info.vendor} ${info.chipFamily} (${info.commonUses})"
    }

    /**
     * Check if the device uses a known cheap IoT chipset. Useful for security warnings (these chips
     * often lack encryption).
     */
    fun isCheapIoTChipset(mac: String): Boolean {
        val info = identify(mac) ?: return false
        return info.vendor in listOf("Telink", "Actions", "Jieli") ||
            info.chipFamily.contains("CC254") // Old TI chips
    }
}
