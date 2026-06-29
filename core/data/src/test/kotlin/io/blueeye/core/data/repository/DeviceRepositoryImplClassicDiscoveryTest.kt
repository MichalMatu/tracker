package io.blueeye.core.data.repository

import io.blueeye.core.data.db.dao.DeviceDao
import io.blueeye.core.data.repository.handler.ble.BleScanHandler
import io.blueeye.core.data.repository.handler.classic.ClassicScanHandler
import io.blueeye.core.data.repository.handler.paired.ProbeResultHandler
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertTrue
import org.junit.Test
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify

class DeviceRepositoryImplClassicDiscoveryTest {
    private val deviceDao: DeviceDao = mock()
    private val deviceHistoryDataSource: DeviceHistoryDataSource = mock()
    private val bleScanHandler: BleScanHandler = mock()
    private val classicScanHandler: ClassicScanHandler = mock()
    private val probeResultHandler: ProbeResultHandler = mock()
    private val probeStateManager = ProbeStateManager()

    private val repository =
        DeviceRepositoryImpl(
            deviceDao = deviceDao,
            deviceHistoryDataSource = deviceHistoryDataSource,
            bleScanHandler = bleScanHandler,
            classicScanHandler = classicScanHandler,
            probeResultHandler = probeResultHandler,
            probeStateManager = probeStateManager,
        )

    @Test
    fun `handleClassicDiscovery forwards SDP service UUIDs to classic handler`() =
        runTest {
            val serviceUuids =
                listOf(
                    "0000110b-0000-1000-8000-00805f9b34fb",
                    "0000110e-0000-1000-8000-00805f9b34fb",
                )

            val result =
                repository.handleClassicDiscovery(
                    mac = "AA:BB:CC:11:22:33",
                    name = "Headphones",
                    rssi = -52,
                    classOfDevice = 0x240404,
                    serviceUuids = serviceUuids,
                )

            assertTrue(result.isSuccess)
            verify(classicScanHandler).handle(
                mac = eq("AA:BB:CC:11:22:33"),
                name = eq("Headphones"),
                rssi = eq(-52),
                classOfDevice = eq(0x240404),
                serviceUuids = eq(serviceUuids),
            )
        }
}
