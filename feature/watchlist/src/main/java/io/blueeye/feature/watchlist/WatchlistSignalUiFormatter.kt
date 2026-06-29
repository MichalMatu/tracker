package io.blueeye.feature.watchlist

import io.blueeye.core.model.DetectionConfidence
import io.blueeye.core.model.PublicSafetySignal

object WatchlistSignalUiFormatter {
    const val CONTEXT_TEXT = "Public-safety-like signals are evidence hints, not confirmed presence."

    fun countText(detectedCount: Int): String = "$detectedCount active evidence hints"

    fun map(
        detections: List<PublicSafetySignal>,
        now: Long,
    ): List<WatchlistSignalUiInfo> =
        detections
            .sortedByDescending { it.lastSeenAt }
            .take(MAX_VISIBLE_SIGNALS)
            .map { detection -> detection.toUiInfo(now) }

    private fun PublicSafetySignal.toUiInfo(now: Long): WatchlistSignalUiInfo =
        WatchlistSignalUiInfo(
            title = "Signal consistent with ${category.toCategoryLabel()}",
            confidenceText = confidence.toConfidenceLabel(),
            detailText = "Evidence: ${evidence.firstOrNull()?.reasonText ?: "No evidence details available"}",
            signalText = "$rssi dBm - seen ${lastSeenAt.formatElapsed(now)}",
        )

    private fun DetectionConfidence.toConfidenceLabel(): String =
        when (this) {
            DetectionConfidence.LOW -> "Low confidence"
            DetectionConfidence.MEDIUM -> "Medium confidence"
            DetectionConfidence.HIGH,
            DetectionConfidence.CRITICAL,
            -> "High confidence"
        }

    private fun String.toCategoryLabel(): String =
        when (uppercase()) {
            "BODY_CAMERA" -> "body-camera-like signal"
            "HOLSTER_SENSOR" -> "holster-sensor-like signal"
            "TACTICAL_AUDIO" -> "professional audio signal"
            "TACTICAL_RADIO" -> "professional radio signal"
            "SMART_WEAPON" -> "smart equipment signal"
            "TACTICAL_EUD" -> "professional terminal signal"
            "FIRE_EMS" -> "emergency medical signal"
            "POLICE_EQUIPMENT" -> "public-safety-like signal"
            "VEHICLE_ROUTER" -> "vehicle router signal"
            "DOCUMENT_READER" -> "document reader signal"
            "FIREFIGHTER" -> "fire telemetry signal"
            else -> lowercase().replace('_', ' ')
        }

    private fun Long.formatElapsed(now: Long): String {
        val diff = now - this
        return when {
            diff < ONE_SECOND_MS -> "now"
            diff < ONE_MINUTE_MS -> "${diff / ONE_SECOND_MS}s ago"
            diff < ONE_HOUR_MS -> "${diff / ONE_MINUTE_MS}m ago"
            else -> ">1h ago"
        }
    }

    private const val MAX_VISIBLE_SIGNALS = 3
    private const val ONE_SECOND_MS = 1_000L
    private const val ONE_MINUTE_MS = 60_000L
    private const val ONE_HOUR_MS = 3_600_000L
}

data class WatchlistSignalUiInfo(
    val title: String,
    val confidenceText: String,
    val detailText: String,
    val signalText: String,
)
