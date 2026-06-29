package io.blueeye.feature.radar.presentation

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Badge
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.blueeye.core.domain.model.DeviceFilter
import io.blueeye.core.domain.scanner.ScannerRuntimeState
import io.blueeye.core.ui.R
import io.blueeye.core.ui.theme.BlueEyeTheme
import io.blueeye.core.ui.theme.Dimens
import io.blueeye.core.ui.theme.extendedColors

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RadarScreen(
    onDeviceClick: (String) -> Unit,
    onMenuClick: () -> Unit,
    viewModel: RadarViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val scannerState by viewModel.scannerState.collectAsStateWithLifecycle()
    val autoActiveProbeEnabled by viewModel.autoActiveProbeEnabled.collectAsStateWithLifecycle()
    val filter by viewModel.filter.collectAsStateWithLifecycle()
    val availableVendors by viewModel.availableVendors.collectAsStateWithLifecycle()
    val isScannerActive =
        scannerState is ScannerRuntimeState.Starting ||
            scannerState is ScannerRuntimeState.Running

    val showClearDialog = remember { mutableStateOf(false) }
    val showFilterDialog = remember { mutableStateOf(false) }
    val showActiveCollectionDialog = remember { mutableStateOf(false) }
    var selectedSectionView by rememberSaveable { mutableStateOf(RadarSectionViewType.ALL) }

    if (showClearDialog.value) {
        AlertDialog(
            onDismissRequest = { showClearDialog.value = false },
            title = { Text("Delete all data?") },
            text = { Text("This will remove all locally observed Bluetooth records from the database.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.clearAllData(keepWatchlist = false)
                        showClearDialog.value = false
                    },
                ) { Text("Delete All", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        viewModel.clearAllData(keepWatchlist = true)
                        showClearDialog.value = false
                    },
                ) { Text("Keep Watchlist") }
            },
        )
    }

    if (showFilterDialog.value) {
        FilterDialog(
            currentFilter = filter,
            availableVendors = availableVendors,
            onDismiss = { showFilterDialog.value = false },
            onApply = { newFilter ->
                viewModel.updateFilter(newFilter)
                showFilterDialog.value = false
            },
            onClear = {
                viewModel.updateFilter(DeviceFilter())
                showFilterDialog.value = false
            },
        )
    }

    ActiveGattCollectionConfirmDialog(
        visible = showActiveCollectionDialog.value,
        onConfirm = {
            viewModel.toggleAutoActiveProbe()
            showActiveCollectionDialog.value = false
        },
        onDismiss = { showActiveCollectionDialog.value = false },
    )

    Scaffold(
        topBar = {
            RadarTopBar(
                state =
                    RadarTopBarState(
                        deviceCount =
                            if (uiState is RadarUiState.Success) {
                                (uiState as RadarUiState.Success).items.size
                            } else {
                                0
                            },
                        totalCount =
                            if (uiState is RadarUiState.Success) {
                                (uiState as RadarUiState.Success).totalCount
                            } else {
                                0
                            },
                        isFilterActive = filter.isActive(),
                        isScanning = isScannerActive,
                        isBaselineActive = (uiState as? RadarUiState.Success)?.isBaselineActive == true,
                        autoActiveProbeEnabled = autoActiveProbeEnabled,
                        decisionSummary = (uiState as? RadarUiState.Success)?.decisionSummary,
                        filterCount = filter.activeFilterCount(),
                    ),
                actions =
                    RadarTopBarActions(
                        onMenuClick = onMenuClick,
                        onScanToggle = { viewModel.toggleScanning() },
                        onBaselineToggle = {
                            val currentItems =
                                (uiState as? RadarUiState.Success)?.items?.map { it.device }.orEmpty()
                            viewModel.toggleBaseline(currentItems)
                        },
                        onAutoActiveProbeToggle = {
                            handleAutoActiveProbeToggle(
                                enabled = autoActiveProbeEnabled,
                                onDisable = { viewModel.toggleAutoActiveProbe() },
                                onEnableRequested = { showActiveCollectionDialog.value = true },
                            )
                        },
                        onFilterClick = { showFilterDialog.value = true },
                        onClearClick = { showClearDialog.value = true },
                    ),
            )
        },
    ) { paddingValues ->
        Column(
            modifier =
                Modifier
                    .padding(paddingValues)
                    .fillMaxSize(),
        ) {
            if (autoActiveProbeEnabled) {
                ActiveCollectionStatusStrip()
            }
            when (val state = uiState) {
                is RadarUiState.Loading -> {
                    Box(Modifier.weight(1f), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator()
                    }
                }
                is RadarUiState.Error -> {
                    Box(Modifier.weight(1f), contentAlignment = Alignment.Center) {
                        Text(state.message, color = MaterialTheme.colorScheme.error)
                    }
                }
                is RadarUiState.Empty -> {
                    EmptyState(
                        text = scannerEmptyText(scannerState),
                        modifier = Modifier.weight(1f),
                    )
                }
                is RadarUiState.FilteredEmpty -> {
                    EmptyState(
                        text = "No devices match filter",
                        modifier = Modifier.weight(1f),
                    )
                }
                is RadarUiState.Success -> {
                    val sectionViewOptions = RadarSectionViewMapper.options(state.sections)
                    val selectedView =
                        resolveRadarSectionView(
                            selectedSectionView = selectedSectionView,
                            options = sectionViewOptions,
                        )
                    val visibleSections =
                        RadarSectionViewMapper.visibleSections(
                            sections = state.sections,
                            selectedView = selectedView,
                        )
                    RadarSectionTabs(
                        options = sectionViewOptions,
                        selectedView = selectedView,
                        onSelected = { selectedSectionView = it },
                    )
                    if (visibleSections.isEmpty()) {
                        EmptyState(
                            text = RadarSectionViewMapper.emptyText(selectedView),
                            modifier = Modifier.weight(1f),
                        )
                    } else {
                        LazyColumn(
                            modifier =
                                Modifier
                                    .fillMaxWidth()
                                    .weight(1f),
                            contentPadding = PaddingValues(bottom = Dimens.PaddingMedium),
                        ) {
                            visibleSections.forEach { section ->
                                item(key = "section-${section.type.name}") {
                                    RadarSectionHeader(section = section)
                                }
                                items(
                                    items = section.items,
                                    key = { it.fingerprint },
                                ) { item ->
                                    RadarDeviceItem(
                                        item = item,
                                        onClick = { onDeviceClick(item.fingerprint) },
                                        onWatchlistClick = { device ->
                                            viewModel.toggleWatchlist(device)
                                        },
                                        onCalibrationClick = { device, label ->
                                            viewModel.updateCalibrationLabel(device, label)
                                        },
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun RadarSectionHeader(section: RadarUiSection) {
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .padding(
                    horizontal = Dimens.PaddingMedium,
                    vertical = Dimens.PaddingSmall,
                ),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = section.type.title,
                style = MaterialTheme.typography.titleSmall,
                color = section.type.tone.resolve(),
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                text = section.type.description,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.outline,
            )
        }
        Surface(
            shape = RoundedCornerShape(Dimens.PaddingExtraSmall),
            color =
                section.type.tone
                    .resolve()
                    .copy(alpha = RadarSectionHeaderDefaults.STATUS_CONTAINER_ALPHA),
            contentColor = section.type.tone.resolve(),
            modifier = Modifier.padding(horizontal = Dimens.PaddingSmall),
        ) {
            Text(
                text = section.type.statusText,
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                modifier =
                    Modifier.padding(
                        horizontal = Dimens.PaddingSmall,
                        vertical = Dimens.PaddingExtraSmall,
                    ),
            )
        }
        Badge(
            containerColor = MaterialTheme.colorScheme.surfaceVariant,
            contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
        ) {
            Text(section.items.size.toString())
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RadarTopBar(
    state: RadarTopBarState,
    actions: RadarTopBarActions,
) {
    TopAppBar(
        title = {
            val countText = RadarTopBarTitleFormatter.countText(state)
            Column {
                Text(
                    text = countText,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                )
                state.decisionSummary?.let { summary ->
                    Text(
                        text = RadarTopBarTitleFormatter.summaryText(summary),
                        style = MaterialTheme.typography.labelSmall,
                        color = summary.tone.resolve(),
                        maxLines = 1,
                        overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                    )
                }
            }
        },
        navigationIcon = {
            IconButton(onClick = actions.onMenuClick) {
                Icon(imageVector = Icons.Default.Menu, contentDescription = "Menu")
            }
        },
        actions = {
            IconButton(onClick = actions.onScanToggle) {
                val iconRes = if (state.isScanning) R.drawable.ic_pause else R.drawable.ic_play
                Icon(
                    painter = painterResource(iconRes),
                    contentDescription = "Scan",
                    tint =
                        if (state.isScanning) {
                            MaterialTheme.extendedColors.dangerous
                        } else {
                            MaterialTheme.extendedColors.safe
                        },
                )
            }

            IconButton(onClick = actions.onBaselineToggle) {
                Icon(
                    painter = painterResource(R.drawable.ic_baseline),
                    contentDescription = "Baseline",
                    tint =
                        if (state.isBaselineActive) {
                            MaterialTheme.extendedColors.dangerous
                        } else {
                            MaterialTheme.colorScheme.outline
                        },
                )
            }

            ActiveGattCollectionButton(
                enabled = state.autoActiveProbeEnabled,
                onClick = actions.onAutoActiveProbeToggle,
            )

            Box {
                IconButton(onClick = actions.onFilterClick) {
                    Icon(
                        painter = painterResource(R.drawable.ic_filter),
                        contentDescription = "Filter",
                        tint =
                            if (state.filterCount > 0) {
                                MaterialTheme.colorScheme.primary
                            } else {
                                MaterialTheme.colorScheme.outline
                            },
                    )
                }
                if (state.filterCount > 0) {
                    Badge(
                        containerColor = MaterialTheme.colorScheme.tertiary,
                        contentColor = MaterialTheme.colorScheme.onTertiary,
                        modifier = Modifier.align(Alignment.TopEnd),
                    ) {
                        Text(state.filterCount.toString())
                    }
                }
            }

            IconButton(onClick = actions.onClearClick) {
                Icon(imageVector = Icons.Default.Delete, contentDescription = "Clear")
            }
        },
    )
}

@Composable
private fun ActiveGattCollectionButton(
    enabled: Boolean,
    onClick: () -> Unit,
) {
    IconButton(onClick = onClick) {
        Icon(
            painter = painterResource(R.drawable.ic_link),
            contentDescription =
                if (enabled) {
                    "Stop active GATT collection"
                } else {
                    "Start active GATT collection"
                },
            tint =
                if (enabled) {
                    MaterialTheme.extendedColors.warning
                } else {
                    MaterialTheme.colorScheme.outline
                },
        )
    }
}

@Composable
fun EmptyState(
    text: String,
    modifier: Modifier = Modifier,
) {
    Box(modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Text(text, color = MaterialTheme.colorScheme.outline)
    }
}

private fun scannerEmptyText(scannerState: ScannerRuntimeState): String {
    return when (scannerState) {
        ScannerRuntimeState.Idle -> "Paused"
        ScannerRuntimeState.Starting -> "Starting scanner..."
        ScannerRuntimeState.Running -> "Scanning..."
        is ScannerRuntimeState.Error -> scannerState.message
    }
}

private fun resolveRadarSectionView(
    selectedSectionView: RadarSectionViewType,
    options: List<RadarSectionViewOption>,
): RadarSectionViewType =
    RadarSectionViewMapper.resolveSelectedView(
        requested = selectedSectionView,
        options = options,
    )

private object RadarSectionHeaderDefaults {
    const val STATUS_CONTAINER_ALPHA = 0.12f
}

@androidx.compose.ui.tooling.preview.Preview(showBackground = true)
@Composable
fun ActiveGattCollectionButtonPreview() {
    BlueEyeTheme {
        ActiveGattCollectionButton(
            enabled = true,
            onClick = {},
        )
    }
}

@androidx.compose.ui.tooling.preview.Preview(showBackground = true)
@Composable
fun RadarScreenPreview() {
    BlueEyeTheme {
        RadarScreen(
            onDeviceClick = {},
            onMenuClick = {},
        )
    }
}

@androidx.compose.ui.tooling.preview.Preview(showBackground = true)
@Composable
fun RadarSectionHeaderPreview() {
    BlueEyeTheme {
        RadarSectionHeader(
            section =
                RadarUiSection(
                    type = RadarUiSectionType.PUBLIC_SAFETY,
                    items = emptyList(),
                ),
        )
    }
}
