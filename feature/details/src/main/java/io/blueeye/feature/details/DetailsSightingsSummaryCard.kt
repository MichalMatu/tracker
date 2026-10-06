package io.blueeye.feature.details

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import io.blueeye.core.ui.stableLiveHeight
import io.blueeye.core.ui.theme.Dimens

@Composable
fun DetailsSightingsSummaryCard(summary: DetailsSightingsSummary) {
    Card(modifier = Modifier.fillMaxWidth().stableLiveHeight()) {
        Column(
            modifier = Modifier.padding(Dimens.PaddingMedium),
            verticalArrangement = Arrangement.spacedBy(Dimens.PaddingSmall),
        ) {
            Text(
                text = "Recent phone observations",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.primary,
            )
            Text(
                text =
                    "These are locations where your phone observed the Bluetooth signal, " +
                        "not the exact location of the device.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            if (summary.hasUsableLocations) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Text(
                        text = "${summary.acceptedObservationCount} usable observations",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    Text(
                        text = "${summary.clusters.size} map groups",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
                if (summary.rejectedObservationCount > 0) {
                    Text(
                        text =
                            "${summary.rejectedObservationCount} observations were omitted because " +
                                "location accuracy was missing, invalid, or worse than 100 m.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                if (summary.omittedClusterCount > 0) {
                    Text(
                        text =
                            "${summary.omittedClusterCount} older/sparser groups are hidden to keep the map bounded.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            } else {
                Text(
                    text =
                        "Location observations exist, but their accuracy is too poor to map responsibly.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}
