package io.blueeye.core.data.classifier.vendor

import io.blueeye.core.data.classifier.vendor.tactical.ConfidenceLevel
import io.blueeye.core.data.classifier.vendor.tactical.TacticalCategory
import io.blueeye.core.data.classifier.vendor.tactical.TacticalNamePatterns
import io.blueeye.core.data.classifier.vendor.tactical.TacticalOuiData
import io.blueeye.core.data.classifier.vendor.tactical.TacticalOuiInfo
import io.blueeye.core.data.classifier.vendor.tactical.TacticalUuids

/** Registry of professional/public-safety Bluetooth identifiers. */
@Suppress("TooManyFunctions")
object TacticalOuiRegistry {

    private val allOuis: Map<String, TacticalOuiInfo> by lazy {
        TacticalOuiData.getAllInfos().associateBy { it.ouiPrefix.uppercase() }
    }

    private const val OUI_LEN = 6

    fun lookup(mac: String): TacticalOuiInfo? {
        val clean = mac.replace("[:-]".toRegex(), "").uppercase()
        return if (clean.length >= OUI_LEN) allOuis[clean.substring(0, OUI_LEN)] else null
    }

    fun isCriticalTactical(mac: String): Boolean = lookup(mac)?.confidence == ConfidenceLevel.CRITICAL

    fun matchByName(name: String): Pair<TacticalCategory, String>? {
        if (name.isBlank()) return null
        return matchSafety(name) ?: matchAudio(name) ?: matchWeapons(name) ?: matchComms(name) ?: matchEuro(name)
    }

    private fun matchSafety(n: String): Pair<TacticalCategory, String>? {
        val p = TacticalNamePatterns
        return when {
            matches(n, p.MTN_PATTERNS) -> TacticalCategory.TACTICAL_EUD to "Mobile Terminal (MTN)"
            matches(n, p.AXON_BODY_CAMERA_PATTERNS) -> TacticalCategory.BODY_CAMERA to "Axon Body Camera"
            matches(n, p.AXON_SIDEARM_PATTERNS) -> TacticalCategory.HOLSTER_SENSOR to "Axon Signal Sidearm"
            matches(n, p.TASER_PATTERNS) -> TacticalCategory.SMART_WEAPON to "Taser equipment"
            matches(n, p.POLISH_BODYCAM_PATTERNS) -> TacticalCategory.BODY_CAMERA to "Body Camera"
            matches(n, p.YARDARM_PATTERNS) -> TacticalCategory.HOLSTER_SENSOR to "Yardarm Safety Sensor"
            else -> null
        }
    }

    private fun matchAudio(n: String): Pair<TacticalCategory, String>? {
        val p = TacticalNamePatterns
        return when {
            matches(n, p.INVISIO_PATTERNS) -> TacticalCategory.TACTICAL_AUDIO to "Invisio professional audio"
            matches(n, p.PELTOR_PATTERNS) -> TacticalCategory.TACTICAL_AUDIO to "Peltor Comms"
            matches(n, p.SORDIN_PATTERNS) -> TacticalCategory.TACTICAL_AUDIO to "Sordin professional audio"
            matches(n, p.EARMOR_PATTERNS) -> TacticalCategory.TACTICAL_AUDIO to "Earmor professional audio"
            matches(n, p.SILYNX_PATTERNS) -> TacticalCategory.TACTICAL_AUDIO to "Silynx professional audio"
            else -> null
        }
    }

    private fun matchWeapons(n: String): Pair<TacticalCategory, String>? {
        val p = TacticalNamePatterns
        return when {
            matches(n, p.SIG_BDX_PATTERNS) -> TacticalCategory.SMART_WEAPON to "Sig Sauer BDX"
            matches(n, p.KESTREL_PATTERNS) -> TacticalCategory.SMART_WEAPON to "Kestrel Ballistics"
            matches(n, p.RADETEC_PATTERNS) -> TacticalCategory.SMART_WEAPON to "Radetec Smart Slide"
            else -> null
        }
    }

    private fun matchComms(n: String): Pair<TacticalCategory, String>? {
        val p = TacticalNamePatterns
        return when {
            matches(n, p.HYTERA_PATTERNS) -> TacticalCategory.TACTICAL_RADIO to "Hytera Radio"
            matches(n, p.SEPURA_PATTERNS) -> TacticalCategory.TACTICAL_RADIO to "Sepura TETRA"
            matches(n, p.MOTOROLA_TETRA_PATTERNS) -> TacticalCategory.TACTICAL_RADIO to "Motorola TETRA/DMR"
            matches(n, p.MOTOROLA_APX_PATTERNS) -> TacticalCategory.TACTICAL_RADIO to "Motorola APX Radio"
            matches(n, p.MESHTASTIC_PATTERNS) -> TacticalCategory.TACTICAL_EUD to "Meshtastic/Public Mesh"
            matches(n, p.VEHICLE_ROUTER_PATTERNS) -> TacticalCategory.VEHICLE_ROUTER to "Vehicle Router"
            matches(n, p.BORDER_CONTROL_PATTERNS) -> TacticalCategory.DOCUMENT_READER to "Document Reader"
            matches(n, p.FIREFIGHTER_PATTERNS) -> TacticalCategory.FIREFIGHTER to "Firefighter SCBA"
            matches(n, p.MEDICAL_PATTERNS) -> TacticalCategory.FIRE_EMS to "Medical Equipment"
            else -> null
        }
    }

    private fun matchEuro(n: String): Pair<TacticalCategory, String>? {
        val p = TacticalNamePatterns
        return when {
            matches(n, p.REVEAL_MEDIA_PATTERNS) -> TacticalCategory.BODY_CAMERA to "Reveal Media BWC (UK)"
            matches(n, p.ZEPCAM_PATTERNS) -> TacticalCategory.BODY_CAMERA to "Zepcam BWC (NL)"
            matches(n, p.CROSSCALL_NEO_PATTERNS) -> TacticalCategory.TACTICAL_EUD to "Crosscall NEO (FR)"
            matches(n, p.AIRBUS_TETRAPOL_PATTERNS) -> TacticalCategory.TACTICAL_RADIO to "Airbus Tetrapol"
            matches(n, p.MOTOROLA_BDBOS_PATTERNS) -> TacticalCategory.TACTICAL_RADIO to "Motorola TETRA"
            matches(n, p.SECUNET_PATTERNS) -> TacticalCategory.TACTICAL_EUD to "secunet Mobile (DE)"
            matches(n, p.INVISIO_EXTENDED_PATTERNS) -> TacticalCategory.TACTICAL_AUDIO to "Invisio V60 II"
            matches(n, p.ESN_TERMINAL_PATTERNS) -> TacticalCategory.TACTICAL_EUD to "ESN Terminal (UK)"
            matches(n, p.ZEBRA_TC7X_PATTERNS) -> TacticalCategory.TACTICAL_EUD to "Zebra TC7x (EU)"
            matches(n, p.BLUEBIRD_PATTERNS) -> TacticalCategory.TACTICAL_EUD to "Bluebird PDA"
            else -> null
        }
    }

    private fun matches(name: String, patterns: List<Regex>): Boolean = patterns.any { it.containsMatchIn(name) }

    fun matchByServiceUuid(serviceUuids: List<String>): Pair<TacticalCategory, String>? {
        val uLower = serviceUuids.map { it.lowercase() }
        val p = TacticalUuids
        return when {
            uLower.any { it.contains(p.MOTOROLA_SOLUTIONS_SHORT) } || p.MOTOROLA_SOLUTIONS_UUID.lowercase() in uLower ->
                TacticalCategory.TACTICAL_RADIO to "Motorola Solutions (FD8E)"
            p.MESHTASTIC_SERVICE.lowercase() in uLower -> TacticalCategory.TACTICAL_EUD to "Meshtastic Public Mesh"
            p.YARDARM_SERVICE.lowercase() in uLower -> TacticalCategory.HOLSTER_SENSOR to "Yardarm Safety Sensor"
            p.HARRIS_MSA_G1_SERVICE.lowercase() in uLower -> TacticalCategory.FIREFIGHTER to "Harris XL-200P / MSA G1"
            p.KESTREL_LINK_SERVICE.lowercase() in uLower -> TacticalCategory.SMART_WEAPON to "Kestrel Ballistics"
            else -> null
        }
    }

    fun matchByManufacturerId(id: Int): Pair<TacticalCategory, String>? {
        val u = TacticalUuids
        return when (id) {
            u.AXON_COMPANY_ID -> TacticalCategory.BODY_CAMERA to "Axon Signal equipment"
            u.YARDARM_COMPANY_ID -> TacticalCategory.HOLSTER_SENSOR to "Yardarm Safety Aware"
            u.MOTOROLA_COMPANY_ID -> TacticalCategory.TACTICAL_RADIO to "Motorola (Radio)"
            u.WURTH_ELEKTRONIK_ID -> TacticalCategory.POLICE_EQUIPMENT to "Würth professional component"
            u.SIERRA_WIRELESS_ID -> TacticalCategory.VEHICLE_ROUTER to "Sierra Wireless Router"
            u.DRAEGER_ID -> TacticalCategory.FIREFIGHTER to "Dräger (SCBA Telemetry)"
            u.PANASONIC_ID -> TacticalCategory.TACTICAL_EUD to "Panasonic Toughbook"
            else -> null
        }
    }

    fun getAllOuiPrefixes(): Set<String> = allOuis.keys
    fun getStats(): Map<ConfidenceLevel, Int> = allOuis.values.groupingBy { it.confidence }.eachCount()

    data class FallbackMatch(val category: TacticalCategory, val name: String)

    fun fallbackIdentify(manufacturerId: Int?, serviceUuids: List<String>): FallbackMatch? {
        val match = (manufacturerId?.let { matchByManufacturerId(it) })
            ?: matchByServiceUuid(serviceUuids)

        return match?.let { FallbackMatch(it.first, it.second) }
    }
}
