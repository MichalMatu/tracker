package io.blueeye.feature.radar.presentation

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import io.blueeye.core.ui.theme.Dimens

@Composable
fun RadarProtocolGroupCard(
    group: RadarProtocolEntry.Group,
    expanded: Boolean,
    onToggle: () -> Unit
) {
    Surface(
        modifier =
            Modifier.fillMaxWidth().padding(
                horizontal = Dimens.PaddingMedium,
                vertical = Dimens.PaddingExtraSmall
            ).clickable(onClick = onToggle),
        shape = MaterialTheme.shapes.medium,
        tonalElevation = 1.dp,
    ) {
        Row(modifier = Modifier.padding(Dimens.PaddingMedium), verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = Modifier.weight(1f)) {
                Text(group.family.title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                Text(
                    "${group.activeCount} active now · ${group.members.size} identities seen in last 3 min",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                group.strongestActiveRssi?.let { rssi ->
                    Text(
                        "Strongest active signal $rssi dBm",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.outline
                    )
                }
            }
            Text(
                if (expanded) "Hide" else "Show",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary
            )
        }
    }
}
