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
import io.blueeye.core.model.FollowMeHistorySample
import io.blueeye.core.model.TrackingStatus
import io.blueeye.core.ui.theme.BlueEyeTheme
import io.blueeye.core.ui.theme.Dimens
import io.blueeye.core.ui.theme.extendedColors

@Composable
fun DetailsFollowMeHistoryCard(
    samples: List<FollowMeHistorySample>,
    modifier: Modifier = Modifier,
) {
    val historyInfo = remember(samples) { DetailsFollowMeHistoryFormatter.format(samples) }
    if (historyInfo == null) return

    Card(modifier = modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(Dimens.PaddingMedium),
            verticalArrangement = Arrangement.spacedBy(Dimens.PaddingSmall),
        ) {
            Text(
                text = "Follow-Me History",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.primary,
            )
            Text(
                text = historyInfo.windowText,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = FollowMeHistoryLayout.WINDOW_TEXT_MAX_LINES,
                overflow = TextOverflow.Ellipsis,
            )
            HorizontalDivider()
            FollowMeHistoryMetric(label = "Snapshots", value = historyInfo.sampleCountText)
            FollowMeHistoryMetric(label = "Latest", value = historyInfo.latestScoreText)
            FollowMeHistoryMetric(label = "Peak", value = historyInfo.peakScoreText)
            FollowMeHistoryMetric(
                label = "Status",
                value = historyInfo.latestStatusText,
                valueColor = historyInfo.latestStatus.resolveColor(),
            )
            FollowMeHistoryMetric(label = "Movement", value = historyInfo.movementText)
            FollowMeHistoryMetric(label = "Components", value = historyInfo.componentText)
            FollowMeHistoryMetric(label = "Reason", value = historyInfo.latestExplanation)
            HorizontalDivider()
            historyInfo.recentItems.forEach { sample ->
                FollowMeHistoryRow(sample = sample)
            }
        }
    }
}

@Composable
private fun FollowMeHistoryMetric(
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
            modifier = Modifier.weight(FollowMeHistoryLayout.LABEL_WEIGHT),
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodySmall,
            color = valueColor,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.weight(FollowMeHistoryLayout.VALUE_WEIGHT),
            maxLines = FollowMeHistoryLayout.VALUE_TEXT_MAX_LINES,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun FollowMeHistoryRow(sample: FollowMeHistorySample) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(Dimens.PaddingMedium),
        verticalAlignment = Alignment.Top,
    ) {
        Text(
            text = DetailsUiFormatter.formatFriendlyTimestamp(sample.timestamp),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(FollowMeHistoryLayout.LABEL_WEIGHT),
            maxLines = FollowMeHistoryLayout.VALUE_TEXT_MAX_LINES,
            overflow = TextOverflow.Ellipsis,
        )
        Text(
            text = "${sample.score.toInt()}/100, ${sample.rssi} dBm, ${sample.trackingStatus.rowStatusText()}",
            style = MaterialTheme.typography.bodySmall,
            color = sample.trackingStatus.resolveColor(),
            modifier = Modifier.weight(FollowMeHistoryLayout.VALUE_WEIGHT),
            maxLines = FollowMeHistoryLayout.VALUE_TEXT_MAX_LINES,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun TrackingStatus.resolveColor(): Color =
    when (this) {
        TrackingStatus.SAFE -> MaterialTheme.extendedColors.safe
        TrackingStatus.SUSPICIOUS -> MaterialTheme.extendedColors.suspicious
        TrackingStatus.DANGEROUS -> MaterialTheme.extendedColors.suspicious
    }

private fun TrackingStatus.rowStatusText(): String =
    when (this) {
        TrackingStatus.SAFE -> "safe"
        TrackingStatus.SUSPICIOUS -> "suspicious"
        TrackingStatus.DANGEROUS -> "high attention"
    }

private object FollowMeHistoryLayout {
    const val LABEL_WEIGHT = 0.32f
    const val VALUE_WEIGHT = 0.68f
    const val WINDOW_TEXT_MAX_LINES = 2
    const val VALUE_TEXT_MAX_LINES = 2
}

@androidx.compose.ui.tooling.preview.Preview(showBackground = true)
@Composable
fun DetailsFollowMeHistoryCardPreview() {
    BlueEyeTheme {
        DetailsFollowMeHistoryCard(
            samples =
                listOf(
                    FollowMeHistorySample(
                        timestamp = 1_789_000_000_000L,
                        observedMac = "AA:BB:CC:DD:EE:FF",
                        trackingStatus = TrackingStatus.SAFE,
                        score = 12f,
                        explanation = "Movement not observed.",
                        rssi = -72,
                        encounterCount = 2,
                        durationScore = 0,
                        rssiStabilityScore = 0,
                        deviceTypeScore = 0,
                        macBehaviorScore = 0,
                        encounterScore = 2,
                        userMoved = false,
                        baselineDevice = true,
                    ),
                    FollowMeHistorySample(
                        timestamp = 1_789_000_060_000L,
                        observedMac = "AA:BB:CC:DD:EE:FF",
                        trackingStatus = TrackingStatus.SUSPICIOUS,
                        score = 58f,
                        explanation = "Seen while moving with stable RSSI.",
                        rssi = -61,
                        encounterCount = 9,
                        durationScore = 25,
                        rssiStabilityScore = 18,
                        deviceTypeScore = 0,
                        macBehaviorScore = 0,
                        encounterScore = 4,
                        userMoved = true,
                        baselineDevice = false,
                    ),
                ),
            modifier = Modifier.padding(Dimens.PaddingMedium),
        )
    }
}
