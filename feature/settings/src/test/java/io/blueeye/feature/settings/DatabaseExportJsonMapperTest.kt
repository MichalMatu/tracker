package io.blueeye.feature.settings

import io.blueeye.core.model.AlertEvidenceEvent
import io.blueeye.core.model.AlertEvidenceEventType
import io.blueeye.core.model.DetectionConfidence
import io.blueeye.core.model.DetectionEvidence
import io.blueeye.core.model.Device
import io.blueeye.core.model.DeviceCalibrationLabel
import io.blueeye.core.model.DeviceType
import io.blueeye.core.model.EvidenceProvenance
import io.blueeye.core.model.EvidenceSource
import io.blueeye.core.model.FollowMeHistorySample
import io.blueeye.core.model.IdentityCarryoverVerdict
import io.blueeye.core.model.MacAddressType
import io.blueeye.core.model.SignalSample
import io.blueeye.core.model.TrackingStatus
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.boolean
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.double
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.long
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DatabaseExportJsonMapperTest {
    @Test
    fun `export includes session summary counters`() {
        val fixture = structuredExportFixture()
        val export = fixture.export

        val session = export.getValue("session").jsonObject
        val calibrationCounts = session.getValue("calibrationCounts").jsonObject
        val reviewCategoryCounts = session.getValue("reviewCategoryCounts").jsonObject
        val reviewReadiness = session.getValue("reviewReadiness").jsonObject
        val reviewDeviceQueue = session.getValue("reviewDeviceQueue").jsonArray.single().jsonObject

        assertEquals(19, export.getValue("schemaVersion").jsonPrimitive.int)
        assertEquals(EXPORT_DATE, export.getValue("exportDate").jsonPrimitive.long)
        assertEquals(2, export.getValue("deviceCount").jsonPrimitive.int)
        assertEquals(2, export.getValue("sampleCount").jsonPrimitive.int)
        assertEquals(1, export.getValue("samplesWithGps").jsonPrimitive.int)

        assertEquals(DeviceCalibrationLabel.SUSPICIOUS.name, session.getValue("label").jsonPrimitive.content)
        assertEquals(SESSION_NOTES, session.getValue("notes").jsonPrimitive.content)
        assertEquals(true, session.getValue("activeCollectionEnabled").jsonPrimitive.boolean)
        assertEquals(21_000L, session.getValue("durationMs").jsonPrimitive.long)
        assertEquals(1, session.getValue("deviceCount").jsonPrimitive.int)
        assertEquals(1, session.getValue("sampleCount").jsonPrimitive.int)
        assertEquals(1, session.getValue("followMeObservationCount").jsonPrimitive.int)
        assertEquals(1, session.getValue("alertEvidenceEventCount").jsonPrimitive.int)
        assertActiveProbeCounts(session, expectedDeviceCount = 1, expectedStatus = "NONE")
        assertEquals(
            1,
            session.getValue("alertEvidenceEventTypeCounts").jsonObject
                .getValue(AlertEvidenceEventType.FOLLOW_ME_ALERT.name)
                .jsonPrimitive
                .int,
        )
        assertEquals(
            0,
            session.getValue("alertEvidenceEventTypeCounts").jsonObject
                .getValue(AlertEvidenceEventType.WATCHLIST_RETURN.name)
                .jsonPrimitive
                .int,
        )
        assertEquals(
            listOf("TOO_FEW_SAMPLES"),
            session.getValue("rssiQuality").jsonObject.stringArray("warnings"),
        )
        assertEquals(1, session.getValue("evidenceCount").jsonPrimitive.int)
        assertEquals(1, session.getValue("attentionEvidenceCount").jsonPrimitive.int)
        assertEquals(1, calibrationCounts.getValue(DeviceCalibrationLabel.SUSPICIOUS.name).jsonPrimitive.int)
        assertEquals(0, calibrationCounts.getValue(DeviceCalibrationLabel.KNOWN_SAFE.name).jsonPrimitive.int)
        assertIdentityCarryoverVerdictCounts(session, IdentityCarryoverVerdict.UNREVIEWED)
        assertEquals(1, reviewCategoryCounts.getValue("SUSPICIOUS").jsonPrimitive.int)
        assertEquals(0, reviewCategoryCounts.getValue("WATCHLIST").jsonPrimitive.int)
        assertEquals(0, reviewCategoryCounts.getValue("PUBLIC_SAFETY").jsonPrimitive.int)
        assertEquals(0, reviewCategoryCounts.getValue("NEARBY").jsonPrimitive.int)
        assertEquals(0, reviewCategoryCounts.getValue("UNKNOWN_NOISE").jsonPrimitive.int)
        assertTrue(reviewReadiness.getValue("readyForHeuristicReview").jsonPrimitive.boolean)
        assertTrue(reviewReadiness.getValue("hasUserVerdict").jsonPrimitive.boolean)
        assertTrue(reviewReadiness.getValue("hasNotes").jsonPrimitive.boolean)
        assertTrue(reviewReadiness.getValue("hasDevices").jsonPrimitive.boolean)
        assertTrue(reviewReadiness.getValue("hasSamples").jsonPrimitive.boolean)
        assertTrue(reviewReadiness.getValue("hasAttentionEvidence").jsonPrimitive.boolean)
        assertTrue(reviewReadiness.getValue("hasActiveProbeData").jsonPrimitive.boolean)
        assertEquals(emptyList<String>(), reviewReadiness.stringArray("blockers"))
        assertEquals(emptyList<String>(), reviewReadiness.stringArray("warnings"))
        assertEquals(
            fixture.sessionDevice.fingerprint,
            session.getValue("deviceFingerprints").jsonArray.single().jsonPrimitive.content,
        )
        assertEquals(
            fixture.sessionDevice.fingerprint,
            reviewDeviceQueue.getValue("fingerprint").jsonPrimitive.content,
        )
        assertEquals("Device session-device", reviewDeviceQueue.getValue("displayName").jsonPrimitive.content)
        assertEquals("Follow-Me alert", reviewDeviceQueue.getValue("reasonText").jsonPrimitive.content)
        assertEquals(
            SessionReviewQueueCopy.FOLLOW_ME_ALERT,
            reviewDeviceQueue.getValue("actionText").jsonPrimitive.content,
        )
        val reviewDeviceDecisions = reviewDeviceQueue.getValue("decisions").jsonArray
        assertEquals(
            "DEVICE_CALIBRATION",
            reviewDeviceDecisions[0].jsonObject.getValue("kind").jsonPrimitive.content,
        )
        assertEquals("Mark suspicious", reviewDeviceDecisions[0].jsonObject.getValue("text").jsonPrimitive.content)
        assertEquals(
            DeviceCalibrationLabel.SUSPICIOUS.name,
            reviewDeviceDecisions[0].jsonObject.getValue("calibrationLabel").jsonPrimitive.content,
        )
        assertEquals(
            null,
            reviewDeviceDecisions[0].jsonObject.getValue("identityCarryoverVerdict").jsonPrimitive.contentOrNull,
        )
        assertEquals(
            null,
            reviewDeviceDecisions[0].jsonObject.getValue("watchlistTrackingEnabled").jsonPrimitive.contentOrNull,
        )
        assertEquals("False positive", reviewDeviceDecisions[1].jsonObject.getValue("text").jsonPrimitive.content)
        assertEquals(
            DeviceCalibrationLabel.FALSE_POSITIVE.name,
            reviewDeviceDecisions[1].jsonObject.getValue("calibrationLabel").jsonPrimitive.content,
        )
    }

    @Test
    fun `export includes privacy notice for sensitive telemetry`() {
        val fixture = structuredExportFixture()
        val privacyNotice = fixture.export.getValue("privacyNotice").jsonObject

        assertTrue(privacyNotice.getValue("containsMacAddresses").jsonPrimitive.boolean)
        assertTrue(privacyNotice.getValue("containsGpsSamples").jsonPrimitive.boolean)
        assertTrue(privacyNotice.getValue("containsRawPayloads").jsonPrimitive.boolean)
        assertTrue(privacyNotice.getValue("containsActiveProbeData").jsonPrimitive.boolean)
        assertEquals(
            "Treat as sensitive local telemetry. Share only with trusted reviewers.",
            privacyNotice.getValue("handling").jsonPrimitive.content,
        )
    }

    @Test
    fun `export includes device summaries for session review`() {
        val fixture = structuredExportFixture()
        val session = fixture.export.getValue("session").jsonObject
        val sessionDeviceSummary = session.getValue("deviceSummaries").jsonArray.single().jsonObject
        val sessionSampleStats = sessionDeviceSummary.getValue("sampleStats").jsonObject
        val strongestSessionEvidence = sessionDeviceSummary.getValue("strongestEvidence").jsonObject
        val latestFollowMeObservation =
            sessionDeviceSummary.getValue("latestFollowMeObservation").jsonObject
        val latestAlertEvidenceEvent =
            sessionDeviceSummary.getValue("latestAlertEvidenceEvent").jsonObject

        assertEquals(
            fixture.sessionDevice.fingerprint,
            sessionDeviceSummary.getValue("fingerprint").jsonPrimitive.content,
        )
        assertEquals("Device session-device", sessionDeviceSummary.getValue("displayName").jsonPrimitive.content)
        assertEquals(
            DeviceCalibrationLabel.SUSPICIOUS.name,
            sessionDeviceSummary.getValue("calibrationLabel").jsonPrimitive.content,
        )
        assertEquals(
            IdentityCarryoverVerdict.UNREVIEWED.name,
            sessionDeviceSummary.getValue("identityCarryoverVerdict").jsonPrimitive.content,
        )
        assertEquals("SUSPICIOUS", sessionDeviceSummary.getValue("reviewCategory").jsonPrimitive.content)
        assertEquals(
            "Tracker-like, Follow-Me, or RSSI-pattern evidence needs review.",
            sessionDeviceSummary.getValue("reviewReason").jsonPrimitive.content,
        )
        assertEquals(
            "Compare movement history with your route, then mark Suspicious, False Positive, or Known Safe.",
            sessionDeviceSummary.getValue("reviewAction").jsonPrimitive.content,
        )
        assertEquals(1, sessionDeviceSummary.getValue("evidenceCount").jsonPrimitive.int)
        assertEquals(1, sessionDeviceSummary.getValue("attentionEvidenceCount").jsonPrimitive.int)
        assertEquals(
            EvidenceSource.USER_CONFIRMATION.name,
            sessionDeviceSummary.getValue("evidenceSources").jsonArray.single().jsonPrimitive.content,
        )
        assertEquals(
            EvidenceSource.USER_CONFIRMATION.name,
            strongestSessionEvidence.getValue("source").jsonPrimitive.content,
        )
        assertEquals(
            DetectionConfidence.MEDIUM.name,
            strongestSessionEvidence.getValue("confidence").jsonPrimitive.content,
        )
        assertEquals(1, sessionDeviceSummary.getValue("followMeObservationCount").jsonPrimitive.int)
        assertEquals(1, sessionDeviceSummary.getValue("alertEvidenceEventCount").jsonPrimitive.int)
        assertEquals(
            fixture.followMeObservation.observation.timestamp,
            latestFollowMeObservation.getValue("timestamp").jsonPrimitive.long,
        )
        assertEquals(
            TrackingStatus.SUSPICIOUS.name,
            latestFollowMeObservation.getValue("trackingStatus").jsonPrimitive.content,
        )
        assertEquals(72.0, latestFollowMeObservation.getValue("score").jsonPrimitive.double, 0.001)
        assertEquals(true, latestFollowMeObservation.getValue("userMoved").jsonPrimitive.boolean)
        assertEquals(false, latestFollowMeObservation.getValue("baselineDevice").jsonPrimitive.boolean)
        assertEquals(
            AlertEvidenceEventType.FOLLOW_ME_ALERT.name,
            latestAlertEvidenceEvent.getValue("eventType").jsonPrimitive.content,
        )
        assertEquals(
            EvidenceSource.FOLLOW_ME_SCORE.name,
            latestAlertEvidenceEvent.getValue("evidence").jsonObject
                .getValue("source")
                .jsonPrimitive
                .content,
        )
        assertEquals(1, sessionSampleStats.getValue("sampleCount").jsonPrimitive.int)
        assertEquals(
            fixture.sessionSample.timestamp,
            sessionSampleStats.getValue("firstSampleAt").jsonPrimitive.long,
        )
        assertEquals(
            fixture.sessionSample.timestamp,
            sessionSampleStats.getValue("lastSampleAt").jsonPrimitive.long,
        )
        assertEquals(-57, sessionSampleStats.getValue("minRssi").jsonPrimitive.int)
        assertEquals(-57, sessionSampleStats.getValue("maxRssi").jsonPrimitive.int)
        assertEquals(-57.0, sessionSampleStats.getValue("averageRssi").jsonPrimitive.double, 0.001)
        val rssiQuality = sessionSampleStats.getValue("rssiQuality").jsonObject
        assertEquals(1, rssiQuality.getValue("uniqueRssiCount").jsonPrimitive.int)
        assertEquals(-57, rssiQuality.getValue("dominantRssi").jsonPrimitive.int)
        assertEquals(1, rssiQuality.getValue("dominantRssiCount").jsonPrimitive.int)
        assertEquals(1.0, rssiQuality.getValue("dominantRssiShare").jsonPrimitive.double, 0.001)
        assertEquals(listOf("TOO_FEW_SAMPLES"), rssiQuality.stringArray("warnings"))
        val rssiTrend = sessionSampleStats.getValue("rssiTrend").jsonObject
        assertEquals(RssiTrendDirection.INSUFFICIENT.name, rssiTrend.getValue("direction").jsonPrimitive.content)
        assertEquals(null, rssiTrend.getValue("deltaRssi").jsonPrimitive.contentOrNull)
    }

    @Test
    fun `export flags flat rssi quality for review`() {
        val device =
            device(
                fingerprint = "flat-rssi",
                lastSeenAt = SESSION_STARTED_AT + 60_000L,
                calibrationLabel = DeviceCalibrationLabel.SUSPICIOUS,
            )
        val samples =
            List(6) { index ->
                sample(
                    deviceFingerprint = device.fingerprint,
                    timestamp = SESSION_STARTED_AT + index * 1_000L,
                    rssi = -50,
                )
            }
        val export =
            DatabaseExportJsonMapper.buildExport(
                DatabaseExportData(
                    devices = listOf(device),
                    samples = samples,
                    session =
                        DatabaseExportSessionData(
                            devices = listOf(device),
                            samples = samples,
                            label = DeviceCalibrationLabel.SUSPICIOUS,
                            startedAt = SESSION_STARTED_AT,
                            notes = SESSION_NOTES,
                            activeCollectionEnabled = false,
                            followMeObservations = emptyList(),
                            alertEvidenceEvents = emptyList(),
                        ),
                    exportDate = EXPORT_DATE,
                ),
            )
        val session = export.getValue("session").jsonObject
        val sessionRssiQuality = session.getValue("rssiQuality").jsonObject
        val deviceRssiQuality =
            session.getValue("deviceSummaries").jsonArray.single().jsonObject
                .getValue("sampleStats").jsonObject
                .getValue("rssiQuality").jsonObject

        assertEquals(1, sessionRssiQuality.getValue("uniqueRssiCount").jsonPrimitive.int)
        assertEquals(-50, sessionRssiQuality.getValue("dominantRssi").jsonPrimitive.int)
        assertEquals(6, sessionRssiQuality.getValue("dominantRssiCount").jsonPrimitive.int)
        assertEquals(1.0, sessionRssiQuality.getValue("dominantRssiShare").jsonPrimitive.double, 0.001)
        assertEquals(listOf("FLAT_RSSI_PATTERN"), sessionRssiQuality.stringArray("warnings"))
        assertEquals(listOf("FLAT_RSSI_PATTERN"), deviceRssiQuality.stringArray("warnings"))
    }

    @Test
    fun `export includes structured evidence for devices`() {
        val fixture = structuredExportFixture()

        val exportedDevice = fixture.export.getValue("devices").jsonArray.first().jsonObject
        val exportedEvidence = exportedDevice.getValue("evidence").jsonArray.single().jsonObject

        assertFalse(exportedDevice.containsKey("evidenceSummary"))
        assertEquals(1, exportedDevice.getValue("evidenceCount").jsonPrimitive.int)
        assertEquals(
            EvidenceSource.USER_CONFIRMATION.name,
            exportedEvidence.getValue("source").jsonPrimitive.content,
        )
        assertEquals(
            DetectionConfidence.MEDIUM.name,
            exportedEvidence.getValue("confidence").jsonPrimitive.content,
        )
        assertEquals(
            "User marked this device as suspicious for future evidence review.",
            exportedEvidence.getValue("reasonText").jsonPrimitive.content,
        )
        assertEquals(
            DeviceCalibrationLabel.SUSPICIOUS.name,
            exportedEvidence.getValue("rawValue").jsonPrimitive.content,
        )
        assertEquals("Suspicious", exportedEvidence.getValue("parsedValue").jsonPrimitive.content)
        assertEquals(
            EvidenceProvenance.UNKNOWN.name,
            exportedEvidence.getValue("provenance").jsonPrimitive.content,
        )
    }

    @Test
    fun `export includes service uuid evidence provenance`() {
        val device =
            device(
                fingerprint = "classic-audio",
                lastSeenAt = SESSION_STARTED_AT + 10_000L,
                calibrationLabel = DeviceCalibrationLabel.UNKNOWN,
                evidence =
                    listOf(
                        evidence(
                            source = EvidenceSource.SERVICE_UUID,
                            confidence = DetectionConfidence.LOW,
                            spec =
                                EvidenceSpec(
                                    reason =
                                        "Bluetooth service UUIDs were observed via opportunistic " +
                                            "Classic SDP discovery.",
                                    rawValue = "0000110b-0000-1000-8000-00805f9b34fb",
                                    parsedValue = "1 service UUID observed",
                                    provenance = EvidenceProvenance.CLASSIC_SDP,
                                ),
                        ),
                    ),
            )
        val export =
            DatabaseExportJsonMapper.buildExport(
                DatabaseExportData(
                    devices = listOf(device),
                    samples = emptyList(),
                    session =
                        DatabaseExportSessionData(
                            devices = listOf(device),
                            samples = emptyList(),
                            label = DeviceCalibrationLabel.UNKNOWN,
                            startedAt = SESSION_STARTED_AT,
                            notes = "",
                            activeCollectionEnabled = false,
                            followMeObservations = emptyList(),
                            alertEvidenceEvents = emptyList(),
                        ),
                    exportDate = EXPORT_DATE,
                ),
            )

        val exportedEvidence =
            export.getValue("devices").jsonArray.single().jsonObject
                .getValue("evidence").jsonArray.single().jsonObject

        assertEquals(EvidenceSource.SERVICE_UUID.name, exportedEvidence.getValue("source").jsonPrimitive.content)
        assertEquals(
            EvidenceProvenance.CLASSIC_SDP.name,
            exportedEvidence.getValue("provenance").jsonPrimitive.content,
        )
    }

    @Test
    fun `export public safety review category remains evidence based`() {
        val publicSafetyDevice =
            device(
                fingerprint = "axon-like",
                lastSeenAt = SESSION_STARTED_AT + 15_000L,
                calibrationLabel = DeviceCalibrationLabel.UNKNOWN,
                evidence =
                    listOf(
                        evidence(
                            source = EvidenceSource.SERVICE_UUID,
                            confidence = DetectionConfidence.HIGH,
                            spec =
                                EvidenceSpec(
                                    reason = "Service UUID is consistent with Axon Body Camera.",
                                    rawValue = "0000fd8e-0000-1000-8000-00805f9b34fb",
                                    parsedValue = DeviceType.BODY_CAMERA.name,
                                ),
                        ),
                    ),
            )
        val export =
            DatabaseExportJsonMapper.buildExport(
                DatabaseExportData(
                    devices = listOf(publicSafetyDevice),
                    samples = emptyList(),
                    session =
                        DatabaseExportSessionData(
                            devices = listOf(publicSafetyDevice),
                            samples = emptyList(),
                            label = DeviceCalibrationLabel.UNKNOWN,
                            startedAt = SESSION_STARTED_AT,
                            notes = "",
                            activeCollectionEnabled = false,
                            followMeObservations = emptyList(),
                            alertEvidenceEvents = emptyList(),
                        ),
                    exportDate = EXPORT_DATE,
                ),
            )
        val session = export.getValue("session").jsonObject
        val reviewCategoryCounts = session.getValue("reviewCategoryCounts").jsonObject
        val deviceSummary = session.getValue("deviceSummaries").jsonArray.single().jsonObject

        assertEquals(1, reviewCategoryCounts.getValue("PUBLIC_SAFETY").jsonPrimitive.int)
        assertEquals("PUBLIC_SAFETY", deviceSummary.getValue("reviewCategory").jsonPrimitive.content)
        assertEquals(
            "Signal is consistent with public-safety or tactical gear; not a confirmed presence.",
            deviceSummary.getValue("reviewReason").jsonPrimitive.content,
        )
    }

    private fun structuredExportFixture(): StructuredExportFixture {
        val sessionDevice =
            device(
                fingerprint = "session-device",
                lastSeenAt = SESSION_STARTED_AT + 10_000L,
                calibrationLabel = DeviceCalibrationLabel.SUSPICIOUS,
                evidence =
                    listOf(
                        evidence(
                            source = EvidenceSource.USER_CONFIRMATION,
                            confidence = DetectionConfidence.MEDIUM,
                            spec =
                                EvidenceSpec(
                                    reason = "User marked this device as suspicious for future evidence review.",
                                    rawValue = DeviceCalibrationLabel.SUSPICIOUS.name,
                                    parsedValue = "Suspicious",
                                ),
                        ),
                    ),
            ).copy(
                serialNumber = "AXN12345",
                lastRawData = "0201061AFF",
            )
        val oldDevice =
            device(
                fingerprint = "old-device",
                lastSeenAt = SESSION_STARTED_AT - 10_000L,
                calibrationLabel = DeviceCalibrationLabel.KNOWN_SAFE,
            )
        val sessionSample =
            sample(
                deviceFingerprint = sessionDevice.fingerprint,
                timestamp = SESSION_STARTED_AT + 12_000L,
            )
        val oldSample =
            sample(
                deviceFingerprint = oldDevice.fingerprint,
                timestamp = SESSION_STARTED_AT - 12_000L,
                latitude = 52.2,
                longitude = 21.0,
            )
        val followMeObservation = followMeObservation(sessionDevice)
        val alertEvidenceEvent = followMeAlertEvent(sessionDevice)

        return StructuredExportFixture(
            export =
                DatabaseExportJsonMapper.buildExport(
                    DatabaseExportData(
                        devices = listOf(sessionDevice, oldDevice),
                        samples = listOf(sessionSample, oldSample),
                        session =
                            DatabaseExportSessionData(
                                devices = listOf(sessionDevice),
                                samples = listOf(sessionSample),
                                label = DeviceCalibrationLabel.SUSPICIOUS,
                                startedAt = SESSION_STARTED_AT,
                                notes = SESSION_NOTES,
                                activeCollectionEnabled = true,
                                followMeObservations = listOf(followMeObservation),
                                alertEvidenceEvents = listOf(alertEvidenceEvent),
                            ),
                        exportDate = EXPORT_DATE,
                    ),
                ),
            sessionDevice = sessionDevice,
            sessionSample = sessionSample,
            followMeObservation = followMeObservation,
            alertEvidenceEvent = alertEvidenceEvent,
        )
    }

    private fun device(
        fingerprint: String,
        lastSeenAt: Long,
        calibrationLabel: DeviceCalibrationLabel,
        evidence: List<DetectionEvidence> = emptyList(),
    ): Device =
        Device(
            fingerprint = fingerprint,
            macAddress = "AA:BB:CC:11:22:33",
            macAddressType = MacAddressType.PUBLIC,
            technology = "BLE",
            name = "Device $fingerprint",
            deviceType = DeviceType.UNKNOWN,
            vendorName = "Unknown Vendor",
            predictedModel = null,
            trackingStatus = TrackingStatus.SAFE,
            followingScore = 0f,
            isSafeBeacon = false,
            isInWatchlist = false,
            userAlias = null,
            userNotes = null,
            alertSound = false,
            alertVibration = false,
            firstSeenAt = lastSeenAt - 60_000L,
            lastSeenAt = lastSeenAt,
            rssi = -55,
            encounterCount = 1,
            calibrationLabel = calibrationLabel,
            evidence = evidence,
        )

    private fun followMeObservation(device: Device): SessionFollowMeObservation =
        SessionFollowMeObservation(
            deviceFingerprint = device.fingerprint,
            observation =
                FollowMeHistorySample(
                    timestamp = SESSION_STARTED_AT + 20_000L,
                    observedMac = device.macAddress,
                    trackingStatus = TrackingStatus.SUSPICIOUS,
                    score = 72f,
                    explanation = "Seen while moving with stable RSSI.",
                    rssi = -57,
                    encounterCount = 4,
                    durationScore = 20,
                    rssiStabilityScore = 15,
                    deviceTypeScore = 10,
                    macBehaviorScore = 12,
                    encounterScore = 15,
                    userMoved = true,
                    baselineDevice = false,
                ),
        )

    private fun followMeAlertEvent(device: Device): AlertEvidenceEvent =
        AlertEvidenceEvent(
            timestamp = SESSION_STARTED_AT + 21_000L,
            deviceFingerprint = device.fingerprint,
            observedMac = device.macAddress,
            eventType = AlertEvidenceEventType.FOLLOW_ME_ALERT,
            evidence =
                evidence(
                    source = EvidenceSource.FOLLOW_ME_SCORE,
                    confidence = DetectionConfidence.HIGH,
                    spec =
                        EvidenceSpec(
                            reason = "Follow-Me alert fired after movement correlation.",
                            rawValue = "72",
                            parsedValue = TrackingStatus.SUSPICIOUS.name,
                        ),
                ),
        )

    private fun evidence(
        source: EvidenceSource,
        confidence: DetectionConfidence,
        spec: EvidenceSpec,
    ): DetectionEvidence =
        DetectionEvidence(
            source = source,
            confidence = confidence,
            reasonText = spec.reason,
            timestamp = SESSION_STARTED_AT + 10_000L,
            rawValue = spec.rawValue,
            parsedValue = spec.parsedValue,
            isPassive = true,
            provenance = spec.provenance,
        )

    private fun sample(
        deviceFingerprint: String,
        timestamp: Long,
        latitude: Double? = null,
        longitude: Double? = null,
        rssi: Int = -57,
    ): SignalSample =
        SignalSample(
            deviceFingerprint = deviceFingerprint,
            rssi = rssi,
            latitude = latitude,
            longitude = longitude,
            locationAccuracy = null,
            timestamp = timestamp,
        )

    private companion object {
        private const val SESSION_STARTED_AT = 1_789_000_000_000L
        private const val EXPORT_DATE = SESSION_STARTED_AT + 120_000L
        private const val SESSION_NOTES = "Home baseline near known headphones."
    }
}

private data class EvidenceSpec(
    val reason: String,
    val rawValue: String?,
    val parsedValue: String?,
    val provenance: EvidenceProvenance = EvidenceProvenance.UNKNOWN,
)

private data class StructuredExportFixture(
    val export: JsonObject,
    val sessionDevice: Device,
    val sessionSample: SignalSample,
    val followMeObservation: SessionFollowMeObservation,
    val alertEvidenceEvent: AlertEvidenceEvent,
)

private fun assertIdentityCarryoverVerdictCounts(
    session: JsonObject,
    nonZeroVerdict: IdentityCarryoverVerdict,
) {
    val counts = session.getValue("identityCarryoverVerdictCounts").jsonObject
    IdentityCarryoverVerdict.entries.forEach { verdict ->
        val expectedCount = if (verdict == nonZeroVerdict) 1 else 0
        assertEquals(expectedCount, counts.getValue(verdict.name).jsonPrimitive.int)
    }
}

private fun assertActiveProbeCounts(
    session: JsonObject,
    expectedDeviceCount: Int,
    expectedStatus: String,
) {
    assertEquals(expectedDeviceCount, session.getValue("activeProbeDataDeviceCount").jsonPrimitive.int)
    assertEquals(
        expectedDeviceCount,
        session.getValue("activeProbeStatusCounts").jsonObject
            .getValue(expectedStatus)
            .jsonPrimitive
            .int,
    )
}

private fun JsonObject.stringArray(key: String): List<String> =
    getValue(key).jsonArray.map { item ->
        item.jsonPrimitive.content
    }
