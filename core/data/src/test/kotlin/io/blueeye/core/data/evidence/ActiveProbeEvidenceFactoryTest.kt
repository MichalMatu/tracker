package io.blueeye.core.data.evidence

import io.blueeye.core.data.db.entity.DeviceEntity
import io.blueeye.core.model.DetectionConfidence
import io.blueeye.core.model.EvidenceProvenance
import io.blueeye.core.model.EvidenceSource
import io.blueeye.core.model.MacAddressType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class ActiveProbeEvidenceFactoryTest {
    @Test
    fun `active GATT probe evidence is marked with active GATT provenance`() {
        val evidence =
            DeviceEvidenceFactory.build(
                device().copy(
                    connectionStatus = "PROBED",
                    lastProbeTimestamp = TIMESTAMP + PROBE_OFFSET_MS,
                    manufacturerName = "Test",
                    modelNumber = "Model",
                    batteryLevel = 88,
                ),
            )

        val probeEvidence = evidence.single { it.source == EvidenceSource.GATT_PROBE }

        assertEquals(DetectionConfidence.MEDIUM, probeEvidence.confidence)
        assertEquals(TIMESTAMP + PROBE_OFFSET_MS, probeEvidence.timestamp)
        assertEquals("PROBED", probeEvidence.rawValue)
        assertEquals("Test Model 88%", probeEvidence.parsedValue)
        assertFalse(probeEvidence.isPassive)
        assertEquals(EvidenceProvenance.ACTIVE_GATT, probeEvidence.provenance)
    }

    @Test
    fun `active RFCOMM probe evidence is marked with active RFCOMM provenance`() {
        val evidence =
            DeviceEvidenceFactory.build(
                device().copy(
                    connectionStatus = "RFCOMM_FAIL",
                    lastProbeTimestamp = TIMESTAMP + PROBE_OFFSET_MS,
                ),
            )

        val probeEvidence = evidence.single { it.source == EvidenceSource.RFCOMM_PROBE }

        assertEquals(DetectionConfidence.MEDIUM, probeEvidence.confidence)
        assertEquals("RFCOMM_FAIL", probeEvidence.rawValue)
        assertFalse(probeEvidence.isPassive)
        assertEquals(EvidenceProvenance.ACTIVE_RFCOMM, probeEvidence.provenance)
    }

    private fun device(): DeviceEntity =
        DeviceEntity(
            fingerprint = MAC,
            lastMacAddress = MAC,
            macAddressType = MacAddressType.PUBLIC,
            technology = "BLE",
            lastDeviceName = null,
            firstSeenAt = TIMESTAMP,
            lastSeenAt = TIMESTAMP,
            encounterCount = 1,
        )

    private companion object {
        const val MAC = "AA:BB:CC:11:22:33"
        const val TIMESTAMP = 1_789_000_000_000L
        const val PROBE_OFFSET_MS = 10_000L
    }
}
