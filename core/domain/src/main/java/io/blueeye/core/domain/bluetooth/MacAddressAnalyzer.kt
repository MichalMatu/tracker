package io.blueeye.core.domain.bluetooth

@Suppress("MagicNumber")
object MacAddressAnalyzer {
    enum class BleAddressType {
        PUBLIC,
        RANDOM_STATIC,
        RESOLVABLE_PRIVATE,
        NON_RESOLVABLE,
        RESERVED,
    }

    enum class PrivacyLevel {
        STATIC,
        SEMI_STATIC,
        DYNAMIC,
        UNKNOWN,
    }

    data class MacAnalysis(
        val cleanAddress: String,
        val bleType: BleAddressType,
        val privacyLevel: PrivacyLevel,
        val isRandomized: Boolean,
        val canLookupOui: Boolean,
        val description: String,
    )

    private const val MAC_LEN = 12
    private const val RADIX = 16
    private const val MSB_MASK = 0xC0
    private const val MSB_SHIFT = 6
    private const val MSB_RPA = 0b01
    private const val MSB_STATIC = 0b11
    private const val MSB_NON_RES = 0b00
    private const val MSB_RES = 0b10

    private val rpaOuis =
        setOf(
            "404CCA",
            "485519",
            "483FDA",
            "543204",
            "58CF79",
            "600194",
            "6055F9",
            "68B6B3",
            "48B4",
            "546009",
            "4006A0",
            "44783E",
            "4098AD",
            "441793",
            "48437C",
            "4447CC",
            "544A16",
        )

    private val lowOuis = setOf("00", "04", "08", "0C", "10", "14", "18", "1C")

    fun analyze(macAddress: String): MacAnalysis {
        val clean = cleanAddress(macAddress)
        if (clean.length != MAC_LEN) {
            return MacAnalysis(
                cleanAddress = macAddress,
                bleType = BleAddressType.PUBLIC,
                privacyLevel = PrivacyLevel.UNKNOWN,
                isRandomized = false,
                canLookupOui = false,
                description = "Invalid",
            )
        }

        val type = determineBleType(clean)
        val level = determinePrivacyLevel(type)
        val description =
            when (type) {
                BleAddressType.RESOLVABLE_PRIVATE -> "Private (RPA) - Changes periodically"
                BleAddressType.RANDOM_STATIC -> "Random Static - Fixed until reboot"
                BleAddressType.PUBLIC -> "Public - Permanent Hardware ID"
                BleAddressType.NON_RESOLVABLE -> "Non-Resolvable (treated as Public)"
                BleAddressType.RESERVED -> "Reserved Range"
            }

        return MacAnalysis(
            cleanAddress = clean,
            bleType = type,
            privacyLevel = level,
            isRandomized = level == PrivacyLevel.DYNAMIC,
            canLookupOui = type == BleAddressType.PUBLIC,
            description = description,
        )
    }

    fun determineBleType(cleanMac: String): BleAddressType {
        val bits =
            try {
                if (cleanMac.length < MIN_HEX_PREFIX_LENGTH) {
                    INVALID_BITS
                } else {
                    (cleanMac.substring(0, MIN_HEX_PREFIX_LENGTH).toInt(RADIX) and MSB_MASK) ushr MSB_SHIFT
                }
            } catch (_: NumberFormatException) {
                INVALID_BITS
            }

        return when (bits) {
            MSB_STATIC -> BleAddressType.RANDOM_STATIC
            MSB_RPA -> {
                val isPublic = rpaOuis.any { cleanMac.startsWith(it) }
                if (isPublic) BleAddressType.PUBLIC else BleAddressType.RESOLVABLE_PRIVATE
            }
            MSB_NON_RES -> {
                val isPublic = lowOuis.any { cleanMac.startsWith(it) }
                if (isPublic) BleAddressType.PUBLIC else BleAddressType.NON_RESOLVABLE
            }
            MSB_RES -> BleAddressType.RESERVED
            else -> BleAddressType.PUBLIC
        }
    }

    fun getPrivacyLabel(mac: String): String =
        when (determineBleType(cleanAddress(mac))) {
            BleAddressType.RESOLVABLE_PRIVATE -> "ROTATING"
            BleAddressType.RANDOM_STATIC -> "STATIC-R"
            BleAddressType.PUBLIC -> "PUBLIC"
            else -> "UNKNOWN"
        }

    fun isRandomized(mac: String): Boolean = determineBleType(cleanAddress(mac)) == BleAddressType.RESOLVABLE_PRIVATE

    private fun determinePrivacyLevel(type: BleAddressType): PrivacyLevel =
        when (type) {
            BleAddressType.RESOLVABLE_PRIVATE,
            BleAddressType.NON_RESOLVABLE,
            -> PrivacyLevel.DYNAMIC
            BleAddressType.RANDOM_STATIC -> PrivacyLevel.SEMI_STATIC
            else -> PrivacyLevel.STATIC
        }

    private fun cleanAddress(mac: String): String = mac.replace("[:\\-./]".toRegex(), "").uppercase()

    private const val MIN_HEX_PREFIX_LENGTH = 2
    private const val INVALID_BITS = -1
}
