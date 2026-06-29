package io.blueeye.feature.details

import io.blueeye.core.domain.evidence.DetectionEvidenceClassifier
import io.blueeye.core.model.AlertEvidenceEvent
import io.blueeye.core.model.AlertEvidenceEventType
import io.blueeye.core.model.DetectionConfidence

object DetailsAlertHistoryFormatter {
    fun format(
        events: List<AlertEvidenceEvent>,
        timestampFormatter: (Long) -> String = DetailsUiFormatter::formatFriendlyTimestamp,
    ): DetailsAlertHistoryUiInfo? {
        if (events.isEmpty()) return null

        val sortedEvents = events.sortedByDescending { it.timestamp }
        val latest = sortedEvents.first()
        val strongest =
            sortedEvents.maxBy { event ->
                DetectionEvidenceClassifier.confidencePriority(event.evidence.confidence)
            }

        return DetailsAlertHistoryUiInfo(
            eventCountText = eventCountText(events.size),
            latestText = "${eventTypeText(latest.eventType)} at ${timestampFormatter(latest.timestamp)}",
            strongestText = "${confidenceText(strongest.evidence.confidence)} ${eventTypeText(strongest.eventType)}",
            strongestConfidence = strongest.evidence.confidence,
            recentItems = sortedEvents.take(RECENT_ITEM_COUNT),
        )
    }

    private fun eventCountText(eventCount: Int): String =
        if (eventCount == 1) {
            "1 alert evidence event"
        } else {
            "$eventCount alert evidence events"
        }

    fun eventTypeText(type: AlertEvidenceEventType): String =
        when (type) {
            AlertEvidenceEventType.WATCHLIST_RETURN -> "watchlist return"
            AlertEvidenceEventType.PUBLIC_SAFETY_SIGNAL -> "public-safety-like signal"
            AlertEvidenceEventType.FOLLOW_ME_ALERT -> "follow-me alert"
        }

    fun confidenceText(confidence: DetectionConfidence): String =
        when (confidence) {
            DetectionConfidence.LOW -> "Low confidence"
            DetectionConfidence.MEDIUM -> "Medium confidence"
            DetectionConfidence.HIGH -> "High confidence"
            DetectionConfidence.CRITICAL -> "Alert"
        }

    private const val RECENT_ITEM_COUNT = 4
}

data class DetailsAlertHistoryUiInfo(
    val eventCountText: String,
    val latestText: String,
    val strongestText: String,
    val strongestConfidence: DetectionConfidence,
    val recentItems: List<AlertEvidenceEvent>,
)
