package io.blueeye.core.connectivity.client

import android.bluetooth.BluetoothGattCharacteristic

sealed class BleGattEvent {
    data class ConnectionStateChanged(val status: Int, val newState: Int) : BleGattEvent()

    data class ServicesDiscovered(val status: Int) : BleGattEvent()

    data class CharacteristicRead(
        val characteristic: BluetoothGattCharacteristic,
        val value: ByteArray,
        val status: Int,
    ) : BleGattEvent()
}
