package io.blueeye.core.data.verify

import io.blueeye.core.data.db.dao.DeviceDao
import io.blueeye.core.data.db.dao.DeviceSearchDao
import io.blueeye.core.data.db.dao.SignalSampleDao
import io.blueeye.core.data.db.entity.DeviceEntity
import io.blueeye.core.data.db.entity.SignalSampleEntity
import io.blueeye.core.data.repository.handler.classic.ClassicDevicePersister
import io.blueeye.core.data.repository.handler.classic.ClassicScanDataContext
import io.blueeye.core.data.repository.handler.common.DeviceTypePriorityHelper
import io.blueeye.core.data.tracker.AddressCarryoverTracker
import io.blueeye.core.data.tracker.strategy.DeviceCorrelationStrategy
import io.blueeye.core.model.DeviceCalibrationLabel
import io.blueeye.core.model.DeviceType
import io.blueeye.core.model.IdentityCarryoverVerdict
import io.blueeye.core.model.MacAddressType
import io.blueeye.core.model.TrackingStatus
import io.blueeye.core.scanner.model.BleScanResultData
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.mockito.kotlin.mock
import java.util.UUID

/**
 * Scenarios verifying the Apple Device Deduplication logic.
 * Requested by User to verify:
 * 1. Concurrent "Orchestra" merging (BLE)
 * 2. Dual-Stack "Shadow" merging (Classic -> BLE)
 */
class AppleDeduplicationScenariosTest {

    // ==========================================
    // SCENARIO 1: Concurrent BLE "Orchestra"
    // ==========================================
    @Test
    fun `Orchestra Scenario - Simultaneous packets merge to single Target`() {
        val strategy = DeviceCorrelationStrategy()
        val tracker = AddressCarryoverTracker(strategy)
        val now = System.currentTimeMillis()

        // 1. Identity Packet (Main) - REAL DATA
        val identityMac = REAL_MACBOOK_MAC
        val identityPacket = createBlePacket(identityMac, -36, now, "Michal's MacBook Air")

        // 2. FindMy Packet (Anonymous, Concurrent)
        val findMyMac = "E2:4A:BB:FD:71:2E"
        val findMyPacket = createBlePacket(findMyMac, -36, now + 10, "Find My") // 10ms later

        // 3. Handoff Packet (Anonymous, Concurrent)
        val handoffMac = "6F:77:9F:57:CB:7B"
        val handoffPacket = createBlePacket(handoffMac, -38, now + 15, "Apple, Inc. Device") // 15ms later, 2dB diff

        // Execution
        val result1 = tracker.processScan(identityPacket, identityPacket.name)
        val result2 = tracker.processScan(findMyPacket, findMyPacket.name)
        val result3 = tracker.processScan(handoffPacket, handoffPacket.name)

        // assertions
        println("Result 1 (Identity): ${result1.targetId} (New: ${result1.isNewTarget})")
        println("Result 2 (FindMy)  : ${result2.targetId} (Carryover: ${result2.isCarryover})")
        println("Result 3 (Handoff) : ${result3.targetId} (Carryover: ${result3.isCarryover})")

        assertTrue("First packet should create new target", result1.isNewTarget)
        
        // Concurrent packets should MAP to the SAME target
        assertEquals("FindMy should merge into Identity", result1.targetId, result2.targetId)
        assertEquals("Handoff should merge into Identity", result1.targetId, result3.targetId)
    }

    @Test
    fun `Orchestra Scenario - Order Independence (Anonymous First)`() {
        val strategy = DeviceCorrelationStrategy()
        val tracker = AddressCarryoverTracker(strategy)
        val now = System.currentTimeMillis()

        // 1. FindMy Packet (Anonymous) - Arrives FIRST
        val findMyMac = "E2:4A:BB:FD:71:2E"
        val findMyPacket = createBlePacket(findMyMac, -36, now, "Find My")

        // 2. Identity Packet (Main) - Arrives 10ms LATER
        val identityMac = REAL_MACBOOK_MAC
        val identityPacket = createBlePacket(identityMac, -36, now + 10, "Michal's MacBook Air")

        // Execution
        val result1 = tracker.processScan(findMyPacket, findMyPacket.name) // New Target T1
        // Simulate persistence of T1 name so next scan sees it? 
        // Tracker keeps state in memory, so T1 has name "Find My" currently.
        
        val result2 = tracker.processScan(identityPacket, identityPacket.name) // Should merge into T1

        // Assertions
        println("Result 1 (FindMy)  : ${result1.targetId}")
        println("Result 2 (Identity): ${result2.targetId}")

        assertTrue("First packet should create new target", result1.isNewTarget)
        
        // Identity should merge into the Anonymous target because they are concurrent
        assertEquals("Identity should merge into FindMy Target", result1.targetId, result2.targetId)
        
        // Verify Name Update Logic (Target should eventually reflect the Real Name)
        // Note: AddressCarryoverTracker updates internal state. 
        val target = tracker.getTargetByMac(findMyMac)
        assertNotNull(target)
        assertEquals("Target Name should update to Identity Name", "Michal's MacBook Air", target?.lastDeviceName)
    }

    @Test
    fun `Orchestra Scenario - RSSI too different fails merge`() {
        val strategy = DeviceCorrelationStrategy()
        val tracker = AddressCarryoverTracker(strategy)
        val now = System.currentTimeMillis()

        // 1. Target A
        val macA = "AA:AA:AA:AA:AA:AA"
        val packetA = createBlePacket(macA, -36, now, "MacBook")

        // 2. Target B (Simultaneous but far away)
        val macB = "BB:BB:BB:BB:BB:BB"
        val packetB = createBlePacket(macB, -60, now + 10, "Apple Device") // 24dB diff (Threshold is 8dB)

        tracker.processScan(packetA, packetA.name)
        val resultB = tracker.processScan(packetB, packetB.name)

        // Should NOT merge
        assertNotEquals("Target IDs should differ", tracker.getTargetByMac(macA)?.targetId, resultB.targetId)
    }

    // ==========================================
    // SCENARIO 2: Classic Dual-Stack Merge
    // ==========================================
    @Test
    fun `Classic Scenario - Generic Apple merges into named BLE Device`() = runBlocking {
        // Setup Fakes
        val fakeDao = FakeDeviceDao()
        val persister = ClassicDevicePersister(fakeDao, FakeSignalSampleDao(), DeviceTypePriorityHelper(), mock())

        val now = System.currentTimeMillis()

        // 1. Existing BLE Device (MacBook) in DB - REAL DATA
        val bleDevice = DeviceEntity(
            fingerprint = "BLE_FINGERPRINT",
            lastMacAddress = REAL_MACBOOK_MAC,
            lastDeviceName = "Michal's MacBook Air",
            lastRssi = -40,
            firstSeenAt = now - 5000,
            lastSeenAt = now - 1000,
            vendorName = "Apple, Inc.",
            manufacturerId = 76,
            technology = "BLE"
        )
        fakeDao.upsert(bleDevice)

        // 2. New Classic Scan (Generic Name, Similar RSSI)
        val classicCtx = ClassicScanDataContext(
            mac = "CLASSIC_MAC",
            name = "Apple, Inc. Device",
            rssi = -41, 
            classOfDevice = 0x240404, // Audio/Video
            timestamp = now
        ).apply { 
            vendorName = "Apple, Inc." // Resolved vendor
            fingerprint = "CLASSIC_MAC" // Explicitly set initial fingerprint
        }
        classicCtx.validRssi = -41

        // Execution
        persister.persist(classicCtx)

        // 3. Assertions
        val classicGhost = fakeDao.getByFingerprint("CLASSIC_MAC")
        val mainDevice = fakeDao.getByFingerprint("BLE_FINGERPRINT")

        // The Classic Ghost should NOT exist (or be deleted/merged)
        assertEquals("Classic Ghost should not exist (merged)", null, classicGhost)
        
        // The Main Device should be updated
        assertNotNull(mainDevice)
        assertEquals("Main Device should have Classic Tech added", "BLE + CLASSIC", mainDevice?.technology)
        assertEquals("Main Device should keep its name", "Michal's MacBook Air", mainDevice?.lastDeviceName)
        
        // Verify Context updated
        assertEquals("Context fingerprint should point to Main", "BLE_FINGERPRINT", classicCtx.fingerprint)
    }

    @Test
    fun `Classic Scenario - UUID Match merges generic Classic into BLE`() = runBlocking {
        // Setup Fakes
        val fakeDao = FakeDeviceDao()
        val persister = ClassicDevicePersister(fakeDao, FakeSignalSampleDao(), DeviceTypePriorityHelper(), mock())
        val now = System.currentTimeMillis()

        // 1. Existing BLE Device (Unique Service UUID)
        val bleDevice = DeviceEntity(
            fingerprint = "BLE_unique",
            lastMacAddress = "11:22:33:44:55:66",
            lastDeviceName = "Specific BLE Device",
            lastRssi = -50,
            firstSeenAt = now - 10000,
            lastSeenAt = now - 2000,
            technology = "BLE",
            gattServices = "0000FEAA-0000-1000-8000-00805F9B34FB" // Eddystone UUID
        )
        fakeDao.upsert(bleDevice)

        // 2. New Classic Scan (Generic Name, Different MAC, Same UUID)
        // Note: Classic devices often expose UUIDs via SDP, which might be mapped to serviceUuids in scan.
        val classicCtx = ClassicScanDataContext(
            mac = "CLASSIC_MAC_2",
            name = "Headphones", // Generic-ish
            rssi = -50,
            classOfDevice = 0x240404,
            timestamp = now
        ).apply {
            // Assume we found the same UUID via SDP
            serviceUuids = listOf("0000FEAA-0000-1000-8000-00805F9B34FB")
        }

        // Execution
        persister.persist(classicCtx)

        // 3. Assertions
        val classicGhost = fakeDao.getByFingerprint("CLASSIC_MAC_2")
        val mainDevice = fakeDao.getByFingerprint("BLE_unique")

        // Should be merged because UUIDs match!
        assertEquals("Classic Ghost should be merged", null, classicGhost)
        assertNotNull(mainDevice)
        assertEquals("Main Device should have Classic Tech added", "BLE + CLASSIC", mainDevice?.technology)
    }

    @Test
    fun `Classic Scenario - new Classic device persists SDP UUIDs`() = runBlocking {
        val fakeDao = FakeDeviceDao()
        val persister = ClassicDevicePersister(fakeDao, FakeSignalSampleDao(), DeviceTypePriorityHelper(), mock())
        val now = System.currentTimeMillis()
        val serviceUuid = "0000110B-0000-1000-8000-00805F9B34FB"

        val classicCtx = ClassicScanDataContext(
            mac = "CLASSIC_UUID_DEVICE",
            name = "Headphones",
            rssi = -55,
            classOfDevice = 0x240404,
            timestamp = now,
        ).apply {
            validRssi = -55
            fingerprint = "CLASSIC_UUID_DEVICE"
            serviceUuids = listOf(serviceUuid)
        }

        persister.persist(classicCtx)

        val saved = fakeDao.getByFingerprint("CLASSIC_UUID_DEVICE")

        assertNotNull(saved)
        assertEquals(serviceUuid, saved?.gattServices)
    }

    @Test
    fun `Classic Scenario - existing Classic device merges SDP UUIDs`() = runBlocking {
        val fakeDao = FakeDeviceDao()
        val persister = ClassicDevicePersister(fakeDao, FakeSignalSampleDao(), DeviceTypePriorityHelper(), mock())
        val now = System.currentTimeMillis()
        val existingUuid = "0000110B-0000-1000-8000-00805F9B34FB"
        val newUuid = "0000110E-0000-1000-8000-00805F9B34FB"
        val existing = DeviceEntity(
            fingerprint = "CLASSIC_UUID_DEVICE",
            lastMacAddress = "CLASSIC_UUID_DEVICE",
            lastDeviceName = "Headphones",
            lastRssi = -58,
            firstSeenAt = now - 10_000,
            lastSeenAt = now - 1_000,
            technology = "CLASSIC",
            gattServices = existingUuid,
        )
        fakeDao.upsert(existing)

        val classicCtx = ClassicScanDataContext.fromScan(
            mac = "CLASSIC_UUID_DEVICE",
            name = "Headphones",
            rssi = -55,
            classOfDevice = 0x240404,
            serviceUuids = listOf(newUuid),
        ).apply {
            existingDevice = existing
        }

        persister.persist(classicCtx)

        val saved = fakeDao.getByFingerprint("CLASSIC_UUID_DEVICE")

        assertNotNull(saved)
        assertEquals("$existingUuid,$newUuid", saved?.gattServices)
    }

    @Test
    fun `Classic Scenario - fromScan carries SDP UUIDs into context`() {
        val serviceUuid = "0000110B-0000-1000-8000-00805F9B34FB"

        val ctx = ClassicScanDataContext.fromScan(
            mac = "CLASSIC_UUID_CONTEXT",
            name = "Headphones",
            rssi = -55,
            classOfDevice = 0x240404,
            serviceUuids = listOf(serviceUuid),
        )

        assertEquals(listOf(serviceUuid), ctx.serviceUuids)
        assertEquals(-55, ctx.validRssi)
    }

    @Test
    fun `Classic Scenario - unknown RSSI clears legacy RFCOMM fallback`() = runBlocking {
        val fakeDao = FakeDeviceDao()
        val sampleDao = FakeSignalSampleDao()
        val persister = ClassicDevicePersister(fakeDao, sampleDao, DeviceTypePriorityHelper(), mock())
        val now = System.currentTimeMillis()
        val mac = "13:02:AB:00:C0:D2"
        val legacyDevice = DeviceEntity(
            fingerprint = mac,
            lastMacAddress = mac,
            lastDeviceName = null,
            lastRssi = -50,
            firstSeenAt = now - 10_000,
            lastSeenAt = now - 5_000,
            technology = "CLASSIC",
            connectionStatus = "RFCOMM_FAIL",
        )
        fakeDao.upsert(legacyDevice)

        val ctx = ClassicScanDataContext.fromScan(
            mac = mac,
            name = null,
            rssi = ClassicScanDataContext.RSSI_UNAVAILABLE,
            classOfDevice = null,
        ).apply {
            existingDevice = legacyDevice
        }

        persister.persist(ctx)

        val updated = fakeDao.getByFingerprint(mac)
        assertNotNull(updated)
        assertEquals(ClassicScanDataContext.RSSI_DEFAULT, updated?.lastRssi)
        assertEquals(0, sampleDao.samples.size)
    }


    // ==========================================
    // HELPERS & FAKES
    // ==========================================

    companion object {
        // Real Data from baseline_v1.db (Michal's MacBook Air)
        const val REAL_MACBOOK_MAC = "69:95:CC:A8:9C:A0"
        // 02011A 020A0C 0BFF4C001006021D3334AA78
        // Flags: 1A, Tx: 12dBm, Manuf: 4C00 (Apple) SubType 10
        const val REAL_MACBOOK_RAW = "02011A020A0C0BFF4C001006021D3334AA78"
    }

    private fun createBlePacket(mac: String, rssi: Int, ts: Long, name: String?): BleScanResultData {
        val rawBytes = if (mac == REAL_MACBOOK_MAC) {
            hexStringToByteArray(REAL_MACBOOK_RAW)
        } else {
             // Apple Manufacturer Data (ID 76) Generic
             byteArrayOf(0x02, 0x15) 
        }
        
        // If raw bytes are present, ManufData should be extracted from it in a real app,
        // but for this fake we can just pass the bytes.
        // The Deduplication Logic prioritizes rawData hash if present.
        
        return BleScanResultData(
            mac = mac,
            rssi = rssi,
            timestamp = ts,
            technology = "BLE",
            name = name,
            manufacturerId = 76,
            manufacturerData = rawBytes, // Simplify: Just use same bytes
            serviceUuids = emptyList(),
            appearance = null,
            txPower = null,
            isConnectable = true,
            primaryPhy = 1,
            secondaryPhy = 0,
            rawData = if (mac == REAL_MACBOOK_MAC) rawBytes else null 
        )
    }
    
    private fun hexStringToByteArray(s: String): ByteArray {
        val len = s.length
        val data = ByteArray(len / 2)
        var i = 0
        while (i < len) {
            data[i / 2] = ((Character.digit(s[i], 16) shl 4) + Character.digit(s[i + 1], 16)).toByte()
            i += 2
        }
        return data
    }

    // Minimal Fake DAO for testing functionality
    class FakeDeviceDao : DeviceDao {
        val db = mutableMapOf<String, DeviceEntity>()

        override suspend fun upsert(device: DeviceEntity) {
            db[device.fingerprint] = device
        }

        override suspend fun getRecentDevicesSnapshot(sinceTimestamp: Long): List<DeviceEntity> {
            return db.values.filter { it.lastSeenAt > sinceTimestamp }.sortedByDescending { it.lastRssi }
        }

        override suspend fun getByFingerprint(fingerprint: String): DeviceEntity? {
            return db[fingerprint]
        }

        override suspend fun update(device: DeviceEntity) {
            db[device.fingerprint] = device
        }

        override suspend fun delete(device: DeviceEntity) {
            db.remove(device.fingerprint)
        }
        
        override suspend fun getByNameOrAlt(name: String, altName: String): DeviceEntity? {
            return db.values.find { it.lastDeviceName == name || it.lastDeviceName == altName }
        }
        
        override suspend fun updateScanData(
            fingerprint: String,
            mac: String?,
            timestamp: Long,
            rssi: Int,
            technology: String,
            name: String?,
            vendor: String?,
            newType: DeviceType,
            sensor: String?,
            tx: Int?,
            connectable: Boolean,
            carryoverReasonCode: String?,
            carryoverConfidence: Float,
            carryoverFeatures: String?,
            phy1: Int?,
            phy2: Int?,
            interval: Long?,
            beacon: String?,
            rawData: String?,
            services: String?,
            probeError: String?,
        ) {
            val existing = db[fingerprint] ?: return
            db[fingerprint] = existing.copy(
                lastRssi = rssi,
                lastSeenAt = timestamp,
                technology = technology,
                lastDeviceName = name ?: existing.lastDeviceName,
                carryoverReasonCode = carryoverReasonCode ?: existing.carryoverReasonCode,
                carryoverConfidence =
                    if (carryoverReasonCode != null) carryoverConfidence else existing.carryoverConfidence,
                carryoverFeatures = carryoverFeatures ?: existing.carryoverFeatures,
            )
        }
        
        // ... stubs ...
        override suspend fun updateIsPaired(fingerprint: String, isPaired: Boolean) {}
        override suspend fun updateLastSeen(fingerprint: String, timestamp: Long) {}
        override suspend fun setWatchlistStatus(fingerprint: String, isWatchlisted: Boolean) {}
        override suspend fun updateTrackingEnabled(fingerprint: String, isEnabled: Boolean) {}
        override suspend fun setUserAlias(fingerprint: String, alias: String?) {}
        override suspend fun recordWatchlistReturnAlert(
            fingerprint: String,
            timestamp: Long,
            offlineDurationMs: Long,
        ) {
            val existing = db[fingerprint] ?: return
            db[fingerprint] = existing.copy(
                lastWatchlistReturnAlertAt = timestamp,
                lastWatchlistReturnOfflineDurationMs = offlineDurationMs,
            )
        }

        override suspend fun setTrackingStatus(
            fingerprint: String,
            status: TrackingStatus,
            score: Float,
            explanation: String?,
            durationScore: Int,
            rssiStabilityScore: Int,
            deviceTypeScore: Int,
            macBehaviorScore: Int,
            encounterScore: Int,
            userMoved: Boolean?,
            baselineDevice: Boolean?,
        ) {}

        override suspend fun updateProbeData(fingerprint: String, status: String, attempts: Int, timestamp: Long, model: String?, serial: String?, firmware: String?, hardware: String?, software: String?, manufacturer: String?, battery: Int?, services: String?, charData: String?, error: String?, newDeviceType: DeviceType) {}
        override suspend fun setIgnoredForTracking(fingerprint: String, ignored: Boolean) {}
        override suspend fun setCalibrationLabel(fingerprint: String, label: DeviceCalibrationLabel) {}
        override suspend fun setIdentityCarryoverVerdict(
            fingerprint: String,
            verdict: IdentityCarryoverVerdict,
        ) {}

        override suspend fun updateClassicScanData(
            fingerprint: String,
            name: String?,
            rssi: Int,
            timestamp: Long,
            cod: Int?,
            technology: String,
            vendor: String?,
            deviceType: DeviceType,
            interval: Long?,
            services: String?,
        ) {
            val existing = db[fingerprint] ?: return
            db[fingerprint] = existing.copy(
                lastDeviceName = name ?: existing.lastDeviceName,
                lastRssi = rssi,
                lastSeenAt = timestamp,
                classOfDevice = cod ?: existing.classOfDevice,
                technology = technology,
                vendorName = vendor ?: existing.vendorName,
                deviceType = if (deviceType != DeviceType.UNKNOWN) deviceType else existing.deviceType,
                encounterCount = existing.encounterCount + 1,
                advertisingIntervalMs = interval,
                gattServices = services ?: existing.gattServices,
            )
        }

        override fun getAllDevicesFlow(): Flow<List<DeviceEntity>> = flowOf(emptyList())
        override suspend fun getAllDevices(): List<DeviceEntity> = emptyList()
        override fun getFlowByFingerprint(fingerprint: String): Flow<DeviceEntity?> = flowOf(null)
        override suspend fun upsertAll(devices: List<DeviceEntity>) {}
        override suspend fun deleteOldDevices(beforeTimestamp: Long): Int = 0
        override suspend fun deleteAll() {}
        override suspend fun deleteNonWatchlistDevices() {}
        override suspend fun moveSamples(targetFingerprint: String, sourceFingerprint: String) {}
        override suspend fun moveFollowMeObservations(targetFingerprint: String, sourceFingerprint: String) {}
        override suspend fun moveAlertEvidenceEvents(targetFingerprint: String, sourceFingerprint: String) {}
        override suspend fun deleteByFingerprint(fingerprint: String) { 
             db.remove(fingerprint) 
        }
        override suspend fun mergeDevices(targetFingerprint: String, duplicateFingerprint: String) {}
        override fun getRecentDevicesFlow(sinceTimestamp: Long): Flow<List<DeviceEntity>> = flowOf(emptyList())
        override fun getWatchlistDevicesFlow(): Flow<List<DeviceEntity>> = flowOf(emptyList())
        override suspend fun getSafeBeacons(): List<DeviceEntity> = emptyList()
        override fun getByTrackingStatus(status: TrackingStatus): Flow<List<DeviceEntity>> = flowOf(emptyList())
        override fun searchDevices(query: String): Flow<List<DeviceEntity>> = flowOf(emptyList())
        override suspend fun findDeviceByRawData(rawDataHex: String): DeviceEntity? = null
        override suspend fun findAllDevicesByRawData(rawDataHex: String): List<DeviceEntity> = emptyList()
        override suspend fun findDeviceByGattServices(gattServices: String, excludeFingerprint: String): DeviceEntity? = null
    }

    class FakeSignalSampleDao : SignalSampleDao {
        val samples = mutableListOf<SignalSampleEntity>()

        override suspend fun insert(sample: SignalSampleEntity) {
            samples += sample
        }

        override suspend fun insertAll(samples: List<SignalSampleEntity>) {
            this.samples += samples
        }

        override suspend fun deleteOldSamples(beforeTimestamp: Long): Int = 0
        override suspend fun countSamplesForDevice(fingerprint: String): Int =
            samples.count { it.deviceFingerprint == fingerprint }

        override fun getSamplesForDevice(fingerprint: String, limit: Int): Flow<List<SignalSampleEntity>> = flowOf(emptyList())
        
        override fun getSamplesWithLocation(limit: Int): Flow<List<SignalSampleEntity>> = flowOf(emptyList())
        override suspend fun getSamplesInTimeRange(fingerprint: String, startTime: Long, endTime: Long): List<SignalSampleEntity> = emptyList()
        override suspend fun getAverageRssi(fingerprint: String, sampleCount: Int): Float? = null
        override suspend fun deleteAll() {}
        override suspend fun deleteOrphanedSamples() {}
        override suspend fun getAllSamples(): List<SignalSampleEntity> = emptyList()
        override fun getAllSamplesFlow(): Flow<List<SignalSampleEntity>> = flowOf(samples)
    }
}
