package io.blueeye.feature.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import io.blueeye.core.model.DeviceCalibrationLabel
import io.blueeye.core.ui.theme.BlueEyeTheme
import io.blueeye.core.ui.theme.Dimens
import io.blueeye.core.ui.theme.extendedColors

@Composable
fun DatabaseExportCard(
    reviewReadiness: SessionReviewReadiness,
    reviewCategoryCounts: SessionReviewCategoryCounts,
    rssiQuality: RssiQualityStats,
    onCopyExport: () -> Unit,
    onShareExport: () -> Unit,
) {
    val readinessInfo = SessionReviewReadinessUiFormatter.format(reviewReadiness)
    val reviewMixText = SessionReviewMixFormatter.format(reviewCategoryCounts)
    val rssiQualityInfo = SessionRssiQualityFormatter.format(rssiQuality)
    val guidanceText = SessionReviewGuidanceFormatter.format(reviewReadiness)
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    ) {
        Column(
            modifier = Modifier.padding(Dimens.PaddingMedium),
            verticalArrangement = Arrangement.spacedBy(Dimens.PaddingSmall),
        ) {
            Text(
                text = "Session export",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
            )
            Text(
                text =
                    "Includes session label, review readiness, device summaries, evidence, " +
                        "RSSI samples, and active collection state.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                text =
                    "Sensitive export: may include MAC addresses, GPS samples, serial numbers, " +
                        "and raw Bluetooth payloads. Share only with trusted reviewers.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.extendedColors.warning,
                fontWeight = FontWeight.SemiBold,
            )
            ExportReadinessText(info = readinessInfo)
            Text(
                text = guidanceText,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            reviewMixText.takeIf { it.isNotBlank() }?.let { text ->
                Text(
                    text = text,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            rssiQualityInfo?.let { info ->
                val color =
                    when (info.tone) {
                        SessionRssiQualityTone.NORMAL -> MaterialTheme.colorScheme.onSurfaceVariant
                        SessionRssiQualityTone.WARNING -> MaterialTheme.extendedColors.warning
                    }
                Text(
                    text = info.text,
                    style = MaterialTheme.typography.bodySmall,
                    color = color,
                    fontWeight = if (info.tone == SessionRssiQualityTone.WARNING) FontWeight.SemiBold else null,
                )
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(Dimens.PaddingSmall),
            ) {
                OutlinedButton(
                    onClick = onCopyExport,
                    modifier = Modifier.weight(1f),
                ) {
                    Icon(
                        imageVector = Icons.Default.Edit,
                        contentDescription = null,
                    )
                    Spacer(modifier = Modifier.width(Dimens.PaddingSmall))
                    Text("Copy")
                }
                Button(
                    onClick = onShareExport,
                    modifier = Modifier.weight(1f),
                ) {
                    Icon(
                        imageVector = Icons.Default.Share,
                        contentDescription = null,
                    )
                    Spacer(modifier = Modifier.width(Dimens.PaddingSmall))
                    Text("Share")
                }
            }
        }
    }
}

@Composable
private fun ExportReadinessText(info: SessionReviewReadinessUiInfo) {
    val statusColor =
        when (info.tone) {
            SessionReviewReadinessTone.READY -> MaterialTheme.extendedColors.safe
            SessionReviewReadinessTone.NEEDS_CONTEXT -> MaterialTheme.extendedColors.warning
        }
    Text(
        text = info.statusText,
        style = MaterialTheme.typography.bodySmall,
        color = statusColor,
        fontWeight = FontWeight.SemiBold,
    )
    info.detailText.takeIf { it.isNotBlank() }?.let { detail ->
        Text(
            text = detail,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Preview(showBackground = true)
@Composable
fun DatabaseExportCardPreview() {
    BlueEyeTheme {
        DatabaseExportCard(
            reviewReadiness =
                SessionReviewReadinessCalculator.calculate(
                    SessionReviewReadinessInput(
                        label = DeviceCalibrationLabel.SUSPICIOUS,
                        notes = "Home baseline near known headphones.",
                        deviceCount = 12,
                        sampleCount = 148,
                        attentionEvidenceCount = 3,
                    ),
                ),
            reviewCategoryCounts =
                SessionReviewCategoryCounts(
                    suspicious = 2,
                    publicSafety = 1,
                    nearby = 7,
                    unknownNoise = 2,
                ),
            rssiQuality =
                RssiQualityStats(
                    sampleCount = 148,
                    uniqueRssiCount = 42,
                    dominantRssi = -57,
                    dominantRssiCount = 9,
                    dominantRssiShare = 0.06,
                ),
            onCopyExport = {},
            onShareExport = {},
        )
    }
}
