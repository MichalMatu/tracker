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
    @Volatile
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
                if (!isCurrentGatt(gatt)) {
                    Log.d(TAG, "Ignoring stale connection callback")
                    return
                }
                Log.d(
                    TAG,
                    "onConnectionStateChange: status=$status newState=$newState",
                )
                _events.tryEmit(BleGattEvent.ConnectionStateChanged(status, newState))
            }

            override fun onServicesDiscovered(
                gatt: BluetoothGatt,
                status: Int,
            ) {
                if (!isCurrentGatt(gatt)) {
                    Log.d(TAG, "Ignoring stale services callback")
                    return
                }
                Log.d(TAG, "onServicesDiscovered: status=$status")
                _events.tryEmit(BleGattEvent.ServicesDiscovered(status))
            }

            override fun onCharacteristicRead(
                gatt: BluetoothGatt,
                characteristic: BluetoothGattCharacteristic,
                value: ByteArray,
                status: Int,
            ) {
                if (!isCurrentGatt(gatt)) {
                    Log.d(TAG, "Ignoring stale characteristic callback")
                    return
                }
                _events.tryEmit(BleGattEvent.CharacteristicRead(characteristic, value, status))
            }

            // Dla starszych Androidów
            @Deprecated("Deprecated in Java")
            override fun onCharacteristicRead(
                gatt: BluetoothGatt,
                characteristic: BluetoothGattCharacteristic,
                status: Int,
            ) {
                if (!isCurrentGatt(gatt)) {
                    Log.d(TAG, "Ignoring stale legacy characteristic callback")
                    return
                }
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

    private fun isCurrentGatt(gatt: BluetoothGatt): Boolean = gatt === bluetoothGatt

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
            Log.e(TAG, "Error closing GATT", e)
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

    private companion object {
        private const val TAG = "BleGattClient"
    }
}
