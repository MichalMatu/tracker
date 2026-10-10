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
    private val stateLock = Any()
    private var pendingRead: PendingRead? = null

    suspend fun read(characteristic: BluetoothGattCharacteristic): ByteArray =
        mutex.withLock {
            withTimeout(READ_TIMEOUT_MS) {
                suspendCancellableCoroutine { continuation ->
                    val request = PendingRead(characteristic, continuation)
                    synchronized(stateLock) { pendingRead = request }
                    continuation.invokeOnCancellation { clearPending(request) }

                    val started = runCatching {
                        bleGattClient.readCharacteristic(characteristic)
                    }.getOrElse { error ->
                        clearPending(request)
                        if (continuation.isActive) continuation.resumeWithException(error)
                        return@suspendCancellableCoroutine
                    }
                    if (!started) {
                        clearPending(request)
                        if (continuation.isActive) {
                            continuation.resumeWithException(
                                IllegalStateException("Failed to initiate read for ${characteristic.uuid}"),
                            )
                        }
                    }
                }
            }
        }

    fun onReadResult(
        characteristic: BluetoothGattCharacteristic,
        value: ByteArray,
        status: Int,
    ) {
        // A late callback for the previous characteristic must not finish a new read.
        val request =
            synchronized(stateLock) {
                pendingRead?.takeIf { it.characteristic === characteristic }?.also {
                    pendingRead = null
                }
            } ?: return

        if (!request.continuation.isActive) return
        if (status == android.bluetooth.BluetoothGatt.GATT_SUCCESS) {
            request.continuation.resume(value)
        } else {
            request.continuation.resumeWithException(
                IllegalStateException("GATT Read failed with status $status"),
            )
        }
    }

    fun reset() {
        val previous = synchronized(stateLock) {
            pendingRead.also { pendingRead = null }
        }
        previous?.continuation?.cancel(IllegalStateException("Disconnected"))
    }

    private fun clearPending(request: PendingRead) {
        synchronized(stateLock) {
            if (pendingRead === request) pendingRead = null
        }
    }

    private data class PendingRead(
        val characteristic: BluetoothGattCharacteristic,
        val continuation: CancellableContinuation<ByteArray>,
    )
}
