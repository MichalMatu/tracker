package io.blueeye.feature.details

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import io.blueeye.core.model.Device
import io.blueeye.core.model.DeviceConnectionState
import io.blueeye.core.model.GattService
import io.blueeye.core.model.SensorData
import io.blueeye.core.ui.theme.Dimens

internal data class DetailsTechnicalState(
    val device: Device,
    val connectionState: DeviceConnectionState,
    val sensorData: SensorData?,
    val services: List<GattService>,
)

@Composable
internal fun DetailsTechnicalSection(
    state: DetailsTechnicalState,
    onConnect: () -> Unit,
    onDisconnect: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val device = state.device
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(Dimens.PaddingMedium),
    ) {
        Text(
            text = "Technical details",
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.primary,
        )

        ConnectionCard(
            connectionState = state.connectionState,
            onConnect = onConnect,
            onDisconnect = onDisconnect,
        )

        state.sensorData?.let { data ->
            SensorDataCard(data)
        }

        InfoSection(
            title = "Radio",
            items =
                listOf(
                    "PHY" to DetailsUiFormatter.formatPhy(device.primaryPhy, device.secondaryPhy),
                    "Interval" to (device.advertisingIntervalMs?.let { "~${it}ms" } ?: "Unknown"),
                    "Beacon Type" to (device.beaconType ?: "N/A"),
                ),
        )

        val metadata =
            listOfNotNull(
                device.serialNumber?.let { "Serial" to it },
                device.firmwareRevision?.let { "Firmware" to it },
                device.batteryLevel?.let { "Battery" to "$it%" },
            )
        if (metadata.isNotEmpty()) {
            InfoSection(
                title = "Device metadata",
                items = metadata,
            )
        }

        if (state.services.isNotEmpty()) {
            InfoSection(
                title = "Services (${state.services.size})",
                items = state.services.map { service -> service.uuid to service.name },
            )
        }
    }
}
