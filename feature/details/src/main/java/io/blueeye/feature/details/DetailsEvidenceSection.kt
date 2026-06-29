package io.blueeye.feature.details

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import io.blueeye.core.model.DetectionConfidence
import io.blueeye.core.model.DetectionEvidence
import io.blueeye.core.model.EvidenceSource
import io.blueeye.core.ui.theme.BlueEyeTheme
import io.blueeye.core.ui.theme.Dimens
import io.blueeye.core.ui.theme.extendedColors

@Composable
fun DetailsEvidenceSection(
    evidence: List<DetectionEvidence>,
    modifier: Modifier = Modifier,
) {
    val evidenceItems = remember(evidence) { DetailsEvidenceUiFormatter.format(evidence) }
    val emptyState = remember { DetailsEvidenceUiFormatter.emptyState() }

    Card(modifier = modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(Dimens.PaddingMedium)) {
            Text(
                text = "Evidence",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.primary,
            )
            HorizontalDivider(modifier = Modifier.padding(vertical = Dimens.PaddingSmall))
            if (evidenceItems.isEmpty()) {
                DetailsEvidenceEmptyStateContent(emptyState)
            } else {
                Column(verticalArrangement = Arrangement.spacedBy(Dimens.PaddingMedium)) {
                    evidenceItems.forEach { item ->
                        DetailsEvidenceItem(item)
                    }
                }
            }
        }
    }
}

@Composable
fun DetailsEvidenceEmptyStateContent(emptyState: DetailsEvidenceEmptyState) {
    Column(verticalArrangement = Arrangement.spacedBy(Dimens.PaddingSmall)) {
        Text(
            text = emptyState.title,
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurface,
        )
        Text(
            text = emptyState.modeText,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.outline,
        )
        Text(
            text = emptyState.detail,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
fun DetailsEvidenceItem(item: DetailsEvidenceUiItem) {
    Column(verticalArrangement = Arrangement.spacedBy(Dimens.PaddingSmall)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.Top,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = item.sourceText,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    text = item.modeText,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            DetailsEvidenceConfidencePill(item)
        }

        Text(
            text = item.reasonText,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface,
        )

        DetailsEvidenceValueRow(label = "Observed", value = item.observedAtText)
        DetailsEvidenceValueRow(label = "Parsed", value = item.parsedValueText)
        DetailsEvidenceValueRow(label = "Raw", value = item.rawValueText)
    }
}

@Composable
fun DetailsEvidenceConfidencePill(item: DetailsEvidenceUiItem) {
    val color = item.confidence.resolveEvidenceColor()
    Text(
        text = item.confidenceText,
        style = MaterialTheme.typography.labelSmall,
        color = color,
        modifier =
            Modifier
                .background(color.copy(alpha = 0.1f), RoundedCornerShape(Dimens.PaddingExtraSmall))
                .padding(horizontal = Dimens.PaddingSmall, vertical = Dimens.PaddingExtraSmall),
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
    )
}

@Composable
fun DetailsEvidenceValueRow(
    label: String,
    value: String,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(Dimens.PaddingMedium),
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(EvidenceLayout.LABEL_WEIGHT),
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.weight(EvidenceLayout.VALUE_WEIGHT),
            maxLines = EvidenceLayout.TECHNICAL_VALUE_MAX_LINES,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun DetectionConfidence.resolveEvidenceColor(): Color =
    when (this) {
        DetectionConfidence.LOW -> MaterialTheme.colorScheme.outline
        DetectionConfidence.MEDIUM -> MaterialTheme.extendedColors.warning
        DetectionConfidence.HIGH -> MaterialTheme.extendedColors.suspicious
        DetectionConfidence.CRITICAL -> MaterialTheme.extendedColors.suspicious
    }

private object EvidenceLayout {
    const val LABEL_WEIGHT = 0.32f
    const val VALUE_WEIGHT = 0.68f
    const val TECHNICAL_VALUE_MAX_LINES = 4
}

@androidx.compose.ui.tooling.preview.Preview(showBackground = true)
@Composable
fun DetailsEvidenceSectionEmptyPreview() {
    BlueEyeTheme {
        DetailsEvidenceSection(
            evidence = emptyList(),
            modifier = Modifier.padding(Dimens.PaddingMedium),
        )
    }
}

@androidx.compose.ui.tooling.preview.Preview(showBackground = true)
@Composable
fun DetailsEvidenceSectionPreview() {
    BlueEyeTheme {
        DetailsEvidenceSection(
            evidence =
                listOf(
                    DetectionEvidence(
                        source = EvidenceSource.SERVICE_UUID,
                        confidence = DetectionConfidence.HIGH,
                        reasonText = "Service UUID is consistent with Axon Body Camera.",
                        timestamp = 1_789_000_000_000L,
                        rawValue = "0000fd8e-0000-1000-8000-00805f9b34fb",
                        parsedValue = "BODY_CAMERA",
                        isPassive = true,
                    ),
                    DetectionEvidence(
                        source = EvidenceSource.GATT_PROBE,
                        confidence = DetectionConfidence.MEDIUM,
                        reasonText = "Active GATT probe returned device information.",
                        timestamp = 1_789_000_000_000L,
                        rawValue = "PROBED",
                        parsedValue = "Axon Body 3 85%",
                        isPassive = false,
                    ),
                ),
            modifier = Modifier.padding(Dimens.PaddingMedium),
        )
    }
}
