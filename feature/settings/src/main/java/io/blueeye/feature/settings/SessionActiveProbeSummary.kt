package io.blueeye.feature.settings

import io.blueeye.core.model.Device
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

data class SessionActiveProbeSummary(
    val dataDeviceCount: Int = 0,
    val statusCounts: Map<String, Int> = emptyMap(),
)

internal object SessionActiveProbeSummaryCalculator {
    fun calculate(devices: List<Device>): SessionActiveProbeSummary {
        val activeProbeDevices = devices.filter(::hasActiveProbeData)
        return SessionActiveProbeSummary(
            dataDeviceCount = activeProbeDevices.size,
            statusCounts =
                activeProbeDevices
                    .groupingBy(::connectionStatus)
                    .eachCount()
                    .toSortedMap(),
        )
    }

    fun hasActiveProbeData(device: Device): Boolean =
        listOf(
            device.modelNumber,
            device.serialNumber,
            device.firmwareRevision,
            device.hardwareRevision,
            device.softwareRevision,
            device.manufacturerName,
            device.gattServices,
            device.characteristicData,
            device.probeError,
        ).any { value -> !value.isNullOrBlank() } ||
            device.batteryLevel != null ||
            device.connectionAttempts > 0 ||
            device.lastProbeTimestamp > 0L ||
            connectionStatus(device) != NoConnectionStatus

    private fun connectionStatus(device: Device): String = device.connectionStatus.ifBlank { NoConnectionStatus }

    private const val NoConnectionStatus = "NONE"
}

internal fun SessionActiveProbeSummary.statusCountsJson(): JsonObject =
    buildJsonObject {
        statusCounts.forEach { (status, count) -> put(status, count) }
    }
