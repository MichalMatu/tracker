package io.blueeye.core.connectivity.manager

import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothManager
import android.content.Context
import android.util.Log
import dagger.hilt.android.qualifiers.ApplicationContext
import io.blueeye.core.connectivity.client.BleGattClient
import io.blueeye.core.connectivity.client.BleGattEvent
import io.blueeye.core.connectivity.client.BleOperationManager
import io.blueeye.core.connectivity.mapper.BleServiceMapper
import io.blueeye.core.domain.details.DeviceConnectionController
import io.blueeye.core.model.DeviceConnectionState
import io.blueeye.core.model.GattService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class BleConnectionManager
@Inject
constructor(
    @ApplicationContext private val context: Context,
    private val bleGattClient: BleGattClient,
    private val bleOperationManager: BleOperationManager,
    private val bleServiceMapper: BleServiceMapper,
    private val bleCharacteristicParser: io.blueeye.core.connectivity.parser.BleCharacteristicParser,
    private val connectionProbeRecorder: ConnectionProbeRecorder,
) : DeviceConnectionController {
    private val bluetoothAdapter: BluetoothAdapter? =
        (context.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager)?.adapter

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val _connectionState = MutableStateFlow<DeviceConnectionState>(DeviceConnectionState.Disconnected)
    override val connectionState: StateFlow<DeviceConnectionState> = _connectionState.asStateFlow()

    private val _services = MutableStateFlow<List<GattService>>(emptyList())
    override val services: StateFlow<List<GattService>> = _services.asStateFlow()

    private val _currentDeviceAddress = MutableStateFlow<String?>(null)
    val currentDeviceAddress: StateFlow<String?> = _currentDeviceAddress.asStateFlow()

    private val _currentRecordFingerprint = MutableStateFlow<String?>(null)

    // Collected characteristic data during live connection (SHORT_UUID -> HEX_VALUE)
    private val collectedCharData = mutableMapOf<String, String>()

    init {
        // Obserwuj zdarzenia z niskiego poziomu (GATT Client)
        scope.launch { bleGattClient.events.collect { event -> handleGattEvent(event) } }
    }

    private fun handleGattEvent(event: BleGattEvent) {
        when (event) {
            is BleGattEvent.ConnectionStateChanged -> {
                if (event.status != android.bluetooth.BluetoothGatt.GATT_SUCCESS) {
                    // Błąd połączenia (np. 133, 257, 8, 19, 22, 137...)
                    // Lub zewan po prostu
                    _connectionState.value =
                        DeviceConnectionState.Error("Connection Failed/Lost. Status: ${event.status}")
                    bleGattClient.close() // Ważne: zamykamy gatt po błędzie
                    bleOperationManager.reset()
                } else if (event.newState == android.bluetooth.BluetoothProfile.STATE_CONNECTED) {
                    _connectionState.value =
                        DeviceConnectionState.Connected(
                            deviceName = "Connected",
                            macAddress = _currentDeviceAddress.value ?: "",
                        )
                    // Auto-discover services
                    scope.launch {
                        delay(SERVICE_DISCOVERY_DELAY_MS)
                        bleGattClient.discoverServices()
                    }
                } else if (event.newState == android.bluetooth.BluetoothProfile.STATE_DISCONNECTED
                ) {
                    // Poprawne rozłączenie (status == 0)
                    _connectionState.value = DeviceConnectionState.Disconnected
                    _services.value = emptyList()
                    bleOperationManager.reset()
                    bleGattClient.close()
                }
            }
            is BleGattEvent.ServicesDiscovered -> {
                if (event.status == android.bluetooth.BluetoothGatt.GATT_SUCCESS) {
                    val gattServices = bleGattClient.getServices()
                    _services.value = bleServiceMapper.map(gattServices)

                    // Czytamy wszystkie charakterystyki po kolei
                    scope.launch {
                        // 1. Zapisz podstawową strukturę (usługi) do bazy
                        currentRecordFingerprint()?.let { fingerprint ->
                            connectionProbeRecorder.recordServices(
                                fingerprint,
                                gattServices
                            )
                        }
                        // 2. Czytaj wartości
                        readAllReadableCharacteristics(gattServices)
                    }
                } else {
                    _connectionState.value =
                        DeviceConnectionState.Error("Service Discovery Failed: ${event.status}")
                }
            }
            is BleGattEvent.CharacteristicRead -> {
                // Przekazujemy wynik do OperationManager (żeby odblokował coroutine czekającą na
                // wynik)
                bleOperationManager.onReadResult(event.characteristic, event.value, event.status)

                // Oraz aktualizujemy UI
                if (event.status == android.bluetooth.BluetoothGatt.GATT_SUCCESS) {
                    val hexValue = event.value.joinToString("") { "%02X".format(it) }
                    val stringValue = bleCharacteristicParser.parseStringValue(event.value)
                    val displayValue = "0xHex:$hexValue\nStr:$stringValue"

                    Log.d("BleManager", "Read Char: ${event.characteristic.uuid} -> $stringValue")

                    updateServiceValue(event.characteristic.uuid.toString(), displayValue)

                    // Collect characteristic data for saving to DB
                    val shortUuid = event.characteristic.uuid.toString().substring(
                        SHORT_UUID_START,
                        SHORT_UUID_END
                    ).uppercase()
                    collectedCharData[shortUuid] = hexValue

                    // === SAVE STANDARD CHARACTERISTICS TO DB ===
                    val uuid = event.characteristic.uuid.toString()
                    val fingerprint = currentRecordFingerprint()

                    if (fingerprint != null) {
                        scope.launch {
                            connectionProbeRecorder.recordStandardCharacteristic(
                                fingerprint,
                                uuid,
                                event.value,
                                stringValue
                            )
                        }
                    }
                }
            }
        }
    }

    @SuppressLint("MissingPermission")
    override fun connect(
        macAddress: String,
        recordFingerprint: String,
    ): Result<Unit> =
        runCatching {
            connectInternal(macAddress, recordFingerprint)
        }

    @SuppressLint("MissingPermission")
    private fun connectInternal(
        macAddress: String,
        recordFingerprint: String,
    ) {
        if (bluetoothAdapter == null || !bluetoothAdapter.isEnabled) {
            _connectionState.value = DeviceConnectionState.Error("Bluetooth disabled")
            return
        }

        if (_connectionState.value is DeviceConnectionState.Connected &&
            _currentDeviceAddress.value == macAddress
        ) {
            return
        }

        disconnectInternal() // Clean up previous

        collectedCharData.clear() // Reset collected data
        _currentDeviceAddress.value = macAddress
        _currentRecordFingerprint.value = recordFingerprint
        _connectionState.value = DeviceConnectionState.Connecting

        try {
            val device = bluetoothAdapter.getRemoteDevice(macAddress)
            bleGattClient.connect(context, device)
        } catch (e: IllegalArgumentException) {
            Log.e("BleManager", "Invalid MAC address: $macAddress", e)
            _connectionState.value = DeviceConnectionState.Error("Invalid address: $macAddress")
        }
    }

    override fun disconnect(): Result<Unit> =
        runCatching {
            disconnectInternal()
        }

    private fun disconnectInternal() {
        bleGattClient.disconnect()
        bleGattClient.close()
        _connectionState.value = DeviceConnectionState.Disconnected
        _services.value = emptyList()
        _currentDeviceAddress.value = null
        _currentRecordFingerprint.value = null
    }

    private suspend fun readAllReadableCharacteristics(services: List<android.bluetooth.BluetoothGattService>) {
        Log.d("BleManager", "Reading ALL readable characteristics for ${services.size} services...")
        services.forEach { service ->
            val readableChars = service.characteristics.filter {
                (it.properties and BluetoothGattCharacteristic.PROPERTY_READ) != 0
            }
            Log.d("BleManager", "Service ${service.uuid} has ${readableChars.size} readable chars")

            readableChars.forEach { char ->
                try {
                    Log.d("BleManager", "Initiating read for char: ${char.uuid}")
                    bleOperationManager.read(char)
                    delay(READ_OPERATION_DELAY_MS)
                } catch (@Suppress("TooGenericExceptionCaught") e: Exception) {
                    Log.w("BleManager", "Failed to read ${char.uuid}: ${e.message}")
                }
            }
        }
        Log.d("BleManager", "Finished reading all characteristics. Total collected: ${collectedCharData.size}")

        // Save all collected characteristic data to database
        currentRecordFingerprint()?.let { fingerprint ->
            connectionProbeRecorder.saveBufferedCharacteristics(fingerprint, collectedCharData)
        }
    }

    private fun currentRecordFingerprint(): String? = _currentRecordFingerprint.value ?: _currentDeviceAddress.value

    private fun updateServiceValue(
        charUuid: String,
        newValue: String,
    ) {
        val currentServices = _services.value
        val updatedServices =
            currentServices.map { service ->
                service.copy(
                    characteristics =
                    service.characteristics.map { char ->
                        if (char.uuid == charUuid) {
                            char.copy(value = newValue)
                        } else {
                            char
                        }
                    },
                )
            }
        _services.value = updatedServices
    }

    private companion object {
        private const val SERVICE_DISCOVERY_DELAY_MS = 300L
        private const val READ_OPERATION_DELAY_MS = 50L
        private const val SHORT_UUID_START = 4
        private const val SHORT_UUID_END = 8
    }
}
