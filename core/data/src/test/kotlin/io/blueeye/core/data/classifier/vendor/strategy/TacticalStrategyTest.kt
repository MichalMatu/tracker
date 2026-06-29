package io.blueeye.core.data.classifier.vendor.strategy

import io.blueeye.core.data.classifier.vendor.TacticalOuiRegistry
import io.blueeye.core.model.DeviceType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class TacticalStrategyTest {
    private val strategy = TacticalStrategy()

    @Test
    fun `Axon body camera name is a medium-confidence body camera signal`() {
        val result = strategy.analyzeName("Axon Body 3")

        assertNotNull(result)
        assertEquals(DeviceType.BODY_CAMERA, result?.deviceType)
        assertEquals("Name match - medium confidence", result?.extraInfo)
        assertNeutral(result?.extraInfo)
    }

    @Test
    fun `Taser name is not classified as body camera`() {
        val result = strategy.analyzeName("Taser 7")

        assertNotNull(result)
        assertEquals(DeviceType.SMART_WEAPON, result?.deviceType)
        assertEquals("Name match - medium confidence", result?.extraInfo)
        assertNeutral(result?.extraInfo)
    }

    @Test
    fun `Hytera name is a medium-confidence radio signal`() {
        val result = strategy.analyzeName("Hytera PD785G")

        assertNotNull(result)
        assertEquals(DeviceType.TACTICAL_RADIO, result?.deviceType)
        assertEquals("Name match - medium confidence", result?.extraInfo)
        assertNeutral(result?.extraInfo)
    }

    @Test
    fun `standalone V60 consumer-style name is not tactical audio`() {
        assertNull(strategy.analyzeName("Samsung V60"))
        assertNull(TacticalOuiRegistry.matchByName("Samsung V60"))
    }

    @Test
    fun `Samsung Clarus consumer-style name is not tactical audio`() {
        assertNull(strategy.analyzeName("Samsung Clarus"))
    }

    @Test
    fun `ATAK substring does not match attack word`() {
        assertNull(strategy.analyzeName("Attack Speaker"))
        assertNull(TacticalOuiRegistry.matchByName("Attack Speaker"))
    }

    @Test
    fun `loose Axon and body words are ignored without device context`() {
        listOf("Axon Speaker", "Body Scale 3", "Body Battery 4").forEach { name ->
            assertNull(strategy.analyzeName(name))
            assertNull(TacticalOuiRegistry.matchByName(name))
        }
    }

    @Test
    fun `loose SIG BDX family words are ignored`() {
        listOf("Sierra Speaker", "Whiskey Speaker", "Kilo Keyboard").forEach { name ->
            assertNull(strategy.analyzeName(name))
            assertNull(TacticalOuiRegistry.matchByName(name))
        }
    }

    @Test
    fun `SIG optics model remains a medium-confidence smart weapon signal`() {
        val result = strategy.analyzeName("Sig Kilo3K")

        assertNotNull(result)
        assertEquals(DeviceType.SMART_WEAPON, result?.deviceType)
        assertEquals("Name match - medium confidence", result?.extraInfo)
        assertNeutral(result?.extraInfo)
    }

    private fun assertNeutral(label: String?) {
        val text = label.orEmpty().lowercase()
        assertFalse(text.contains("police"))
        assertFalse(text.contains("law enforcement"))
        assertFalse(text.contains("alarm"))
        assertFalse(text.contains("nearby"))
        assertFalse(text.contains("⚠"))
        assertFalse(text.contains("🚨"))
    }
}
