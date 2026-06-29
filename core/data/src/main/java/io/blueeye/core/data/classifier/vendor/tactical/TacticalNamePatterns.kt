@file:Suppress("MagicNumber", "ReturnCount", "TooGenericExceptionCaught", "SwallowedException", "CyclomaticComplexMethod", "ComplexCondition", "LongParameterList", "MaximumLineLength", "MaxLineLength", "NestedBlockDepth", "TooManyFunctions", "LongMethod", "WildcardImport", "NoWildcardImports", "UnusedPrivateProperty", "PrintStackTrace", "LoopWithTooManyJumpStatements", "EmptyCatchBlock", "Wrapping")

package io.blueeye.core.data.classifier.vendor.tactical

/**
 * Characteristic device name patterns for tactical equipment.
 */
object TacticalNamePatterns {
    // === RUGGED PUBLIC-SAFETY MOBILE TERMINALS ===
    // Device families often used as field terminals; name matches stay evidence-only.
    val MTN_PATTERNS = listOf(
        Regex("XCover\\s*6", RegexOption.IGNORE_CASE), // Samsung Galaxy XCover 6 Pro
        Regex("SM-G736", RegexOption.IGNORE_CASE), // Model number for XCover 6 Pro
        Regex("Zebra.*ET5", RegexOption.IGNORE_CASE), // Zebra ET55 rugged tablet
        Regex("EF-500", RegexOption.IGNORE_CASE), // Bluebird EF-500R rugged handheld
        Regex("Getac.*F110", RegexOption.IGNORE_CASE) // Getac F110 rugged tablet
    )

    // Axon body cameras. Bare "Axon" or "Body 3" are intentionally not enough:
    // consumer devices and fitness products can use similar words.
    val AXON_BODY_CAMERA_PATTERNS = listOf(
        Regex("\\bAxon\\s+Body\\s*[234]\\b", RegexOption.IGNORE_CASE),
        Regex("\\bAxon\\s+Flex\\s*2\\b", RegexOption.IGNORE_CASE),
        Regex("\\bAxon\\s+(BWC|Camera)\\b", RegexOption.IGNORE_CASE)
    )

    val AXON_SIDEARM_PATTERNS = listOf(
        Regex("^X8[12]\\d{6,}", RegexOption.IGNORE_CASE), // Signal Sidearm serial (Source: Expert Audit)
        Regex("^X8[12]", RegexOption.IGNORE_CASE), // Broad match for Sidearm
        Regex("\\bSignal\\s*Sidearm\\b", RegexOption.IGNORE_CASE)
    )

    val TASER_PATTERNS = listOf(
        Regex("Taser", RegexOption.IGNORE_CASE),
    )

    /**
     * Invisio equipment - FCC ID: 2AUGTASM11374 (R30 PTT)
     *
     * Name "IAD 01" may appear during pairing on some devices.
     * Nordic nRF52832-based accessories can expose recognizable BLE names.
     */
    val INVISIO_PATTERNS = listOf(
        Regex("IAD\\s*01", RegexOption.IGNORE_CASE), // Invisio Audio Device pairing
        Regex("INVISIO", RegexOption.IGNORE_CASE),
        Regex("INVISIO.*V60|V60.*INVISIO", RegexOption.IGNORE_CASE), // V60 controller with brand context
        Regex("INVISIO.*V50|V50.*INVISIO", RegexOption.IGNORE_CASE), // V50 controller with brand context
        Regex("R30", RegexOption.IGNORE_CASE), // R30 Wireless PTT
        Regex("ASM11374", RegexOption.IGNORE_CASE) // FCC ID
    )

    /**
     * Yardarm holster sensors - FCC ID: 2AJ3810242
     *
     * Some pairing names may include serial-like identifiers, which can create
     * persistent identity correlation when repeatedly observed.
     */
    val YARDARM_PATTERNS = listOf(
        Regex("YHA", RegexOption.IGNORE_CASE),
        Regex("Yardarm", RegexOption.IGNORE_CASE),
        Regex("Holster\\s*Aware", RegexOption.IGNORE_CASE),
        Regex("2AJ38", RegexOption.IGNORE_CASE) // FCC prefix
    )

    /**
     * Sig Sauer BDX - Ballistic Data Xchange
     *
     * KILO rangefinder and SIERRA scope family names can appear in BLE names.
     * These are medium-confidence name signals because consumer contexts exist.
     */
    val SIG_BDX_PATTERNS = listOf(
        Regex("KILO\\d", RegexOption.IGNORE_CASE), // KILO2400, KILO3K, KILO10K
        Regex("KILO\\s*\\d", RegexOption.IGNORE_CASE),
        Regex("SIERRA\\d", RegexOption.IGNORE_CASE), // SIERRA6, SIERRA3
        Regex("SIERRA\\s*\\d", RegexOption.IGNORE_CASE),
        Regex("BDX", RegexOption.IGNORE_CASE),
        Regex("Applied.*Ballistics", RegexOption.IGNORE_CASE)
    )

    // Kestrel Ballistics Weather Meters
    val KESTREL_PATTERNS = listOf(
        Regex("Kestrel", RegexOption.IGNORE_CASE)
    )

    // Silynx professional audio
    val SILYNX_PATTERNS = listOf(
        Regex("Silynx", RegexOption.IGNORE_CASE),
        Regex("Clarus", RegexOption.IGNORE_CASE)
    )

    // Meshtastic/ATAK
    val MESHTASTIC_PATTERNS = listOf(
        Regex("Meshtastic", RegexOption.IGNORE_CASE),
        Regex("ATAK", RegexOption.IGNORE_CASE)
    )

    /**
     * Radetec Smart Slide - Ammunition counter
     *
     * Connected accessory family that may expose recognizable BLE names.
     * Treat name-only matches as medium-confidence until corroborated.
     */
    val RADETEC_PATTERNS = listOf(
        Regex("Radetec", RegexOption.IGNORE_CASE),
        Regex("RISC", RegexOption.IGNORE_CASE), // RISCpro (Professional)
        Regex("Smart\\s*Slide", RegexOption.IGNORE_CASE),
        Regex("Round\\s*Count", RegexOption.IGNORE_CASE)
    )

    // === PUBLIC-SAFETY AND PROFESSIONAL RADIO/AUDIO EQUIPMENT ===

    // Hytera DMR/TETRA radios seen in professional/public-safety deployments
    val HYTERA_PATTERNS = listOf(
        Regex("Hytera", RegexOption.IGNORE_CASE),
        Regex("PD7\\d{2}", RegexOption.IGNORE_CASE), // PD785, PD782
        Regex("PD6\\d{2}", RegexOption.IGNORE_CASE), // PD685, PD662
        Regex("X1P", RegexOption.IGNORE_CASE), // X1P radio family
        Regex("HP7\\d{2}", RegexOption.IGNORE_CASE), // HP785
        Regex("MD7\\d{2}", RegexOption.IGNORE_CASE), // MD785 mobile
        Regex("PT5\\d{2}", RegexOption.IGNORE_CASE) // PT580H TETRA
    )

    // Sepura TETRA radio families
    val SEPURA_PATTERNS = listOf(
        Regex("Sepura", RegexOption.IGNORE_CASE),
        Regex("STP8\\d{3}", RegexOption.IGNORE_CASE), // STP8000 series
        Regex("STP9\\d{3}", RegexOption.IGNORE_CASE), // STP9000 series
        Regex("SC2[01]", RegexOption.IGNORE_CASE), // SC20, SC21
        Regex("SCG2\\d", RegexOption.IGNORE_CASE), // SCG22 gateway
        Regex("SRG3900", RegexOption.IGNORE_CASE) // SRG3900 mobile
    )

    // Motorola TETRA/DMR radio families
    val MOTOROLA_TETRA_PATTERNS = listOf(
        Regex("MTP\\d{4}", RegexOption.IGNORE_CASE), // MTP850, MTP3550
        Regex("MTH\\d{3}", RegexOption.IGNORE_CASE), // MTH800
        Regex("MTM\\d{4}", RegexOption.IGNORE_CASE), // MTM800, MTM5400
        Regex("MXP\\d{3}", RegexOption.IGNORE_CASE), // MXP600
        Regex("MOTOTRBO", RegexOption.IGNORE_CASE), // MOTOTRBO system
        Regex("SLR\\d{4}", RegexOption.IGNORE_CASE), // SLR5500 repeaters
        Regex("Dimetra", RegexOption.IGNORE_CASE) // Dimetra IP infrastructure
    )

    // Peltor / 3M professional hearing protection and comms headsets
    val PELTOR_PATTERNS = listOf(
        Regex("Peltor", RegexOption.IGNORE_CASE),
        Regex("ComTac", RegexOption.IGNORE_CASE),
        Regex("WS\\s*Alert", RegexOption.IGNORE_CASE), // WS Alert XPI
        Regex("LiteCom", RegexOption.IGNORE_CASE), // LiteCom Plus
        Regex("SWAT\\s*Tac", RegexOption.IGNORE_CASE)
    )

    // Sordin professional hearing protection
    val SORDIN_PATTERNS = listOf(
        Regex("Sordin", RegexOption.IGNORE_CASE),
        Regex("Supreme", RegexOption.IGNORE_CASE) // Supreme Pro-X
    )

    // Earmor professional/security headset family
    val EARMOR_PATTERNS = listOf(
        Regex("Earmor", RegexOption.IGNORE_CASE),
        Regex("M3[12]", RegexOption.IGNORE_CASE) // M31, M32
    )

    // Medical equipment (PRM - Ratownictwo Medyczne)
    val MEDICAL_PATTERNS = listOf(
        Regex("Stryker", RegexOption.IGNORE_CASE),
        Regex("LifePak", RegexOption.IGNORE_CASE),
        Regex("LUCAS", RegexOption.IGNORE_CASE), // LUCAS CPR
        Regex("Corpuls", RegexOption.IGNORE_CASE),
        Regex("Zoll", RegexOption.IGNORE_CASE),
        Regex("Philips\\s*MRx", RegexOption.IGNORE_CASE),
        Regex("Defibrilator", RegexOption.IGNORE_CASE)
    )

    // Body camera vendors and model families
    // Note: Reveal Media is UK, handled separately in REVEAL_MEDIA_PATTERNS
    val POLISH_BODYCAM_PATTERNS = listOf(
        Regex("Enigma", RegexOption.IGNORE_CASE), // Enigma Systemy Ochrony
        Regex("Wolfcom", RegexOption.IGNORE_CASE),
        Regex("VB[34]00", RegexOption.IGNORE_CASE), // Motorola VB300, VB400
        Regex("V[37]00", RegexOption.IGNORE_CASE) // Motorola V300, V700
    )

    // === VEHICLE ROUTERS ===
    // Rugged router families often found in fleet/mobile deployments.
    val VEHICLE_ROUTER_PATTERNS = listOf(
        Regex("AirLink", RegexOption.IGNORE_CASE), // Sierra Wireless AirLink
        Regex("MP70", RegexOption.IGNORE_CASE), // Sierra MP70
        Regex("MG90", RegexOption.IGNORE_CASE), // Sierra MG90
        Regex("IBR9\\d{2}", RegexOption.IGNORE_CASE), // Cradlepoint IBR900/950
        Regex("IBR1[17]00", RegexOption.IGNORE_CASE), // Cradlepoint IBR1100/1700
        Regex("Cradlepoint", RegexOption.IGNORE_CASE),
        Regex("Sierra.*Wireless", RegexOption.IGNORE_CASE)
    )

    // === DOCUMENT READERS AND BORDER/CUSTOMS WORKFLOWS ===
    val BORDER_CONTROL_PATTERNS = listOf(
        Regex("Regula", RegexOption.IGNORE_CASE), // Regula document readers
        Regex("70X9", RegexOption.IGNORE_CASE), // Regula 70X9
        Regex("ARH", RegexOption.IGNORE_CASE), // ARH Authenticator
        Regex("Desko", RegexOption.IGNORE_CASE), // Desko document readers
        Regex("Toughbook", RegexOption.IGNORE_CASE), // Panasonic Toughbook
        Regex("CF-\\d{2}", RegexOption.IGNORE_CASE), // Panasonic CF-31, CF-54
        Regex("FZ-\\d{2}", RegexOption.IGNORE_CASE) // Panasonic FZ-G1
    )

    // === FIREFIGHTER / EMS EQUIPMENT ===
    val FIREFIGHTER_PATTERNS = listOf(
        Regex("Dräger", RegexOption.IGNORE_CASE), // Dräger SCBA
        Regex("Draeger", RegexOption.IGNORE_CASE), // Alternate spelling
        Regex("PSS\\s*Merlin", RegexOption.IGNORE_CASE), // PSS Merlin telemetry
        Regex("MSA\\s*G1", RegexOption.IGNORE_CASE), // MSA G1 mask
        Regex("MSA\\s*Auer", RegexOption.IGNORE_CASE), // MSA Auer
        Regex("Scott\\s*Air", RegexOption.IGNORE_CASE), // Scott Air-Pak
        Regex("LUNAR", RegexOption.IGNORE_CASE), // MSA LUNAR locator
        Regex("Altair", RegexOption.IGNORE_CASE) // MSA Altair gas detector
    )

    // === MOTOROLA APX ECOSYSTEM ===
    val MOTOROLA_APX_PATTERNS = listOf(
        Regex("APX\\d{4}", RegexOption.IGNORE_CASE), // APX8000, APX6000
        Regex("APX.*NEXT", RegexOption.IGNORE_CASE), // APX NEXT
        Regex("XPR\\d{4}", RegexOption.IGNORE_CASE), // XPR7550, XPR3500
        Regex("DP\\d{4}", RegexOption.IGNORE_CASE), // DP4800, DP4600
        Regex("M500", RegexOption.IGNORE_CASE), // M500 in-car video
        Regex("4RE", RegexOption.IGNORE_CASE) // 4RE in-car system
    )

    // ============================================================
    // === EUROPEAN PUBLIC-SAFETY / PROFESSIONAL EQUIPMENT PATTERNS ===
    // ============================================================

    // === UK: REVEAL MEDIA (FCC: 2AL26-D5, 2AL26-K6) ===
    // UK body-camera vendor family also used in healthcare and retail settings.
    // Characteristic: Front-facing screen design
    val REVEAL_MEDIA_PATTERNS = listOf(
        Regex("Reveal", RegexOption.IGNORE_CASE), // Main brand
        Regex("D[345]", RegexOption.IGNORE_CASE), // D-Series (D3, D4, D5)
        Regex("K[567]", RegexOption.IGNORE_CASE), // K-Series (K5, K6, K7 Live)
        Regex("RS[23]", RegexOption.IGNORE_CASE) // RS2, RS3 retail security
    )

    // === NETHERLANDS: ZEPCAM ===
    // Dutch manufacturer of body-camera systems with 4G live streaming.
    val ZEPCAM_PATTERNS = listOf(
        Regex("Zepcam", RegexOption.IGNORE_CASE),
        Regex("T2\\+", RegexOption.IGNORE_CASE), // T2+ model
        Regex("T3\\s*Live", RegexOption.IGNORE_CASE) // T3 Live (4G streaming)
    )

    // === FRANCE: CROSSCALL NEO PROJECT ===
    // French hardened mobile ecosystem for public-sector field terminals.
    val CROSSCALL_NEO_PATTERNS = listOf(
        Regex("Crosscall", RegexOption.IGNORE_CASE),
        Regex("Core-X[45]", RegexOption.IGNORE_CASE), // Core-X4, Core-X5
        Regex("Action-X[35]", RegexOption.IGNORE_CASE), // Action-X3, Action-X5
        Regex("Trekker-X[34]", RegexOption.IGNORE_CASE) // Trekker series
    )

    // === FRANCE: AIRBUS TETRAPOL (INPT/Rubis network) ===
    // France uses Tetrapol (FDMA) instead of TETRA (TDMA)
    val AIRBUS_TETRAPOL_PATTERNS = listOf(
        Regex("TH[89]", RegexOption.IGNORE_CASE), // TH8, TH9 terminals
        Regex("Tetrapol", RegexOption.IGNORE_CASE),
        Regex("Eads", RegexOption.IGNORE_CASE), // Legacy EADS brand
        Regex("THR[89]", RegexOption.IGNORE_CASE) // THR8, THR9 rugged
    )

    // === GERMANY: MOTOROLA TETRA (BDBOS Network) ===
    // Bundespolizei standard - MTP6000 series
    val MOTOROLA_BDBOS_PATTERNS = listOf(
        Regex("MTP6\\d{3}", RegexOption.IGNORE_CASE), // MTP6650, MTP6750
        Regex("MTP8\\d{3}", RegexOption.IGNORE_CASE), // MTP8550
        Regex("BDBOS", RegexOption.IGNORE_CASE) // Network identifier
    )

    // === GERMANY: SECUNET ===
    // Mobile document verification via NFC
    val SECUNET_PATTERNS = listOf(
        Regex("secunet", RegexOption.IGNORE_CASE),
        Regex("biomiddle", RegexOption.IGNORE_CASE) // NFC document verification
    )

    // === SCANDINAVIA: INVISIO EXTENDED (V60 II, ADP) ===
    // Updated with newer models from report
    val INVISIO_EXTENDED_PATTERNS = listOf(
        Regex("V60\\s*II", RegexOption.IGNORE_CASE), // V60 II controller
        Regex("V60\\s*ADP", RegexOption.IGNORE_CASE), // V60 ADP II adapter
        Regex("V20", RegexOption.IGNORE_CASE), // V20 single-com
        Regex("X5", RegexOption.IGNORE_CASE), // X5 headset
        Regex("M3s", RegexOption.IGNORE_CASE) // M3s in-ear
    )

    // === UK ESN TRANSITION: Samsung XCover FieldPro ===
    // UK Emergency Services Network (ESN) migration from TETRA to LTE
    val ESN_TERMINAL_PATTERNS = listOf(
        Regex("XCover.*Field", RegexOption.IGNORE_CASE), // XCover FieldPro
        Regex("SM-G889", RegexOption.IGNORE_CASE), // FieldPro model number
        Regex("Galaxy.*Tactical", RegexOption.IGNORE_CASE), // Product naming used by some field-device variants
        Regex("FirstNet", RegexOption.IGNORE_CASE) // US equivalent (compatible)
    )

    // === ZEBRA TC77 SERIES ===
    // Rugged Android terminals for field and ID-check workflows.
    val ZEBRA_TC7X_PATTERNS = listOf(
        Regex("TC7[257]", RegexOption.IGNORE_CASE), // TC72, TC75, TC77
        Regex("TC5[12]", RegexOption.IGNORE_CASE), // TC51, TC52
        Regex("EC[35]0", RegexOption.IGNORE_CASE), // EC30, EC50
        Regex("MC9\\d{3}", RegexOption.IGNORE_CASE) // MC9300 series
    )

    // === BLUEBIRD (Netherlands, Poland) ===
    val BLUEBIRD_PATTERNS = listOf(
        Regex("EF50[01]", RegexOption.IGNORE_CASE), // EF500, EF501R
        Regex("Bluebird", RegexOption.IGNORE_CASE),
        Regex("Pidion", RegexOption.IGNORE_CASE) // Legacy brand
    )
}
