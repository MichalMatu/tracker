package io.blueeye.feature.details

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import io.blueeye.core.ui.theme.Dimens

@Composable
fun DetailsSightingsSummaryCard(summary: DetailsSightingsSummary) {
    var showMap by remember { mutableStateOf(false) }

    Card(modifier = Modifier.fillMaxWidth()) {
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

                Text(
                    text =
                        "Showing the map requests third-party map tiles for the displayed area. " +
                            "Stored sighting records are not uploaded.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                TextButton(onClick = { showMap = !showMap }) {
                    Text(if (showMap) "Hide map" else "Show map")
                }
                if (showMap) {
                    DetailsSightingsMapView(clusters = summary.clusters)
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
