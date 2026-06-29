@file:Suppress("WildcardImport", "NoWildcardImports")

package io.blueeye.core.data.classifier.vendor

import io.blueeye.core.data.classifier.vendor.tactical.*
import io.blueeye.core.model.DeviceType
import org.junit.Assert.*
import org.junit.Test

/**
 * Unit tests for TacticalOuiRegistry.
 *
 * Tests OUI lookup, name pattern matching, and UUID detection for tactical equipment.
 */
class TacticalOuiRegistryTest {
    // === OUI LOOKUP TESTS ===

    @Test
    fun `lookup returns CRITICAL for Axon OUI`() {
        val result = TacticalOuiRegistry.lookup("00:25:DF:12:34:56")

        assertNotNull(result)
        assertEquals("Axon Enterprise, Inc.", result!!.vendorName)
        assertEquals(ConfidenceLevel.CRITICAL, result.confidence)
        assertEquals(TacticalCategory.BODY_CAMERA, result.category)
        assertEquals(DeviceType.BODY_CAMERA, result.deviceType)
    }

    @Test
    fun `lookup returns CRITICAL for Invisio OUI`() {
        val result = TacticalOuiRegistry.lookup("00:14:CF:AA:BB:CC")

        assertNotNull(result)
        assertEquals("INVISIO Communications", result!!.vendorName)
        assertEquals(ConfidenceLevel.CRITICAL, result.confidence)
        assertEquals(TacticalCategory.TACTICAL_AUDIO, result.category)
        assertEquals(DeviceType.TACTICAL_AUDIO, result.deviceType)
    }

    @Test
    fun `lookup returns HIGH for Motorola Solutions OUI`() {
        val result = TacticalOuiRegistry.lookup("00:04:7D:11:22:33")

        assertNotNull(result)
        assertEquals("Motorola Solutions Inc.", result!!.vendorName)
        assertEquals(ConfidenceLevel.HIGH, result.confidence)
        assertEquals(TacticalCategory.TACTICAL_RADIO, result.category)
    }

    @Test
    fun `lookup returns HIGH for Cradlepoint OUI`() {
        val result = TacticalOuiRegistry.lookup("00:30:44:11:22:33")

        assertNotNull(result)
        assertEquals("Cradlepoint", result!!.vendorName)
        assertEquals(ConfidenceLevel.HIGH, result.confidence)
        assertEquals(TacticalCategory.VEHICLE_ROUTER, result.category)
    }

    @Test
    fun `lookup handles MAC without colons`() {
        val result = TacticalOuiRegistry.lookup("0025DF123456")

        assertNotNull(result)
        assertEquals("Axon Enterprise, Inc.", result!!.vendorName)
    }

    @Test
    fun `lookup handles lowercase MAC`() {
        val result = TacticalOuiRegistry.lookup("00:25:df:12:34:56")

        assertNotNull(result)
        assertEquals("Axon Enterprise, Inc.", result!!.vendorName)
    }

    @Test
    fun `lookup returns null for unknown OUI`() {
        val result = TacticalOuiRegistry.lookup("AA:BB:CC:DD:EE:FF")

        assertNull(result)
    }

    @Test
    fun `lookup returns null for Apple OUI formerly duplicated in tactical data`() {
        val result = TacticalOuiRegistry.lookup("00:17:F2:12:34:56")

        assertNull(result)
    }

    @Test
    fun `lookup returns null for invalid MAC`() {
        val result = TacticalOuiRegistry.lookup("invalid")

        assertNull(result)
    }

    // === isCriticalTactical TESTS ===

    @Test
    fun `isCriticalTactical returns true for Axon`() {
        assertTrue(TacticalOuiRegistry.isCriticalTactical("00:25:DF:12:34:56"))
    }

    @Test
    fun `isCriticalTactical returns true for Invisio`() {
        assertTrue(TacticalOuiRegistry.isCriticalTactical("00:14:CF:AA:BB:CC"))
    }

    @Test
    fun `isCriticalTactical returns false for Motorola (HIGH confidence)`() {
        assertFalse(TacticalOuiRegistry.isCriticalTactical("00:04:7D:11:22:33"))
    }

    @Test
    fun `isCriticalTactical returns false for unknown MAC`() {
        assertFalse(TacticalOuiRegistry.isCriticalTactical("AA:BB:CC:DD:EE:FF"))
    }

    // === NAME MATCHING TESTS ===

    @Test
    fun `matchByName detects Axon body camera`() {
        val result = TacticalOuiRegistry.matchByName("Axon Body 3")

        assertNotNull(result)
        assertEquals(TacticalCategory.BODY_CAMERA, result!!.first)
    }

    @Test
    fun `matchByName detects Signal Sidearm serial`() {
        val result = TacticalOuiRegistry.matchByName("X81001683")

        assertNotNull(result)
        assertEquals(TacticalCategory.HOLSTER_SENSOR, result!!.first)
    }

    @Test
    fun `matchByName ignores loose Axon and body words`() {
        assertNull(TacticalOuiRegistry.matchByName("Axon Speaker"))
        assertNull(TacticalOuiRegistry.matchByName("Body Scale 3"))
    }

    @Test
    fun `matchByName detects Invisio IAD`() {
        val result = TacticalOuiRegistry.matchByName("IAD 01")

        assertNotNull(result)
        assertEquals(TacticalCategory.TACTICAL_AUDIO, result!!.first)
    }

    @Test
    fun `matchByName detects Yardarm sensor`() {
        val result = TacticalOuiRegistry.matchByName("YHA-12345")

        assertNotNull(result)
        assertEquals(TacticalCategory.HOLSTER_SENSOR, result!!.first)
    }

    @Test
    fun `matchByName detects Sig Sauer BDX`() {
        val result = TacticalOuiRegistry.matchByName("KILO3K")

        assertNotNull(result)
        assertEquals(TacticalCategory.SMART_WEAPON, result!!.first)
    }

    @Test
    fun `matchByName detects Meshtastic`() {
        val result = TacticalOuiRegistry.matchByName("Meshtastic_1234")

        assertNotNull(result)
        assertEquals(TacticalCategory.TACTICAL_EUD, result!!.first)
    }

    @Test
    fun `matchByName returns null for regular device`() {
        val result = TacticalOuiRegistry.matchByName("Samsung Galaxy S24")

        assertNull(result)
    }

    @Test
    fun `matchByName returns null for empty string`() {
        val result = TacticalOuiRegistry.matchByName("")

        assertNull(result)
    }

    // === SERVICE UUID MATCHING TESTS ===

    @Test
    fun `matchByServiceUuid detects Meshtastic`() {
        val uuids = listOf("6ba1b218-15a8-461f-9fa8-5dcae273eafd")

        val result = TacticalOuiRegistry.matchByServiceUuid(uuids)

        assertNotNull(result)
        assertEquals(TacticalCategory.TACTICAL_EUD, result!!.first)
        assertTrue(result.second.contains("Meshtastic"))
    }

    @Test
    fun `matchByServiceUuid returns null for generic UUIDs`() {
        val uuids =
            listOf(
                "0000180a-0000-1000-8000-00805f9b34fb", // Device Information
                "0000180f-0000-1000-8000-00805f9b34fb" // Battery Service
            )

        val result = TacticalOuiRegistry.matchByServiceUuid(uuids)

        assertNull(result)
    }

    @Test
    fun `matchByServiceUuid returns null for empty list`() {
        val result = TacticalOuiRegistry.matchByServiceUuid(emptyList())

        assertNull(result)
    }

    // === STATS AND UTILITY TESTS ===

    @Test
    fun `getAllOuiPrefixes returns non-empty set`() {
        val prefixes = TacticalOuiRegistry.getAllOuiPrefixes()

        assertTrue(prefixes.isNotEmpty())
        assertTrue(prefixes.contains("0025DF")) // Axon
        assertTrue(prefixes.contains("0014CF")) // Invisio
        assertFalse(prefixes.contains("0017F2")) // Apple in app/src/main/assets/manuf.txt
    }

    @Test
    fun `OUI prefixes are unique`() {
        val duplicatePrefixes =
            TacticalOuiData.getAllInfos()
                .groupBy { it.ouiPrefix }
                .filterValues { it.size > 1 }

        assertTrue("Duplicate tactical OUI prefixes: $duplicatePrefixes", duplicatePrefixes.isEmpty())
    }

    @Test
    fun `getStats returns counts by confidence level`() {
        val stats = TacticalOuiRegistry.getStats()

        assertTrue(stats.containsKey(ConfidenceLevel.CRITICAL))
        assertTrue((stats[ConfidenceLevel.CRITICAL] ?: 0) >= 2) // At least Axon + Invisio
    }

    @Test
    fun `OUI descriptions stay evidence based and neutral`() {
        TacticalOuiData.getAllInfos().forEach { info ->
            assertNeutralEvidenceLabel(info.description)
        }
    }

    @Test
    fun `manufacturer fallback labels stay evidence based and neutral`() {
        listOf(
            TacticalUuids.AXON_COMPANY_ID,
            TacticalUuids.YARDARM_COMPANY_ID,
            TacticalUuids.MOTOROLA_COMPANY_ID,
            TacticalUuids.WURTH_ELEKTRONIK_ID,
            TacticalUuids.SIERRA_WIRELESS_ID,
            TacticalUuids.DRAEGER_ID,
            TacticalUuids.PANASONIC_ID,
        ).mapNotNull(TacticalOuiRegistry::matchByManufacturerId)
            .forEach { match ->
                assertNeutralEvidenceLabel(match.second)
            }
    }

    // === PROFESSIONAL EQUIPMENT PATTERNS ===

    @Test
    fun `matchByServiceUuid detects Motorola Solutions UUID 0xFD8E`() {
        val uuids = listOf("0000fd8e-0000-1000-8000-00805f9b34fb")

        val result = TacticalOuiRegistry.matchByServiceUuid(uuids)

        assertNotNull(result)
        assertEquals(TacticalCategory.TACTICAL_RADIO, result!!.first)
        assertTrue(result.second.contains("Motorola"))
    }

    @Test
    fun `matchByServiceUuid detects short Motorola UUID format`() {
        val uuids = listOf("fd8e")

        val result = TacticalOuiRegistry.matchByServiceUuid(uuids)

        assertNotNull(result)
        assertEquals(TacticalCategory.TACTICAL_RADIO, result!!.first)
    }

    @Test
    fun `matchByName detects Radetec Smart Slide`() {
        val result = TacticalOuiRegistry.matchByName("Radetec Smart Slide")

        assertNotNull(result)
        assertEquals(TacticalCategory.SMART_WEAPON, result!!.first)
        assertTrue(result.second.contains("Radetec"))
    }

    @Test
    fun `matchByName detects Hytera PD785 radio family`() {
        val result = TacticalOuiRegistry.matchByName("PD785G")

        assertNotNull(result)
        assertEquals(TacticalCategory.TACTICAL_RADIO, result!!.first)
    }

    @Test
    fun `matchByName detects Invisio R30 PTT`() {
        val result = TacticalOuiRegistry.matchByName("R30 Wireless")

        assertNotNull(result)
        assertEquals(TacticalCategory.TACTICAL_AUDIO, result!!.first)
    }

    @Test
    fun `lookup returns HIGH for Hytera OUI`() {
        val result = TacticalOuiRegistry.lookup("9C:06:6E:12:34:56")

        assertNotNull(result)
        assertEquals("Hytera Communications", result!!.vendorName)
        assertEquals(ConfidenceLevel.HIGH, result.confidence)
    }

    @Test
    fun `matchByName detects Peltor ComTac`() {
        val result = TacticalOuiRegistry.matchByName("ComTac VII")

        assertNotNull(result)
        assertEquals(TacticalCategory.TACTICAL_AUDIO, result!!.first)
    }

    @Test
    fun `matchByName detects Medical LifePak (PRM)`() {
        val result = TacticalOuiRegistry.matchByName("LifePak 15")

        assertNotNull(result)
        assertEquals(TacticalCategory.FIRE_EMS, result!!.first)
    }

    // ============================================================
    // === EUROPEAN EQUIPMENT TESTS
    // ============================================================

    @Test
    fun `matchByName detects Reveal Media D5`() {
        val result = TacticalOuiRegistry.matchByName("Reveal D5")

        assertNotNull(result)
        assertEquals(TacticalCategory.BODY_CAMERA, result!!.first)
        assertTrue(result.second.contains("UK"))
    }

    @Test
    fun `matchByName detects Zepcam T3 Live (Netherlands)`() {
        val result = TacticalOuiRegistry.matchByName("Zepcam T3 Live")

        assertNotNull(result)
        assertEquals(TacticalCategory.BODY_CAMERA, result!!.first)
        assertTrue(result.second.contains("NL"))
    }

    @Test
    fun `matchByName detects Crosscall Core-X5 (France NEO)`() {
        val result = TacticalOuiRegistry.matchByName("Core-X5")

        assertNotNull(result)
        assertEquals(TacticalCategory.TACTICAL_EUD, result!!.first)
        assertTrue(result.second.contains("FR"))
    }

    @Test
    fun `matchByName detects Airbus TH9 Tetrapol (France)`() {
        val result = TacticalOuiRegistry.matchByName("TH9 Terminal")

        assertNotNull(result)
        assertEquals(TacticalCategory.TACTICAL_RADIO, result!!.first)
    }

    @Test
    fun `matchByName detects Motorola MTP6650 (Germany BDBOS)`() {
        val result = TacticalOuiRegistry.matchByName("MTP6650")

        assertNotNull(result)
        assertEquals(TacticalCategory.TACTICAL_RADIO, result!!.first)
        // Note: matches broader MOTOROLA_TETRA_PATTERNS first
        assertTrue(result.second.contains("Motorola") || result.second.contains("TETRA"))
    }

    @Test
    fun `matchByName detects Samsung XCover FieldPro (UK ESN)`() {
        val result = TacticalOuiRegistry.matchByName("Galaxy XCover FieldPro")

        assertNotNull(result)
        assertEquals(TacticalCategory.TACTICAL_EUD, result!!.first)
    }

    @Test
    fun `matchByName detects Zebra TC77 rugged terminal`() {
        val result = TacticalOuiRegistry.matchByName("TC77")

        assertNotNull(result)
        assertEquals(TacticalCategory.TACTICAL_EUD, result!!.first)
    }

    @Test
    fun `matchByName detects Bluebird EF501 rugged terminal`() {
        val result = TacticalOuiRegistry.matchByName("Bluebird EF501R")

        assertNotNull(result)
        assertEquals(TacticalCategory.TACTICAL_EUD, result!!.first)
    }

    @Test
    fun `matchByManufacturerId detects Yardarm Holster Aware`() {
        val result = TacticalOuiRegistry.matchByManufacturerId(0x025F)

        assertNotNull(result)
        assertEquals(TacticalCategory.HOLSTER_SENSOR, result!!.first)
        assertTrue(result.second.contains("Yardarm"))
    }

    @Test
    fun `matchByManufacturerId detects Axon Signal`() {
        val result = TacticalOuiRegistry.matchByManufacturerId(0x034D)

        assertNotNull(result)
        assertEquals(TacticalCategory.BODY_CAMERA, result!!.first)
        assertTrue(result.second.contains("Axon"))
    }

    private fun assertNeutralEvidenceLabel(label: String) {
        val text = label.lowercase()
        assertFalse(text.contains("police"))
        assertFalse(text.contains("policja"))
        assertFalse(text.contains("law enforcement"))
        assertFalse(text.contains("s\u0142u\u017c"))
        assertFalse(text.contains("sluz"))
        assertFalse(text.contains("swat"))
        assertFalse(text.contains("sof"))
        assertFalse(text.contains("military"))
        assertFalse(text.contains("nearby"))
        assertFalse(text.contains("alarm"))
    }
}
