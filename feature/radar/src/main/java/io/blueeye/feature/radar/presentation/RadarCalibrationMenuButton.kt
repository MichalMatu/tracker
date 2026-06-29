package io.blueeye.feature.radar.presentation

import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.tooling.preview.Preview
import io.blueeye.core.model.DeviceCalibrationLabel
import io.blueeye.core.ui.R
import io.blueeye.core.ui.theme.BlueEyeTheme

@Composable
fun RadarCalibrationMenuButton(
    selectedLabel: DeviceCalibrationLabel,
    onSelectLabel: (DeviceCalibrationLabel) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }

    IconButton(onClick = { expanded = true }) {
        Icon(
            painter = painterResource(R.drawable.ic_edit),
            contentDescription = "Calibrate",
            tint =
                if (selectedLabel == DeviceCalibrationLabel.UNKNOWN) {
                    MaterialTheme.colorScheme.outline
                } else {
                    selectedLabel.colorToken.resolve()
                },
        )
    }

    DropdownMenu(
        expanded = expanded,
        onDismissRequest = { expanded = false },
    ) {
        CalibrationMenuOption.entries.forEach { option ->
            DropdownMenuItem(
                text = { Text(option.title) },
                onClick = {
                    expanded = false
                    onSelectLabel(option.label)
                },
            )
        }
    }
}

private enum class CalibrationMenuOption(
    val label: DeviceCalibrationLabel,
    val title: String,
) {
    TRUE_POSITIVE(DeviceCalibrationLabel.TRUE_POSITIVE, "True positive"),
    FALSE_POSITIVE(DeviceCalibrationLabel.FALSE_POSITIVE, "False positive"),
    KNOWN_SAFE(DeviceCalibrationLabel.KNOWN_SAFE, "Known safe"),
    SUSPICIOUS(DeviceCalibrationLabel.SUSPICIOUS, "Suspicious"),
    UNKNOWN(DeviceCalibrationLabel.UNKNOWN, "Clear verdict"),
}

private val DeviceCalibrationLabel.colorToken: RadarUiColorToken
    get() =
        when (this) {
            DeviceCalibrationLabel.TRUE_POSITIVE -> RadarUiColorToken.SUSPICIOUS
            DeviceCalibrationLabel.FALSE_POSITIVE -> RadarUiColorToken.SAFE
            DeviceCalibrationLabel.KNOWN_SAFE -> RadarUiColorToken.SAFE
            DeviceCalibrationLabel.SUSPICIOUS -> RadarUiColorToken.SUSPICIOUS
            DeviceCalibrationLabel.UNKNOWN -> RadarUiColorToken.OUTLINE
        }

@Preview(showBackground = true)
@Composable
fun RadarCalibrationMenuButtonPreview() {
    BlueEyeTheme {
        RadarCalibrationMenuButton(
            selectedLabel = DeviceCalibrationLabel.SUSPICIOUS,
            onSelectLabel = {},
        )
    }
}
