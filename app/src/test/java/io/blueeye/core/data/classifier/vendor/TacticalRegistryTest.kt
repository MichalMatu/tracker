@file:Suppress("WildcardImport", "NoWildcardImports")

package io.blueeye.core.data.classifier.vendor

import io.blueeye.core.data.classifier.vendor.tactical.*
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class TacticalRegistryTest {
    @Test
    fun `test Axon bodycam detection`() {
        val mac = "00:25:DF:11:22:33" // Axon OUI
        val result = TacticalOuiRegistry.lookup(mac)

        assertNotNull("Should detect Axon device", result)
        assertEquals("Wrong vendor", "Axon Enterprise, Inc.", result?.vendorName)
        assertEquals("Wrong category", TacticalCategory.BODY_CAMERA, result?.category)
        assertEquals("Wrong confidence", ConfidenceLevel.CRITICAL, result?.confidence)
    }

    @Test
    fun `test Motorola radio detection`() {
        // Using valid Motorola OUI from registry: 00:04:7D
        val mac = "00:04:7D:AA:BB:CC"
        val result = TacticalOuiRegistry.lookup(mac)

        assertNotNull("Should detect Motorola device", result)
        assertEquals("Wrong vendor", "Motorola Solutions Inc.", result?.vendorName)
        assertEquals("Wrong confidence", ConfidenceLevel.HIGH, result?.confidence)
    }

    @Test
    fun `test Civilian detection should fail`() {
        val mac = "AA:BB:CC:11:22:33"
        val result = TacticalOuiRegistry.lookup(mac)

        assertNull("Should NOT detect random civilian device as tactical", result)
    }

    @Test
    fun `test Lowercase MAC handling`() {
        val mac = "00:25:df:11:22:33" // Axon lower
        val result = TacticalOuiRegistry.lookup(mac)

        assertNotNull("Should match case-insensitively", result)
    }

    @Test
    fun `test New Axon OUI detection`() {
        val mac = "84:70:03:11:22:33"
        val result = TacticalOuiRegistry.lookup(mac)

        assertNotNull("Should detect new Axon Networks OUI", result)
        assertEquals("Axon Networks Inc.", result?.vendorName)
    }

    @Test
    fun `test Radetec Name detection`() {
        val name = "My RISCpro Device"
        val result = TacticalOuiRegistry.matchByName(name)

        assertNotNull("Should detect RISC in name", result)
        assertEquals(TacticalCategory.SMART_WEAPON, result?.first)
    }

    @Test
    fun `test Samsung Tactical detection`() {
        val mac = "00:E0:64:11:22:33"
        val result = TacticalOuiRegistry.lookup(mac)

        assertNotNull("Should detect Samsung Tactical", result)
        assertEquals("Samsung Electronics", result?.vendorName)
        assertEquals(TacticalCategory.TACTICAL_EUD, result?.category)
    }

    @Test
    fun `test Kestrel Name detection`() {
        assertNotNull(TacticalOuiRegistry.matchByName("Kestrel 5700"))
    }
}
