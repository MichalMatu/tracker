package io.blueeye.feature.settings

import io.blueeye.core.model.DeviceCalibrationLabel
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

data class SessionReviewReadiness(
    val readyForHeuristicReview: Boolean = false,
    val hasUserVerdict: Boolean = false,
    val hasNotes: Boolean = false,
    val hasDevices: Boolean = false,
    val hasSamples: Boolean = false,
    val hasAttentionEvidence: Boolean = false,
    val hasActiveProbeData: Boolean = false,
    val blockers: List<SessionReviewReadinessItem> = emptyList(),
    val warnings: List<SessionReviewReadinessItem> = emptyList(),
)

enum class SessionReviewReadinessItem {
    SESSION_LABEL,
    SESSION_DEVICES,
    RSSI_SAMPLES,
    SESSION_NOTES,
    ATTENTION_EVIDENCE,
    ACTIVE_PROBE_DATA,
}

data class SessionReviewActiveCollection(
    val enabled: Boolean = false,
    val dataDeviceCount: Int = 0,
)

data class SessionReviewReadinessInput(
    val label: DeviceCalibrationLabel,
    val notes: String,
    val deviceCount: Int,
    val sampleCount: Int,
    val attentionEvidenceCount: Int,
    val activeCollection: SessionReviewActiveCollection = SessionReviewActiveCollection(),
)

internal object SessionReviewReadinessCalculator {
    fun calculate(input: SessionReviewReadinessInput): SessionReviewReadiness {
        val hasUserVerdict = input.label != DeviceCalibrationLabel.UNKNOWN
        val hasDevices = input.deviceCount > 0
        val hasSamples = input.sampleCount > 0
        val hasNotes = input.notes.isNotBlank()
        val hasAttentionEvidence = input.attentionEvidenceCount > 0
        val hasActiveProbeData = input.activeCollection.dataDeviceCount > 0
        val benefitsFromAttentionEvidence = input.label != DeviceCalibrationLabel.KNOWN_SAFE
        val blockers =
            listOfNotNull(
                SessionReviewReadinessItem.SESSION_LABEL.takeUnless { hasUserVerdict },
                SessionReviewReadinessItem.SESSION_DEVICES.takeUnless { hasDevices },
                SessionReviewReadinessItem.RSSI_SAMPLES.takeUnless { hasSamples },
            )
        val warnings =
            listOfNotNull(
                SessionReviewReadinessItem.SESSION_NOTES.takeUnless { hasNotes },
                SessionReviewReadinessItem.ATTENTION_EVIDENCE.takeIf {
                    benefitsFromAttentionEvidence && !hasAttentionEvidence
                },
                SessionReviewReadinessItem.ACTIVE_PROBE_DATA.takeIf {
                    input.activeCollection.enabled && !hasActiveProbeData
                },
            )

        return SessionReviewReadiness(
            readyForHeuristicReview = blockers.isEmpty(),
            hasUserVerdict = hasUserVerdict,
            hasNotes = hasNotes,
            hasDevices = hasDevices,
            hasSamples = hasSamples,
            hasAttentionEvidence = hasAttentionEvidence,
            hasActiveProbeData = hasActiveProbeData,
            blockers = blockers,
            warnings = warnings,
        )
    }
}

internal fun SessionReviewReadiness.toJson(): JsonObject =
    buildJsonObject {
        put("readyForHeuristicReview", readyForHeuristicReview)
        put("hasUserVerdict", hasUserVerdict)
        put("hasNotes", hasNotes)
        put("hasDevices", hasDevices)
        put("hasSamples", hasSamples)
        put("hasAttentionEvidence", hasAttentionEvidence)
        put("hasActiveProbeData", hasActiveProbeData)
        put("blockers", JsonArray(blockers.map { item -> JsonPrimitive(item.name) }))
        put("warnings", JsonArray(warnings.map { item -> JsonPrimitive(item.name) }))
    }
