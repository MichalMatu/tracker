package io.blueeye.core.data.classifier

import io.blueeye.core.data.classifier.pipeline.NameClassifier
import io.blueeye.core.model.DeviceType

internal object AppleIdentityConflictGuard {
    fun preferredNameTypeForConflict(
        name: String?,
        candidateType: DeviceType,
    ): DeviceType? {
        val nameType = NameClassifier.classify(name)
        return nameType.takeIf {
            hasConflict(
                name = name,
                nameType = it,
                candidateType = candidateType,
            )
        }
    }

    fun hasLabelConflict(
        name: String?,
        label: String?,
    ): Boolean {
        val candidateType = typeForAppleLabel(label)
        if (candidateType == DeviceType.UNKNOWN) return false
        return preferredNameTypeForConflict(name, candidateType) != null
    }

    fun hasNameFamilyConflict(
        firstName: String?,
        secondName: String?,
    ): Boolean {
        if (!isAppleIdentityName(firstName) || !isAppleIdentityName(secondName)) return false

        val firstType = NameClassifier.classify(firstName)
        val secondType = NameClassifier.classify(secondName)
        return firstType != DeviceType.UNKNOWN &&
            secondType != DeviceType.UNKNOWN &&
            firstType != secondType
    }

    fun typeForAppleLabel(label: String?): DeviceType =
        label
            ?.takeIf { it.isNotBlank() }
            ?.lowercase()
            ?.let(::typeForNormalizedAppleLabel)
            ?: DeviceType.UNKNOWN

    private fun typeForNormalizedAppleLabel(normalized: String): DeviceType =
        if (normalized == "mac") {
            DeviceType.LAPTOP
        } else {
            APPLE_LABEL_TYPE_RULES
                .firstOrNull { rule -> rule.keywords.any(normalized::contains) }
                ?.deviceType
                ?: DeviceType.UNKNOWN
        }

    private fun hasConflict(
        name: String?,
        nameType: DeviceType,
        candidateType: DeviceType,
    ): Boolean =
        isAppleIdentityName(name) &&
            nameType != DeviceType.UNKNOWN &&
            candidateType != DeviceType.UNKNOWN &&
            nameType != candidateType

    private fun isAppleIdentityName(name: String?): Boolean {
        if (name.isNullOrBlank()) return false
        val normalized = name.lowercase()

        return APPLE_IDENTITY_KEYWORDS.any(normalized::contains)
    }

    private val APPLE_IDENTITY_KEYWORDS =
        listOf(
            "airpods",
            "airpod",
            "beats",
            "powerbeats",
            "iphone",
            "ipad",
            "macbook",
            "imac",
            "mac mini",
            "mac studio",
            "apple watch",
            "homepod",
            "apple tv",
        )

    private val APPLE_LABEL_TYPE_RULES =
        listOf(
            AppleLabelTypeRule(
                keywords = listOf("airpods", "airpod", "beats", "powerbeats"),
                deviceType = DeviceType.HEADPHONES,
            ),
            AppleLabelTypeRule(
                keywords = listOf("iphone"),
                deviceType = DeviceType.PHONE,
            ),
            AppleLabelTypeRule(
                keywords = listOf("ipad"),
                deviceType = DeviceType.TABLET,
            ),
            AppleLabelTypeRule(
                keywords = listOf("macbook", "mac desktop", "mac mini", "mac studio", "imac"),
                deviceType = DeviceType.LAPTOP,
            ),
            AppleLabelTypeRule(
                keywords = listOf("apple watch"),
                deviceType = DeviceType.WEARABLE,
            ),
            AppleLabelTypeRule(
                keywords = listOf("homepod", "apple tv"),
                deviceType = DeviceType.AUDIO_VIDEO,
            ),
            AppleLabelTypeRule(
                keywords = listOf("airtag", "find my", "findmy"),
                deviceType = DeviceType.TRACKER,
            ),
        )

    private data class AppleLabelTypeRule(
        val keywords: List<String>,
        val deviceType: DeviceType,
    )
}
