package io.blueeye.feature.settings

import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.blueeye.core.ui.theme.Dimens

@OptIn(ExperimentalMaterial3Api::class, androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
fun SettingsScreen(
    onMenuClick: () -> Unit,
    onDeviceClick: (String) -> Unit,
    viewModel: SettingsViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val context = androidx.compose.ui.platform.LocalContext.current

    // Internal Navigation State
    var currentSection by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf(SettingsSection.MAIN) }
    var showActiveCollectionDialog by remember { mutableStateOf(false) }

    ActiveCollectionConfirmDialog(
        visible = showActiveCollectionDialog,
        onConfirm = {
            viewModel.setAutoActiveProbeEnabled(true)
            showActiveCollectionDialog = false
        },
        onDismiss = { showActiveCollectionDialog = false },
    )

    // Handle Back Press
    androidx.activity.compose.BackHandler(enabled = currentSection != SettingsSection.MAIN) {
        currentSection = SettingsSection.MAIN
    }

    Scaffold(
        topBar = {
            androidx.compose.material3.CenterAlignedTopAppBar(
                title = {
                    Text(
                        text =
                            when (currentSection) {
                                SettingsSection.MAIN -> "Settings"
                                SettingsSection.ALERTS -> "Alerts & Collection"
                                SettingsSection.APPEARANCE -> "Appearance"
                                SettingsSection.DATABASE -> "Database & Updates"
                            },
                        fontWeight = FontWeight.Bold
                    )
                },
                navigationIcon = {
                    if (currentSection == SettingsSection.MAIN) {
                        IconButton(onClick = onMenuClick) {
                            Icon(imageVector = Icons.Default.Menu, contentDescription = "Menu")
                        }
                    } else {
                        IconButton(onClick = { currentSection = SettingsSection.MAIN }) {
                            Icon(imageVector = Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                        }
                    }
                }
            )
        }
    ) { paddingValues ->
        Box(
            modifier =
                Modifier
                    .padding(paddingValues)
                    .fillMaxSize()
        ) {
            when (currentSection) {
                SettingsSection.MAIN ->
                    MainSettingsList(
                        onNavigate = { currentSection = it }
                    )
                SettingsSection.ALERTS ->
                    AlertSettingsContent(
                        uiState = uiState,
                        viewModel = viewModel,
                        onAutoActiveProbeChange = { requestedEnabled ->
                            handleActiveCollectionSettingChange(
                                enabled = uiState.autoActiveProbeEnabled,
                                requestedEnabled = requestedEnabled,
                                onDisable = { viewModel.setAutoActiveProbeEnabled(false) },
                                onEnableRequested = { showActiveCollectionDialog = true },
                            )
                        },
                    )
                SettingsSection.APPEARANCE ->
                    AppearanceContent(
                        uiState = uiState,
                        viewModel = viewModel
                    )
                SettingsSection.DATABASE ->
                    DatabaseContent(
                        uiState = uiState,
                        viewModel = viewModel,
                        context = context,
                        onDeviceClick = onDeviceClick,
                    )
            }
        }
    }
}

enum class SettingsSection {
    MAIN,
    ALERTS,
    APPEARANCE,
    DATABASE
}

@Composable
fun MainSettingsList(onNavigate: (SettingsSection) -> Unit) {
    Column(
        modifier =
            Modifier
                .fillMaxSize()
                .padding(Dimens.PaddingMedium)
                .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(Dimens.PaddingMedium)
    ) {
        SettingsCategoryCard(
            title = "Alerts & Collection",
            icon = Icons.Default.Notifications,
            description = "Manage alerts, sound, and active GATT collection",
            onClick = { onNavigate(SettingsSection.ALERTS) }
        )

        SettingsCategoryCard(
            title = "Appearance",
            icon = Icons.Default.Edit, // Using Edit as Palette might require extended icons or specific import check
            description = "Theme, colors, and dynamic styling",
            onClick = { onNavigate(SettingsSection.APPEARANCE) }
        )

        SettingsCategoryCard(
            title = "Database & Updates",
            icon = Icons.Default.Info, // Generic Info/Storage icon
            description = "Manage manufacturers, MACs, and GATT data",
            onClick = { onNavigate(SettingsSection.DATABASE) }
        )
    }
}

@Composable
fun SettingsCategoryCard(
    title: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    description: String,
    onClick: () -> Unit
) {
    Card(
        modifier =
            Modifier
                .fillMaxWidth()
                .clickable { onClick() },
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Row(
            modifier =
                Modifier
                    .padding(Dimens.PaddingMedium)
                    .fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(32.dp)
            )
            Spacer(modifier = Modifier.width(Dimens.PaddingMedium))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = description,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Icon(
                imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                contentDescription = "Go",
                tint = MaterialTheme.colorScheme.outline
            )
        }
    }
}

@Composable
fun AlertSettingsContent(
    uiState: SettingsUiState,
    viewModel: SettingsViewModel,
    onAutoActiveProbeChange: (Boolean) -> Unit,
) {
    Column(
        modifier =
            Modifier
                .fillMaxSize()
                .padding(Dimens.PaddingMedium)
                .verticalScroll(rememberScrollState())
    ) {
        AlertSettingsCard(
            detectionEnabled = uiState.trackerDetectionEnabled,
            vibrateEnabled = uiState.trackerVibrationEnabled,
            soundEnabled = uiState.trackerSoundEnabled,
            headsUpEnabled = uiState.trackerHeadsUpEnabled,
            autoActiveProbeEnabled = uiState.autoActiveProbeEnabled,
            onDetectionChange = { viewModel.setTrackerDetectionEnabled(it) },
            onVibrateChange = { viewModel.setTrackerVibrationEnabled(it) },
            onSoundChange = { viewModel.setTrackerSoundEnabled(it) },
            onHeadsUpChange = { viewModel.setTrackerHeadsUpEnabled(it) },
            onAutoActiveProbeChange = onAutoActiveProbeChange,
        )
    }
}

@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
fun AppearanceContent(
    uiState: SettingsUiState,
    viewModel: SettingsViewModel
) {
    Column(
        modifier =
            Modifier
                .fillMaxSize()
                .padding(Dimens.PaddingMedium)
                .verticalScroll(rememberScrollState())
    ) {
        AppearanceCard(
            themeMode = uiState.themeMode,
            colorSchemeName = uiState.colorSchemeName,
            useDynamicColors = uiState.useDynamicColors,
            onThemeModeChange = { viewModel.setThemeMode(it) },
            onColorSchemeChange = { viewModel.setColorScheme(it) },
            onDynamicColorToggle = { viewModel.setUseDynamicColors(it) }
        )
    }
}

@Composable
fun DatabaseContent(
    uiState: SettingsUiState,
    viewModel: SettingsViewModel,
    context: Context,
    onDeviceClick: (String) -> Unit,
) {
    Column(
        modifier =
            Modifier
                .fillMaxSize()
                .padding(Dimens.PaddingMedium)
                .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(Dimens.PaddingMedium)
    ) {
        // Status Card
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
        ) {
            Column(Modifier.padding(16.dp)) {
                val timestamp = uiState.lastUpdateTimestamp
                val lastUpdateText =
                    if (timestamp == 0L) {
                        "Never"
                    } else {
                        java.text.SimpleDateFormat("yyyy-MM-dd HH:mm", java.util.Locale.getDefault()).format(java.util.Date(timestamp))
                    }
                Text("Last updated: $lastUpdateText", style = MaterialTheme.typography.bodyMedium)

                val updateStatus = uiState.updateStatus
                if (updateStatus != null) {
                    val statusColor =
                        if (updateStatus.startsWith("Error")) {
                            MaterialTheme.colorScheme.error
                        } else {
                            MaterialTheme.colorScheme.primary
                        }
                    Spacer(Modifier.height(8.dp))
                    Text(
                        text = updateStatus,
                        color = statusColor
                    )
                }
            }
        }

        SessionCalibrationCard(
            state =
                SessionCalibrationCardState(
                    label = uiState.sessionCalibrationLabel,
                    startedAt = uiState.sessionStartedAt,
                    notes = uiState.sessionNotes,
                    stats = uiState.sessionStats,
                    reviewReadiness = uiState.sessionReviewReadiness,
                ),
            actions =
                SessionCalibrationCardActions(
                    onSelectLabel = { viewModel.setSessionCalibrationLabel(it) },
                    onNotesChange = { viewModel.setSessionNotes(it) },
                    onStartNewSession = { viewModel.startNewSession() },
                    onReviewDeviceClick = onDeviceClick,
                    onReviewDeviceAction = { fingerprint, action ->
                        viewModel.applyReviewDeviceAction(fingerprint, action)
                    },
                ),
        )

        // Database Items
        DatabaseCard("Company IDs", uiState.companyIdCount, uiState.isUpdating) { viewModel.updateCompanyIds() }
        DatabaseCard("MAC OUI Database", uiState.ouiCount, uiState.isUpdating) { viewModel.updateOui() }
        DatabaseCard("GATT Services", uiState.gattServiceCount, uiState.isUpdating) { viewModel.updateGattServices() }
        DatabaseCard("GATT Characteristics", uiState.gattCharacteristicCount, uiState.isUpdating) { viewModel.updateGattCharacteristics() }

        Spacer(Modifier.height(16.dp))

        // Actions
        Button(
            onClick = { viewModel.updateAll() },
            enabled = !uiState.isUpdating,
            modifier = Modifier.fillMaxWidth()
        ) {
            if (uiState.isUpdating) {
                androidx.compose.material3.CircularProgressIndicator(
                    modifier = Modifier.size(24.dp),
                    color = MaterialTheme.colorScheme.onPrimary,
                    strokeWidth = 2.dp
                )
                Spacer(Modifier.width(8.dp))
                Text("Updating...")
            } else {
                Text("Update All Databases")
            }
        }

        DatabaseExportCard(
            reviewReadiness = uiState.sessionReviewReadiness,
            reviewCategoryCounts = uiState.sessionStats.reviewCategoryCounts,
            rssiQuality = uiState.sessionStats.rssiQuality,
            onCopyExport = {
                prepareDatabaseExport(context, viewModel) { json ->
                    copyExportToClipboard(context, json)
                }
            },
            onShareExport = {
                prepareDatabaseExport(context, viewModel) { json ->
                    shareExport(context, json)
                }
            },
        )
    }
}

@Composable
@Suppress("LongParameterList")
fun AlertSettingsCard(
    detectionEnabled: Boolean,
    vibrateEnabled: Boolean,
    soundEnabled: Boolean,
    headsUpEnabled: Boolean,
    autoActiveProbeEnabled: Boolean,
    onDetectionChange: (Boolean) -> Unit,
    onVibrateChange: (Boolean) -> Unit,
    onSoundChange: (Boolean) -> Unit,
    onHeadsUpChange: (Boolean) -> Unit,
    onAutoActiveProbeChange: (Boolean) -> Unit,
) {
    androidx.compose.material3.Card(
        modifier = Modifier.fillMaxWidth(),
        colors =
            androidx.compose.material3.CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
            )
    ) {
        Column(Modifier.padding(Dimens.PaddingMedium)) {
            SettingsSwitch(
                title = "Tracker Detection",
                subtitle = "Master switch for all tracker alerts",
                checked = detectionEnabled,
                onCheckedChange = onDetectionChange
            )
            androidx.compose.material3.HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
            SettingsSwitch(
                title = "Automatic Active Collection",
                subtitle = "Explicit opt-in: connect to every connectable BLE device one at a time for GATT evidence",
                checked = autoActiveProbeEnabled,
                onCheckedChange = onAutoActiveProbeChange
            )
            androidx.compose.material3.HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
            SettingsSwitch(
                title = "Vibration",
                subtitle = "Vibrate on alert",
                checked = vibrateEnabled,
                onCheckedChange = onVibrateChange,
                enabled = detectionEnabled
            )
            SettingsSwitch(
                title = "Sound",
                subtitle = "Play alarm sound",
                checked = soundEnabled,
                onCheckedChange = onSoundChange,
                enabled = detectionEnabled
            )
            SettingsSwitch(
                title = "Heads-Up Notification",
                subtitle = "Show popup banner",
                checked = headsUpEnabled,
                onCheckedChange = onHeadsUpChange,
                enabled = detectionEnabled
            )
        }
    }
}

@androidx.compose.foundation.layout.ExperimentalLayoutApi
@Composable
fun AppearanceCard(
    themeMode: String,
    colorSchemeName: String,
    useDynamicColors: Boolean,
    onThemeModeChange: (String) -> Unit,
    onColorSchemeChange: (String) -> Unit,
    onDynamicColorToggle: (Boolean) -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(Modifier.padding(16.dp)) {
            Text("Theme Mode", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(8.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf("System", "Light", "Dark").forEach { mode ->
                    OutlinedButton(
                        onClick = { onThemeModeChange(mode) },
                        modifier = Modifier.weight(1f),
                        border = if (themeMode == mode) androidx.compose.foundation.BorderStroke(2.dp, MaterialTheme.colorScheme.primary) else ButtonDefaults.outlinedButtonBorder,
                        colors = if (themeMode == mode) ButtonDefaults.outlinedButtonColors(containerColor = MaterialTheme.colorScheme.primaryContainer) else ButtonDefaults.outlinedButtonColors()
                    ) {
                        Text(mode)
                    }
                }
            }

            Spacer(Modifier.height(16.dp))
            Text("Color Scheme", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(8.dp))

            // Chips or Row for color schemes
            val schemes = listOf("Classic", "Tactical", "Midnight", "Forest")
            androidx.compose.foundation.layout.FlowRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                schemes.forEach { scheme ->
                    val isSelected = colorSchemeName == scheme
                    val previewColor = io.blueeye.core.ui.theme.getThemePrimaryColor(scheme, themeMode == "Dark" || (themeMode == "System" && androidx.compose.foundation.isSystemInDarkTheme()))

                    OutlinedButton(
                        onClick = { onColorSchemeChange(scheme) },
                        border = if (isSelected) androidx.compose.foundation.BorderStroke(2.dp, MaterialTheme.colorScheme.primary) else ButtonDefaults.outlinedButtonBorder,
                        colors = if (isSelected) ButtonDefaults.outlinedButtonColors(containerColor = MaterialTheme.colorScheme.primaryContainer) else ButtonDefaults.outlinedButtonColors(),
                        contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 12.dp, vertical = 8.dp)
                    ) {
                        // Colored Circle
                        androidx.compose.foundation.layout.Box(
                            modifier =
                                Modifier
                                    .size(12.dp)
                                    .background(previewColor, androidx.compose.foundation.shape.CircleShape)
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(scheme, color = if (isSelected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface)
                    }
                }
            }

            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S) {
                Spacer(Modifier.height(16.dp))
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Dynamic Colors (Material You)", style = MaterialTheme.typography.bodyLarge)
                    androidx.compose.material3.Switch(
                        checked = useDynamicColors,
                        onCheckedChange = onDynamicColorToggle
                    )
                }
            }
        }
    }
}

@Composable
fun DatabaseCard(
    name: String,
    count: Int,
    isGlobalUpdating: Boolean,
    onUpdate: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Row(
            modifier =
                Modifier
                    .padding(16.dp)
                    .fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(text = name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Text(
                    text = String.format(java.util.Locale.US, "%,d entries", count),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            IconButton(onClick = onUpdate, enabled = !isGlobalUpdating) {
                Icon(imageVector = Icons.Default.Refresh, contentDescription = "Update")
            }
        }
    }
}
