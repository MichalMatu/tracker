package io.blueeye.core.data.tracker

import io.blueeye.core.data.tracker.model.CarryoverMatchReason
import io.blueeye.core.data.tracker.strategy.DeviceCorrelationStrategy
import io.blueeye.core.scanner.model.BleScanResultData
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AddressCarryoverTrackerTest {

    private val strategy = DeviceCorrelationStrategy()
    private val tracker = AddressCarryoverTracker(strategy)

    @Test
    fun `weak signal should be pending`() {
        val mac = "AA:BB:CC:DD:EE:FF"
        val data = createScanData(mac, name = null, manufacturerData = null, rawData = null)

        val result = tracker.processScan(data, deviceName = null)

        assertTrue("Result should be pending", result.isPending)
        assertEquals("Target ID should be empty for pending", "", result.targetId)
        assertFalse("Should not be new target yet", result.isNewTarget)
    }

    @Test
    fun `strong signal should not be pending`() {
        val mac = "11:22:33:44:55:66"
        val manufData = byteArrayOf(0x01, 0x02, 0x03)
        val data = createScanData(mac, name = null, manufacturerData = manufData, rawData = null)

        val result = tracker.processScan(data, deviceName = null)

        assertFalse("Result should not be pending", result.isPending)
        assertTrue("Should be new target", result.isNewTarget)
        assertTrue("Target ID should be valid", result.targetId.startsWith("TGT_"))
    }

    @Test
    fun `weak then strong signal should promote`() {
        val mac = "AA:BB:CC:DD:EE:FF"
        val weakData = createScanData(mac, name = null, manufacturerData = null, rawData = ByteArray(2))
        val result1 = tracker.processScan(weakData, deviceName = null)
        assertTrue("First scan should be pending", result1.isPending)

        val strongData = createScanData(mac, name = "MyDevice", manufacturerData = null, rawData = null)
        val result2 = tracker.processScan(strongData, deviceName = "MyDevice")

        assertFalse("Second scan should not be pending", result2.isPending)
        assertTrue("Should be new target now", result2.isNewTarget)
        assertTrue("Target ID should be valid", result2.targetId.startsWith("TGT_"))
    }

    @Test
    fun `same specific name and RSSI alone should stay separate`() {
        val first = createScanData("21:11:11:11:11:11", "JBL Tune 520BT-LE", null, null)
        val second = createScanData("22:22:22:22:22:22", "JBL Tune 520BT-LE", null, null)

        val firstResult = tracker.processScan(first, first.name)
        val secondResult = tracker.processScan(second, second.name)

        assertTrue(firstResult.isNewTarget)
        assertTrue(secondResult.isNewTarget)
        assertFalse(secondResult.isCarryover)
        assertNotEquals(firstResult.targetId, secondResult.targetId)
    }

    @Test
    fun `same specific name with service UUID corroboration may carry over sequentially`() {
        val now = System.currentTimeMillis()
        val serviceUuids = listOf("0000fe2c-0000-1000-8000-00805f9b34fb")
        val first =
            createScanData("31:11:11:11:11:11", "JBL Tune 520BT-LE", null, null).copy(
                timestamp = now,
                serviceUuids = serviceUuids,
            )
        val second =
            createScanData("32:22:22:22:22:22", "JBL Tune 520BT-LE", null, null).copy(
                timestamp = now + 3_000L,
                serviceUuids = serviceUuids,
            )

        val firstResult = tracker.processScan(first, first.name)
        val secondResult = tracker.processScan(second, second.name)

        assertTrue(firstResult.isNewTarget)
        assertTrue(secondResult.isCarryover)
        assertEquals(firstResult.targetId, secondResult.targetId)
        assertEquals(
            CarryoverMatchReason.SAME_NAME_PROXIMITY,
            secondResult.matchEvidence?.reasonCode,
        )
    }

    @Test
    fun `same corroborated device seen under two addresses concurrently stays separate`() {
        val now = System.currentTimeMillis()
        val serviceUuids = listOf("0000fe2c-0000-1000-8000-00805f9b34fb")
        val first =
            createScanData("33:11:11:11:11:11", "JBL Tune 520BT-LE", null, null).copy(
                timestamp = now,
                serviceUuids = serviceUuids,
            )
        val second =
            createScanData("34:22:22:22:22:22", "JBL Tune 520BT-LE", null, null).copy(
                timestamp = now + 500L,
                serviceUuids = serviceUuids,
            )

        val firstResult = tracker.processScan(first, first.name)
        val secondResult = tracker.processScan(second, second.name)

        assertTrue(firstResult.isNewTarget)
        assertTrue(secondResult.isNewTarget)
        assertFalse(secondResult.isCarryover)
        assertNotEquals(firstResult.targetId, secondResult.targetId)
    }

    @Test
    fun `long gap corroborated JBL name becomes candidate without carryover`() {
        val now = System.currentTimeMillis()
        val serviceUuids = listOf("0000fe2c-0000-1000-8000-00805f9b34fb")
        val first =
            createScanData("41:11:11:11:11:11", "JBL Tune 520BT-LE", null, null).copy(
                timestamp = now,
                serviceUuids = serviceUuids,
            )
        val second =
            createScanData("42:22:22:22:22:22", "JBL Tune 520BT-LE", null, null).copy(
                timestamp = now + 108_000L,
                serviceUuids = serviceUuids,
            )

        val firstResult = tracker.processScan(first, first.name)
        val secondResult = tracker.processScan(second, second.name)

        assertTrue(firstResult.isNewTarget)
        assertTrue(secondResult.isNewTarget)
        assertFalse(secondResult.isCarryover)
        assertNotEquals(firstResult.targetId, secondResult.targetId)
        assertEquals(first.mac, secondResult.identityCandidate?.candidateFingerprint)
        assertEquals(
            CarryoverMatchReason.SAME_NAME_PROXIMITY,
            secondResult.identityCandidate?.evidence?.reasonCode,
        )
        assertTrue(secondResult.identityCandidate?.evidence?.featureSummary.orEmpty().contains("candidateOnly=true"))
        assertTrue(secondResult.identityCandidate?.evidence?.featureSummary.orEmpty().contains("timeDeltaMs=108000"))
    }

    @Test
    fun `long gap OPPO name without corroboration stays separate without candidate`() {
        val now = System.currentTimeMillis()
        val first =
            createScanData("51:11:11:11:11:11", "OPPO Enco Buds3 Pro", null, null).copy(timestamp = now)
        val second =
            createScanData("52:22:22:22:22:22", "OPPO Enco Buds3 Pro", null, null).copy(timestamp = now + 72_000L)

        val firstResult = tracker.processScan(first, first.name)
        val secondResult = tracker.processScan(second, second.name)

        assertTrue(firstResult.isNewTarget)
        assertTrue(secondResult.isNewTarget)
        assertFalse(secondResult.isCarryover)
        assertNotEquals(firstResult.targetId, secondResult.targetId)
        assertNull(secondResult.identityCandidate)
    }

    @Test
    fun `concurrent apple shadows stay separate despite matching payload and RSSI`() {
        val now = System.currentTimeMillis()
        val first = createAppleScanData("61:11:11:11:11:11", "Find My", -45, now)
        val second = createAppleScanData("62:22:22:22:22:22", "Find My", -46, now + 500L)

        val firstResult = tracker.processScan(first, first.name)
        val secondResult = tracker.processScan(second, second.name)

        assertTrue(firstResult.isNewTarget)
        assertTrue(secondResult.isNewTarget)
        assertFalse(secondResult.isCarryover)
        assertNotEquals(firstResult.targetId, secondResult.targetId)
    }

    @Test
    fun `sequential apple shadow may carry over after coexistence guard window`() {
        val now = System.currentTimeMillis()
        val primary = createAppleScanData("63:11:11:11:11:11", "MacBook Air", -45, now)
        val shadow = createAppleScanData("64:22:22:22:22:22", "Find My", -46, now + 3_000L)

        val primaryResult = tracker.processScan(primary, primary.name)
        val shadowResult = tracker.processScan(shadow, shadow.name)

        assertTrue(primaryResult.isNewTarget)
        assertTrue(shadowResult.isCarryover)
        assertEquals(primaryResult.targetId, shadowResult.targetId)
        assertEquals(CarryoverMatchReason.APPLE_SHADOW, shadowResult.matchEvidence?.reasonCode)
    }

    @Test
    fun `known alias should keep reporting primary mac for persistence`() {
        val primaryMac = "69:95:CC:A8:9C:A0"
        val aliasMac = "F1:B0:CE:0D:2E:4B"
        val now = System.currentTimeMillis()

        val primary = createAppleScanData(primaryMac, "Michal's MacBook Air", -45, now)
        val alias = createAppleScanData(aliasMac, "Find My", -45, now + 3_000L)
        val aliasAgain = createAppleScanData(aliasMac, "Find My", -46, now + 3_010L)

        val primaryResult = tracker.processScan(primary, primary.name)
        val aliasResult = tracker.processScan(alias, alias.name)
        val aliasAgainResult = tracker.processScan(aliasAgain, aliasAgain.name)

        assertTrue("Primary scan should create target", primaryResult.isNewTarget)
        assertEquals("Alias should merge into primary target", primaryResult.targetId, aliasResult.targetId)
        assertEquals("Repeated alias should stay on primary target", primaryResult.targetId, aliasAgainResult.targetId)
        assertEquals("Alias carryover should expose primary MAC", primaryMac, aliasResult.correlatedMac)
        assertEquals("Known alias should still expose primary MAC", primaryMac, aliasAgainResult.correlatedMac)
        assertEquals("First alias should count as one MAC rotation", 1, aliasResult.macChangeCount)
        assertEquals("Known alias should preserve accumulated MAC rotation count", 1, aliasAgainResult.macChangeCount)
        assertEquals(CarryoverMatchReason.APPLE_SHADOW, aliasResult.matchEvidence?.reasonCode)
        assertEquals(1.0f, aliasResult.matchEvidence?.confidence ?: 0f, 0.001f)
        assertTrue(aliasResult.matchEvidence?.featureSummary.orEmpty().contains("rssiDiff=0"))
        assertTrue(aliasResult.matchEvidence?.featureSummary.orEmpty().contains("scorePct=100"))
        assertNull("Known alias should not recreate matcher evidence", aliasAgainResult.matchEvidence)
    }

    @Test
    fun `multiple sequential carryover aliases expose accumulated mac change count`() {
        val primaryMac = "69:95:CC:A8:9C:A0"
        val aliasMacA = "F1:B0:CE:0D:2E:4B"
        val aliasMacB = "F2:B0:CE:0D:2E:4C"
        val now = System.currentTimeMillis()

        val primary = createAppleScanData(primaryMac, "Michal's MacBook Air", -45, now)
        val aliasA = createAppleScanData(aliasMacA, "Find My", -45, now + 3_000L)
        val aliasB = createAppleScanData(aliasMacB, "Find My", -46, now + 6_000L)

        tracker.processScan(primary, primary.name)
        val aliasAResult = tracker.processScan(aliasA, aliasA.name)
        val aliasBResult = tracker.processScan(aliasB, aliasB.name)

        assertTrue(aliasAResult.isCarryover)
        assertTrue(aliasBResult.isCarryover)
        assertEquals(1, aliasAResult.macChangeCount)
        assertEquals(2, aliasBResult.macChangeCount)
        assertEquals(primaryMac, aliasBResult.correlatedMac)
    }

    @Test
    fun `apple shadow should not merge into non apple target`() {
        val nonAppleMac = "68:74:D6:61:D5:11"
        val appleShadowMac = "77:5F:70:CE:B3:86"
        val now = System.currentTimeMillis()

        val smartLight = createNonAppleScanData(nonAppleMac, "TY", -74, now)
        val appleShadow = createAppleScanData(appleShadowMac, "Apple Device", -68, now + 3_000L)

        val smartLightResult = tracker.processScan(smartLight, smartLight.name)
        val appleShadowResult = tracker.processScan(appleShadow, appleShadow.name)

        assertTrue("Non-Apple target should be persisted as its own target", smartLightResult.isNewTarget)
        assertTrue("Apple shadow should create a separate target", appleShadowResult.isNewTarget)
        assertNull("Apple shadow must not correlate to non-Apple primary MAC", appleShadowResult.correlatedMac)
        assertFalse(
            "Apple shadow should not merge into non-Apple target",
            smartLightResult.targetId == appleShadowResult.targetId,
        )
    }

    @Test
    fun `apple carryover should not merge explicit different device families`() {
        val phoneMac = "53:A5:54:7E:9A:12"
        val laptopMac = "68:F6:7D:C0:E5:2C"
        val now = System.currentTimeMillis()

        val phone = createAppleScanData(phoneMac, "iPhone", -45, now)
        val laptop = createAppleScanData(laptopMac, "MacBook Air", -45, now + 3_000L)

        val phoneResult = tracker.processScan(phone, phone.name)
        val laptopResult = tracker.processScan(laptop, laptop.name)

        assertTrue("Phone scan should create target", phoneResult.isNewTarget)
        assertTrue("Laptop scan should create its own target", laptopResult.isNewTarget)
        assertFalse("Different Apple families must not be carryover", laptopResult.isCarryover)
        assertNotEquals(phoneResult.targetId, laptopResult.targetId)
    }

    private fun createScanData(
        mac: String,
        name: String?,
        manufacturerData: ByteArray?,
        rawData: ByteArray?,
    ): BleScanResultData =
        BleScanResultData(
            mac = mac,
            rssi = -70,
            timestamp = System.currentTimeMillis(),
            technology = "BLE",
            name = name,
            manufacturerId = if (manufacturerData != null) 123 else null,
            manufacturerData = manufacturerData,
            serviceUuids = emptyList(),
            appearance = null,
            txPower = null,
            isConnectable = true,
            primaryPhy = 1,
            secondaryPhy = 0,
            rawData = rawData,
        )

    private fun createAppleScanData(
        mac: String,
        name: String?,
        rssi: Int,
        timestamp: Long,
    ): BleScanResultData {
        val rawData = byteArrayOf(
            0x02,
            0x01,
            0x1A,
            0x0A,
            0xFF.toByte(),
            0x4C,
            0x00,
            0x10,
            0x06,
            0x02,
            0x1D,
            0x33,
            0x34,
            0x55,
        )
        return BleScanResultData(
            mac = mac,
            rssi = rssi,
            timestamp = timestamp,
            technology = "BLE",
            name = name,
            manufacturerId = 76,
            manufacturerData = rawData,
            serviceUuids = emptyList(),
            appearance = null,
            txPower = null,
            isConnectable = true,
            primaryPhy = 1,
            secondaryPhy = 0,
            rawData = rawData,
        )
    }

    private fun createNonAppleScanData(
        mac: String,
        name: String?,
        rssi: Int,
        timestamp: Long,
    ): BleScanResultData {
        val rawData = byteArrayOf(
            0x02,
            0x01,
            0x06,
            0x05,
            0xFF.toByte(),
            0xE5.toByte(),
            0x02,
            0x01,
            0x02,
        )
        return BleScanResultData(
            mac = mac,
            rssi = rssi,
            timestamp = timestamp,
            technology = "BLE",
            name = name,
            manufacturerId = 741,
            manufacturerData = rawData,
            serviceUuids = emptyList(),
            appearance = null,
            txPower = null,
            isConnectable = true,
            primaryPhy = 1,
            secondaryPhy = 0,
            rawData = rawData,
        )
    }
}
