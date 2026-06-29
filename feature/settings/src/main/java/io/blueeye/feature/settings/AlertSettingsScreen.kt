package io.blueeye.feature.settings

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.blueeye.core.ui.theme.Dimens

@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
fun AlertSettingsScreen(
    viewModel: SettingsViewModel = hiltViewModel(),
    onMenuClick: () -> Unit
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    var showActiveCollectionDialog by remember { mutableStateOf(false) }

    ActiveCollectionConfirmDialog(
        visible = showActiveCollectionDialog,
        onConfirm = {
            viewModel.setAutoActiveProbeEnabled(true)
            showActiveCollectionDialog = false
        },
        onDismiss = { showActiveCollectionDialog = false },
    )

    Scaffold(
        topBar = {
            androidx.compose.material3.CenterAlignedTopAppBar(
                title = {
                    Text(
                        "Alert Settings",
                        style = MaterialTheme.typography.titleLarge
                    )
                },
                navigationIcon = {
                    androidx.compose.material3.IconButton(onClick = onMenuClick) {
                        androidx.compose.material3.Icon(
                            imageVector = androidx.compose.material.icons.Icons.Default.Menu,
                            contentDescription = "Menu"
                        )
                    }
                },
                colors =
                    androidx.compose.material3.TopAppBarDefaults.centerAlignedTopAppBarColors(
                        containerColor = MaterialTheme.colorScheme.surface,
                        titleContentColor = MaterialTheme.colorScheme.primary
                    )
            )
        }
    ) { paddingValues ->
        Column(
            modifier =
                Modifier
                    .padding(paddingValues)
                    .fillMaxSize()
                    .padding(Dimens.PaddingMedium)
        ) {
            // Master Toggle
            SettingsSwitch(
                title = "Tracker Detection Enabled",
                subtitle = "Detect and alert on possible follow-me patterns.",
                checked = uiState.trackerDetectionEnabled,
                onCheckedChange = { viewModel.setTrackerDetectionEnabled(it) }
            )

            SettingsSwitch(
                title = "Automatic Active Collection",
                subtitle = "Explicit opt-in: connect to every connectable BLE device one at a time for GATT evidence.",
                checked = uiState.autoActiveProbeEnabled,
                onCheckedChange = { requestedEnabled ->
                    handleActiveCollectionSettingChange(
                        enabled = uiState.autoActiveProbeEnabled,
                        requestedEnabled = requestedEnabled,
                        onDisable = { viewModel.setAutoActiveProbeEnabled(false) },
                        onEnableRequested = { showActiveCollectionDialog = true },
                    )
                }
            )

            Spacer(Modifier.height(Dimens.PaddingLarge))

            Text(
                "Notifications",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(bottom = Dimens.PaddingMedium)
            )

            SettingsSwitch(
                title = "Vibration",
                subtitle = "Vibrate when a device reaches a high follow-me score.",
                checked = uiState.trackerVibrationEnabled,
                onCheckedChange = { viewModel.setTrackerVibrationEnabled(it) },
                enabled = uiState.trackerDetectionEnabled
            )

            SettingsSwitch(
                title = "Sound",
                subtitle = "Play a system alert sound.",
                checked = uiState.trackerSoundEnabled,
                onCheckedChange = { viewModel.setTrackerSoundEnabled(it) },
                enabled = uiState.trackerDetectionEnabled
            )

            SettingsSwitch(
                title = "Heads-Up Notification",
                subtitle = "Show high-priority notification on screen.",
                checked = uiState.trackerHeadsUpEnabled,
                onCheckedChange = { viewModel.setTrackerHeadsUpEnabled(it) },
                enabled = uiState.trackerDetectionEnabled
            )
        }
    }
}

@Composable
fun SettingsSwitch(
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    enabled: Boolean = true
) {
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .padding(vertical = Dimens.PaddingSmall),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge,
                color = if (enabled) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.outline
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = if (enabled) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.outline
            )
        }
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            enabled = enabled
        )
    }
}
