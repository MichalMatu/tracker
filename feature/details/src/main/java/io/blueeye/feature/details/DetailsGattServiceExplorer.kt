package io.blueeye.feature.details

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import io.blueeye.core.model.GattCharacteristic
import io.blueeye.core.model.GattService
import io.blueeye.core.ui.theme.Dimens

/** Live GATT discovery and saved active-probe evidence are different sources. */
@Composable
internal fun DetailsGattServiceExplorer(
    liveServices: List<GattService>,
    savedServices: String?,
    savedCharacteristics: String?,
) {
    if (!DetailsGattEvidencePolicy.hasEvidence(liveServices, savedServices, savedCharacteristics)) return

    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(Dimens.PaddingSmall),
    ) {
        Text(
            text = "GATT services (live: ${liveServices.size})",
            style = MaterialTheme.typography.titleMedium,
        )
        liveServices.forEach { service ->
            var expanded by rememberSaveable(service.uuid) { mutableStateOf(false) }
            OutlinedButton(
                onClick = { expanded = !expanded },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text("${service.name} (${service.characteristics.size}) — ${if (expanded) "Hide" else "Show"}")
            }
            if (expanded) {
                Text(service.uuid, style = MaterialTheme.typography.bodySmall)
                if (service.characteristics.isEmpty()) {
                    Text("No characteristics discovered", style = MaterialTheme.typography.bodySmall)
                }
                service.characteristics.forEach { characteristic ->
                    val info = DetailsGattCharacteristicFormatter.format(characteristic)
                    Text(info.heading, style = MaterialTheme.typography.bodyMedium)
                    Text(info.properties, style = MaterialTheme.typography.bodySmall)
                    info.value?.let { value ->
                        Text(value, style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
        }

        if (!savedServices.isNullOrBlank() || !savedCharacteristics.isNullOrBlank()) {
            var savedExpanded by rememberSaveable { mutableStateOf(false) }
            OutlinedButton(
                onClick = { savedExpanded = !savedExpanded },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(if (savedExpanded) "Hide saved GATT evidence" else "Show saved GATT evidence")
            }
            if (savedExpanded) {
                Text(
                    text = "Previously discovered via active connection (not BLE advertising)",
                    style = MaterialTheme.typography.bodySmall,
                )
                savedServices?.takeIf { it.isNotBlank() }?.let { services ->
                    Text(services, style = MaterialTheme.typography.bodySmall)
                }
                savedCharacteristics?.takeIf { it.isNotBlank() }?.let { data ->
                    Text("Saved characteristic values: $data", style = MaterialTheme.typography.bodySmall)
                }
            }
        }
    }
}

/** Recorded characteristic values are valid active evidence even if service-list persistence is absent. */
internal object DetailsGattEvidencePolicy {
    fun hasEvidence(
        liveServices: List<GattService>,
        savedServices: String?,
        savedCharacteristics: String?,
    ): Boolean =
        liveServices.isNotEmpty() || !savedServices.isNullOrBlank() || !savedCharacteristics.isNullOrBlank()
}

internal data class DetailsGattCharacteristicInfo(
    val heading: String,
    val properties: String,
    val value: String?,
)

internal object DetailsGattCharacteristicFormatter {
    fun format(characteristic: GattCharacteristic): DetailsGattCharacteristicInfo =
        DetailsGattCharacteristicInfo(
            heading = "${characteristic.name} — ${characteristic.uuid}",
            properties = "Properties: ${characteristic.properties.joinToString(", ").ifEmpty { "Unknown" }}",
            value = characteristic.value?.let { "Read value: $it" },
        )
}
