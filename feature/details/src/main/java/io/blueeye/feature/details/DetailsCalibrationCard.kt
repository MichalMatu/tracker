package io.blueeye.feature.details

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import io.blueeye.core.model.Device
import io.blueeye.core.model.DeviceCalibrationLabel
import io.blueeye.core.model.DeviceType
import io.blueeye.core.model.MacAddressType
import io.blueeye.core.model.TrackingStatus
import io.blueeye.core.ui.theme.BlueEyeTheme
import io.blueeye.core.ui.theme.Dimens

@Composable
fun CalibrationCard(
    device: Device,
    onSelectLabel: (DeviceCalibrationLabel) -> Unit,
) {
    val calibrationInfo = DetailsCalibrationUiFormatter.format(device)
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
    ) {
        Column(
            modifier = Modifier.padding(Dimens.PaddingMedium),
            verticalArrangement = Arrangement.spacedBy(Dimens.PaddingSmall),
        ) {
            Text(
                text = "Calibration",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.primary,
            )
            Text(
                text = calibrationInfo.statusText,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Bold,
            )
            Text(
                text = calibrationInfo.description,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            CalibrationActionRow(
                actions = calibrationInfo.actions.take(CalibrationActionLayout.FIRST_ROW_ACTION_COUNT),
                onSelectLabel = onSelectLabel,
            )
            CalibrationActionRow(
                actions =
                    calibrationInfo.actions
                        .drop(CalibrationActionLayout.FIRST_ROW_ACTION_COUNT)
                        .take(CalibrationActionLayout.SECOND_ROW_ACTION_COUNT),
                onSelectLabel = onSelectLabel,
            )
            CalibrationActionRow(
                actions = calibrationInfo.actions.drop(CalibrationActionLayout.LAST_ROW_START_INDEX),
                onSelectLabel = onSelectLabel,
            )
        }
    }
}

@Composable
private fun CalibrationActionRow(
    actions: List<DetailsCalibrationAction>,
    onSelectLabel: (DeviceCalibrationLabel) -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(Dimens.PaddingSmall),
    ) {
        actions.forEach { action ->
            CalibrationActionButton(
                action = action,
                onSelectLabel = onSelectLabel,
                modifier = Modifier.weight(1f),
            )
        }
    }
}

@Composable
private fun CalibrationActionButton(
    action: DetailsCalibrationAction,
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

@androidx.compose.ui.tooling.preview.Preview(showBackground = true)
@Composable
fun CalibrationCardPreview() {
    BlueEyeTheme {
        CalibrationCard(
            device = previewDevice(),
            onSelectLabel = {},
        )
    }
}

private fun previewDevice(): Device =
    Device(
        fingerprint = "AA:BB:CC:DD:EE:FF",
        macAddress = "AA:BB:CC:DD:EE:FF",
        macAddressType = MacAddressType.PUBLIC,
        technology = "BLE",
        name = "Sample Beacon",
        deviceType = DeviceType.UNKNOWN,
        vendorName = "Unknown Vendor",
        predictedModel = null,
        trackingStatus = TrackingStatus.SAFE,
        followingScore = CalibrationPreviewDefaults.FOLLOWING_SCORE,
        isSafeBeacon = false,
        isInWatchlist = false,
        userAlias = null,
        userNotes = null,
        alertSound = false,
        alertVibration = false,
        isTrackingEnabled = true,
        isIgnoredForTracking = false,
        firstSeenAt = CalibrationPreviewDefaults.TIMESTAMP,
        lastSeenAt = CalibrationPreviewDefaults.TIMESTAMP,
        rssi = CalibrationPreviewDefaults.RSSI,
        encounterCount = CalibrationPreviewDefaults.ENCOUNTER_COUNT,
    )

private object CalibrationPreviewDefaults {
    const val FOLLOWING_SCORE = 0f
    const val TIMESTAMP = 1_789_000_000_000L
    const val RSSI = -55
    const val ENCOUNTER_COUNT = 1
}

private object CalibrationActionLayout {
    const val FIRST_ROW_ACTION_COUNT = 2
    const val SECOND_ROW_ACTION_COUNT = 2
    const val LAST_ROW_START_INDEX = 4
}
