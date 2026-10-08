package io.blueeye.core.data.classifier.ble

import io.blueeye.core.model.DeviceType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class ServiceUuidClassifierTest {
    @Test
    fun `SmartThings Find advertised service is not proof of SmartTag hardware`() {
        val result = ServiceUuidClassifier.classify(listOf("0000fd5a-0000-1000-8000-00805f9b34fb"))
        assertEquals(DeviceType.UNKNOWN, result.deviceType)
        assertFalse(result.isTracker)
        assertFalse(ServiceUuidClassifier.isKnownTracker(listOf("fd5a")))
    }

    @Test
    fun `FEAA service alone cannot distinguish Eddystone from Find Hub`() {
        val result = ServiceUuidClassifier.classify(listOf("0000feaa-0000-1000-8000-00805f9b34fb"))
        assertEquals(DeviceType.UNKNOWN, result.deviceType)
        assertFalse(result.isBeacon)
    }

    @Test
    fun `DULT service indicates location capability without determining physical type`() {
        val result = ServiceUuidClassifier.classify(listOf("0000fcb2-0000-1000-8000-00805f9b34fb"))
        assertEquals(DeviceType.UNKNOWN, result.deviceType)
        assertFalse(result.isTracker)
        assertEquals("DULT location-enabled accessory", result.serviceName)
    }

    @Test
    fun `dedicated Tile service still classifies a Tile tracker`() {
        val result = ServiceUuidClassifier.classify(listOf("feed"))
        assertEquals(DeviceType.TILE, result.deviceType)
    }
}
