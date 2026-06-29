package io.blueeye.feature.radar.presentation

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.width
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import io.blueeye.core.domain.model.DeviceFilter
import io.blueeye.core.domain.model.TechnologyFilter
import io.blueeye.core.ui.theme.extendedColors

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun FilterDialog(
    currentFilter: DeviceFilter,
    availableVendors: List<String>,
    onDismiss: () -> Unit,
    onApply: (DeviceFilter) -> Unit,
    onClear: () -> Unit
) {
    // Local state for editing
    val hideUnknown = remember { mutableStateOf(currentFilter.hideUnknown) }
    val onlyConnectable = remember { mutableStateOf(currentFilter.onlyConnectable) }
    val selectedTechnologies = remember { mutableStateOf(currentFilter.technologies) }

    // Vendor filtering can be added later as it requires more complex UI (dropdown/search)
    // For now we stick to the main functional filters requested.

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Filter Devices") },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Toggles
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Hide Unknown Devices")
                    Switch(
                        checked = hideUnknown.value,
                        onCheckedChange = { hideUnknown.value = it }
                    )
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Only Connectable")
                    Switch(
                        checked = onlyConnectable.value,
                        onCheckedChange = { onlyConnectable.value = it }
                    )
                }

                // Technologies
                Text(
                    "Technology",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold
                )

                FlowRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    TechnologyFilter.entries.forEach { tech ->
                        val isSelected = selectedTechnologies.value.contains(tech)
                        FilterChip(
                            selected = isSelected,
                            onClick = {
                                val current = selectedTechnologies.value.toMutableSet()
                                if (isSelected) current.remove(tech) else current.add(tech)
                                selectedTechnologies.value = current
                            },
                            label = { Text(tech.displayName) }
                        )
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    onApply(
                        currentFilter.copy(
                            hideUnknown = hideUnknown.value,
                            onlyConnectable = onlyConnectable.value,
                            technologies = selectedTechnologies.value
                        )
                    )
                }
            ) {
                Text("Apply")
            }
        },
        dismissButton = {
            Row {
                TextButton(onClick = onClear) {
                    Text("Clear All", color = MaterialTheme.extendedColors.dangerous)
                }
                Spacer(modifier = Modifier.width(8.dp))
                TextButton(onClick = onDismiss) {
                    Text("Cancel")
                }
            }
        }
    )
}
