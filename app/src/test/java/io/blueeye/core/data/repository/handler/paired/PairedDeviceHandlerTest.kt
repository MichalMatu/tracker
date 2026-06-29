package io.blueeye.core.data.repository.handler.paired

import android.content.Context
import io.blueeye.core.data.db.dao.DeviceDao
import io.blueeye.core.data.repository.VendorRepository
import kotlinx.coroutines.ExperimentalCoroutinesApi
import org.mockito.Mock

@ExperimentalCoroutinesApi
class PairedDeviceHandlerTest {
    @Mock lateinit var deviceDao: DeviceDao

    @Mock lateinit var vendorRepository: VendorRepository

    @Mock lateinit var persister: PairedDevicePersister

    @Mock lateinit var context: Context

    // We can't easily mock BluetoothDevice without Robolectric because it's final.
    // However, since we've refactored, we can verify that IF we could invoke processPairedDevice,
    // it would delegate to persister.
    // BUT since `processPairedDevice` is private and called from `syncPairedDevices` which uses
    // BluetoothManager static calls or Service calls, this is hard to unit test without Robolectric.
    //
    // A better approach for Unit Testing the LOGIC is to test `PairedDevicePersister` separately,
    // and rely on integration tests or Robolectric for the Handler.
    //
    // For now, let's create a test for `PairedDevicePersister` instead, as the logic there is what we moved.
}
