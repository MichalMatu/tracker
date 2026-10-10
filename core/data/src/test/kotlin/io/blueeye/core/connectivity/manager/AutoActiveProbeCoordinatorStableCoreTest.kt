package io.blueeye.core.connectivity.manager

import dagger.Lazy
import io.blueeye.core.data.db.dao.DeviceDao
import io.blueeye.core.data.preferences.WatchlistPreferences
import io.blueeye.core.data.repository.ProbeStateManager
import org.junit.Test
import org.mockito.kotlin.mock
import kotlinx.coroutines.flow.flowOf
import org.mockito.kotlin.verifyNoInteractions
import org.mockito.kotlin.whenever

class AutoActiveProbeCoordinatorStableCoreTest {
    @Test
    fun `automatic GATT never probes without explicit preference opt in`() {
        val connectionManager: Lazy<BleConnectionManager> = mock()
        val deviceDao: DeviceDao = mock()
        val probeStateManager: ProbeStateManager = mock()
        val preferences: WatchlistPreferences = mock()
        whenever(preferences.autoActiveProbeEnabled).thenReturn(flowOf(false))
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

        // Reading the preference and clearing an empty queue are expected.
        verifyNoInteractions(connectionManager, deviceDao)
    }
}
