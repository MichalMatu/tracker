package io.blueeye.feature.radar.presentation

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.blueeye.core.ui.theme.Dimens
import io.blueeye.core.ui.theme.extendedColors

@Composable
fun RadarDeviceItem(
    item: RadarUiItem,
    onClick: (String) -> Unit,
    onWatchlistClick: (String) -> Unit,
) {
    val cardBackgroundColor =
        item.statusInfo.cardBackgroundColor?.resolve()
            ?: MaterialTheme.colorScheme.surface

    Card(
        modifier =
            Modifier
                .fillMaxWidth()
                .padding(horizontal = Dimens.PaddingMedium, vertical = Dimens.PaddingExtraSmall)
                .clickable { onClick(item.fingerprint) },
        colors = CardDefaults.cardColors(containerColor = cardBackgroundColor),
        elevation = CardDefaults.cardElevation(defaultElevation = Dimens.CardElevation),
    ) {
        Row(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(Dimens.CardCornerRadius),
            verticalAlignment = Alignment.Top,
        ) {
            DeviceIcon(item = item)

            Spacer(modifier = Modifier.width(Dimens.CardCornerRadius))

            Column(modifier = Modifier.weight(1f)) {
                RadarPrimaryRow(
                    item = item,
                    onWatchlistClick = onWatchlistClick,
                )

                Text(
                    text = item.vendorAndType.ifBlank { " " },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.outline,
                    minLines = 1,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )

                Text(
                    text = "Seen ${item.signalInfo.timeSinceSeen} • ${item.signalInfo.techBadge}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    minLines = 1,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )

                RadarDecisionBadges(item = item)
            }
        }
    }
}

@Composable
private fun RadarPrimaryRow(
    item: RadarUiItem,
    onWatchlistClick: (String) -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = item.displayName,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = item.nameColor.resolve(),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )

        Spacer(modifier = Modifier.width(Dimens.PaddingSmall))

        Text(
            text = item.signalInfo.rssiText,
            color = item.signalInfo.signalColor.resolve(),
            fontWeight = FontWeight.Bold,
            style = MaterialTheme.typography.bodyMedium,
            maxLines = 1,
        )

        IconButton(
            onClick = { onWatchlistClick(item.fingerprint) },
            modifier = Modifier.size(40.dp),
        ) {
            Icon(
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
                    },
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
            modifier = Modifier.fillMaxSize(),
        )
        if (item.isProbing) {
            androidx.compose.material3.CircularProgressIndicator(
                modifier =
                    Modifier
                        .fillMaxSize()
                        .padding(2.dp),
                strokeWidth = 2.dp,
                color = MaterialTheme.colorScheme.tertiary,
            )
        }
    }
}

@Composable
private fun RadarDecisionBadges(item: RadarUiItem) {
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .heightIn(min = 24.dp)
                .padding(top = Dimens.PaddingExtraSmall),
        horizontalArrangement = Arrangement.spacedBy(Dimens.PaddingExtraSmall),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (item.isNew || item.statusInfo.isWarning) {
            Badge(
                text = item.statusInfo.text,
                color =
                    if (item.isNew) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        item.statusInfo.textColor.resolve()
                    },
            )
        }

        if (item.isInWatchlist) {
            Badge(
                text = "WATCHLIST",
                color = MaterialTheme.colorScheme.primary,
            )
        }
    }
}

@Composable
fun Badge(
    text: String,
    color: Color = MaterialTheme.colorScheme.onSurface,
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
