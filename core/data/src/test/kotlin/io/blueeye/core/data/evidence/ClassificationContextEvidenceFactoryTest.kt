package io.blueeye.core.data.evidence

import io.blueeye.core.data.db.entity.DeviceEntity
import io.blueeye.core.model.DetectionConfidence
import io.blueeye.core.model.DeviceType
import io.blueeye.core.model.EvidenceProvenance
import io.blueeye.core.model.EvidenceSource
import io.blueeye.core.model.MacAddressType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ClassificationContextEvidenceFactoryTest {
    @Test
    fun `ble appearance evidence explains passive classification context`() {
        val evidence =
            DeviceEvidenceFactory.build(
                device(rawData = FLAGS_AD + WATCH_APPEARANCE_AD),
            )

        val appearanceEvidence = evidence.single { it.source == EvidenceSource.APPEARANCE }

        assertEquals(DetectionConfidence.LOW, appearanceEvidence.confidence)
        assertEquals("0x0180", appearanceEvidence.rawValue)
        assertEquals(DeviceType.WATCH.name, appearanceEvidence.parsedValue)
        assertTrue(appearanceEvidence.reasonText.contains("classification context"))
        assertTrue(appearanceEvidence.reasonText.contains("not as a risk signal"))
        assertTrue(appearanceEvidence.isPassive)
        assertEquals(EvidenceProvenance.BLE_ADVERTISEMENT, appearanceEvidence.provenance)
    }

    @Test
    fun `classic class of device evidence explains passive classification context`() {
        val evidence =
            DeviceEvidenceFactory.build(
                device().copy(
                    technology = "CLASSIC",
                    classOfDevice = HEADPHONES_CLASS_OF_DEVICE,
                ),
            )

        val classEvidence = evidence.single { it.source == EvidenceSource.CLASS_OF_DEVICE }

        assertEquals(DetectionConfidence.LOW, classEvidence.confidence)
        assertEquals("0x200418", classEvidence.rawValue)
        assertEquals(DeviceType.HEADPHONES.name, classEvidence.parsedValue)
        assertTrue(classEvidence.reasonText.contains("classification context"))
        assertTrue(classEvidence.reasonText.contains("not as a risk signal"))
        assertTrue(classEvidence.isPassive)
        assertEquals(EvidenceProvenance.CLASSIC_DISCOVERY, classEvidence.provenance)
    }

    private fun device(rawData: String? = null): DeviceEntity =
        DeviceEntity(
            fingerprint = MAC,
            lastMacAddress = MAC,
            macAddressType = MacAddressType.PUBLIC,
            technology = "BLE",
            lastDeviceName = null,
            firstSeenAt = TIMESTAMP,
            lastSeenAt = TIMESTAMP,
            encounterCount = 1,
            lastRawData = rawData,
        )

    private companion object {
        const val MAC = "AA:BB:CC:11:22:33"
        const val TIMESTAMP = 1_789_000_000_000L
        const val FLAGS_AD = "020106"
        const val WATCH_APPEARANCE_AD = "03198001"
        const val HEADPHONES_CLASS_OF_DEVICE = 0x200418
    }
}
