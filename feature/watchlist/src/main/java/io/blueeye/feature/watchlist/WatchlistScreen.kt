package io.blueeye.feature.watchlist

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.blueeye.core.domain.repository.WatchlistDeviceItem
import io.blueeye.core.model.DetectionConfidence
import io.blueeye.core.model.DetectionEvidence
import io.blueeye.core.model.EvidenceSource
import io.blueeye.core.model.PublicSafetySignal
import io.blueeye.core.ui.R
import io.blueeye.core.ui.theme.BlueEyeTheme
import io.blueeye.core.ui.theme.Dimens
import io.blueeye.core.ui.theme.extendedColors

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WatchlistScreen(
    onDeviceClick: (String) -> Unit,
    onMenuClick: () -> Unit,
    viewModel: WatchlistViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Watchlist", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onMenuClick) {
                        Icon(Icons.Default.Menu, contentDescription = "Menu")
                    }
                }
            )
        }
    ) { paddingValues ->
        when (val state = uiState) {
            is WatchlistUiState.Loading -> {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    androidx.compose.material3.CircularProgressIndicator()
                }
            }
            is WatchlistUiState.Error -> {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(state.message, color = MaterialTheme.colorScheme.error)
                }
            }
            is WatchlistUiState.Success -> {
                LazyColumn(
                    modifier =
                        Modifier
                            .fillMaxSize()
                            .padding(paddingValues),
                    contentPadding = PaddingValues(Dimens.PaddingMedium),
                    verticalArrangement = Arrangement.spacedBy(Dimens.PaddingMedium)
                ) {
                    // Public-safety signal evidence card.
                    item {
                        PublicSafetySignalCard(
                            isEnabled = state.publicSafetySignalReviewEnabled,
                            detectedCount = state.publicSafetySignalCount,
                            detections = state.publicSafetySignals,
                            now = state.statusUpdatedAt,
                            onToggle = { viewModel.togglePublicSafetySignalReview() }
                        )
                    }

                    // Section Header
                    item {
                        Text(
                            text = "Watchlist",
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }

                    items(
                        items = state.watchedDevices,
                        key = { it.device.fingerprint }
                    ) { deviceItem ->
                        WatchlistDeviceItemRow(
                            item = deviceItem,
                            now = state.statusUpdatedAt,
                            onClick = { onDeviceClick(it) },
                            onRemoveFromWatchlistClick = { viewModel.removeFromWatchlist(it) },
                            onTrackingToggle = { fingerprint, enabled ->
                                viewModel.setTrackingEnabled(fingerprint, enabled)
                            },
                        )
                    }

                    if (state.watchedDevices.isEmpty()) {
                        item {
                            Text(
                                "No watched devices yet. Add them from Radar.",
                                color = MaterialTheme.colorScheme.outline,
                                modifier = Modifier.padding(top = Dimens.PaddingMedium)
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun PublicSafetySignalCard(
    isEnabled: Boolean,
    detectedCount: Int,
    detections: List<PublicSafetySignal>,
    now: Long,
    onToggle: () -> Unit
) {
    val signalItems = WatchlistSignalUiFormatter.map(detections, now)

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors =
            CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surface
            ),
        elevation = CardDefaults.cardElevation(defaultElevation = Dimens.CardElevation)
    ) {
        Column(
            modifier = Modifier.padding(Dimens.PaddingMedium)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        Icons.Default.Warning,
                        contentDescription = null,
                        tint =
                            if (isEnabled) {
                                MaterialTheme.extendedColors.warning
                            } else {
                                MaterialTheme.colorScheme.outline
                            },
                    )
                    Spacer(modifier = Modifier.width(Dimens.PaddingSmall))
                    Text(
                        text = "Signal Hints",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold
                    )
                }
                Switch(checked = isEnabled, onCheckedChange = { onToggle() })
            }

            Spacer(modifier = Modifier.height(Dimens.PaddingSmall))
            Text(
                text = WatchlistSignalUiFormatter.CONTEXT_TEXT,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(modifier = Modifier.height(Dimens.PaddingSmall))

            if (isEnabled) {
                Text(
                    text = WatchlistSignalUiFormatter.countText(detectedCount),
                    style = MaterialTheme.typography.bodyLarge,
                    color =
                        if (detectedCount > 0) {
                            MaterialTheme.extendedColors.warning
                        } else {
                            MaterialTheme.colorScheme.onSurface
                        },
                    fontWeight = FontWeight.Bold
                )
                PublicSafetySignalHints(signalItems)
            } else {
                Text(
                    text = "Paused",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.outline
                )
            }
        }
    }
}

@Composable
private fun PublicSafetySignalHints(items: List<WatchlistSignalUiInfo>) {
    if (items.isEmpty()) {
        Text(
            text = "No current public-safety-style signal evidence.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = Dimens.PaddingExtraSmall),
        )
        return
    }

    Column(
        modifier = Modifier.padding(top = Dimens.PaddingSmall),
        verticalArrangement = Arrangement.spacedBy(Dimens.PaddingSmall),
    ) {
        items.forEach { item ->
            PublicSafetySignalHintRow(item)
        }
    }
}

@Composable
private fun PublicSafetySignalHintRow(item: WatchlistSignalUiInfo) {
    Column {
        Text(
            text = item.title,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface,
            fontWeight = FontWeight.SemiBold,
        )
        Text(
            text = item.confidenceText,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.extendedColors.warning,
            fontWeight = FontWeight.SemiBold,
        )
        Text(
            text = item.detailText,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
        Text(
            text = item.signalText,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

// Local implementation of Device Item for Watchlist
@Composable
fun WatchlistDeviceItemRow(
    item: WatchlistDeviceItem,
    now: Long,
    onClick: (String) -> Unit,
    onRemoveFromWatchlistClick: (String) -> Unit,
    onTrackingToggle: (String, Boolean) -> Unit,
) {
    val device = item.device
    val info = WatchlistUiFormatter.map(item, now)

    Card(
        modifier =
            Modifier
                .fillMaxWidth()
                .clickable { onClick(device.fingerprint) },
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = Dimens.CardElevation)
    ) {
        Row(
            modifier =
                Modifier
                    .padding(Dimens.PaddingMedium)
                    .fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Icon
            Icon(
                imageVector = Icons.Default.Info,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(Dimens.IconLarge)
            )

            Spacer(modifier = Modifier.width(Dimens.PaddingSmall))

            // Main Content
            Column(modifier = Modifier.weight(1f)) {
                WatchlistDeviceHeader(info)
                WatchlistDeviceMetadata(info)
                WatchlistAlertStatus(info, isTrackingEnabled = device.isTrackingEnabled)
            }

            Switch(
                checked = device.isTrackingEnabled,
                onCheckedChange = { enabled -> onTrackingToggle(device.fingerprint, enabled) },
            )

            IconButton(onClick = { onRemoveFromWatchlistClick(device.fingerprint) }) {
                Icon(
                    painter = painterResource(id = R.drawable.ic_visibility_off),
                    contentDescription = "Remove from Watchlist",
                    tint = MaterialTheme.extendedColors.dangerous
                )
            }
        }
    }
}

@Composable
private fun WatchlistDeviceHeader(info: WatchlistDeviceUiInfo) {
    val statusColor =
        when (info.status) {
            WatchlistRangeStatus.IN_RANGE -> MaterialTheme.extendedColors.safe
            WatchlistRangeStatus.OFFLINE -> MaterialTheme.colorScheme.outline
        }

    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(
            text = info.displayName,
            style = MaterialTheme.typography.bodyLarge,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f, fill = false),
        )
        Spacer(modifier = Modifier.width(Dimens.PaddingSmall))
        Text(
            text = info.status.label,
            style = MaterialTheme.typography.labelMedium,
            color = statusColor,
            fontWeight = FontWeight.SemiBold,
        )
    }
}

@Composable
private fun WatchlistDeviceMetadata(info: WatchlistDeviceUiInfo) {
    Text(
        text = info.identityText,
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
    )

    Row(
        modifier = Modifier.padding(top = Dimens.PaddingExtraSmall),
        horizontalArrangement = Arrangement.spacedBy(Dimens.PaddingSmall),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = info.lastSeenText,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            text = info.rssiText,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun WatchlistAlertStatus(
    info: WatchlistDeviceUiInfo,
    isTrackingEnabled: Boolean,
) {
    val alertColor =
        if (isTrackingEnabled) {
            MaterialTheme.extendedColors.safe
        } else {
            MaterialTheme.colorScheme.outline
        }

    Column(
        modifier = Modifier.padding(top = Dimens.PaddingExtraSmall),
    ) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(Dimens.PaddingSmall),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = info.alertsText,
                style = MaterialTheme.typography.labelMedium,
                color = alertColor,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                text = "${info.alertTypeText} • ${info.priorityText}",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        info.returnEvidence?.let { evidence ->
            WatchlistReturnEvidenceSummary(
                evidence = evidence,
                modifier = Modifier.padding(top = Dimens.PaddingExtraSmall),
            )
        }
    }
}

@androidx.compose.ui.tooling.preview.Preview(showBackground = true)
@Composable
fun PublicSafetySignalCardPreview() {
    BlueEyeTheme {
        PublicSafetySignalCard(
            isEnabled = true,
            detectedCount = 1,
            detections =
                listOf(
                    PublicSafetySignal(
                        deviceId = "AA:BB:CC:11:22:33",
                        vendorName = "Axon Enterprise, Inc.",
                        category = "BODY_CAMERA",
                        confidence = DetectionConfidence.HIGH,
                        description = "Body camera and signal sensor evidence",
                        evidence =
                            listOf(
                                DetectionEvidence(
                                    source = EvidenceSource.OUI,
                                    confidence = DetectionConfidence.HIGH,
                                    reasonText =
                                        "MAC OUI is consistent with Axon Enterprise, Inc.: " +
                                            "Body camera and signal sensor evidence.",
                                    timestamp = WatchlistPreviewData.NOW - 30_000L,
                                    rawValue = "0025DF",
                                    parsedValue = "BODY_CAMERA: Body camera and signal sensor evidence",
                                    isPassive = true,
                                ),
                            ),
                        rssi = -61,
                        firstSeenAt = WatchlistPreviewData.NOW - 120_000L,
                        lastSeenAt = WatchlistPreviewData.NOW - 30_000L,
                    ),
                ),
            now = WatchlistPreviewData.NOW,
            onToggle = {},
        )
    }
}

@androidx.compose.ui.tooling.preview.Preview(showBackground = true)
@Composable
fun WatchlistScreenPreview() {
    BlueEyeTheme {
        WatchlistScreen(
            onDeviceClick = {},
            onMenuClick = {}
        )
    }
}

private object WatchlistPreviewData {
    const val NOW = 1_789_000_000_000L
}
