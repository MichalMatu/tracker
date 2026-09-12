from pathlib import Path

screen = Path('feature/details/src/main/java/io/blueeye/feature/details/DetailsScreen.kt')
text = screen.read_text()

old_call = '''                DetailsActionsReviewCard(
                    device = dev,
                    onToggleWatchlist = { viewModel.toggleWatchlist() },
                    onEdit = { showEditDialog.value = true },
                )

                CalibrationCard(
                    device = dev,
                    onSelectLabel = { viewModel.updateCalibrationLabel(it) },
                )
'''
new_call = '''                DetailsActionsReviewCard(
                    device = dev,
                    onToggleWatchlist = { viewModel.toggleWatchlist() },
                    onEdit = { showEditDialog.value = true },
                    onSelectCalibrationLabel = { viewModel.updateCalibrationLabel(it) },
                )
'''
assert text.count(old_call) == 1
text = text.replace(old_call, new_call, 1)

old_signature = '''fun DetailsActionsReviewCard(
    device: Device,
    onToggleWatchlist: () -> Unit,
    onEdit: () -> Unit,
) {
'''
new_signature = '''fun DetailsActionsReviewCard(
    device: Device,
    onToggleWatchlist: () -> Unit,
    onEdit: () -> Unit,
    onSelectCalibrationLabel: (io.blueeye.core.model.DeviceCalibrationLabel) -> Unit,
) {
'''
assert text.count(old_signature) == 1
text = text.replace(old_signature, new_signature, 1)

old_description = '''                text = "Manage Watchlist status, alias, notes and alert preferences for this device.",
'''
new_description = '''                text = "Manage Watchlist status, profile, alert preferences and calibration for this device.",
'''
assert text.count(old_description) == 1
text = text.replace(old_description, new_description, 1)

old_row_end = '''                Button(
                    onClick = onEdit,
                    modifier = Modifier.weight(1f),
                ) {
                    Text("Edit profile & alerts")
                }
            }
        }
    }
}
'''
new_row_end = '''                Button(
                    onClick = onEdit,
                    modifier = Modifier.weight(1f),
                ) {
                    Text("Edit profile & alerts")
                }
            }
            HorizontalDivider()
            CalibrationContent(
                device = device,
                onSelectLabel = onSelectCalibrationLabel,
            )
        }
    }
}
'''
assert text.count(old_row_end) == 1
text = text.replace(old_row_end, new_row_end, 1)
screen.write_text(text)

calibration = Path('feature/details/src/main/java/io/blueeye/feature/details/DetailsCalibrationCard.kt')
text = calibration.read_text()
old_body = '''fun CalibrationCard(
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
'''
new_body = '''fun CalibrationCard(
    device: Device,
    onSelectLabel: (DeviceCalibrationLabel) -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
    ) {
        CalibrationContent(
            device = device,
            onSelectLabel = onSelectLabel,
            modifier = Modifier.padding(Dimens.PaddingMedium),
        )
    }
}

@Composable
internal fun CalibrationContent(
    device: Device,
    onSelectLabel: (DeviceCalibrationLabel) -> Unit,
    modifier: Modifier = Modifier,
) {
    val calibrationInfo = DetailsCalibrationUiFormatter.format(device)
    Column(
        modifier = modifier,
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
'''
assert text.count(old_body) == 1
calibration.write_text(text.replace(old_body, new_body, 1))
