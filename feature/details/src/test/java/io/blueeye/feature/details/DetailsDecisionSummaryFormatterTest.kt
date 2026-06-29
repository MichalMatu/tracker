package io.blueeye.feature.details

import io.blueeye.core.model.DetectionConfidence
import io.blueeye.core.model.DetectionEvidence
import io.blueeye.core.model.Device
import io.blueeye.core.model.DeviceCalibrationLabel
import io.blueeye.core.model.DeviceType
import io.blueeye.core.model.EvidenceSource
import io.blueeye.core.model.IdentityCarryoverVerdict
import io.blueeye.core.model.MacAddressType
import io.blueeye.core.model.TrackingStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DetailsDecisionSummaryFormatterTest {
    @Test
    fun `watchlist summary explains alert state`() {
        val summary =
            DetailsDecisionSummaryFormatter.format(
                device(
                    DeviceSpec(
                        isInWatchlist = true,
                        isTrackingEnabled = true,
                    ),
                ),
            )

        assertEquals("Watchlist device", summary.headline)
        assertEquals("Return alerts are active for this device.", summary.detail)
        assertEquals("Keep watching, edit alerts, or remove from Watchlist if this is expected.", summary.actionText)
        assertEquals(DetailsDecisionTone.WARNING, summary.tone)
    }

    @Test
    fun `public safety summary stays evidence based and avoids confirmed presence claim`() {
        val summary =
            DetailsDecisionSummaryFormatter.format(
                device(
                    DeviceSpec(
                        deviceType = DeviceType.BODY_CAMERA,
                        evidence =
                            listOf(
                                evidence(
                                    source = EvidenceSource.SERVICE_UUID,
                                    confidence = DetectionConfidence.HIGH,
                                    reasonText = "Service UUID is consistent with Axon Body Camera.",
                                    parsedValue = "BODY_CAMERA",
                                ),
                            ),
                    ),
                ),
            )

        assertEquals("Public-safety-like signal", summary.headline)
        assertEquals(DetailsDecisionTone.WARNING, summary.tone)
        assertContains(summary.detail, "Evidence is consistent with body camera")
        assertContains(summary.detail, "not a confirmed presence")
        assertEquals("Review evidence only; do not treat this as confirmed presence.", summary.actionText)
        assertNoOverclaim(summary)
    }

    @Test
    fun `public safety device type without evidence shows no attention summary`() {
        val summary =
            DetailsDecisionSummaryFormatter.format(
                device(
                    DeviceSpec(
                        deviceType = DeviceType.BODY_CAMERA,
                    ),
                ),
            )

        assertEquals("No attention evidence", summary.headline)
        assertEquals(DetailsDecisionTone.SAFE, summary.tone)
    }

    @Test
    fun `follow me summary reports score without dangerous claim`() {
        val summary =
            DetailsDecisionSummaryFormatter.format(
                device(
                    DeviceSpec(
                        trackingStatus = TrackingStatus.DANGEROUS,
                        followingScore = 82f,
                    ),
                ),
            )

        assertEquals("Review movement evidence", summary.headline)
        assertContains(summary.detail, "82/100")
        assertContains(summary.detail, "Review movement history before acting")
        assertContains(summary.actionText, "Compare the timeline")
        assertFalse("${summary.headline} ${summary.detail}".lowercase().contains("dangerous"))
        assertEquals(DetailsDecisionTone.SUSPICIOUS, summary.tone)
    }

    @Test
    fun `safe score below suspicious boundary does not trigger follow me review`() {
        val summary =
            DetailsDecisionSummaryFormatter.format(
                device(
                    DeviceSpec(
                        trackingStatus = TrackingStatus.SAFE,
                        followingScore = 50f,
                    ),
                ),
            )

        assertEquals("No attention evidence", summary.headline)
        assertEquals(DetailsDecisionTone.SAFE, summary.tone)
    }

    @Test
    fun `safe score at suspicious boundary triggers follow me review`() {
        val summary =
            DetailsDecisionSummaryFormatter.format(
                device(
                    DeviceSpec(
                        trackingStatus = TrackingStatus.SAFE,
                        followingScore = 51f,
                    ),
                ),
            )

        assertEquals("Review movement evidence", summary.headline)
        assertContains(summary.detail, "51/100")
        assertEquals(DetailsDecisionTone.SUSPICIOUS, summary.tone)
    }

    @Test
    fun `known safe calibration suppresses attention summary`() {
        val summary =
            DetailsDecisionSummaryFormatter.format(
                device(
                    DeviceSpec(
                        calibrationLabel = DeviceCalibrationLabel.KNOWN_SAFE,
                        trackingStatus = TrackingStatus.DANGEROUS,
                        followingScore = 90f,
                    ),
                ),
            )

        assertEquals("Marked known safe", summary.headline)
        assertContains(summary.detail, "suppresses alerts and Follow-Me scoring")
        assertEquals("Change the verdict if this device starts behaving unexpectedly.", summary.actionText)
        assertEquals(DetailsDecisionTone.SAFE, summary.tone)
    }

    @Test
    fun `false identity carryover verdict warns against merged history`() {
        val summary =
            DetailsDecisionSummaryFormatter.format(
                device(
                    DeviceSpec(
                        identityCarryoverVerdict = IdentityCarryoverVerdict.FALSE_MATCH,
                        evidence =
                            listOf(
                                evidence(
                                    source = EvidenceSource.IDENTITY_CARRYOVER,
                                    confidence = DetectionConfidence.LOW,
                                    reasonText = "Rotating address was correlated with this device.",
                                    parsedValue = "Identity continuity",
                                ),
                            ),
                    ),
                ),
            )

        assertEquals("Identity carryover marked false", summary.headline)
        assertContains(summary.actionText, "Do not trust merged history")
        assertEquals(DetailsDecisionTone.WARNING, summary.tone)
    }

    @Test
    fun `confirmed identity carryover verdict is shown as reviewed context`() {
        val summary =
            DetailsDecisionSummaryFormatter.format(
                device(
                    DeviceSpec(
                        identityCarryoverVerdict = IdentityCarryoverVerdict.CONFIRMED_SAME_DEVICE,
                        evidence =
                            listOf(
                                evidence(
                                    source = EvidenceSource.IDENTITY_CARRYOVER,
                                    confidence = DetectionConfidence.LOW,
                                    reasonText = "Rotating address was correlated with this device.",
                                    parsedValue = "Identity continuity",
                                ),
                            ),
                    ),
                ),
            )

        assertEquals("Identity carryover confirmed", summary.headline)
        assertContains(summary.detail, "same device")
        assertEquals(DetailsDecisionTone.SAFE, summary.tone)
    }

    @Test
    fun `ordinary low confidence device shows no attention evidence`() {
        val summary =
            DetailsDecisionSummaryFormatter.format(
                device(
                    DeviceSpec(
                        evidence =
                            listOf(
                                evidence(
                                    source = EvidenceSource.NAME,
                                    confidence = DetectionConfidence.LOW,
                                    reasonText = "Device advertised a Bluetooth name.",
                                    parsedValue = "Keyboard",
                                ),
                            ),
                    ),
                ),
            )

        assertEquals("No attention evidence", summary.headline)
        assertEquals("This device has no medium-or-higher confidence evidence right now.", summary.detail)
        assertEquals("No action needed unless you recognize it and want return alerts.", summary.actionText)
        assertEquals(DetailsDecisionTone.SAFE, summary.tone)
    }

    @Test
    fun `tracker summary recommends calibration after history review`() {
        val summary =
            DetailsDecisionSummaryFormatter.format(
                device(
                    DeviceSpec(
                        deviceType = DeviceType.TRACKER,
                        followingScore = 64f,
                        evidence =
                            listOf(
                                evidence(
                                    source = EvidenceSource.NAME,
                                    confidence = DetectionConfidence.MEDIUM,
                                    reasonText =
                                        "Signal is consistent with a Bluetooth tracker accessory; " +
                                            "review movement history before acting.",
                                    parsedValue = DeviceType.TRACKER.name,
                                ),
                            ),
                    ),
                ),
            )

        assertEquals("Tracker-like evidence", summary.headline)
        assertContains(summary.actionText, "Check signal history")
        assertContains(summary.actionText, "False Positive")
        assertContains(summary.actionText, "Known Safe")
    }

    @Test
    fun `tracker device type without evidence shows no attention summary`() {
        val summary =
            DetailsDecisionSummaryFormatter.format(
                device(
                    DeviceSpec(
                        deviceType = DeviceType.TRACKER,
                    ),
                ),
            )

        assertEquals("No attention evidence", summary.headline)
        assertEquals(DetailsDecisionTone.SAFE, summary.tone)
    }

    private fun assertContains(
        text: String,
        expected: String,
    ) {
        assertTrue("Expected <$text> to contain <$expected>", text.contains(expected))
    }

    private fun assertNoOverclaim(summary: DetailsDecisionSummary) {
        val text = "${summary.headline} ${summary.detail} ${summary.actionText}".lowercase()
        assertFalse(text.contains("law enforcement nearby"))
        assertFalse(text.contains("police nearby"))
        assertFalse(text.contains("confirmed law enforcement"))
    }

    private fun evidence(
        source: EvidenceSource,
        confidence: DetectionConfidence,
        reasonText: String,
        parsedValue: String?,
    ): DetectionEvidence =
        DetectionEvidence(
            source = source,
            confidence = confidence,
            reasonText = reasonText,
            timestamp = NOW,
            rawValue = null,
            parsedValue = parsedValue,
            isPassive = true,
        )

    private data class DeviceSpec(
        val deviceType: DeviceType = DeviceType.UNKNOWN,
        val trackingStatus: TrackingStatus = TrackingStatus.SAFE,
        val followingScore: Float = 0f,
        val isInWatchlist: Boolean = false,
        val isTrackingEnabled: Boolean = false,
        val calibrationLabel: DeviceCalibrationLabel = DeviceCalibrationLabel.UNKNOWN,
        val identityCarryoverVerdict: IdentityCarryoverVerdict = IdentityCarryoverVerdict.UNREVIEWED,
        val evidence: List<DetectionEvidence> = emptyList(),
    )

    private fun device(spec: DeviceSpec = DeviceSpec()): Device =
        Device(
            fingerprint = "AA:BB:CC:11:22:33",
            macAddress = "AA:BB:CC:11:22:33",
            macAddressType = MacAddressType.PUBLIC,
            technology = "BLE",
            name = "Device",
            deviceType = spec.deviceType,
            vendorName = "Unknown Vendor",
            predictedModel = null,
            trackingStatus = spec.trackingStatus,
            followingScore = spec.followingScore,
            isSafeBeacon = false,
            isInWatchlist = spec.isInWatchlist,
            userAlias = null,
            userNotes = null,
            alertSound = false,
            alertVibration = false,
            isTrackingEnabled = spec.isTrackingEnabled,
            calibrationLabel = spec.calibrationLabel,
            identityCarryoverVerdict = spec.identityCarryoverVerdict,
            firstSeenAt = NOW,
            lastSeenAt = NOW,
            rssi = -55,
            encounterCount = 1,
            evidence = spec.evidence,
        )

    private companion object {
        private const val NOW = 1_789_000_000_000L
    }
}
