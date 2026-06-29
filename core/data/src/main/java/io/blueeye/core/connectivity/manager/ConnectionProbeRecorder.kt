package io.blueeye.core.connectivity.manager

import android.bluetooth.BluetoothGattService
import io.blueeye.core.domain.repository.DeviceRepository
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Handles persistence of connection probe data to the DeviceRepository.
 */
@Singleton
class ConnectionProbeRecorder @Inject constructor(
    private val deviceRepository: DeviceRepository
) {
    private companion object {
        const val UUID_16_START = 4
        const val UUID_16_END = 8
        const val BT_BASE_UUID = "-0000-1000-8000-00805f9b34fb"
    }

    suspend fun recordServices(mac: String, services: List<BluetoothGattService>) {
        val sb = StringBuilder()
        services.forEach { service ->
            val sUuid = service.uuid.toString()
            val sPrefix = if (sUuid.startsWith("0000") && sUuid.endsWith(BT_BASE_UUID)) {
                sUuid.substring(UUID_16_START, UUID_16_END)
            } else {
                sUuid
            }

            sb.append(sPrefix).append(":[")
            val charPrefixes = service.characteristics.map { char ->
                val cUuid = char.uuid.toString()
                if (cUuid.startsWith("0000") && cUuid.endsWith(BT_BASE_UUID)) {
                    cUuid.substring(UUID_16_START, UUID_16_END)
                } else {
                    cUuid
                }
            }
            sb.append(charPrefixes.joinToString(",")).append("];")
        }

        val params = io.blueeye.core.domain.repository.RepoProbeParams(
            status = "CONNECTED",
            attempts = 1,
            timestamp = System.currentTimeMillis(),
            model = null,
            serial = null,
            firmware = null,
            hardware = null,
            software = null,
            manufacturer = null,
            battery = null,
            services = sb.toString().removeSuffix(";")
        )
        deviceRepository.updateProbeData(mac, params)
    }

    suspend fun recordStandardCharacteristic(mac: String, uuid: String, value: ByteArray, stringValue: String) {
        val now = System.currentTimeMillis()
        when {
            uuid.contains("00002a24") -> {
                val params = io.blueeye.core.domain.repository.RepoProbeParams(
                    status = "CONNECTED",
                    attempts = 1,
                    timestamp = now,
                    model = stringValue,
                    serial = null,
                    firmware = null,
                    hardware = null,
                    software = null,
                    manufacturer = null,
                    battery = null,
                    services = null
                )
                deviceRepository.updateProbeData(mac, params)
            }
            uuid.contains("00002a29") -> {
                val params = io.blueeye.core.domain.repository.RepoProbeParams(
                    status = "CONNECTED",
                    attempts = 1,
                    timestamp = now,
                    model = null,
                    serial = null,
                    firmware = null,
                    hardware = null,
                    software = null,
                    manufacturer = stringValue,
                    battery = null,
                    services = null
                )
                deviceRepository.updateProbeData(mac, params)
            }
            uuid.contains("00002a26") -> {
                val params = io.blueeye.core.domain.repository.RepoProbeParams(
                    status = "CONNECTED",
                    attempts = 1,
                    timestamp = now,
                    model = null,
                    serial = null,
                    firmware = stringValue,
                    hardware = null,
                    software = null,
                    manufacturer = null,
                    battery = null,
                    services = null
                )
                deviceRepository.updateProbeData(mac, params)
            }
            uuid.contains("00002a19") -> {
                val battery = if (value.isNotEmpty()) value[0].toInt() else null
                val params = io.blueeye.core.domain.repository.RepoProbeParams(
                    status = "CONNECTED",
                    attempts = 1,
                    timestamp = now,
                    model = null,
                    serial = null,
                    firmware = null,
                    hardware = null,
                    software = null,
                    manufacturer = null,
                    battery = battery,
                    services = null
                )
                deviceRepository.updateProbeData(mac, params)
            }
        }
    }

    suspend fun saveBufferedCharacteristics(mac: String, charData: Map<String, String>) {
        if (charData.isEmpty()) return
        val dataStr = charData.entries.joinToString("|") { "${it.key}=${it.value}" }
        val params = io.blueeye.core.domain.repository.RepoProbeParams(
            status = "CONNECTED",
            attempts = 1,
            timestamp = System.currentTimeMillis(),
            model = null,
            serial = null,
            firmware = null,
            hardware = null,
            software = null,
            manufacturer = null,
            battery = null,
            services = null,
            charData = dataStr
        )
        deviceRepository.updateProbeData(mac, params)
    }
}
