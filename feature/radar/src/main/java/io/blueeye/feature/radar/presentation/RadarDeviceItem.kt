package io.blueeye.feature.radar.presentation

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.blueeye.core.model.Device
import io.blueeye.core.model.DeviceCalibrationLabel
import io.blueeye.core.ui.theme.Dimens
import io.blueeye.core.ui.theme.extendedColors

@Composable
fun RadarDeviceItem(
    item: RadarUiItem,
    onClick: (Device) -> Unit,
    onWatchlistClick: (Device) -> Unit,
    onCalibrationClick: (Device, DeviceCalibrationLabel) -> Unit,
) {
    val cardBackgroundColor =
        if (item.statusInfo.cardBackgroundColor != null) {
            item.statusInfo.cardBackgroundColor.resolve()
        } else {
            MaterialTheme.colorScheme.surface
        }

    Card(
        modifier =
            Modifier
                .fillMaxWidth()
                .padding(horizontal = Dimens.PaddingMedium, vertical = Dimens.PaddingExtraSmall)
                .clickable { onClick(item.device) },
        colors = CardDefaults.cardColors(containerColor = cardBackgroundColor),
        elevation = CardDefaults.cardElevation(defaultElevation = Dimens.CardElevation)
    ) {
        Column(
            modifier =
                Modifier
                    .padding(Dimens.CardCornerRadius) // 12.dp
                    .fillMaxWidth(),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.Top,
            ) {
                DeviceIcon(item = item)

                Spacer(modifier = Modifier.width(Dimens.CardCornerRadius))

                Column(modifier = Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = item.displayName,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = item.nameColor.resolve(),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f, fill = false)
                        )

                        Spacer(modifier = Modifier.width(Dimens.PaddingSmall))

                        val scale by animateFloatAsState(targetValue = 1f, label = "rssiScale")

                        Text(
                            text = "${item.signalInfo.rssi}",
                            color = item.signalInfo.signalColor.resolve(),
                            fontWeight = FontWeight.Bold,
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.scale(scale)
                        )
                    }

                    if (item.vendorAndType.isNotBlank()) {
                        Text(
                            text = item.vendorAndType,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.outline,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }

                    Text(
                        text = "Seen ${item.signalInfo.timeSinceSeen} • RSSI ${item.signalInfo.rssiText}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )

                    BadgeRow(item = item)

                    item.evidenceInfo?.let { evidenceInfo ->
                        RadarEvidenceSummary(
                            evidenceInfo = evidenceInfo,
                            modifier = Modifier.padding(top = Dimens.PaddingSmall),
                        )
                    }
                }
            }

            RadarDeviceActions(
                item = item,
                onClick = onClick,
                onWatchlistClick = onWatchlistClick,
                onCalibrationClick = onCalibrationClick,
            )
        }
    }
}

@Composable
private fun DeviceIcon(item: RadarUiItem) {
    Box(modifier = Modifier.size(Dimens.IconHuge)) {
        Image(
            painter = painterResource(id = item.icons.mainIconRes),
            contentDescription = null,
            colorFilter = ColorFilter.tint(MaterialTheme.colorScheme.primary),
            modifier = Modifier.fillMaxSize()
        )
        if (item.isProbing) {
            androidx.compose.material3.CircularProgressIndicator(
                modifier =
                    Modifier
                        .fillMaxSize()
                        .padding(2.dp),
                strokeWidth = 2.dp,
                color = MaterialTheme.colorScheme.tertiary
            )
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun BadgeRow(item: RadarUiItem) {
    FlowRow(
        modifier = Modifier.padding(top = Dimens.PaddingExtraSmall),
        horizontalArrangement = Arrangement.spacedBy(Dimens.PaddingExtraSmall),
        verticalArrangement = Arrangement.spacedBy(Dimens.PaddingExtraSmall),
    ) {
        Badge(text = item.badges.techBadge, color = item.badges.techColor.resolve())
        Badge(text = item.badges.privacyBadge, color = MaterialTheme.colorScheme.outline)

        item.badges.watchlistBadge?.let {
            Badge(text = it, color = item.badges.watchlistColor.resolve())
        }
        item.badges.statusBadge?.let {
            Badge(text = it, color = item.badges.statusColor.resolve())
        }
        item.badges.calibrationBadge?.let {
            Badge(text = it, color = item.badges.calibrationColor.resolve())
        }
        item.badges.batteryText?.let { Badge(text = it) }
        item.badges.temperatureText?.let { Badge(text = it) }
    }
}

@Composable
private fun RadarDeviceActions(
    item: RadarUiItem,
    onClick: (Device) -> Unit,
    onWatchlistClick: (Device) -> Unit,
    onCalibrationClick: (Device, DeviceCalibrationLabel) -> Unit,
) {
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .padding(top = Dimens.PaddingSmall),
        horizontalArrangement = Arrangement.End,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButton(onClick = { onClick(item.device) }) {
            Icon(
                imageVector = Icons.Default.Info,
                contentDescription = "Details",
                tint = MaterialTheme.colorScheme.outline,
            )
        }

        RadarCalibrationMenuButton(
            selectedLabel = item.device.calibrationLabel,
            onSelectLabel = { label -> onCalibrationClick(item.device, label) },
        )

        IconButton(onClick = { onWatchlistClick(item.device) }) {
            androidx.compose.material3.Icon(
                painter =
                    painterResource(
                        id =
                            if (item.isInWatchlist) {
                                io.blueeye.core.ui.R.drawable.ic_visibility_off
                            } else {
                                io.blueeye.core.ui.R.drawable.ic_visibility
                            },
                    ),
                contentDescription =
                    if (item.isInWatchlist) {
                        "Remove from watchlist"
                    } else {
                        "Watch device"
                    },
                tint =
                    if (item.isInWatchlist) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.outline
                    }
            )
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun RadarEvidenceSummary(
    evidenceInfo: RadarEvidenceInfo,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier) {
        EvidenceConfidencePill(evidenceInfo)

        Text(
            text = evidenceInfo.primarySourceText,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.outline,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(top = Dimens.PaddingExtraSmall),
        )

        Text(
            text = evidenceInfo.primaryReasonText,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 3,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(top = Dimens.PaddingExtraSmall),
        )

        evidenceInfo.primaryValueText?.let { valueText ->
            Text(
                text = valueText,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(top = Dimens.PaddingExtraSmall),
            )
        }

        FlowRow(
            modifier = Modifier.padding(top = Dimens.PaddingExtraSmall),
            horizontalArrangement = Arrangement.spacedBy(Dimens.PaddingExtraSmall),
            verticalArrangement = Arrangement.spacedBy(Dimens.PaddingExtraSmall),
        ) {
            evidenceInfo.chips.forEach { chip ->
                EvidenceChip(chip)
            }
        }
    }
}

@Composable
fun EvidenceConfidencePill(evidenceInfo: RadarEvidenceInfo) {
    val color = evidenceInfo.confidenceColor.resolve()
    Text(
        text = evidenceInfo.confidenceText,
        style = MaterialTheme.typography.labelSmall,
        color = color,
        modifier =
            Modifier
                .background(color.copy(alpha = 0.1f), RoundedCornerShape(Dimens.PaddingExtraSmall))
                .padding(horizontal = Dimens.PaddingSmall, vertical = Dimens.PaddingExtraSmall),
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        softWrap = false,
    )
}

@Composable
fun EvidenceChip(chip: RadarEvidenceChipInfo) {
    val color = chip.color.resolve()
    Text(
        text = chip.text,
        style = MaterialTheme.typography.labelSmall,
        color = color,
        modifier =
            Modifier
                .background(color.copy(alpha = 0.1f), RoundedCornerShape(Dimens.PaddingExtraSmall))
                .padding(horizontal = Dimens.PaddingSmall, vertical = Dimens.PaddingExtraSmall),
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        softWrap = false,
    )
}

@Composable
fun Badge(
    text: String,
    color: Color = MaterialTheme.colorScheme.onSurface
) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelSmall,
        color = color,
        modifier =
            Modifier
                .background(color.copy(alpha = 0.1f), RoundedCornerShape(Dimens.PaddingExtraSmall))
                .padding(horizontal = Dimens.PaddingExtraSmall, vertical = 2.dp),
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        softWrap = false,
    )
}

@Composable
fun RadarUiColorToken.resolve(): Color {
    val extended = MaterialTheme.extendedColors
    return when (this) {
        RadarUiColorToken.PRIMARY -> MaterialTheme.colorScheme.primary
        RadarUiColorToken.SECONDARY -> MaterialTheme.colorScheme.secondary
        RadarUiColorToken.TERTIARY -> MaterialTheme.colorScheme.tertiary
        RadarUiColorToken.DANGEROUS -> extended.dangerous
        RadarUiColorToken.DANGEROUS_CONTAINER -> extended.dangerous.copy(alpha = 0.2f)
        RadarUiColorToken.SAFE -> extended.safe
        RadarUiColorToken.SAFE_CONTAINER -> extended.safe.copy(alpha = 0.2f)
        RadarUiColorToken.SUSPICIOUS -> extended.suspicious
        RadarUiColorToken.SUSPICIOUS_CONTAINER -> extended.suspicious.copy(alpha = 0.2f)
        RadarUiColorToken.WARNING -> extended.warning
        RadarUiColorToken.SURFACE -> MaterialTheme.colorScheme.surface
        RadarUiColorToken.ON_SURFACE -> MaterialTheme.colorScheme.onSurface
        RadarUiColorToken.ON_SURFACE_VARIANT -> MaterialTheme.colorScheme.onSurfaceVariant
        RadarUiColorToken.OUTLINE -> MaterialTheme.colorScheme.outline
        RadarUiColorToken.WHITE -> Color.White
        RadarUiColorToken.GRAY -> MaterialTheme.colorScheme.outline
        RadarUiColorToken.TRANSPARENT -> Color.Transparent
    }
}

@androidx.compose.ui.tooling.preview.Preview(showBackground = true)
@Composable
fun RadarEvidenceSummaryPreview() {
    io.blueeye.core.ui.theme.BlueEyeTheme {
        RadarEvidenceSummary(
            evidenceInfo =
                RadarEvidenceInfo(
                    confidenceText = "High confidence",
                    confidenceColor = RadarUiColorToken.SUSPICIOUS,
                    primarySourceText = "Source: Service - BLE ad",
                    primaryReasonText = "Service UUID is consistent with Axon Body Camera.",
                    primaryValueText = "Value: 0000fd8e-0000-1000-8000-00805f9b34fb -> BODY_CAMERA",
                    chips =
                        listOf(
                            RadarEvidenceChipInfo("Service BLE ad", RadarUiColorToken.SUSPICIOUS),
                            RadarEvidenceChipInfo("OUI registry", RadarUiColorToken.SUSPICIOUS),
                            RadarEvidenceChipInfo("GATT active", RadarUiColorToken.WARNING),
                        ),
                ),
            modifier = Modifier.padding(Dimens.PaddingMedium),
        )
    }
}
