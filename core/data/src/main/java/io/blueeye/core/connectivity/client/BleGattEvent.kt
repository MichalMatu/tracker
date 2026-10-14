package io.blueeye.core.connectivity.client

import android.bluetooth.BluetoothGattCharacteristic

sealed class BleGattEvent {
    abstract val sessionEpoch: Long

    data class ConnectionStateChanged(
        val status: Int,
        val newState: Int,
        override val sessionEpoch: Long,
    ) : BleGattEvent()

    data class ServicesDiscovered(
        val status: Int,
        override val sessionEpoch: Long,
    ) : BleGattEvent()

    data class CharacteristicRead(
        val characteristic: BluetoothGattCharacteristic,
        val value: ByteArray,
        val status: Int,
        override val sessionEpoch: Long,
    ) : BleGattEvent()
}
