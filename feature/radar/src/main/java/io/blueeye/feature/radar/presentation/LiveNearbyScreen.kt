package io.blueeye.feature.radar.presentation

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.blueeye.core.domain.scanner.ScannerRuntimeState
import io.blueeye.core.ui.theme.Dimens
import kotlinx.coroutines.flow.distinctUntilChanged

@Suppress("LongMethod")
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LiveNearbyScreen(
    onDeviceClick: (String) -> Unit,
    onMenuClick: () -> Unit,
    viewModel: LiveNearbyViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val scannerState by viewModel.scannerState.collectAsStateWithLifecycle()
    val listState = rememberLazyListState()
    var expandedGroups by remember { mutableStateOf(emptySet<String>()) }

    LaunchedEffect(listState) {
        snapshotFlow { listState.isScrollInProgress }
            .distinctUntilChanged()
            .collect(viewModel::setOrderFrozen)
    }

    val scannerActive =
        scannerState == ScannerRuntimeState.Running || scannerState == ScannerRuntimeState.Starting

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Live Nearby") },
                navigationIcon = {
                    IconButton(onClick = onMenuClick) {
                        Icon(Icons.Default.Menu, contentDescription = "Open menu")
                    }
                },
                actions = {
                    TextButton(onClick = viewModel::toggleScanning) {
                        Text(if (scannerActive) "Pause" else "Scan")
                    }
                },
            )
        },
    ) { padding ->
        when (val state = uiState) {
            LiveNearbyUiState.Loading ->
                Column(
                    modifier = Modifier.fillMaxSize().padding(padding),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                ) {
                    CircularProgressIndicator()
                }
            is LiveNearbyUiState.Error ->
                Column(
                    modifier = Modifier.fillMaxSize().padding(padding).padding(Dimens.PaddingMedium),
                ) {
                    Text("Live Nearby unavailable", style = MaterialTheme.typography.titleMedium)
                    Text(state.message, color = MaterialTheme.colorScheme.error)
                }
            is LiveNearbyUiState.Success -> {
                val snapshot = state.snapshot
                LazyColumn(
                    modifier = Modifier.fillMaxSize().padding(padding),
                    state = listState,
                    contentPadding = PaddingValues(vertical = Dimens.PaddingSmall),
                ) {
                    item(key = "summary") {
                        LiveNearbySummary(
                            snapshot = snapshot,
                            scannerState = scannerState,
                        )
                    }
                    snapshot.pinned?.let { pinned ->
                        item(key = "pinned:${pinned.item.fingerprint}") {
                            LiveNearbySectionHeader("Pinned")
                            LiveNearbyDeviceCard(
                                device = pinned,
                                pinned = true,
                                onClick = onDeviceClick,
                                onPin = viewModel::togglePin,
                            )
                        }
                    }
                    liveNearbyEntries(
                        title = "Active now · ${snapshot.activeIdentityCount}",
                        entries = snapshot.active,
                        expandedGroups = expandedGroups,
                        onToggleGroup = { key ->
                            expandedGroups =
                                if (key in expandedGroups) expandedGroups - key else expandedGroups + key
                        },
                        onDeviceClick = onDeviceClick,
                        onPin = viewModel::togglePin,
                    )
                    liveNearbyEntries(
                        title = "Recently seen · ${snapshot.recentIdentityCount}",
                        entries = snapshot.recent,
                        expandedGroups = expandedGroups,
                        onToggleGroup = { key ->
                            expandedGroups =
                                if (key in expandedGroups) expandedGroups - key else expandedGroups + key
                        },
                        onDeviceClick = onDeviceClick,
                        onPin = viewModel::togglePin,
                    )
                }
            }
        }
    }
}

@Suppress("LongParameterList", "NestedBlockDepth")
private fun LazyListScope.liveNearbyEntries(
    title: String,
    entries: List<LiveNearbyEntry>,
    expandedGroups: Set<String>,
    onToggleGroup: (String) -> Unit,
    onDeviceClick: (String) -> Unit,
    onPin: (String) -> Unit,
) {
    item(key = "header:$title") { LiveNearbySectionHeader(title) }
    if (entries.isEmpty()) {
        item(key = "empty:$title") {
            Text(
                "No signals in this section.",
                modifier = Modifier.padding(horizontal = Dimens.PaddingMedium),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodySmall,
            )
        }
    }
    entries.forEach { entry ->
        when (entry) {
            is LiveNearbyEntry.Device ->
                item(key = "live:${entry.key}") {
                    LiveNearbyDeviceCard(
                        device = entry.device,
                        pinned = false,
                        onClick = onDeviceClick,
                        onPin = onPin,
                    )
                }
            is LiveNearbyEntry.ProtocolGroup -> {
                item(key = "live:${entry.key}") {
                    LiveNearbyProtocolGroupCard(
                        group = entry,
                        expanded = entry.key in expandedGroups,
                        onToggle = { onToggleGroup(entry.key) },
                    )
                }
                if (entry.key in expandedGroups) {
                    entry.members.forEach { member ->
                        item(key = "live:${entry.key}:${member.item.fingerprint}") {
                            LiveNearbyDeviceCard(
                                device = member,
                                pinned = false,
                                onClick = onDeviceClick,
                                onPin = onPin,
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun LiveNearbySummary(
    snapshot: LiveNearbySnapshot,
    scannerState: ScannerRuntimeState,
) {
    val scannerText =
        when (scannerState) {
            ScannerRuntimeState.Running -> "Scanning"
            ScannerRuntimeState.Starting -> "Starting scanner"
            ScannerRuntimeState.Idle -> "Scanner paused"
            is ScannerRuntimeState.Error -> "Scanner error"
        }
    Column(
        modifier = Modifier.fillMaxWidth().padding(Dimens.PaddingMedium),
        verticalArrangement = Arrangement.spacedBy(Dimens.PaddingExtraSmall),
    ) {
        Text(
            "$scannerText · ${snapshot.activeIdentityCount} active now · " +
                "${snapshot.recentIdentityCount} recent",
            style = MaterialTheme.typography.titleSmall,
        )
        Text(
            "Active ≤15s · recent ≤60s · order refreshes every 5s. " +
                "Signal strength is not exact distance.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun LiveNearbySectionHeader(title: String) {
    Text(
        title,
        modifier = Modifier.padding(
            horizontal = Dimens.PaddingMedium,
            vertical = Dimens.PaddingSmall,
        ),
        style = MaterialTheme.typography.titleMedium,
        fontWeight = FontWeight.SemiBold,
    )
}

@Composable
private fun LiveNearbyDeviceCard(
    device: LiveNearbyDevice,
    pinned: Boolean,
    onClick: (String) -> Unit,
    onPin: (String) -> Unit,
) {
    Surface(
        modifier =
            Modifier
                .fillMaxWidth()
                .padding(horizontal = Dimens.PaddingMedium, vertical = Dimens.PaddingExtraSmall)
                .clickable { onClick(device.item.fingerprint) },
        shape = MaterialTheme.shapes.medium,
        tonalElevation = Dimens.CardElevation,
    ) {
        Row(
            modifier = Modifier.padding(Dimens.PaddingMedium),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    device.item.displayName,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    device.item.vendorAndType,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    "${device.smoothedRssi} dBm · ${device.trend.label} · ${device.ageMs.ageLabel}",
                    style = MaterialTheme.typography.bodyMedium,
                )
                if (ProtocolNoiseClassifier.mustStayStandalone(device.item)) {
                    Text(
                        "Attention evidence",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            }
            TextButton(
                onClick = {
                    onPin(device.item.fingerprint)
                },
            ) {
                Text(if (pinned) "Unpin" else "Pin")
            }
        }
    }
}

@Composable
private fun LiveNearbyProtocolGroupCard(
    group: LiveNearbyEntry.ProtocolGroup,
    expanded: Boolean,
    onToggle: () -> Unit,
) {
    Surface(
        modifier =
            Modifier
                .fillMaxWidth()
                .padding(horizontal = Dimens.PaddingMedium, vertical = Dimens.PaddingExtraSmall)
                .clickable(onClick = onToggle),
        shape = MaterialTheme.shapes.medium,
        tonalElevation = Dimens.CardElevation,
    ) {
        Row(
            modifier = Modifier.padding(Dimens.PaddingMedium),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(group.family.title, style = MaterialTheme.typography.titleSmall)
                Text(
                    "${group.activeCount} active now · ${group.recentCount} recent · " +
                        "${group.totalIdentities} identities seen in last 3 min",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    "Strongest ${group.sortRssi} dBm",
                    style = MaterialTheme.typography.labelSmall,
                )
            }
            Text(if (expanded) "Hide" else "Show", color = MaterialTheme.colorScheme.primary)
        }
    }
}

private val LiveSignalTrend.label: String
    get() =
        when (this) {
            LiveSignalTrend.STRONGER -> "↑ stronger"
            LiveSignalTrend.STEADY -> "steady"
            LiveSignalTrend.WEAKER -> "↓ weaker"
            LiveSignalTrend.UNKNOWN -> "trend pending"
        }

private val Long.ageLabel: String
    get() =
        when {
            this < 1_000L -> "now"
            this < 60_000L -> "${this / 1_000L}s ago"
            else -> "${this / 60_000L}m ago"
        }
