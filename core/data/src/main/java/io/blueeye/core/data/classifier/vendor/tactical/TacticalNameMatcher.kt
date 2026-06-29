package io.blueeye.core.data.classifier.vendor.tactical

import io.blueeye.core.data.classifier.vendor.TacticalOuiRegistry
import io.blueeye.core.model.DeviceType

data class TacticalNameMatch(
    val category: TacticalCategory,
    val deviceType: DeviceType,
    val modelName: String,
    val evidenceDescription: String,
)

object TacticalNameMatcher {
    private data class NameRule(
        val matches: (String, String) -> Boolean,
        val build: (String, String) -> TacticalNameMatch,
    )

    private val explicitRules =
        listOf(
            NameRule(
                matches = { _, original ->
                    TacticalNamePatterns.AXON_BODY_CAMERA_PATTERNS.any { it.containsMatchIn(original) }
                },
                build = { normalized, _ -> axonCameraMatch(normalized) },
            ),
            NameRule(
                matches = { _, original ->
                    TacticalNamePatterns.TASER_PATTERNS.any { it.containsMatchIn(original) } ||
                        TacticalNamePatterns.AXON_SIDEARM_PATTERNS.any { it.containsMatchIn(original) }
                },
                build = { _, original -> axonSafetyMatch(original) },
            ),
            NameRule(
                matches = { normalized, _ ->
                    normalized.contains("invisio") ||
                        normalized.contains("peltor") ||
                        normalized.contains("comtac") ||
                        normalized.contains("silynx") ||
                        normalized.contains("clarus") ||
                        normalized.contains("iad 01")
                },
                build = { _, _ ->
                    TacticalNameMatch(
                        category = TacticalCategory.TACTICAL_AUDIO,
                        deviceType = DeviceType.TACTICAL_AUDIO,
                        modelName = "Professional audio equipment",
                        evidenceDescription = "professional audio equipment",
                    )
                },
            ),
            NameRule(
                matches = { normalized, _ ->
                    normalized.contains("yardarm") ||
                        normalized.contains("yha") ||
                        normalized.contains("holster aware")
                },
                build = { _, _ ->
                    TacticalNameMatch(
                        category = TacticalCategory.HOLSTER_SENSOR,
                        deviceType = DeviceType.HOLSTER_SENSOR,
                        modelName = "Yardarm Sensor",
                        evidenceDescription = "Yardarm Safety Sensor",
                    )
                },
            ),
            NameRule(
                matches = { normalized, _ ->
                    matchesSigBdxName(normalized)
                },
                build = { normalized, _ -> sigBdxMatch(normalized) },
            ),
            NameRule(
                matches = { normalized, _ ->
                    normalized.contains("meshtastic") ||
                        (normalized.contains("atak") && !normalized.contains("attack"))
                },
                build = { _, _ ->
                    TacticalNameMatch(
                        category = TacticalCategory.TACTICAL_EUD,
                        deviceType = DeviceType.TACTICAL_EUD,
                        modelName = "Mesh Node",
                        evidenceDescription = "Meshtastic/Public Mesh",
                    )
                },
            ),
            NameRule(
                matches = { normalized, _ ->
                    normalized.contains("sepura") ||
                        normalized.contains("stp8") ||
                        normalized.contains("stp9") ||
                        normalized.contains("sc20") ||
                        normalized.contains("sc21") ||
                        (normalized.contains("apx") && normalized.any { it.isDigit() })
                },
                build = { _, original -> radioMatch(original) },
            ),
        )

    fun match(name: String): TacticalNameMatch? {
        val normalized = name.lowercase()
        return when {
            name.isBlank() -> null
            isSuppressedConsumerName(normalized) -> null
            else -> explicitMatch(normalized, name) ?: TacticalOuiRegistry.matchByName(name)?.toNameMatch()
        }
    }

    private fun explicitMatch(normalized: String, original: String): TacticalNameMatch? =
        explicitRules.firstNotNullOfOrNull { rule ->
            if (rule.matches(normalized, original)) rule.build(normalized, original) else null
        }

    private fun axonCameraMatch(normalized: String): TacticalNameMatch {
        val model =
            when {
                normalized.contains("body 4") -> "Axon Body 4"
                normalized.contains("body 3") -> "Axon Body 3"
                normalized.contains("body 2") -> "Axon Body 2"
                normalized.contains("flex 2") -> "Axon Flex 2"
                else -> "Axon Camera"
            }
        return TacticalNameMatch(
            category = TacticalCategory.BODY_CAMERA,
            deviceType = DeviceType.BODY_CAMERA,
            modelName = model,
            evidenceDescription = "Axon Body Camera",
        )
    }

    private fun axonSafetyMatch(original: String): TacticalNameMatch {
        val sidearm = TacticalNamePatterns.AXON_SIDEARM_PATTERNS.any { it.containsMatchIn(original) }
        val model = if (sidearm) "Signal Sidearm" else "Taser Device"
        return TacticalNameMatch(
            category = if (sidearm) TacticalCategory.HOLSTER_SENSOR else TacticalCategory.SMART_WEAPON,
            deviceType = if (sidearm) DeviceType.HOLSTER_SENSOR else DeviceType.SMART_WEAPON,
            modelName = model,
            evidenceDescription = model,
        )
    }

    private fun sigBdxMatch(normalized: String): TacticalNameMatch {
        val model =
            when {
                SIG_KILO_3K_PATTERN.containsMatchIn(normalized) -> "Sig Kilo3K"
                normalized.contains("kilo") -> "Sig Kilo"
                normalized.contains("sierra") -> "Sig Sierra"
                normalized.contains("whiskey") -> "Sig Whiskey"
                else -> "Sig Sauer BDX"
            }
        return TacticalNameMatch(
            category = TacticalCategory.SMART_WEAPON,
            deviceType = DeviceType.SMART_WEAPON,
            modelName = model,
            evidenceDescription = "Sig Sauer BDX",
        )
    }

    private fun radioMatch(original: String): TacticalNameMatch {
        val model =
            when {
                original.contains("8000", ignoreCase = true) -> "APX 8000"
                original.contains("6000", ignoreCase = true) -> "APX 6000"
                original.contains("Sepura", ignoreCase = true) -> "Sepura"
                else -> "Tactical Radio"
            }
        return TacticalNameMatch(
            category = TacticalCategory.TACTICAL_RADIO,
            deviceType = DeviceType.TACTICAL_RADIO,
            modelName = model,
            evidenceDescription = "$model equipment",
        )
    }

    private fun Pair<TacticalCategory, String>.toNameMatch(): TacticalNameMatch =
        TacticalNameMatch(
            category = first,
            deviceType = first.toDeviceType(),
            modelName = second,
            evidenceDescription = second,
        )

    private fun TacticalCategory.toDeviceType(): DeviceType =
        when (this) {
            TacticalCategory.BODY_CAMERA -> DeviceType.BODY_CAMERA
            TacticalCategory.HOLSTER_SENSOR -> DeviceType.HOLSTER_SENSOR
            TacticalCategory.TACTICAL_AUDIO -> DeviceType.TACTICAL_AUDIO
            TacticalCategory.TACTICAL_RADIO -> DeviceType.TACTICAL_RADIO
            TacticalCategory.SMART_WEAPON -> DeviceType.SMART_WEAPON
            TacticalCategory.TACTICAL_EUD -> DeviceType.TACTICAL_EUD
            TacticalCategory.FIRE_EMS -> DeviceType.MEDICAL
            TacticalCategory.POLICE_EQUIPMENT -> DeviceType.POLICE
            TacticalCategory.VEHICLE_ROUTER -> DeviceType.VEHICLE_ROUTER
            TacticalCategory.DOCUMENT_READER -> DeviceType.DOCUMENT_READER
            TacticalCategory.FIREFIGHTER -> DeviceType.FIREFIGHTER
        }

    private fun isSuppressedConsumerName(normalized: String): Boolean =
        normalized.contains("samsung") && normalized.contains("clarus")

    private fun matchesSigBdxName(normalized: String): Boolean {
        val hasModel =
            SIG_KILO_MODEL_PATTERN.containsMatchIn(normalized) ||
                SIG_SIERRA_MODEL_PATTERN.containsMatchIn(normalized) ||
                SIG_WHISKEY_MODEL_PATTERN.containsMatchIn(normalized)
        val hasContext =
            SIG_CONTEXT_PATTERN.containsMatchIn(normalized) &&
                SIG_FAMILY_PATTERN.containsMatchIn(normalized)

        return hasModel ||
            SIG_BDX_TOKEN_PATTERN.containsMatchIn(normalized) ||
            APPLIED_BALLISTICS_PATTERN.containsMatchIn(normalized) ||
            hasContext
    }

    private val SIG_KILO_3K_PATTERN = Regex("\\bkilo\\s*3k\\b")
    private val SIG_KILO_MODEL_PATTERN = Regex("\\bkilo\\s*(?:\\d|3k|10k)\\w*\\b")
    private val SIG_SIERRA_MODEL_PATTERN = Regex("\\bsierra\\s*\\d\\w*\\b")
    private val SIG_WHISKEY_MODEL_PATTERN = Regex("\\bwhiskey\\s*\\d\\w*\\b")
    private val SIG_BDX_TOKEN_PATTERN = Regex("\\bbdx\\b")
    private val APPLIED_BALLISTICS_PATTERN = Regex("\\bapplied\\s+ballistics\\b")
    private val SIG_CONTEXT_PATTERN = Regex("\\b(sig|sauer|bdx|ballistics?|rangefinder|riflescope|scope)\\b")
    private val SIG_FAMILY_PATTERN = Regex("\\b(kilo|sierra|whiskey)\\b")
}
