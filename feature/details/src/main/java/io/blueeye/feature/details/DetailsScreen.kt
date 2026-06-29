package io.blueeye.feature.details

import androidx.compose.foundation.background
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.blueeye.core.model.Device
import io.blueeye.core.model.DeviceConnectionState
import io.blueeye.core.model.SensorData
import io.blueeye.core.ui.R
import io.blueeye.core.ui.theme.BlueEyeTheme
import io.blueeye.core.ui.theme.Dimens

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DetailsScreen(
    fingerprint: String,
    onBackClick: () -> Unit,
    viewModel: DetailsViewModel = hiltViewModel()
) {
    val device by viewModel.device.collectAsStateWithLifecycle()
    val connectionState by viewModel.connectionState.collectAsStateWithLifecycle()
    val signalSamples by viewModel.signalSamples.collectAsStateWithLifecycle(initialValue = emptyList())
    val followMeHistory by viewModel.followMeHistory.collectAsStateWithLifecycle(initialValue = emptyList())
    val alertEvidenceEvents by viewModel.alertEvidenceEvents.collectAsStateWithLifecycle(initialValue = emptyList())
    val services by viewModel.discoveredServices.collectAsStateWithLifecycle(initialValue = emptyList())
    val sensorData by viewModel.sensorData.collectAsStateWithLifecycle(initialValue = null)

    val showEditDialog = remember { mutableStateOf(false) }
    val showRawDataDialog = remember { mutableStateOf(false) }

    if (showEditDialog.value && device != null) {
        EditDeviceDialog(
            device = device!!,
            onDismiss = { showEditDialog.value = false },
            onSave = { alias, notes, sound, vib ->
                viewModel.updateDeviceConfig(alias, notes, sound, vib)
                showEditDialog.value = false
            }
        )
    }

    if (showRawDataDialog.value && device != null) {
        RawDataDialog(
            device = device!!,
            onDismiss = { showRawDataDialog.value = false },
            onExportJson = { callback -> viewModel.exportJson(callback) }
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(text = device?.getDisplayName() ?: "Unknown Device", style = MaterialTheme.typography.titleMedium)
                        Text(text = fingerprint, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBackClick) {
                        Icon(imageVector = Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    IconButton(onClick = { viewModel.refreshFocusedScan() }) {
                        Icon(imageVector = Icons.Default.Refresh, contentDescription = "Refresh focused scan")
                    }
                    IconButton(onClick = { showEditDialog.value = true }) {
                        Icon(imageVector = Icons.Default.Edit, contentDescription = "Edit")
                    }
                    IconButton(onClick = { showRawDataDialog.value = true }) {
                        Icon(imageVector = Icons.Default.Info, contentDescription = "Raw Data")
                    }
                }
            )
        },
        floatingActionButton = {
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
    ) { paddingValues ->
        Column(
            modifier =
                Modifier
                    .padding(paddingValues)
                    .fillMaxSize()
                    .padding(Dimens.PaddingMedium)
                    .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            device?.let { dev ->
                // Header Info
                HeaderCard(dev)

                DetailsEvidenceSection(evidence = dev.evidence)

                if (alertEvidenceEvents.isNotEmpty()) {
                    DetailsAlertHistoryCard(events = alertEvidenceEvents)
                }

                CalibrationCard(
                    device = dev,
                    onSelectLabel = { viewModel.updateCalibrationLabel(it) },
                )

                // Connection Control
                ConnectionCard(
                    connectionState = connectionState,
                    onConnect = { viewModel.connect() },
                    onDisconnect = { viewModel.disconnect() }
                )

                if (signalSamples.isNotEmpty()) {
                    DetailsSignalHistoryCard(samples = signalSamples)
                }

                if (followMeHistory.isNotEmpty()) {
                    DetailsFollowMeHistoryCard(samples = followMeHistory)
                }

                // Sensor Data
                if (sensorData != null) {
                    SensorDataCard(sensorData!!)
                }

                // Info Sections
                InfoSection("Identity",
                    listOf(
                        "Vendor" to (dev.vendorName ?: "Unknown"),
                        "Technology" to dev.technology,
                        "Type" to dev.deviceType.name
                    )
                )

                InfoSection("Activity",
                    listOf(
                        "First Seen" to DetailsUiFormatter.formatFriendlyTimestamp(dev.firstSeenAt),
                        "Last Seen" to DetailsUiFormatter.formatFriendlyTimestamp(dev.lastSeenAt),
                        "Encounters" to dev.encounterCount.toString()
                    )
                )

                InfoSection("Radio",
                    listOf(
                        "PHY" to DetailsUiFormatter.formatPhy(dev.primaryPhy, dev.secondaryPhy),
                        "Interval" to (dev.advertisingIntervalMs?.let { "~${it}ms" } ?: "Unknown"),
                        "Beacon Type" to (dev.beaconType ?: "N/A")
                    )
                )

                // Extended Info (Conditional)
                val extendedProps =
                    listOfNotNull(
                        dev.modelNumber?.let { "Model" to it },
                        dev.serialNumber?.let { "Serial" to it },
                        dev.firmwareRevision?.let { "Firmware" to it },
                        dev.batteryLevel?.let { "Battery" to "$it%" }
                    )
                if (extendedProps.isNotEmpty()) {
                    InfoSection("Extended Info", extendedProps)
                }

                // Services
                if (services.isNotEmpty()) {
                    InfoSection("Services (${services.size})", services.map { it.uuid to it.name })
                }

                Spacer(Modifier.height(Dimens.PaddingExtraLarge * 2)) // Spacing for FAB
            } ?: run {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            }
        }
    }
}

@Composable
fun HeaderCard(device: Device) {
    val summary = remember(device) { DetailsDecisionSummaryFormatter.format(device) }

    Card(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Placeholder Icon
            Box(
                modifier =
                    Modifier
                        .size(48.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.primaryContainer),
                contentAlignment = Alignment.Center
            ) {
                Icon(imageVector = Icons.Default.Info, contentDescription = null, tint = MaterialTheme.colorScheme.onPrimaryContainer)
            }
            Spacer(Modifier.width(Dimens.PaddingMedium))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = device.getDisplayName(),
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(text = "${device.rssi} dBm", style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.primary)
                DetailsDecisionSummaryText(summary)
            }
        }
    }
}

@Composable
fun ConnectionCard(
    connectionState: DeviceConnectionState,
    onConnect: () -> Unit,
    onDisconnect: () -> Unit
) {
    Card(modifier = Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Column {
                    Text("Connection Status", style = MaterialTheme.typography.labelMedium)
                    val statusText =
                        when (connectionState) {
                            is DeviceConnectionState.Connected -> "Connected"
                            is DeviceConnectionState.Connecting -> "Connecting..."
                            is DeviceConnectionState.Disconnected -> "Disconnected"
                            is DeviceConnectionState.Error -> "Error"
                        }
                    Text(statusText, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Bold)
                }

                when (connectionState) {
                    is DeviceConnectionState.Disconnected, is DeviceConnectionState.Error -> {
                        Button(onClick = onConnect) { Text("Connect") }
                    }
                    is DeviceConnectionState.Connected -> {
                        OutlinedButton(onClick = onDisconnect) { Text("Disconnect") }
                    }
                    is DeviceConnectionState.Connecting -> {
                        CircularProgressIndicator(modifier = Modifier.size(24.dp))
                    }
                }
            }

            if (connectionState is DeviceConnectionState.Error) {
                Text(text = connectionState.message, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(top = Dimens.PaddingSmall))
            }
        }
    }
}

@Composable
fun InfoSection(
    title: String,
    items: List<Pair<String, String>>
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
            HorizontalDivider(modifier = Modifier.padding(vertical = Dimens.PaddingSmall))
            items.forEach { (label, value) ->
                Row(modifier = Modifier.fillMaxWidth().padding(vertical = Dimens.PaddingExtraSmall), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text(label, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(value, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                }
            }
        }
    }
}

@Composable
fun SensorDataCard(data: SensorData) {
    Card(modifier = Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer)) {
        Column(modifier = Modifier.padding(Dimens.PaddingMedium)) {
            Text("Sensor Data", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSecondaryContainer)
            Spacer(Modifier.height(8.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                data.temperatureCelcius?.let { SensorItem("Temp", String.format("%.1f°C", it)) }
                data.humidityPercent?.let { SensorItem("Hum", String.format("%.0f%%", it)) }
                data.batteryLevel?.let { SensorItem("Bat", "$it%") }
            }
        }
    }
}

@Composable
fun SensorItem(
    label: String,
    value: String
) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(value, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        Text(label, style = MaterialTheme.typography.bodySmall)
    }
}

@Composable
fun EditDeviceDialog(
    device: Device,
    onDismiss: () -> Unit,
    onSave: (String?, String?, Boolean, Boolean) -> Unit
) {
    var alias by remember { mutableStateOf(device.userAlias ?: "") }
    var notes by remember { mutableStateOf(device.userNotes ?: "") }
    var alertSound by remember { mutableStateOf(device.alertSound) }
    var alertVibration by remember { mutableStateOf(device.alertVibration) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Edit Device") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                androidx.compose.material3.OutlinedTextField(
                    value = alias,
                    onValueChange = { alias = it },
                    label = { Text("Alias") },
                    singleLine = true
                )
                androidx.compose.material3.OutlinedTextField(
                    value = notes,
                    onValueChange = { notes = it },
                    label = { Text("Notes") }
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    androidx.compose.material3.Switch(checked = alertSound, onCheckedChange = { alertSound = it })
                    Spacer(Modifier.width(8.dp))
                    Text("Alert Sound")
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    androidx.compose.material3.Switch(checked = alertVibration, onCheckedChange = { alertVibration = it })
                    Spacer(Modifier.width(8.dp))
                    Text("Alert Vibration")
                }
            }
        },
        confirmButton = {
            Button(onClick = { onSave(alias.ifBlank { null }, notes.ifBlank { null }, alertSound, alertVibration) }) {
                Text("Save")
            }
        },
        dismissButton = {
            androidx.compose.material3.TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}

@Composable
fun RawDataDialog(
    device: Device,
    onDismiss: () -> Unit,
    onExportJson: ((String?) -> Unit) -> Unit
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Raw Data") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Last Advertisement:", style = MaterialTheme.typography.labelSmall)
                Text(device.lastRawData ?: "No data", style = MaterialTheme.typography.bodySmall, color = Color.Gray)

                Button(onClick = {
                    val raw = device.lastRawData ?: ""
                    val clipboard = context.getSystemService(android.content.Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
                    val clip = android.content.ClipData.newPlainText("Raw Data", raw)
                    clipboard.setPrimaryClip(clip)
                    android.widget.Toast.makeText(context, "Copied!", android.widget.Toast.LENGTH_SHORT).show()
                }) {
                    Text("Copy Raw Data")
                }

                Button(onClick = {
                    onExportJson { json ->
                        if (json != null) {
                            val clipboard = context.getSystemService(android.content.Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
                            val clip = android.content.ClipData.newPlainText("Device JSON", json)
                            clipboard.setPrimaryClip(clip)
                            android.widget.Toast.makeText(context, "JSON copied!", android.widget.Toast.LENGTH_SHORT).show()
                        } else {
                            android.widget.Toast.makeText(context, "Export failed", android.widget.Toast.LENGTH_SHORT).show()
                        }
                    }
                }) {
                    Text("Export JSON to Clipboard")
                }
            }
        },
        confirmButton = {
            androidx.compose.material3.TextButton(onClick = onDismiss) {
                Text("Close")
            }
        }
    )
}

// private fun formatTimestamp removed as DetailsUiFormatter is used
@androidx.compose.ui.tooling.preview.Preview(showBackground = true)
@Composable
fun DetailsScreenPreview() {
    BlueEyeTheme {
        DetailsScreen(
            fingerprint = "AA:BB:CC:DD:EE:FF",
            onBackClick = {}
        )
    }
}
