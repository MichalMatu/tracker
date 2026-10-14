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
    private val sessionLock = Any()
    private var sessionEpoch = 0L

    fun currentSessionEpoch(): Long = synchronized(sessionLock) { sessionEpoch }

    private fun epochFor(gatt: BluetoothGatt): Long? =
        synchronized(sessionLock) { sessionEpoch.takeIf { gatt === bluetoothGatt } }

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
                val epoch = epochFor(gatt) ?: run {
                    Log.d(TAG, "Ignoring stale connection callback")
                    return
                }
                Log.d(
                    TAG,
                    "onConnectionStateChange: status=$status newState=$newState",
                )
                _events.tryEmit(BleGattEvent.ConnectionStateChanged(status, newState, epoch))
            }

            override fun onServicesDiscovered(
                gatt: BluetoothGatt,
                status: Int,
            ) {
                val epoch = epochFor(gatt) ?: run {
                    Log.d(TAG, "Ignoring stale services callback")
                    return
                }
                Log.d(TAG, "onServicesDiscovered: status=$status")
                _events.tryEmit(BleGattEvent.ServicesDiscovered(status, epoch))
            }

            override fun onCharacteristicRead(
                gatt: BluetoothGatt,
                characteristic: BluetoothGattCharacteristic,
                value: ByteArray,
                status: Int,
            ) {
                val epoch = epochFor(gatt) ?: run {
                    Log.d(TAG, "Ignoring stale characteristic callback")
                    return
                }
                _events.tryEmit(BleGattEvent.CharacteristicRead(characteristic, value, status, epoch))
            }

            // Dla starszych Androidów
            @Deprecated("Deprecated in Java")
            override fun onCharacteristicRead(
                gatt: BluetoothGatt,
                characteristic: BluetoothGattCharacteristic,
                status: Int,
            ) {
                val epoch = epochFor(gatt) ?: run {
                    Log.d(TAG, "Ignoring stale legacy characteristic callback")
                    return
                }
                @Suppress("DEPRECATION")
                _events.tryEmit(
                    BleGattEvent.CharacteristicRead(
                        characteristic,
                        characteristic.value,
                        status,
                        epoch,
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
        val connectedGatt = device.connectGatt(context, false, callback, BluetoothDevice.TRANSPORT_LE)
        synchronized(sessionLock) { bluetoothGatt = connectedGatt }
    }

    @SuppressLint("MissingPermission")
    fun disconnect() {
        bluetoothGatt?.disconnect()
    }

    @SuppressLint("MissingPermission")
    fun close() {
        val oldGatt = synchronized(sessionLock) {
            val previous = bluetoothGatt
            bluetoothGatt = null
            sessionEpoch++
            previous
        }
        try {
            oldGatt?.close()
        } catch (@Suppress("TooGenericExceptionCaught") e: Exception) {
            Log.e(TAG, "Error closing GATT", e)
        }
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
