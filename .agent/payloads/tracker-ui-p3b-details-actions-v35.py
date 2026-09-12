from pathlib import Path

path = Path('feature/details/src/main/java/io/blueeye/feature/details/DetailsScreen.kt')
text = path.read_text()

replacements = [
    ('import androidx.compose.material.icons.filled.Edit\n', ''),
    ('import androidx.compose.material3.FloatingActionButton\n', ''),
    ('import androidx.compose.ui.res.painterResource\n', ''),
    ('import io.blueeye.core.ui.R\n', ''),
]
for old, new in replacements:
    if text.count(old) != 1:
        raise SystemExit(f'expected exactly one import match for {old!r}, got {text.count(old)}')
    text = text.replace(old, new, 1)

edit_action = '''                    IconButton(onClick = { showEditDialog.value = true }) {
                        Icon(imageVector = Icons.Default.Edit, contentDescription = "Edit")
                    }
'''
if text.count(edit_action) != 1:
    raise SystemExit(f'expected one topbar edit action, got {text.count(edit_action)}')
text = text.replace(edit_action, '', 1)

fab_block = '''        floatingActionButton = {
            device?.let { dev ->
                FloatingActionButton(onClick = { viewModel.toggleWatchlist() }) {
                    Icon(
                        painter =
                            painterResource(
                                id =
                                    if (dev.isInWatchlist) {
                                        R.drawable.ic_visibility_off
                                    } else {
                                        R.drawable.ic_visibility
                                    },
                            ),
                        contentDescription =
                            if (dev.isInWatchlist) {
                                "Remove from watchlist"
                            } else {
                                "Watch device"
                            },
                    )
                }
            }
        }
'''
if text.count(fab_block) != 1:
    raise SystemExit(f'expected one watchlist FAB block, got {text.count(fab_block)}')
text = text.replace(fab_block, '', 1)

identity_then_calibration = '''                InfoSection("Identity",
                    listOf(
                        "Vendor" to (dev.vendorName ?: "Unknown"),
                        "Technology" to dev.technology,
                        "Type" to dev.deviceType.name
                    )
                )

                CalibrationCard(
'''
identity_with_actions = '''                InfoSection("Identity",
                    listOf(
                        "Vendor" to (dev.vendorName ?: "Unknown"),
                        "Technology" to dev.technology,
                        "Type" to dev.deviceType.name
                    )
                )

                DetailsActionsReviewCard(
                    device = dev,
                    onToggleWatchlist = { viewModel.toggleWatchlist() },
                    onEdit = { showEditDialog.value = true },
                )

                CalibrationCard(
'''
if text.count(identity_then_calibration) != 1:
    raise SystemExit(f'expected one identity/calibration seam, got {text.count(identity_then_calibration)}')
text = text.replace(identity_then_calibration, identity_with_actions, 1)

header_end = '''    }
}

@Composable
fun ConnectionCard(
'''
actions_card = '''    }
}

@Composable
fun DetailsActionsReviewCard(
    device: Device,
    onToggleWatchlist: () -> Unit,
    onEdit: () -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth().stableLiveHeight(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
    ) {
        Column(
            modifier = Modifier.padding(Dimens.PaddingMedium),
            verticalArrangement = Arrangement.spacedBy(Dimens.PaddingSmall),
        ) {
            Text(
                text = "Actions / Review",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.primary,
            )
            Text(
                text = "Manage Watchlist status, alias, notes and alert preferences for this device.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(Dimens.PaddingSmall),
            ) {
                OutlinedButton(
                    onClick = onToggleWatchlist,
                    modifier = Modifier.weight(1f),
                ) {
                    Text(if (device.isInWatchlist) "Remove Watchlist" else "Add Watchlist")
                }
                Button(
                    onClick = onEdit,
                    modifier = Modifier.weight(1f),
                ) {
                    Text("Edit profile & alerts")
                }
            }
        }
    }
}

@Composable
fun ConnectionCard(
'''
if text.count(header_end) != 1:
    raise SystemExit(f'expected one HeaderCard/ConnectionCard seam, got {text.count(header_end)}')
text = text.replace(header_end, actions_card, 1)

path.write_text(text)
