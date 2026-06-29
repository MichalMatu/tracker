package io.blueeye.core.data.classifier.vendor.tactical

import io.blueeye.core.model.DeviceType

/**
 * Confidence level for tactical device detection.
 */
enum class ConfidenceLevel {
    /** Strong registry signal; user-facing evidence is capped before alerting. */
    CRITICAL,

    /** Commonly used by professionals, possible civilian use */
    HIGH,

    /** May appear in civilian contexts, requires additional signals */
    MEDIUM
}

/**
 * Information about a tactical OUI prefix.
 */
data class TacticalOuiInfo(
    val ouiPrefix: String, // 6 hex chars (e.g., "0025DF")
    val vendorName: String,
    val category: TacticalCategory,
    val deviceType: DeviceType,
    val confidence: ConfidenceLevel,
    val description: String,
    val fccIds: List<String> = emptyList()
)

/**
 * Database of Tactical OUIs.
 */
object TacticalOuiData {

    fun getAllInfos(): List<TacticalOuiInfo> {
        return CRITICAL_OUIS + HIGH_CONFIDENCE_OUIS + MEDIUM_CONFIDENCE_OUIS
    }

    /**
     * Strong registry OUI prefixes.
     */
    private val CRITICAL_OUIS = listOf(
        // === AXON ENTERPRISE (body cameras and connected safety sensors) ===
        TacticalOuiInfo(
            ouiPrefix = "0025DF",
            vendorName = "Axon Enterprise, Inc.",
            category = TacticalCategory.BODY_CAMERA,
            deviceType = DeviceType.BODY_CAMERA,
            confidence = ConfidenceLevel.CRITICAL,
            description = "Body camera and connected safety sensor equipment",
            fccIds = listOf("X4GS01105", "X4GS00701")
        ),
        TacticalOuiInfo(
            ouiPrefix = "005828",
            vendorName = "Axon Networks Inc.",
            category = TacticalCategory.BODY_CAMERA,
            deviceType = DeviceType.BODY_CAMERA,
            confidence = ConfidenceLevel.CRITICAL,
            description = "Newer Axon infrastructure (2022+)"
        ),
        TacticalOuiInfo(
            ouiPrefix = "00C0D4",
            vendorName = "Axon Networks Inc.",
            category = TacticalCategory.BODY_CAMERA,
            deviceType = DeviceType.BODY_CAMERA,
            confidence = ConfidenceLevel.HIGH,
            description = "Legacy Axon equipment"
        ),
        TacticalOuiInfo(
            ouiPrefix = "847003",
            vendorName = "Axon Networks Inc.",
            category = TacticalCategory.BODY_CAMERA,
            deviceType = DeviceType.BODY_CAMERA,
            confidence = ConfidenceLevel.CRITICAL,
            description = "Axon fleet or infrastructure equipment"
        ),

        // === INVISIO COMMUNICATIONS (professional comms headsets) ===
        TacticalOuiInfo(
            ouiPrefix = "0014CF",
            vendorName = "INVISIO Communications",
            category = TacticalCategory.TACTICAL_AUDIO,
            deviceType = DeviceType.TACTICAL_AUDIO,
            confidence = ConfidenceLevel.CRITICAL,
            description = "V60/V50 controllers and professional comms headsets",
            fccIds = listOf("2AUGTASM17582")
        )
    )

    /**
     * High-confidence OUI prefixes - professional equipment, rarely civilian.
     */
    private val HIGH_CONFIDENCE_OUIS = listOf(
        // === MOTOROLA SOLUTIONS (APX Radios, VB400 Cameras) ===
        TacticalOuiInfo(
            ouiPrefix = "00047D",
            vendorName = "Motorola Solutions Inc.",
            category = TacticalCategory.TACTICAL_RADIO,
            deviceType = DeviceType.TACTICAL_RADIO,
            confidence = ConfidenceLevel.HIGH,
            description = "APX radios, professional communication"
        ),
        TacticalOuiInfo(
            ouiPrefix = "001F92",
            vendorName = "Motorola Solutions Inc.",
            category = TacticalCategory.TACTICAL_RADIO,
            deviceType = DeviceType.TACTICAL_RADIO,
            confidence = ConfidenceLevel.HIGH,
            description = "APX infrastructure"
        ),
        TacticalOuiInfo(
            ouiPrefix = "001885",
            vendorName = "Motorola Solutions Inc.",
            category = TacticalCategory.TACTICAL_RADIO,
            deviceType = DeviceType.TACTICAL_RADIO,
            confidence = ConfidenceLevel.HIGH,
            description = "Telecom equipment"
        ),
        TacticalOuiInfo(
            ouiPrefix = "4CCC34",
            vendorName = "Motorola Solutions Inc.",
            category = TacticalCategory.TACTICAL_RADIO,
            deviceType = DeviceType.TACTICAL_RADIO,
            confidence = ConfidenceLevel.HIGH,
            description = "Post-2013 implementations"
        ),

        // === L3HARRIS (professional radios) ===
        TacticalOuiInfo(
            ouiPrefix = "001959",
            vendorName = "L3 Technologies (Harris)",
            category = TacticalCategory.TACTICAL_RADIO,
            deviceType = DeviceType.TACTICAL_RADIO,
            confidence = ConfidenceLevel.HIGH,
            description = "XL/XG series radios"
        ),

        // === SEPURA (TETRA Radios) ===
        TacticalOuiInfo(
            ouiPrefix = "001B2A",
            vendorName = "Sepura PLC",
            category = TacticalCategory.TACTICAL_RADIO,
            deviceType = DeviceType.TACTICAL_RADIO,
            confidence = ConfidenceLevel.HIGH,
            description = "TETRA radios (STP8/9, SC20/21)"
        ),

        // === HYTERA (DMR/TETRA) ===
        TacticalOuiInfo(
            ouiPrefix = "9C066E",
            vendorName = "Hytera Communications",
            category = TacticalCategory.TACTICAL_RADIO,
            deviceType = DeviceType.TACTICAL_RADIO,
            confidence = ConfidenceLevel.HIGH,
            description = "DMR/TETRA radio equipment"
        ),
        TacticalOuiInfo(
            ouiPrefix = "6469BC",
            vendorName = "Hytera Communications",
            category = TacticalCategory.TACTICAL_RADIO,
            deviceType = DeviceType.TACTICAL_RADIO,
            confidence = ConfidenceLevel.HIGH,
            description = "DMR radio equipment"
        ),

        // === VEHICLE ROUTERS (Sierra Wireless) ===
        TacticalOuiInfo(
            "00143E",
            "Sierra Wireless",
            TacticalCategory.VEHICLE_ROUTER,
            DeviceType.VEHICLE_ROUTER,
            ConfidenceLevel.HIGH,
            "AirLink MP70/MG90"
        ),
        TacticalOuiInfo(
            "001E42",
            "Sierra Wireless",
            TacticalCategory.VEHICLE_ROUTER,
            DeviceType.VEHICLE_ROUTER,
            ConfidenceLevel.HIGH,
            "AirLink Series"
        ),
        TacticalOuiInfo(
            "00A0D5",
            "Sierra Wireless",
            TacticalCategory.VEHICLE_ROUTER,
            DeviceType.VEHICLE_ROUTER,
            ConfidenceLevel.HIGH,
            "AirLink Legacy"
        ),

        // === VEHICLE ROUTERS (Cradlepoint) ===
        TacticalOuiInfo(
            "003044",
            "Cradlepoint",
            TacticalCategory.VEHICLE_ROUTER,
            DeviceType.VEHICLE_ROUTER,
            ConfidenceLevel.HIGH,
            "IBR Series Router"
        ),

        // === FIREFIGHTER EQUIPMENT (Dräger, MSA) ===
        TacticalOuiInfo(
            "001EC0",
            "Dräger Safety",
            TacticalCategory.FIREFIGHTER,
            DeviceType.FIREFIGHTER,
            ConfidenceLevel.HIGH,
            "PSS Merlin Telemetry"
        ),
        TacticalOuiInfo(
            "000D6F",
            "Dräger Safety",
            TacticalCategory.FIREFIGHTER,
            DeviceType.FIREFIGHTER,
            ConfidenceLevel.HIGH,
            "Dräger Gas Detection"
        ),
        TacticalOuiInfo(
            "001BC5",
            "MSA Safety",
            TacticalCategory.FIREFIGHTER,
            DeviceType.FIREFIGHTER,
            ConfidenceLevel.HIGH,
            "MSA G1/Altair"
        ),

        // === SAMSUNG (field-device variants and ATAK-compatible workflows) ===
        TacticalOuiInfo(
            ouiPrefix = "00E064",
            vendorName = "Samsung Electronics",
            category = TacticalCategory.TACTICAL_EUD,
            deviceType = DeviceType.PHONE,
            confidence = ConfidenceLevel.HIGH,
            description = "Galaxy field-device variant (ATAK-compatible)"
        ),
        TacticalOuiInfo(
            ouiPrefix = "842519",
            vendorName = "Samsung Electronics",
            category = TacticalCategory.TACTICAL_EUD,
            deviceType = DeviceType.PHONE,
            confidence = ConfidenceLevel.HIGH,
            description = "Galaxy field-device variant or access point"
        )
    )

    /**
     * Medium-confidence OUI prefixes - may have civilian variants.
     */
    private val MEDIUM_CONFIDENCE_OUIS = listOf(
        // === GETAC (rugged tablets) ===
        TacticalOuiInfo(
            ouiPrefix = "002421",
            vendorName = "Getac Technology Corp.",
            category = TacticalCategory.TACTICAL_EUD,
            deviceType = DeviceType.TACTICAL_EUD,
            confidence = ConfidenceLevel.MEDIUM,
            description = "Rugged tablets, BC-04 cameras"
        ),

        // === GARMIN (Foretrex/Tactix outdoor and professional wearables) ===
        TacticalOuiInfo(
            ouiPrefix = "001C27",
            vendorName = "Garmin International",
            category = TacticalCategory.TACTICAL_EUD,
            deviceType = DeviceType.WEARABLE,
            confidence = ConfidenceLevel.MEDIUM,
            description = "Foretrex and Tactix wearable devices"
        ),

        // === PANASONIC (Toughbook) ===
        TacticalOuiInfo(
            "008045",
            "Panasonic",
            TacticalCategory.TACTICAL_EUD,
            DeviceType.TACTICAL_EUD,
            ConfidenceLevel.MEDIUM,
            "Toughbook CF-Series"
        ),
        TacticalOuiInfo(
            "000B97",
            "Panasonic",
            TacticalCategory.TACTICAL_EUD,
            DeviceType.TACTICAL_EUD,
            ConfidenceLevel.MEDIUM,
            "Toughbook Legacy"
        )
    )
}
