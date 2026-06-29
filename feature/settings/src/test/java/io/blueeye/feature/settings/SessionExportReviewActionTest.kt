package io.blueeye.feature.settings

import io.blueeye.core.model.DetectionConfidence
import io.blueeye.core.model.DetectionEvidence
import io.blueeye.core.model.Device
import io.blueeye.core.model.DeviceCalibrationLabel
import io.blueeye.core.model.DeviceType
import io.blueeye.core.model.EvidenceProvenance
import io.blueeye.core.model.EvidenceSource
import io.blueeye.core.model.IdentityCarryoverVerdict
import io.blueeye.core.model.MacAddressType
import io.blueeye.core.model.TrackingStatus
import org.junit.Assert.assertEquals
import org.junit.Test

class SessionExportReviewActionTest {
    @Test
    fun `identity carryover action asks reviewer to check merged history`() {
        val device =
            device(
                evidence =
                    listOf(
                        DetectionEvidence(
                            source = EvidenceSource.IDENTITY_CARRYOVER,
                            confidence = DetectionConfidence.LOW,
                            reasonText = "Rotating address was correlated with an existing device record.",
                            timestamp = NOW,
                            rawValue = "reason=UUID_OVERLAP;confidence=0.68",
                            parsedValue = "Identity continuity",
                            isPassive = true,
                            provenance = EvidenceProvenance.FOLLOW_ME_ANALYSIS,
                        ),
                    ),
            )

        assertEquals(
            "Review identity carryover before trusting merged history; mark Same Device or False Match.",
            device.sessionExportReviewAction(device.sessionExportReviewCategory()),
        )
    }

    @Test
    fun `reviewed identity carryover action explains saved verdict`() {
        val device =
            device(
                identityCarryoverVerdict = IdentityCarryoverVerdict.FALSE_MATCH,
                evidence =
                    listOf(
                        DetectionEvidence(
                            source = EvidenceSource.IDENTITY_CARRYOVER,
                            confidence = DetectionConfidence.LOW,
                            reasonText = "Rotating address was correlated with an existing device record.",
                            timestamp = NOW,
                            rawValue = "reason=UUID_OVERLAP;confidence=0.68",
                            parsedValue = "Identity continuity",
                            isPassive = true,
                            provenance = EvidenceProvenance.FOLLOW_ME_ANALYSIS,
                        ),
                    ),
            )

        assertEquals(
            "Identity carryover already reviewed as false match; change the verdict only if new evidence appears.",
            device.sessionExportReviewAction(device.sessionExportReviewCategory()),
        )
    }

    @Test
    fun `suppressed device action explains user calibration`() {
        val device =
            device(
                calibrationLabel = DeviceCalibrationLabel.FALSE_POSITIVE,
                followingScore = 82f,
                trackingStatus = TrackingStatus.SUSPICIOUS,
            )

        assertEquals(
            "Already suppressed by user calibration; change the verdict only if new evidence appears.",
            device.sessionExportReviewAction(device.sessionExportReviewCategory()),
        )
    }

    private fun device(
        calibrationLabel: DeviceCalibrationLabel = DeviceCalibrationLabel.UNKNOWN,
        followingScore: Float = 0f,
        trackingStatus: TrackingStatus = TrackingStatus.SAFE,
        identityCarryoverVerdict: IdentityCarryoverVerdict = IdentityCarryoverVerdict.UNREVIEWED,
        evidence: List<DetectionEvidence> = emptyList(),
    ): Device =
        Device(
            fingerprint = "review-device",
            macAddress = "AA:BB:CC:11:22:33",
            macAddressType = MacAddressType.PUBLIC,
            technology = "BLE",
            name = "Review device",
            deviceType = DeviceType.UNKNOWN,
            vendorName = "Unknown Vendor",
            predictedModel = null,
            trackingStatus = trackingStatus,
            followingScore = followingScore,
            isSafeBeacon = false,
            isInWatchlist = false,
            userAlias = null,
            userNotes = null,
            alertSound = false,
            alertVibration = false,
            calibrationLabel = calibrationLabel,
            identityCarryoverVerdict = identityCarryoverVerdict,
            firstSeenAt = NOW,
            lastSeenAt = NOW,
            rssi = -55,
            encounterCount = 1,
            evidence = evidence,
        )

    private companion object {
        private const val NOW = 1_789_000_000_000L
    }
}
