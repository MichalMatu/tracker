package io.blueeye.core.connectivity.client

import android.bluetooth.BluetoothGattCharacteristic
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.async
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever

class BleOperationManagerTest {
    @Test
    fun `late callback for another characteristic cannot complete pending read`() = runBlocking {
        val client: BleGattClient = mock()
        whenever(client.readCharacteristic(any())).thenReturn(true)
        val manager = BleOperationManager(client)
        val expectedCharacteristic: BluetoothGattCharacteristic = mock()
        val oldCharacteristic: BluetoothGattCharacteristic = mock()

        val pending = async(start = CoroutineStart.UNDISPATCHED) {
            manager.read(expectedCharacteristic)
        }
        manager.onReadResult(oldCharacteristic, byteArrayOf(9), 0)
        assertFalse(pending.isCompleted)

        manager.onReadResult(expectedCharacteristic, byteArrayOf(7), 0)
        assertArrayEquals(byteArrayOf(7), pending.await())
    }

    @Test
    fun `disconnect cancels pending read and allows the next read`() = runBlocking {
        val client: BleGattClient = mock()
        whenever(client.readCharacteristic(any())).thenReturn(true)
        val manager = BleOperationManager(client)
        val firstCharacteristic: BluetoothGattCharacteristic = mock()
        val secondCharacteristic: BluetoothGattCharacteristic = mock()

        val first = async(start = CoroutineStart.UNDISPATCHED) {
            runCatching { manager.read(firstCharacteristic) }
        }
        manager.reset()
        assertTrue(first.await().isFailure)

        val second = async(start = CoroutineStart.UNDISPATCHED) {
            manager.read(secondCharacteristic)
        }
        manager.onReadResult(firstCharacteristic, byteArrayOf(9), 0)
        assertFalse(second.isCompleted)
        manager.onReadResult(secondCharacteristic, byteArrayOf(4), 0)
        assertArrayEquals(byteArrayOf(4), second.await())
    }
}
