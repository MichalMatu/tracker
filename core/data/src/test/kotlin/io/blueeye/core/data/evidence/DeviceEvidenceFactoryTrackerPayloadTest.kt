package io.blueeye.core.data.evidence

import io.blueeye.core.data.db.entity.DeviceEntity
import io.blueeye.core.model.DetectionConfidence
import io.blueeye.core.model.DeviceType
import io.blueeye.core.model.EvidenceSource
import io.blueeye.core.model.MacAddressType
import org.junit.Assert.assertTrue
import org.junit.Test

class DeviceEvidenceFactoryTrackerPayloadTest {
    @Test
    fun `tracker beacon payload does not override named tablet identity`() {
        val evidence =
            DeviceEvidenceFactory.build(
                device(
                    name = "Mac",
                    deviceType = DeviceType.TABLET,
                    beaconType = "Apple FindMy / AirTag",
                ),
            )

        assertTrue(
            evidence.none {
                it.confidence == DetectionConfidence.MEDIUM &&
                    it.reasonText.contains("Bluetooth tracker accessory")
            },
        )
        assertTrue(evidence.any { it.source == EvidenceSource.NAME && it.confidence == DetectionConfidence.LOW })
        assertTrue(evidence.any { it.source == EvidenceSource.RAW_PAYLOAD && it.confidence == DetectionConfidence.LOW })
    }

    private fun device(
        name: String,
        deviceType: DeviceType,
        beaconType: String,
    ): DeviceEntity =
        DeviceEntity(
            fingerprint = "AA:BB:CC:11:22:33",
            lastMacAddress = "AA:BB:CC:11:22:33",
            macAddressType = MacAddressType.PUBLIC,
            technology = "BLE",
            lastDeviceName = name,
            deviceType = deviceType,
            firstSeenAt = TIMESTAMP,
            lastSeenAt = TIMESTAMP,
            encounterCount = 1,
            beaconType = beaconType,
        )

    private companion object {
        private const val TIMESTAMP = 1_789_000_000_000L
    }
}
