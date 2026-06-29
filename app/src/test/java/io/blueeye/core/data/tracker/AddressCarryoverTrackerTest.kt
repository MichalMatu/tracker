package io.blueeye.core.data.tracker

import io.blueeye.core.data.tracker.strategy.DeviceCorrelationStrategy
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.mockito.MockedStatic
import org.mockito.Mockito

class AddressCarryoverTrackerTest {
    private lateinit var tracker: AddressCarryoverTracker
    private lateinit var strategy: DeviceCorrelationStrategy
    private lateinit var logMock: MockedStatic<android.util.Log>

    @Before
    fun setup() {
        // Mock Android Log to prevent RuntimeException
        logMock = Mockito.mockStatic(android.util.Log::class.java)
        logMock.`when`<Int> { android.util.Log.i(Mockito.anyString(), Mockito.anyString()) }.thenReturn(0)
        logMock.`when`<Int> { android.util.Log.e(Mockito.anyString(), Mockito.anyString()) }.thenReturn(0)

        strategy = DeviceCorrelationStrategy()
        tracker = AddressCarryoverTracker(strategy)
    }

    @After
    fun tearDown() {
        logMock.close()
    }

    @Test
    fun `processScan correlates device with identical Payload Hash regardless of RSSI`() {
        val hashData = byteArrayOf(0x01, 0x02, 0x03, 0x04, 0x05)

        // 1. First Scan (Mac A)
        val data1 =
            io.blueeye.core.scanner.model.BleScanResultData(
                mac = "11:11:11:11:11:11",
                rssi = -50,
                timestamp = 1000L,
                technology = "BLE",
                manufacturerData = hashData,
                serviceUuids = emptyList(),
                rawData = hashData
            )
        val res1 = tracker.processScan(data1, deviceName = null)
        assertTrue("First device should be new", res1.isNewTarget)

        // 2. Second Scan (Mac B)
        val data2 =
            io.blueeye.core.scanner.model.BleScanResultData(
                mac = "22:22:22:22:22:22",
                rssi = -80,
                timestamp = 2000L,
                technology = "BLE",
                manufacturerData = hashData,
                serviceUuids = emptyList(),
                rawData = hashData
            )
        val res2 = tracker.processScan(data2, deviceName = null)

        assertTrue("Should be carryover due to payload match", res2.isCarryover)
        assertEquals("Target ID should match", res1.targetId, res2.targetId)
    }

    @Test
    fun `processScan correlates device with Same Name and Similar RSSI`() {
        // 1. First Scan
        val data1 =
            io.blueeye.core.scanner.model.BleScanResultData(
                mac = "AA:AA:AA:AA:AA:AA",
                rssi = -50,
                timestamp = 1000L,
                technology = "BLE",
                manufacturerData = null,
                serviceUuids = emptyList(),
                name = "MyHeadphones",
                rawData = null
            )
        val res1 = tracker.processScan(data1, deviceName = "MyHeadphones")
        assertTrue(res1.isNewTarget)

        // 2. Second Scan
        val data2 =
            io.blueeye.core.scanner.model.BleScanResultData(
                mac = "BB:BB:BB:BB:BB:BB",
                rssi = -55,
                timestamp = 2000L,
                technology = "BLE",
                manufacturerData = null,
                serviceUuids = emptyList(),
                name = "MyHeadphones",
                rawData = null
            )
        val res2 = tracker.processScan(data2, deviceName = "MyHeadphones")

        assertTrue("Should be carryover due to Name+RSSI", res2.isCarryover)
        assertEquals(res1.targetId, res2.targetId)
    }

    @Test
    fun `processScan DOES NOT correlate huge RSSI difference even with same name`() {
        // 1. First Scan
        val data1 =
            io.blueeye.core.scanner.model.BleScanResultData(
                mac = "CC:CC:CC:11:11:11",
                rssi = -40,
                timestamp = 1000L,
                technology = "BLE",
                serviceUuids = emptyList(),
                name = "GenericDevice"
            )
        val res1 = tracker.processScan(data1, deviceName = "GenericDevice")

        // 2. Second Scan
        val data2 =
            io.blueeye.core.scanner.model.BleScanResultData(
                mac = "DD:DD:DD:22:22:22",
                rssi = -90,
                timestamp = 2000L,
                technology = "BLE",
                serviceUuids = emptyList(),
                name = "GenericDevice"
            )
        val res2 = tracker.processScan(data2, deviceName = "GenericDevice")

        assertFalse("Should NOT be carryover (RSSI Diff)", res2.isCarryover)
        assertTrue("Should be new target", res2.isNewTarget)
        assert(res1.targetId != res2.targetId)
    }

    @Test
    fun `processScan detects Service UUID match with RSSI`() {
        val uuids = listOf("0000fe95-0000-1000-8000-00805f9b34fb")

        // 1. First Scan
        val data1 =
            io.blueeye.core.scanner.model.BleScanResultData(
                mac = "EE:EE:EE:11:11:11",
                rssi = -60,
                timestamp = 1000L,
                technology = "BLE",
                serviceUuids = uuids
            )
        val res1 = tracker.processScan(data1, deviceName = null)

        // 2. Second Scan
        val data2 =
            io.blueeye.core.scanner.model.BleScanResultData(
                mac = "FF:FF:FF:22:22:22",
                rssi = -65,
                timestamp = 2000L,
                technology = "BLE",
                serviceUuids = uuids
            )
        val res2 = tracker.processScan(data2, deviceName = null)

        assertTrue("Should be carryover due to UUID+RSSI", res2.isCarryover)
        assertEquals(res1.targetId, res2.targetId)
    }
}
