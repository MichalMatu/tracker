package io.blueeye.feature.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import io.blueeye.core.model.DeviceCalibrationLabel
import io.blueeye.core.ui.theme.BlueEyeTheme
import io.blueeye.core.ui.theme.Dimens
import io.blueeye.core.ui.theme.extendedColors
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
internal fun SessionCalibrationCard(
    state: SessionCalibrationCardState,
    actions: SessionCalibrationCardActions,
) {
    SessionCalibrationCardContent(
        state = state,
        actions = actions,
    )
}

internal data class SessionCalibrationCardState(
    val label: DeviceCalibrationLabel,
    val startedAt: Long,
    val notes: String,
    val stats: SessionStats,
    val reviewReadiness: SessionReviewReadiness,
)

internal data class SessionCalibrationCardActions(
    val onSelectLabel: (DeviceCalibrationLabel) -> Unit,
    val onNotesChange: (String) -> Unit,
    val onStartNewSession: () -> Unit,
    val onReviewDeviceClick: (String) -> Unit,
    val onReviewDeviceAction: (String, SessionReviewQueueAction) -> Unit,
)

@Composable
private fun SessionCalibrationCardContent(
    state: SessionCalibrationCardState,
    actions: SessionCalibrationCardActions,
) {
    val info = SessionCalibrationUiFormatter.format(state.label)
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
    ) {
        Column(
            modifier = Modifier.padding(Dimens.PaddingMedium),
            verticalArrangement = Arrangement.spacedBy(Dimens.PaddingSmall),
        ) {
            Text(
                text = "Session Calibration",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.primary,
            )
            Text(
                text = info.statusText,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Bold,
            )
            Text(
                text = formatSessionStartedAt(state.startedAt),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                text = info.description,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            SessionStatsText(
                stats = state.stats,
                onReviewDeviceClick = actions.onReviewDeviceClick,
                onReviewDeviceAction = actions.onReviewDeviceAction,
            )
            SessionReviewReadinessText(readiness = state.reviewReadiness)
            Text(
                text = SessionReviewGuidanceFormatter.format(state.reviewReadiness),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            OutlinedTextField(
                value = state.notes,
                onValueChange = actions.onNotesChange,
                modifier = Modifier.fillMaxWidth(),
                label = { Text("Session notes") },
                minLines = SessionCalibrationLayout.NOTES_MIN_LINES,
                maxLines = SessionCalibrationLayout.NOTES_MAX_LINES,
            )
            SessionCalibrationActionRow(
                actions = info.actions.take(SessionCalibrationLayout.FIRST_ROW_ACTION_COUNT),
                onSelectLabel = actions.onSelectLabel,
            )
            SessionCalibrationActionRow(
                actions =
                    info.actions
                        .drop(SessionCalibrationLayout.FIRST_ROW_ACTION_COUNT)
                        .take(SessionCalibrationLayout.SECOND_ROW_ACTION_COUNT),
                onSelectLabel = actions.onSelectLabel,
            )
            SessionCalibrationActionRow(
                actions = info.actions.drop(SessionCalibrationLayout.LAST_ROW_START_INDEX),
                onSelectLabel = actions.onSelectLabel,
            )
            OutlinedButton(
                onClick = actions.onStartNewSession,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text("Start new session")
            }
        }
    }
}

@Composable
private fun SessionStatsText(
    stats: SessionStats,
    onReviewDeviceClick: (String) -> Unit,
    onReviewDeviceAction: (String, SessionReviewQueueAction) -> Unit,
) {
    Text(
        text = SessionCollectionSummaryFormatter.format(stats),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.primary,
        fontWeight = FontWeight.SemiBold,
    )
    SessionReviewMixFormatter.format(stats.reviewCategoryCounts)
        .takeIf { it.isNotBlank() }
        ?.let { reviewMixText ->
            Text(
                text = reviewMixText,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    SessionReviewNextStepFormatter.format(stats)
        .takeIf { it.isNotBlank() }
        ?.let { nextStepText ->
            Text(
                text = nextStepText,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.SemiBold,
            )
        }
    val reviewQueue = SessionReviewQueueFormatter.format(stats)
    if (reviewQueue.isNotEmpty()) {
        SessionReviewQueue(
            items = reviewQueue,
            onReviewDeviceClick = onReviewDeviceClick,
            onReviewDeviceAction = onReviewDeviceAction,
        )
    }
    SessionRssiQualityFormatter.format(stats.rssiQuality)?.let { info ->
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
    if (stats.hasStarted && stats.gpsSampleCount > 0) {
        Text(
            text = "${stats.gpsSampleCount} samples include GPS context",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun SessionReviewQueue(
    items: List<SessionReviewQueueItem>,
    onReviewDeviceClick: (String) -> Unit,
    onReviewDeviceAction: (String, SessionReviewQueueAction) -> Unit,
) {
    Text(
        text = "Review Queue",
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.primary,
        fontWeight = FontWeight.SemiBold,
    )
    items.forEach { item ->
        Column(
            verticalArrangement = Arrangement.spacedBy(Dimens.PaddingExtraSmall),
        ) {
            Text(
                text = "${item.title}: ${item.actionText}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            item.deviceFingerprint?.let { fingerprint ->
                OutlinedButton(
                    onClick = { onReviewDeviceClick(fingerprint) },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text("Open Details")
                }
                if (item.actions.isNotEmpty()) {
                    FlowRow(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(Dimens.PaddingSmall),
                        verticalArrangement = Arrangement.spacedBy(Dimens.PaddingSmall),
                    ) {
                        item.actions.forEach { action ->
                            OutlinedButton(
                                onClick = { onReviewDeviceAction(fingerprint, action) },
                                modifier = Modifier.widthIn(min = Dimens.ActionButtonMinWidth),
                            ) {
                                Text(action.text)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SessionReviewReadinessText(readiness: SessionReviewReadiness) {
    val info = SessionReviewReadinessUiFormatter.format(readiness)
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

@Composable
private fun SessionCalibrationActionRow(
    actions: List<SessionCalibrationAction>,
    onSelectLabel: (DeviceCalibrationLabel) -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(Dimens.PaddingSmall),
    ) {
        actions.forEach { action ->
            SessionCalibrationActionButton(
                action = action,
                onSelectLabel = onSelectLabel,
                modifier = Modifier.weight(1f),
            )
        }
    }
}

@Composable
private fun SessionCalibrationActionButton(
    action: SessionCalibrationAction,
    onSelectLabel: (DeviceCalibrationLabel) -> Unit,
    modifier: Modifier,
) {
    if (action.isSelected) {
        Button(
            onClick = { onSelectLabel(action.label) },
            enabled = false,
            modifier = modifier,
        ) {
            Text(action.text)
        }
    } else {
        OutlinedButton(
            onClick = { onSelectLabel(action.label) },
            modifier = modifier,
        ) {
            Text(action.text)
        }
    }
}

private fun formatSessionStartedAt(startedAt: Long): String {
    if (startedAt == 0L) return "No session started"
    val text = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()).format(Date(startedAt))
    return "Started $text"
}

@androidx.compose.ui.tooling.preview.Preview(showBackground = true)
@Composable
fun SessionCalibrationCardPreview() {
    BlueEyeTheme {
        SessionCalibrationCard(
            state =
                SessionCalibrationCardState(
                    label = DeviceCalibrationLabel.SUSPICIOUS,
                    startedAt = SessionCalibrationPreviewDefaults.STARTED_AT,
                    notes = "Home baseline near known headphones.",
                    stats =
                        SessionStats(
                            hasStarted = true,
                            deviceCount = 12,
                            sampleCount = 148,
                            gpsSampleCount = 32,
                            evidenceCount = 9,
                            attentionEvidenceCount = 3,
                            durationMs = 48 * 60 * 1000L,
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
                            rssiTrendSummary =
                                SessionRssiTrendSummary(
                                    deviceCount = 12,
                                    strengtheningCount = 1,
                                    stableCount = 8,
                                    insufficientCount = 3,
                                    strongestStrengtheningDeltaRssi = 9.0,
                                ),
                            alertHistorySummary =
                                SessionAlertHistorySummary(
                                    eventCount = 1,
                                    followMeAlertCount = 1,
                                ),
                            identityCarryoverSummary =
                                SessionIdentityCarryoverSummary(
                                    deviceCount = 1,
                                ),
                            reviewDeviceQueue =
                                listOf(
                                    SessionReviewDeviceQueueItem(
                                        fingerprint = "preview-device",
                                        displayName = "Preview headphones",
                                        reasonText = "Follow-Me alert",
                                        actionText = SessionReviewQueueCopy.FOLLOW_ME_ALERT,
                                        decisions =
                                            listOf(
                                                SessionReviewDeviceQueueDecision(
                                                    text = "Mark suspicious",
                                                    deviceCalibrationLabel = DeviceCalibrationLabel.SUSPICIOUS,
                                                ),
                                                SessionReviewDeviceQueueDecision(
                                                    text = "False positive",
                                                    deviceCalibrationLabel = DeviceCalibrationLabel.FALSE_POSITIVE,
                                                ),
                                            ),
                                    ),
                                ),
                        ),
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
                ),
            actions =
                SessionCalibrationCardActions(
                    onSelectLabel = {},
                    onNotesChange = {},
                    onStartNewSession = {},
                    onReviewDeviceClick = {},
                    onReviewDeviceAction = { _, _ -> },
                ),
        )
    }
}

private object SessionCalibrationLayout {
    const val FIRST_ROW_ACTION_COUNT = 2
    const val SECOND_ROW_ACTION_COUNT = 2
    const val LAST_ROW_START_INDEX = 4
    const val NOTES_MIN_LINES = 2
    const val NOTES_MAX_LINES = 4
}

private object SessionCalibrationPreviewDefaults {
    const val STARTED_AT = 1_789_000_000_000L
}
