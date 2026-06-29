package io.blueeye.core.data.details

import io.blueeye.core.connectivity.resolver.BluetoothNamesResolver
import io.blueeye.core.domain.details.DeviceServiceResolver
import io.blueeye.core.model.Device
import io.blueeye.core.model.GattCharacteristic
import io.blueeye.core.model.GattService
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class DeviceServiceResolverImpl
    @Inject
    constructor(
        private val bleNamesResolver: BluetoothNamesResolver,
    ) : DeviceServiceResolver {
        override fun resolvePersistedServices(device: Device): Result<List<GattService>> =
            runCatching {
                val services = device.gattServices?.takeIf { it.isNotBlank() } ?: return@runCatching emptyList()
                if (services.contains(STRUCTURED_SERVICE_MARKER)) {
                    parseStructuredServices(device, services)
                } else {
                    parseUuidList(services)
                }
            }

        private fun parseStructuredServices(
            device: Device,
            services: String,
        ): List<GattService> {
            val characteristicData = device.characteristicData.toCharacteristicDataMap()
            return services.split(STRUCTURED_SERVICE_SEPARATOR).mapNotNull { serviceEntry ->
                val parts = serviceEntry.split(SERVICE_CHARACTERISTIC_SEPARATOR, limit = SERVICE_PART_LIMIT)
                if (parts.size < SERVICE_PART_LIMIT) return@mapNotNull null

                val serviceUuid = parts[0].toFullBluetoothUuid()
                val charsContent = parts[1].removeSurrounding("[", "]")
                GattService(
                    uuid = serviceUuid,
                    name = bleNamesResolver.resolveServiceName(serviceUuid),
                    characteristics = parseCharacteristics(charsContent, characteristicData),
                )
            }
        }

        private fun parseCharacteristics(
            charsContent: String,
            characteristicData: Map<String, String?>,
        ): List<GattCharacteristic> {
            if (charsContent.isEmpty()) return emptyList()

            return charsContent.split(",").map { charPrefix ->
                val charUuid = charPrefix.toFullBluetoothUuid()
                val shortUuid = charPrefix.takeLast(SHORT_UUID_HEX_LENGTH).uppercase()
                GattCharacteristic(
                    uuid = charUuid,
                    name = bleNamesResolver.resolveCharName(charUuid),
                    properties = emptyList(),
                    value = characteristicData[shortUuid] ?: characteristicData[charUuid.uppercase()],
                )
            }
        }

        private fun parseUuidList(services: String): List<GattService> =
            services.split(",")
                .map { it.trim() }
                .filter { it.isNotBlank() }
                .distinct()
                .map { serviceUuid ->
                    val fullUuid = serviceUuid.toFullBluetoothUuid()
                    GattService(
                        uuid = fullUuid,
                        name = bleNamesResolver.resolveServiceName(fullUuid),
                        characteristics = emptyList(),
                    )
                }

        private fun String?.toCharacteristicDataMap(): Map<String, String?> =
            this
                ?.split("|")
                ?.filter { it.isNotBlank() }
                ?.associate { entry ->
                    if (entry.contains("=")) {
                        val parts = entry.split("=", limit = CHARACTERISTIC_DATA_PART_LIMIT)
                        parts[0] to parts[1]
                    } else {
                        entry.substringBefore("[") to null
                    }
                } ?: emptyMap()

        private fun String.toFullBluetoothUuid(): String =
            when (length) {
                SHORT_UUID_HEX_LENGTH -> "0000$this-0000-1000-8000-00805f9b34fb"
                SHORT_SERVICE_UUID_HEX_LENGTH -> "$this-0000-1000-8000-00805f9b34fb"
                else -> this
            }

        private companion object {
            private const val STRUCTURED_SERVICE_SEPARATOR = ";"
            private const val STRUCTURED_SERVICE_MARKER = ":["
            private const val SERVICE_CHARACTERISTIC_SEPARATOR = ":"
            private const val SERVICE_PART_LIMIT = 2
            private const val CHARACTERISTIC_DATA_PART_LIMIT = 2
            private const val SHORT_UUID_HEX_LENGTH = 4
            private const val SHORT_SERVICE_UUID_HEX_LENGTH = 8
        }
    }
