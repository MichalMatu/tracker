package io.blueeye.feature.settings

import io.blueeye.core.model.Device
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

internal object SessionDecodedSignalExportMapper {
    fun countDevices(devices: List<Device>): Int = devices.count { device -> device.hasDecodedSignal() }

    fun kindCounts(devices: List<Device>): JsonObject =
        buildJsonObject {
            DecodedSignalKind.entries.forEach { kind ->
                put(kind.name, devices.count { device -> kind in device.decodedSignalKinds() })
            }
        }

    fun decodedSignals(devices: List<Device>): JsonArray =
        JsonArray(
            devices
                .filter { device -> device.hasDecodedSignal() }
                .map(::mapDecodedSignal),
        )

    fun decodedSignal(device: Device): JsonElement =
        if (device.hasDecodedSignal()) {
            mapDecodedSignal(device)
        } else {
            JsonNull
        }

    private fun mapDecodedSignal(device: Device): JsonObject =
        buildJsonObject {
            val rawPayload = device.lastRawData?.takeIf { payload -> payload.isNotBlank() }
            put("fingerprint", device.fingerprint)
            put("displayName", device.getDisplayName())
            put("beaconType", device.beaconType)
            put("sensorData", device.sensorData)
            put("hasRawPayload", rawPayload != null)
            if (rawPayload == null) {
                put("rawPayloadByteLength", JsonNull)
            } else {
                put("rawPayloadByteLength", rawPayload.length / 2)
            }
            put(
                "kinds",
                JsonArray(device.decodedSignalKinds().map { kind -> JsonPrimitive(kind.name) }),
            )
        }

    private fun Device.hasDecodedSignal(): Boolean = decodedSignalKinds().isNotEmpty()

    private fun Device.decodedSignalKinds(): List<DecodedSignalKind> =
        listOfNotNull(
            DecodedSignalKind.BEACON_TYPE.takeIf { !beaconType.isNullOrBlank() },
            DecodedSignalKind.SENSOR_DATA.takeIf { !sensorData.isNullOrBlank() },
            DecodedSignalKind.RAW_PAYLOAD.takeIf { !lastRawData.isNullOrBlank() },
        )
}

private enum class DecodedSignalKind {
    BEACON_TYPE,
    SENSOR_DATA,
    RAW_PAYLOAD,
}
