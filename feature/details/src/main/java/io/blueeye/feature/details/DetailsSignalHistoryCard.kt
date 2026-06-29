package io.blueeye.feature.details

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import io.blueeye.core.model.SignalSample
import io.blueeye.core.ui.theme.BlueEyeTheme
import io.blueeye.core.ui.theme.Dimens
import io.blueeye.core.ui.theme.extendedColors

@Composable
fun DetailsSignalHistoryCard(
    samples: List<SignalSample>,
    modifier: Modifier = Modifier,
) {
    val sortedSamples = remember(samples) { samples.sortedBy { it.timestamp } }
    val historyInfo = remember(sortedSamples) { DetailsSignalHistoryFormatter.format(sortedSamples) }
    if (historyInfo == null) return

    Card(modifier = modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(Dimens.PaddingMedium),
            verticalArrangement = Arrangement.spacedBy(Dimens.PaddingSmall),
        ) {
            Text(
                text = "Signal History",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.primary,
            )
            Text(
                text = historyInfo.windowText,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = SignalHistoryLayout.WINDOW_TEXT_MAX_LINES,
                overflow = TextOverflow.Ellipsis,
            )
            HorizontalDivider()
            SignalHistoryMetrics(historyInfo)
            SignalHistoryChart(samples = sortedSamples)
        }
    }
}

@Composable
private fun SignalHistoryMetrics(historyInfo: DetailsSignalHistoryUiInfo) {
    SignalHistoryMetric(label = "Samples", value = historyInfo.sampleCountText)
    SignalHistoryMetric(label = "Latest", value = historyInfo.latestRssiText)
    SignalHistoryMetric(label = "Average", value = historyInfo.averageRssiText)
    SignalHistoryMetric(label = "Range", value = historyInfo.rangeText)
    SignalHistoryMetric(
        label = "Trend",
        value = historyInfo.trendText,
        valueColor = historyInfo.trendTone.resolveColor(),
    )
}

@Composable
private fun SignalHistoryMetric(
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
            modifier = Modifier.weight(SignalHistoryLayout.LABEL_WEIGHT),
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodySmall,
            color = valueColor,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.weight(SignalHistoryLayout.VALUE_WEIGHT),
            maxLines = SignalHistoryLayout.VALUE_TEXT_MAX_LINES,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun SignalHistoryChart(samples: List<SignalSample>) {
    Box(
        modifier =
            Modifier
                .fillMaxWidth()
                .height(Dimens.PaddingExtraLarge * SignalHistoryLayout.CHART_HEIGHT_MULTIPLIER)
                .background(
                    color =
                        MaterialTheme.colorScheme.surfaceVariant.copy(
                            alpha = SignalHistoryLayout.CHART_BACKGROUND_ALPHA,
                        ),
                    shape = RoundedCornerShape(Dimens.PaddingSmall),
                ),
    ) {
        if (samples.size > SignalHistoryLayout.MIN_CHART_SAMPLES) {
            SignalHistoryLine(samples = samples)
        } else {
            Text(
                text = "Not enough data",
                modifier = Modifier.align(Alignment.Center),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun SignalHistoryLine(samples: List<SignalSample>) {
    val lineColor = MaterialTheme.colorScheme.primary
    Canvas(
        modifier =
            Modifier
                .fillMaxSize()
                .padding(Dimens.PaddingSmall),
    ) {
        val range = SignalHistoryLayout.MAX_RSSI - SignalHistoryLayout.MIN_RSSI
        val path = Path()
        val stepX = size.width / (samples.size - 1)

        samples.forEachIndexed { index, sample ->
            val x = index * stepX
            val clampedRssi =
                sample.rssi.toFloat().coerceIn(
                    SignalHistoryLayout.MIN_RSSI,
                    SignalHistoryLayout.MAX_RSSI,
                )
            val normalizedRssi = (clampedRssi - SignalHistoryLayout.MIN_RSSI) / range
            val y = size.height - (normalizedRssi * size.height)

            if (index == 0) {
                path.moveTo(x, y)
            } else {
                path.lineTo(x, y)
            }
        }

        drawPath(
            path = path,
            color = lineColor,
            style = Stroke(width = Dimens.PaddingExtraSmall.toPx()),
        )
    }
}

@Composable
private fun DetailsSignalTrendTone.resolveColor(): Color =
    when (this) {
        DetailsSignalTrendTone.STRENGTHENING -> MaterialTheme.extendedColors.warning
        DetailsSignalTrendTone.FADING -> MaterialTheme.extendedColors.safe
        DetailsSignalTrendTone.STABLE -> MaterialTheme.colorScheme.onSurfaceVariant
        DetailsSignalTrendTone.INSUFFICIENT -> MaterialTheme.colorScheme.outline
    }

private object SignalHistoryLayout {
    const val LABEL_WEIGHT = 0.32f
    const val VALUE_WEIGHT = 0.68f
    const val WINDOW_TEXT_MAX_LINES = 2
    const val VALUE_TEXT_MAX_LINES = 2
    const val MIN_CHART_SAMPLES = 1
    const val CHART_HEIGHT_MULTIPLIER = 3
    const val MIN_RSSI = -100f
    const val MAX_RSSI = -30f
    const val CHART_BACKGROUND_ALPHA = 0.35f
}

@androidx.compose.ui.tooling.preview.Preview(showBackground = true)
@Composable
fun DetailsSignalHistoryCardPreview() {
    BlueEyeTheme {
        DetailsSignalHistoryCard(
            samples =
                listOf(
                    SignalSample(timestamp = 1_789_000_000_000L, rssi = -72),
                    SignalSample(timestamp = 1_789_000_030_000L, rssi = -64),
                    SignalSample(timestamp = 1_789_000_060_000L, rssi = -58),
                ),
            modifier = Modifier.padding(Dimens.PaddingMedium),
        )
    }
}
