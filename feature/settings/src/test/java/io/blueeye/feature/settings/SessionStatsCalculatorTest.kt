package io.blueeye.feature.settings

import io.blueeye.core.model.AlertEvidenceEvent
import io.blueeye.core.model.AlertEvidenceEventType
import io.blueeye.core.model.DetectionConfidence
import io.blueeye.core.model.DetectionEvidence
import io.blueeye.core.model.Device
import io.blueeye.core.model.DeviceCalibrationLabel
import io.blueeye.core.model.DeviceType
import io.blueeye.core.model.EvidenceSource
import io.blueeye.core.model.IdentityCarryoverVerdict
import io.blueeye.core.model.MacAddressType
import io.blueeye.core.model.SignalSample
import io.blueeye.core.model.TrackingStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SessionStatsCalculatorTest {
    @Test
    fun `inactive session has empty stats`() {
        val stats =
            SessionStatsCalculator.calculate(
                startedAt = 0L,
                devices = listOf(device(lastSeenAt = NOW, evidence = listOf(evidence()))),
                samples = listOf(sample(timestamp = NOW)),
                alertEvidenceEvents = listOf(alertEvent(timestamp = NOW)),
            )

        assertFalse(stats.hasStarted)
        assertEquals(0, stats.deviceCount)
        assertEquals(0, stats.sampleCount)
        assertEquals(0, stats.evidenceCount)
    }

    @Test
    fun `active session counts only current session data`() {
        val stats =
            SessionStatsCalculator.calculate(
                startedAt = NOW,
                devices =
                    listOf(
                        device(
                            lastSeenAt = NOW + 1_000L,
                            evidence =
                                listOf(
                                    evidence(confidence = DetectionConfidence.LOW),
                                    evidence(confidence = DetectionConfidence.HIGH),
                                ),
                        ),
                        device(
                            lastSeenAt = NOW - 1_000L,
                            evidence = listOf(evidence(confidence = DetectionConfidence.CRITICAL)),
                        ),
                    ),
                samples =
                    listOf(
                        sample(timestamp = NOW + 1_000L),
                        sample(timestamp = NOW + 2_000L, latitude = 52.2, longitude = 21.0),
                        sample(timestamp = NOW - 1_000L),
                    ),
                alertEvidenceEvents = emptyList(),
            )

        assertTrue(stats.hasStarted)
        assertEquals(1, stats.deviceCount)
        assertEquals(2, stats.sampleCount)
        assertEquals(1, stats.gpsSampleCount)
        assertEquals(2, stats.evidenceCount)
        assertEquals(1, stats.attentionEvidenceCount)
        assertEquals(2_000L, stats.durationMs)
        assertEquals(0, stats.reviewCategoryCounts.watchlist)
        assertEquals(1, stats.reviewCategoryCounts.suspicious)
        assertEquals(0, stats.reviewCategoryCounts.publicSafety)
        assertEquals(0, stats.reviewCategoryCounts.nearby)
        assertEquals(0, stats.reviewCategoryCounts.unknownNoise)
        assertEquals(2, stats.rssiQuality.sampleCount)
        assertEquals(listOf(RssiQualityWarning.TOO_FEW_SAMPLES), stats.rssiQuality.warnings)
    }

    @Test
    fun `active session exposes review category mix`() {
        val stats =
            SessionStatsCalculator.calculate(
                startedAt = NOW,
                devices =
                    listOf(
                        device(lastSeenAt = NOW + 1_000L, evidence = emptyList()).copy(isInWatchlist = true),
                        device(lastSeenAt = NOW + 2_000L, evidence = listOf(evidence(DetectionConfidence.HIGH))),
                        device(
                            lastSeenAt = NOW + 3_000L,
                            evidence =
                                listOf(
                                    evidence(source = EvidenceSource.MANUFACTURER_ID).copy(
                                        reasonText = "Axon body camera signature.",
                                        parsedValue = "BODY_CAMERA",
                                    ),
                                ),
                        ).copy(deviceType = DeviceType.BODY_CAMERA),
                        device(lastSeenAt = NOW + 4_000L, evidence = emptyList()).copy(
                            name = "AirPods",
                            vendorName = "Apple",
                        ),
                        device(lastSeenAt = NOW + 5_000L, evidence = emptyList()).copy(
                            name = "Unknown Device",
                            vendorName = "Unknown Vendor",
                        ),
                        device(lastSeenAt = NOW - 1_000L, evidence = emptyList()).copy(isInWatchlist = true),
                    ),
                samples = emptyList(),
                alertEvidenceEvents = emptyList(),
            )

        assertEquals(5, stats.deviceCount)
        assertEquals(1, stats.reviewCategoryCounts.watchlist)
        assertEquals(1, stats.reviewCategoryCounts.suspicious)
        assertEquals(1, stats.reviewCategoryCounts.publicSafety)
        assertEquals(1, stats.reviewCategoryCounts.nearby)
        assertEquals(1, stats.reviewCategoryCounts.unknownNoise)
    }

    @Test
    fun `active session exposes active probe summary`() {
        val stats =
            SessionStatsCalculator.calculate(
                startedAt = NOW,
                devices =
                    listOf(
                        device(lastSeenAt = NOW + 1_000L, evidence = emptyList()).copy(
                            fingerprint = "probed-device",
                            serialNumber = "SN123",
                            connectionStatus = "PROBED",
                        ),
                        device(lastSeenAt = NOW + 2_000L, evidence = emptyList()).copy(
                            fingerprint = "passive-device",
                        ),
                        device(lastSeenAt = NOW - 1_000L, evidence = emptyList()).copy(
                            fingerprint = "old-probed-device",
                            serialNumber = "OLD123",
                            connectionStatus = "FAILED",
                        ),
                    ),
                samples = emptyList(),
                alertEvidenceEvents = emptyList(),
            )

        assertEquals(1, stats.activeProbeSummary.dataDeviceCount)
        assertEquals(mapOf("PROBED" to 1), stats.activeProbeSummary.statusCounts)
    }

    @Test
    fun `active session exposes rssi trend summary per device`() {
        val stats =
            SessionStatsCalculator.calculate(
                startedAt = NOW,
                devices =
                    listOf(
                        device(lastSeenAt = NOW + 1_000L, evidence = emptyList()),
                    ),
                samples =
                    listOf(
                        sample(
                            timestamp = NOW + 1_000L,
                            rssi = -72,
                            deviceFingerprint = "strengthening",
                        ),
                        sample(
                            timestamp = NOW + 2_000L,
                            rssi = -68,
                            deviceFingerprint = "strengthening",
                        ),
                        sample(
                            timestamp = NOW + 3_000L,
                            rssi = -60,
                            deviceFingerprint = "strengthening",
                        ),
                        sample(
                            timestamp = NOW + 4_000L,
                            rssi = -56,
                            deviceFingerprint = "strengthening",
                        ),
                        sample(
                            timestamp = NOW + 1_000L,
                            rssi = -60,
                            deviceFingerprint = "stable",
                        ),
                        sample(
                            timestamp = NOW + 2_000L,
                            rssi = -61,
                            deviceFingerprint = "stable",
                        ),
                        sample(
                            timestamp = NOW + 3_000L,
                            rssi = -59,
                            deviceFingerprint = "stable",
                        ),
                        sample(
                            timestamp = NOW + 4_000L,
                            rssi = -60,
                            deviceFingerprint = "stable",
                        ),
                    ),
                alertEvidenceEvents = emptyList(),
            )

        assertEquals(2, stats.rssiTrendSummary.deviceCount)
        assertEquals(1, stats.rssiTrendSummary.strengtheningCount)
        assertEquals(0, stats.rssiTrendSummary.weakeningCount)
        assertEquals(1, stats.rssiTrendSummary.stableCount)
        assertEquals(0, stats.rssiTrendSummary.insufficientCount)
        assertEquals(12.0, stats.rssiTrendSummary.strongestStrengtheningDeltaRssi ?: 0.0, 0.001)
    }

    @Test
    fun `active session duration uses latest device when there are no samples`() {
        val stats =
            SessionStatsCalculator.calculate(
                startedAt = NOW,
                devices =
                    listOf(
                        device(lastSeenAt = NOW + 10 * 60 * 1_000L, evidence = emptyList()),
                    ),
                samples = emptyList(),
                alertEvidenceEvents = emptyList(),
            )

        assertEquals(10 * 60 * 1_000L, stats.durationMs)
    }

    @Test
    fun `active session exposes alert history summary`() {
        val stats =
            SessionStatsCalculator.calculate(
                startedAt = NOW,
                devices =
                    listOf(
                        device(lastSeenAt = NOW + 1_000L, evidence = emptyList()),
                    ),
                samples = emptyList(),
                alertEvidenceEvents =
                    listOf(
                        alertEvent(
                            timestamp = NOW - 1_000L,
                            eventType = AlertEvidenceEventType.FOLLOW_ME_ALERT,
                        ),
                        alertEvent(
                            timestamp = NOW + 2_000L,
                            eventType = AlertEvidenceEventType.FOLLOW_ME_ALERT,
                        ),
                        alertEvent(
                            timestamp = NOW + 3_000L,
                            eventType = AlertEvidenceEventType.WATCHLIST_RETURN,
                        ),
                        alertEvent(
                            timestamp = NOW + 4_000L,
                            eventType = AlertEvidenceEventType.PUBLIC_SAFETY_SIGNAL,
                        ),
                    ),
            )

        assertEquals(3, stats.alertHistorySummary.eventCount)
        assertEquals(1, stats.alertHistorySummary.followMeAlertCount)
        assertEquals(1, stats.alertHistorySummary.watchlistReturnCount)
        assertEquals(1, stats.alertHistorySummary.publicSafetySignalCount)
        assertEquals(4_000L, stats.durationMs)
    }

    @Test
    fun `active session exposes identity carryover summary`() {
        val stats =
            SessionStatsCalculator.calculate(
                startedAt = NOW,
                devices =
                    listOf(
                        device(
                            lastSeenAt = NOW + 1_000L,
                            evidence = listOf(evidence(source = EvidenceSource.IDENTITY_CARRYOVER)),
                        ),
                        device(
                            lastSeenAt = NOW + 2_000L,
                            evidence = emptyList(),
                        ),
                        device(
                            lastSeenAt = NOW - 1_000L,
                            evidence = listOf(evidence(source = EvidenceSource.IDENTITY_CARRYOVER)),
                        ),
                    ),
                samples = emptyList(),
                alertEvidenceEvents = emptyList(),
            )

        assertEquals(1, stats.identityCarryoverSummary.deviceCount)
    }

    @Test
    fun `reviewed identity carryover is not actionable in session review`() {
        val stats =
            SessionStatsCalculator.calculate(
                startedAt = NOW,
                devices =
                    listOf(
                        device(
                            lastSeenAt = NOW + 1_000L,
                            evidence = listOf(evidence(source = EvidenceSource.IDENTITY_CARRYOVER)),
                        ).copy(
                            identityCarryoverVerdict = IdentityCarryoverVerdict.CONFIRMED_SAME_DEVICE,
                        ),
                    ),
                samples = emptyList(),
                alertEvidenceEvents = emptyList(),
            )

        assertEquals(0, stats.identityCarryoverSummary.deviceCount)
        assertTrue(stats.reviewDeviceQueue.isEmpty())
    }

    @Test
    fun `active session exposes prioritized device review queue`() {
        val stats =
            SessionStatsCalculator.calculate(
                startedAt = NOW,
                devices =
                    listOf(
                        device(lastSeenAt = NOW + 1_000L, evidence = emptyList()).copy(
                            fingerprint = "suspicious-device",
                            macAddress = "AA:BB:CC:11:22:01",
                            name = "Suspicious headphones",
                            followingScore = 51f,
                        ),
                        device(
                            lastSeenAt = NOW + 2_000L,
                            evidence = listOf(evidence(source = EvidenceSource.IDENTITY_CARRYOVER)),
                        ).copy(
                            fingerprint = "identity-device",
                            macAddress = "AA:BB:CC:11:22:02",
                            name = "Merged identity",
                        ),
                        device(lastSeenAt = NOW + 3_000L, evidence = emptyList()).copy(
                            fingerprint = "watchlist-device",
                            macAddress = "AA:BB:CC:11:22:03",
                            name = "Watchlisted headphones",
                            isInWatchlist = true,
                        ),
                        device(lastSeenAt = NOW + 4_000L, evidence = emptyList()).copy(
                            fingerprint = "follow-device",
                            macAddress = "AA:BB:CC:11:22:04",
                            name = "Follow alert device",
                        ),
                    ),
                samples = emptyList(),
                alertEvidenceEvents =
                    listOf(
                        alertEvent(
                            timestamp = NOW + 5_000L,
                            eventType = AlertEvidenceEventType.FOLLOW_ME_ALERT,
                            deviceFingerprint = "follow-device",
                        ),
                    ),
            )

        assertEquals(3, stats.reviewDeviceQueue.size)
        assertEquals("follow-device", stats.reviewDeviceQueue[0].fingerprint)
        assertEquals("Follow alert device", stats.reviewDeviceQueue[0].displayName)
        assertEquals("Follow-Me alert", stats.reviewDeviceQueue[0].reasonText)
        assertEquals(
            DeviceCalibrationLabel.SUSPICIOUS,
            stats.reviewDeviceQueue[0].decisions[0].deviceCalibrationLabel,
        )
        assertEquals(
            DeviceCalibrationLabel.FALSE_POSITIVE,
            stats.reviewDeviceQueue[0].decisions[1].deviceCalibrationLabel,
        )
        assertEquals("identity-device", stats.reviewDeviceQueue[1].fingerprint)
        assertEquals("Identity carryover", stats.reviewDeviceQueue[1].reasonText)
        assertEquals(
            IdentityCarryoverVerdict.CONFIRMED_SAME_DEVICE,
            stats.reviewDeviceQueue[1].decisions[0].identityCarryoverVerdict,
        )
        assertEquals(
            IdentityCarryoverVerdict.FALSE_MATCH,
            stats.reviewDeviceQueue[1].decisions[1].identityCarryoverVerdict,
        )
        assertEquals(
            IdentityCarryoverVerdict.INCONCLUSIVE,
            stats.reviewDeviceQueue[1].decisions[2].identityCarryoverVerdict,
        )
        assertEquals("suspicious-device", stats.reviewDeviceQueue[2].fingerprint)
        assertEquals("Suspicious device", stats.reviewDeviceQueue[2].reasonText)
    }

    @Test
    fun `watchlist return review can pause future alerts`() {
        val stats =
            SessionStatsCalculator.calculate(
                startedAt = NOW,
                devices =
                    listOf(
                        device(lastSeenAt = NOW + 1_000L, evidence = emptyList()).copy(
                            fingerprint = "watchlist-device",
                            macAddress = "AA:BB:CC:11:22:03",
                            name = "Watchlisted headphones",
                            isInWatchlist = true,
                            isTrackingEnabled = true,
                        ),
                    ),
                samples = emptyList(),
                alertEvidenceEvents =
                    listOf(
                        alertEvent(
                            timestamp = NOW + 2_000L,
                            eventType = AlertEvidenceEventType.WATCHLIST_RETURN,
                            deviceFingerprint = "watchlist-device",
                        ),
                    ),
            )

        assertEquals(1, stats.reviewDeviceQueue.size)
        assertEquals("Watchlist return", stats.reviewDeviceQueue[0].reasonText)
        assertEquals("Pause alerts", stats.reviewDeviceQueue[0].decisions[0].text)
        assertEquals(false, stats.reviewDeviceQueue[0].decisions[0].watchlistTrackingEnabled)
        assertEquals(DeviceCalibrationLabel.KNOWN_SAFE, stats.reviewDeviceQueue[0].decisions[1].deviceCalibrationLabel)
    }

    @Test
    fun `public safety review can mark benign repeated signals without confirming presence`() {
        val stats =
            SessionStatsCalculator.calculate(
                startedAt = NOW,
                devices =
                    listOf(
                        device(lastSeenAt = NOW + 1_000L, evidence = emptyList()).copy(
                            fingerprint = "public-safety-like-device",
                            macAddress = "AA:BB:CC:11:22:46",
                            name = "Axon-like signal",
                            deviceType = DeviceType.BODY_CAMERA,
                        ),
                    ),
                samples = emptyList(),
                alertEvidenceEvents =
                    listOf(
                        alertEvent(
                            timestamp = NOW + 2_000L,
                            eventType = AlertEvidenceEventType.PUBLIC_SAFETY_SIGNAL,
                            deviceFingerprint = "public-safety-like-device",
                        ),
                    ),
            )

        assertEquals(1, stats.reviewDeviceQueue.size)
        assertEquals("Public-safety-like signal", stats.reviewDeviceQueue[0].reasonText)
        assertEquals(SessionReviewQueueCopy.PUBLIC_SAFETY_ALERT, stats.reviewDeviceQueue[0].actionText)
        assertEquals(DeviceCalibrationLabel.KNOWN_SAFE, stats.reviewDeviceQueue[0].decisions[0].deviceCalibrationLabel)
        assertEquals(
            DeviceCalibrationLabel.FALSE_POSITIVE,
            stats.reviewDeviceQueue[0].decisions[1].deviceCalibrationLabel,
        )
    }

    @Test
    fun `user suppressed devices do not produce actionable review signals from stale long session data`() {
        val knownSafeDevice =
            device(
                lastSeenAt = NOW + 12 * 60 * 1_000L,
                evidence =
                    listOf(
                        evidence(
                            confidence = DetectionConfidence.HIGH,
                            source = EvidenceSource.FOLLOW_ME_SCORE,
                        ),
                        evidence(source = EvidenceSource.IDENTITY_CARRYOVER),
                    ),
            ).copy(
                fingerprint = "known-safe-device",
                macAddress = "AA:BB:CC:11:22:44",
                calibrationLabel = DeviceCalibrationLabel.KNOWN_SAFE,
                isIgnoredForTracking = true,
                followingScore = 96f,
                trackingStatus = TrackingStatus.SUSPICIOUS,
            )
        val falsePositiveDevice =
            device(
                lastSeenAt = NOW + 14 * 60 * 1_000L,
                evidence =
                    listOf(
                        evidence(
                            confidence = DetectionConfidence.HIGH,
                            source = EvidenceSource.RSSI_PATTERN,
                        ),
                    ),
            ).copy(
                fingerprint = "false-positive-device",
                macAddress = "AA:BB:CC:11:22:45",
                calibrationLabel = DeviceCalibrationLabel.FALSE_POSITIVE,
                isIgnoredForTracking = true,
                followingScore = 88f,
                trackingStatus = TrackingStatus.SUSPICIOUS,
            )

        val stats =
            SessionStatsCalculator.calculate(
                startedAt = NOW,
                devices = listOf(knownSafeDevice, falsePositiveDevice),
                samples =
                    strengtheningSamples("known-safe-device") +
                        strengtheningSamples("false-positive-device"),
                alertEvidenceEvents =
                    listOf(
                        alertEvent(
                            timestamp = NOW + 13 * 60 * 1_000L,
                            eventType = AlertEvidenceEventType.FOLLOW_ME_ALERT,
                            deviceFingerprint = knownSafeDevice.fingerprint,
                        ),
                    ),
            )

        assertEquals(2, stats.deviceCount)
        assertEquals(8, stats.sampleCount)
        assertEquals(0, stats.reviewCategoryCounts.suspicious)
        assertEquals(0, stats.reviewCategoryCounts.unknownNoise)
        assertEquals(0, stats.alertHistorySummary.followMeAlertCount)
        assertEquals(0, stats.identityCarryoverSummary.deviceCount)
        assertEquals(0, stats.rssiTrendSummary.strengtheningCount)
        assertTrue(stats.reviewDeviceQueue.isEmpty())
    }

    @Test
    fun `review category uses radar follow me suspicious boundary`() {
        val stats =
            SessionStatsCalculator.calculate(
                startedAt = NOW,
                devices =
                    listOf(
                        device(lastSeenAt = NOW + 1_000L, evidence = emptyList()).copy(
                            name = "AirPods",
                            vendorName = "Apple",
                            followingScore = 50f,
                        ),
                        device(lastSeenAt = NOW + 2_000L, evidence = emptyList()).copy(
                            name = "AirPods",
                            vendorName = "Apple",
                            followingScore = 51f,
                        ),
                    ),
                samples = emptyList(),
                alertEvidenceEvents = emptyList(),
            )

        assertEquals(2, stats.deviceCount)
        assertEquals(1, stats.reviewCategoryCounts.suspicious)
        assertEquals(1, stats.reviewCategoryCounts.nearby)
    }

    private fun device(
        lastSeenAt: Long,
        evidence: List<DetectionEvidence>,
    ): Device =
        Device(
            fingerprint = "AA:BB:CC:11:22:33",
            macAddress = "AA:BB:CC:11:22:33",
            macAddressType = MacAddressType.PUBLIC,
            technology = "BLE",
            name = "Device",
            deviceType = DeviceType.UNKNOWN,
            vendorName = null,
            predictedModel = null,
            trackingStatus = TrackingStatus.SAFE,
            followingScore = 0f,
            isSafeBeacon = false,
            isInWatchlist = false,
            userAlias = null,
            userNotes = null,
            alertSound = true,
            alertVibration = true,
            firstSeenAt = lastSeenAt - 60_000L,
            lastSeenAt = lastSeenAt,
            rssi = -60,
            encounterCount = 1,
            calibrationLabel = DeviceCalibrationLabel.UNKNOWN,
            evidence = evidence,
        )

    private fun evidence(
        confidence: DetectionConfidence = DetectionConfidence.MEDIUM,
        source: EvidenceSource = EvidenceSource.RSSI_PATTERN,
    ): DetectionEvidence =
        DetectionEvidence(
            source = source,
            confidence = confidence,
            reasonText = "RSSI pattern needs review.",
            timestamp = NOW,
            rawValue = null,
            parsedValue = null,
            isPassive = true,
        )

    private fun alertEvent(
        timestamp: Long,
        eventType: AlertEvidenceEventType = AlertEvidenceEventType.FOLLOW_ME_ALERT,
        deviceFingerprint: String = "AA:BB:CC:11:22:33",
    ): AlertEvidenceEvent =
        AlertEvidenceEvent(
            timestamp = timestamp,
            deviceFingerprint = deviceFingerprint,
            observedMac = deviceFingerprint,
            eventType = eventType,
            evidence = evidence(),
        )

    private fun sample(
        timestamp: Long,
        rssi: Int = -57,
        deviceFingerprint: String = "AA:BB:CC:11:22:33",
        latitude: Double? = null,
        longitude: Double? = null,
    ): SignalSample =
        SignalSample(
            deviceFingerprint = deviceFingerprint,
            rssi = rssi,
            latitude = latitude,
            longitude = longitude,
            locationAccuracy = null,
            timestamp = timestamp,
        )

    private fun strengtheningSamples(deviceFingerprint: String): List<SignalSample> =
        listOf(
            sample(
                timestamp = NOW + 1_000L,
                rssi = -76,
                deviceFingerprint = deviceFingerprint,
            ),
            sample(
                timestamp = NOW + 2_000L,
                rssi = -70,
                deviceFingerprint = deviceFingerprint,
            ),
            sample(
                timestamp = NOW + 3_000L,
                rssi = -63,
                deviceFingerprint = deviceFingerprint,
            ),
            sample(
                timestamp = NOW + 4_000L,
                rssi = -55,
                deviceFingerprint = deviceFingerprint,
            ),
        )

    private companion object {
        private const val NOW = 1_789_000_000_000L
    }
}
