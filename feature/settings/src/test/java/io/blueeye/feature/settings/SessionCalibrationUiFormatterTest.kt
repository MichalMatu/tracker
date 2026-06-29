package io.blueeye.feature.settings

import io.blueeye.core.model.DeviceCalibrationLabel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SessionCalibrationUiFormatterTest {
    @Test
    fun `format exposes unknown as default review state`() {
        val info = SessionCalibrationUiFormatter.format(DeviceCalibrationLabel.UNKNOWN)

        assertEquals("Unknown", info.statusText)
        assertSelected(info, DeviceCalibrationLabel.UNKNOWN)
        assertTrue(info.description.contains("No verdict yet"))
    }

    @Test
    fun `format exposes all session labels`() {
        val info = SessionCalibrationUiFormatter.format(DeviceCalibrationLabel.SUSPICIOUS)

        assertEquals(DeviceCalibrationLabel.entries.size, info.actions.size)
        assertTrue(info.actions.any { it.label == DeviceCalibrationLabel.TRUE_POSITIVE })
        assertTrue(info.actions.any { it.label == DeviceCalibrationLabel.FALSE_POSITIVE })
        assertTrue(info.actions.any { it.label == DeviceCalibrationLabel.KNOWN_SAFE })
        assertTrue(info.actions.any { it.label == DeviceCalibrationLabel.UNKNOWN })
        assertTrue(info.actions.any { it.label == DeviceCalibrationLabel.SUSPICIOUS })
        assertSelected(info, DeviceCalibrationLabel.SUSPICIOUS)
    }

    private fun assertSelected(
        info: SessionCalibrationUiInfo,
        label: DeviceCalibrationLabel,
    ) {
        assertTrue(info.actions.single { it.label == label }.isSelected)
        assertFalse(info.actions.filterNot { it.label == label }.any { it.isSelected })
    }
}
