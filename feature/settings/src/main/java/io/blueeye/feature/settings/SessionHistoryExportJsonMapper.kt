package io.blueeye.feature.settings

import io.blueeye.core.model.AlertEvidenceEvent
import io.blueeye.core.model.AlertEvidenceEventType
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonObjectBuilder
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

internal object SessionHistoryExportJsonMapper {
    fun eventTypeCounts(events: List<AlertEvidenceEvent>): JsonObject =
        buildJsonObject {
            AlertEvidenceEventType.entries.forEach { type ->
                put(type.name, events.count { event -> event.eventType == type })
            }
        }

    fun mapFollowMeObservation(observation: SessionFollowMeObservation): JsonObject =
        buildJsonObject {
            val sample = observation.observation
            put("deviceFingerprint", observation.deviceFingerprint)
            put("timestamp", sample.timestamp)
            put("observedMac", sample.observedMac)
            put("trackingStatus", sample.trackingStatus.name)
            put("score", sample.score)
            put("explanation", sample.explanation)
            put("rssi", sample.rssi)
            put("encounterCount", sample.encounterCount)
            put("durationScore", sample.durationScore)
            put("rssiStabilityScore", sample.rssiStabilityScore)
            put("deviceTypeScore", sample.deviceTypeScore)
            put("macBehaviorScore", sample.macBehaviorScore)
            put("encounterScore", sample.encounterScore)
            putNullableBoolean("userMoved", sample.userMoved)
            putNullableBoolean("baselineDevice", sample.baselineDevice)
        }

    fun mapAlertEvidenceEvent(event: AlertEvidenceEvent): JsonObject =
        buildJsonObject {
            put("deviceFingerprint", event.deviceFingerprint)
            put("timestamp", event.timestamp)
            put("observedMac", event.observedMac)
            put("eventType", event.eventType.name)
            put("evidence", DatabaseExportJsonMapper.mapEvidence(event.evidence))
        }
}

private fun JsonObjectBuilder.putNullableBoolean(
    key: String,
    value: Boolean?,
) {
    if (value == null) {
        put(key, JsonNull)
    } else {
        put(key, value)
    }
}
