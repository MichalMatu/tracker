package io.blueeye.core.connectivity.client

import android.bluetooth.BluetoothGattCharacteristic
import kotlinx.coroutines.CancellableContinuation
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeout
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

@Singleton
class BleOperationManager
@Inject
constructor(private val bleGattClient: BleGattClient) {
    companion object {
        private const val READ_TIMEOUT_MS = 2000L
    }
    private val mutex = Mutex()
    private var pendingReadContinuation: CancellableContinuation<ByteArray>? = null

    suspend fun read(characteristic: BluetoothGattCharacteristic): ByteArray =
        mutex.withLock {
            // Zabezpieczenie timeoutem, żeby nie zablokować kolejki na zawsze
            withTimeout(READ_TIMEOUT_MS) { // 2 sekundy timeout na operację
                suspendCancellableCoroutine { cont ->
                    pendingReadContinuation = cont

                    val success = bleGattClient.readCharacteristic(characteristic)
                    if (!success) {
                        pendingReadContinuation = null
                        cont.resumeWithException(
                            Exception("Failed to initiate read for ${characteristic.uuid}"),
                        )
                    }
                }
            }
        }

    // Ta metoda musi byc wywolana gdy przyjdzie callback z BleGattClient
    fun onReadResult(
        @Suppress("UNUSED_PARAMETER") characteristic: BluetoothGattCharacteristic,
        value: ByteArray,
        status: Int,
    ) {
        val cont = pendingReadContinuation
        if (cont != null && cont.isActive) {
            if (status == android.bluetooth.BluetoothGatt.GATT_SUCCESS) {
                cont.resume(value)
            } else {
                cont.resumeWithException(Exception("GATT Read failed with status $status"))
            }
        }
        pendingReadContinuation = null
    }

    // Resetuje stan (np. przy rozłączeniu)
    fun reset() {
        pendingReadContinuation?.cancel(Exception("Disconnected"))
        pendingReadContinuation = null
        if (mutex.isLocked) {
            // Uwaga: Mutex nie ma metody force unlock, ale przy nowym połączeniu i tak będziemy
            // używać nowej instancji managera
            // lub po prostu musimy dbać o flow.
        }
    }
}
