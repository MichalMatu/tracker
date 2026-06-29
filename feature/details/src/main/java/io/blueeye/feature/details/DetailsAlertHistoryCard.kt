package io.blueeye.feature.details

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import io.blueeye.core.model.AlertEvidenceEvent
import io.blueeye.core.model.DetectionConfidence
import io.blueeye.core.ui.theme.BlueEyeTheme
import io.blueeye.core.ui.theme.Dimens
import io.blueeye.core.ui.theme.extendedColors

@Composable
fun DetailsAlertHistoryCard(
    events: List<AlertEvidenceEvent>,
    modifier: Modifier = Modifier,
) {
    val historyInfo = remember(events) { DetailsAlertHistoryFormatter.format(events) }
    if (historyInfo == null) return

    Card(modifier = modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(Dimens.PaddingMedium),
            verticalArrangement = Arrangement.spacedBy(Dimens.PaddingSmall),
        ) {
            Text(
                text = "Alert Evidence History",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.primary,
            )
            DetailsAlertHistoryMetric(label = "Events", value = historyInfo.eventCountText)
            DetailsAlertHistoryMetric(label = "Latest", value = historyInfo.latestText)
            DetailsAlertHistoryMetric(
                label = "Strongest",
                value = historyInfo.strongestText,
                valueColor = historyInfo.strongestConfidence.resolveColor(),
            )
            HorizontalDivider()
            historyInfo.recentItems.forEach { event ->
                DetailsAlertHistoryRow(event = event)
            }
        }
    }
}

@Composable
private fun DetailsAlertHistoryMetric(
    label: String,
    value: String,
    valueColor: Color = MaterialTheme.colorScheme.onSurface,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(Dimens.PaddingMedium),
        verticalAlignment = Alignment.Top,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(AlertHistoryLayout.LABEL_WEIGHT),
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodySmall,
            color = valueColor,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.weight(AlertHistoryLayout.VALUE_WEIGHT),
            maxLines = AlertHistoryLayout.VALUE_TEXT_MAX_LINES,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun DetailsAlertHistoryRow(event: AlertEvidenceEvent) {
    Column(verticalArrangement = Arrangement.spacedBy(Dimens.PaddingExtraSmall)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(Dimens.PaddingMedium),
            verticalAlignment = Alignment.Top,
        ) {
            Text(
                text = DetailsUiFormatter.formatFriendlyTimestamp(event.timestamp),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.weight(AlertHistoryLayout.LABEL_WEIGHT),
                maxLines = AlertHistoryLayout.VALUE_TEXT_MAX_LINES,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text =
                    "${DetailsAlertHistoryFormatter.eventTypeText(event.eventType)} / " +
                        DetailsAlertHistoryFormatter.confidenceText(event.evidence.confidence),
                style = MaterialTheme.typography.bodySmall,
                color = event.evidence.confidence.resolveColor(),
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.weight(AlertHistoryLayout.VALUE_WEIGHT),
                maxLines = AlertHistoryLayout.VALUE_TEXT_MAX_LINES,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Text(
            text = event.evidence.reasonText,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = AlertHistoryLayout.REASON_TEXT_MAX_LINES,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun DetectionConfidence.resolveColor(): Color =
    when (this) {
        DetectionConfidence.LOW -> MaterialTheme.colorScheme.onSurfaceVariant
        DetectionConfidence.MEDIUM -> MaterialTheme.extendedColors.suspicious
        DetectionConfidence.HIGH -> MaterialTheme.extendedColors.suspicious
        DetectionConfidence.CRITICAL -> MaterialTheme.extendedColors.suspicious
    }

private object AlertHistoryLayout {
    const val LABEL_WEIGHT = 0.32f
    const val VALUE_WEIGHT = 0.68f
    const val VALUE_TEXT_MAX_LINES = 2
    const val REASON_TEXT_MAX_LINES = 3
}

@androidx.compose.ui.tooling.preview.Preview(showBackground = true)
@Composable
fun DetailsAlertHistoryCardPreview() {
    BlueEyeTheme {
        DetailsAlertHistoryCard(
            events =
                listOf(
                    AlertEvidenceEvent(
                        timestamp = 1_789_000_000_000L,
                        deviceFingerprint = "AA:BB:CC:DD:EE:FF",
                        observedMac = "AA:BB:CC:DD:EE:FF",
                        eventType = io.blueeye.core.model.AlertEvidenceEventType.WATCHLIST_RETURN,
                        evidence =
                            io.blueeye.core.model.DetectionEvidence(
                                source = io.blueeye.core.model.EvidenceSource.WATCHLIST,
                                confidence = DetectionConfidence.CRITICAL,
                                reasonText = "Watchlist device returned after 90s offline.",
                                timestamp = 1_789_000_000_000L,
                                rawValue = "AA:BB:CC:DD:EE:FF",
                                parsedValue = "Keys",
                                isPassive = true,
                            ),
                    ),
                ),
            modifier = Modifier.padding(Dimens.PaddingMedium),
        )
    }
}
