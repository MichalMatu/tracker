package io.blueeye.core.connectivity.client

import android.annotation.SuppressLint
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCallback
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothGattService
import android.content.Context
import android.util.Log
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class BleGattClient
@Inject
constructor() {
    private var bluetoothGatt: BluetoothGatt? = null

    // Używamy SharedFlow z replay=0 i extraBufferCapacity, żeby nie gubić zdarzeń
    private val _events =
        MutableSharedFlow<BleGattEvent>(
            replay = 0,
            extraBufferCapacity = 64,
            onBufferOverflow = BufferOverflow.DROP_OLDEST,
        )
    val events: SharedFlow<BleGattEvent> = _events.asSharedFlow()

    private val callback =
        object : BluetoothGattCallback() {
            override fun onConnectionStateChange(
                gatt: BluetoothGatt,
                status: Int,
                newState: Int,
            ) {
                Log.d(
                    "BleGattClient",
                    "onConnectionStateChange: status=$status newState=$newState",
                )
                _events.tryEmit(BleGattEvent.ConnectionStateChanged(status, newState))
            }

            override fun onServicesDiscovered(
                gatt: BluetoothGatt,
                status: Int,
            ) {
                Log.d("BleGattClient", "onServicesDiscovered: status=$status")
                _events.tryEmit(BleGattEvent.ServicesDiscovered(status))
            }

            override fun onCharacteristicRead(
                gatt: BluetoothGatt,
                characteristic: BluetoothGattCharacteristic,
                value: ByteArray,
                status: Int,
            ) {
                _events.tryEmit(BleGattEvent.CharacteristicRead(characteristic, value, status))
            }

            // Dla starszych Androidów
            @Deprecated("Deprecated in Java")
            override fun onCharacteristicRead(
                gatt: BluetoothGatt,
                characteristic: BluetoothGattCharacteristic,
                status: Int,
            ) {
                @Suppress("DEPRECATION")
                _events.tryEmit(
                    BleGattEvent.CharacteristicRead(
                        characteristic,
                        characteristic.value,
                        status,
                    ),
                )
            }
        }

    @SuppressLint("MissingPermission")
    fun connect(
        context: Context,
        device: BluetoothDevice,
    ) {
        close() // Zamknij poprzednie jeśli istnieje
        bluetoothGatt = device.connectGatt(context, false, callback, BluetoothDevice.TRANSPORT_LE)
    }

    @SuppressLint("MissingPermission")
    fun disconnect() {
        bluetoothGatt?.disconnect()
    }

    @SuppressLint("MissingPermission")
    fun close() {
        try {
            bluetoothGatt?.close()
        } catch (@Suppress("TooGenericExceptionCaught") e: Exception) {
            Log.e("BleGattClient", "Error closing GATT", e)
        }
        bluetoothGatt = null
    }

    @SuppressLint("MissingPermission")
    fun discoverServices() {
        bluetoothGatt?.discoverServices()
    }

    @SuppressLint("MissingPermission")
    fun readCharacteristic(characteristic: BluetoothGattCharacteristic): Boolean {
        return bluetoothGatt?.readCharacteristic(characteristic) ?: false
    }

    fun getService(uuid: java.util.UUID): BluetoothGattService? {
        return bluetoothGatt?.getService(uuid)
    }

    fun getServices(): List<BluetoothGattService> {
        return bluetoothGatt?.services ?: emptyList()
    }
}
