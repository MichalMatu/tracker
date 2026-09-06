package io.blueeye.core.connectivity.manager

import dagger.Lazy
import io.blueeye.core.data.db.dao.DeviceDao
import io.blueeye.core.data.preferences.WatchlistPreferences
import io.blueeye.core.data.repository.ProbeStateManager
import org.junit.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.verifyNoInteractions

class AutoActiveProbeCoordinatorStableCoreTest {
    @Test
    fun `persisted auto probe preference cannot bypass stable core`() {
        val connectionManager: Lazy<BleConnectionManager> = mock()
        val deviceDao: DeviceDao = mock()
        val probeStateManager: ProbeStateManager = mock()
        val preferences: WatchlistPreferences = mock()
        val coordinator =
            AutoActiveProbeCoordinator(
                bleConnectionManager = connectionManager,
                deviceDao = deviceDao,
                probeStateManager = probeStateManager,
                watchlistPreferences = preferences,
            )

        coordinator.enqueueCandidate(
            AutoActiveProbeScanCandidate(
                fingerprint = "AA:BB:CC:DD:EE:FF",
                mac = "AA:BB:CC:DD:EE:FF",
                isConnectable = true,
                connectionStatus = null,
                lastProbeTimestamp = 0L,
                now = 123_456L,
            )
        )

        verifyNoInteractions(connectionManager, deviceDao, probeStateManager, preferences)
    }
}
