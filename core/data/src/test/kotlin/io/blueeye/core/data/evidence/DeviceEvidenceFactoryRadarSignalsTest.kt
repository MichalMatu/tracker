package io.blueeye.core.data.evidence

import io.blueeye.core.data.db.entity.DeviceEntity
import io.blueeye.core.domain.evidence.DetectionEvidenceClassifier
import io.blueeye.core.model.DeviceCalibrationLabel
import io.blueeye.core.model.DeviceType
import io.blueeye.core.model.EvidenceSource
import io.blueeye.core.model.RadarEvidenceSignals
import io.blueeye.core.model.TrackingStatus
import org.junit.Assert.assertEquals
import org.junit.Test

class DeviceEvidenceFactoryRadarSignalsTest {
    @Test
    fun `radar decision signals stay equivalent to complete evidence construction`() {
        val fixtures =
            listOf(
                device(),
                device(isInWatchlist = true),
                device(
                    lastDeviceName = "AirTag",
                    deviceType = DeviceType.TRACKER,
                ),
                device(
                    lastDeviceName = "Axon Body 3",
                    deviceType = DeviceType.BODY_CAMERA,
                ),
                device(
                    trackingStatus = TrackingStatus.SUSPICIOUS,
                    followingScore = 75f,
                ),
                device(calibrationLabel = DeviceCalibrationLabel.TRUE_POSITIVE),
                device(calibrationLabel = DeviceCalibrationLabel.SUSPICIOUS),
                device(connectionStatus = "PROBED"),
                device(connectionStatus = "RFCOMM_FAIL"),
                device(gattServices = "0000180f-0000-1000-8000-00805f9b34fb"),
            )

        fixtures.forEach { device ->
            assertEquals(
                expectedRadarSignals(device),
                DeviceEvidenceFactory.buildRadarSignals(device),
            )
        }
    }

    private fun expectedRadarSignals(device: DeviceEntity): RadarEvidenceSignals {
        val evidence = DeviceEvidenceFactory.build(device)
        return RadarEvidenceSignals(
            hasWatchlistEvidence = evidence.any { it.source == EvidenceSource.WATCHLIST },
            hasTrackerLikeEvidence =
                evidence.any(DetectionEvidenceClassifier::isTrackerLikeEvidence),
            hasPublicSafetyLikeEvidence =
                evidence.any(DetectionEvidenceClassifier::isPublicSafetyLikeEvidence),
            hasAttentionEvidence =
                evidence.any(DetectionEvidenceClassifier::isAttentionEvidence),
            hasAttentionFollowMeEvidence =
                evidence.any {
                    it.source in FOLLOW_ME_EVIDENCE_SOURCES &&
                        DetectionEvidenceClassifier.isAttentionConfidence(it.confidence)
                },
        )
    }

    @Suppress("LongParameterList")
    private fun device(
        lastDeviceName: String? = "Known headphones",
        deviceType: DeviceType = DeviceType.HEADPHONES,
        trackingStatus: TrackingStatus = TrackingStatus.SAFE,
        followingScore: Float = 0f,
        isInWatchlist: Boolean = false,
        calibrationLabel: DeviceCalibrationLabel = DeviceCalibrationLabel.UNKNOWN,
        connectionStatus: String = "NONE",
        gattServices: String? = null,
    ): DeviceEntity =
        DeviceEntity(
            fingerprint = "fixture",
            lastMacAddress = "AA:BB:CC:11:22:33",
            technology = "BLE",
            lastDeviceName = lastDeviceName,
            deviceType = deviceType,
            trackingStatus = trackingStatus,
            followingScore = followingScore,
            isInWatchlist = isInWatchlist,
            calibrationLabel = calibrationLabel,
            firstSeenAt = NOW - 1_000,
            lastSeenAt = NOW,
            lastRssi = -60,
            connectionStatus = connectionStatus,
            gattServices = gattServices,
        )

    private companion object {
        private const val NOW = 1_789_000_000_000L
        private val FOLLOW_ME_EVIDENCE_SOURCES =
            setOf(
                EvidenceSource.FOLLOW_ME_SCORE,
                EvidenceSource.RSSI_PATTERN,
            )
    }
}
