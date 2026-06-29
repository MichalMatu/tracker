package io.blueeye.core.data.evidence

import io.blueeye.core.data.db.entity.DeviceEntity
import io.blueeye.core.model.DetectionConfidence
import io.blueeye.core.model.DetectionEvidence
import io.blueeye.core.model.EvidenceProvenance
import io.blueeye.core.model.EvidenceSource
import io.blueeye.core.model.IdentityCarryoverVerdict
import io.blueeye.core.model.MacAddressType
import java.text.NumberFormat
import java.util.Locale

internal object IdentityCarryoverEvidenceFactory {
    fun build(device: DeviceEntity): DetectionEvidence? {
        val observedMac = device.observedCarryoverMac() ?: return null
        val confidenceText = device.carryoverConfidence.formatCarryoverConfidence()
        val matcherContext = device.matcherContext(confidenceText)

        return DetectionEvidence(
            source = EvidenceSource.IDENTITY_CARRYOVER,
            confidence = DetectionConfidence.LOW,
            reasonText =
                "Rotating Bluetooth address was correlated with an existing device record. " +
                    matcherContext?.let { "Matcher context: $it. " }.orEmpty() +
                    "Use this as identity continuity context, not as a risk signal.",
            timestamp = device.lastSeenAt,
            rawValue = observedMac,
            parsedValue = device.parsedValue(confidenceText),
            isPassive = true,
            provenance = EvidenceProvenance.FOLLOW_ME_ANALYSIS,
        )
    }

    private fun DeviceEntity.observedCarryoverMac(): String? =
        lastMacAddress
            ?.takeIf { it.isNotBlank() }
            ?.takeIf { it != fingerprint && macAddressType == MacAddressType.RANDOM }

    private fun Float.formatCarryoverConfidence(): String? =
        takeIf { it > 0f }
            ?.coerceIn(0f, 1f)
            ?.let { confidence ->
                NumberFormat
                    .getPercentInstance(Locale.US)
                    .apply { maximumFractionDigits = 0 }
                    .format(confidence)
            }

    private fun DeviceEntity.matcherContext(confidenceText: String?): String? =
        listOfNotNull(
            carryoverReasonText(),
            confidenceText?.let { "confidence=$it" },
            carryoverFeatures?.takeIf { it.isNotBlank() }?.let { "features=$it" },
            carryoverVerdictContexts[identityCarryoverVerdict],
        ).joinToString("; ")
            .takeIf { it.isNotBlank() }

    private fun DeviceEntity.carryoverReasonText(): String? =
        carryoverReasonCode?.let { reasonCode ->
            val label = carryoverReasonLabels[reasonCode] ?: reasonCode.replace('_', ' ').lowercase()
            "match=$label"
        }

    private fun DeviceEntity.parsedValue(confidenceText: String?): String =
        listOfNotNull(
            fingerprint,
            carryoverReasonCode?.takeIf { it.isNotBlank() }?.let { "reason=$it" },
            confidenceText?.let { "confidence=$it" },
            carryoverVerdictValues[identityCarryoverVerdict],
        ).joinToString(";")
}

private val carryoverReasonLabels =
    mapOf(
        "APPLE_SHADOW" to "Apple shadow advertisement",
        "MICROSOFT_SHADOW" to "Microsoft rotating advertisement",
        "SAME_NAME_PROXIMITY" to "same advertised name and close signal",
        "WEIGHTED_FEATURE_MATCH" to "weighted BLE feature match",
    )

private val carryoverVerdictContexts =
    mapOf(
        IdentityCarryoverVerdict.CONFIRMED_SAME_DEVICE to "userVerdict=same device",
        IdentityCarryoverVerdict.FALSE_MATCH to "userVerdict=false match; do not trust merged history",
        IdentityCarryoverVerdict.INCONCLUSIVE to "userVerdict=inconclusive; needs more observations",
    )

private val carryoverVerdictValues =
    mapOf(
        IdentityCarryoverVerdict.CONFIRMED_SAME_DEVICE to "verdict=CONFIRMED_SAME_DEVICE",
        IdentityCarryoverVerdict.FALSE_MATCH to "verdict=FALSE_MATCH",
        IdentityCarryoverVerdict.INCONCLUSIVE to "verdict=INCONCLUSIVE",
    )
