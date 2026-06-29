package io.blueeye.feature.radar.presentation

import io.blueeye.core.model.DetectionConfidence
import io.blueeye.core.model.DetectionEvidence
import io.blueeye.core.model.Device
import io.blueeye.core.model.DeviceCalibrationLabel
import io.blueeye.core.model.DeviceType
import io.blueeye.core.model.EvidenceSource
import io.blueeye.core.model.MacAddressType
import io.blueeye.core.model.TrackingStatus
import org.junit.Assert.assertEquals
import org.junit.Test

class RadarUiSectionMapperTest {
    @Test
    fun `groups devices into decision sections with stable priority`() {
        val watchlist = item(device(DeviceSpec(fingerprint = "watch", isInWatchlist = true)))
        val suspicious =
            item(
                device(
                    DeviceSpec(
                        fingerprint = "follow",
                        trackingStatus = TrackingStatus.SUSPICIOUS,
                        followingScore = 75f,
                        evidence =
                            listOf(
                                evidence(
                                    source = EvidenceSource.RSSI_PATTERN,
                                    confidence = DetectionConfidence.MEDIUM,
                                    parsedValue = "SUSPICIOUS",
                                ),
                            ),
                    ),
                ),
            )
        val publicSafety =
            item(
                device(
                    DeviceSpec(
                        fingerprint = "axon",
                        deviceType = DeviceType.BODY_CAMERA,
                        name = "Axon Body 3",
                        evidence =
                            listOf(
                                evidence(
                                    source = EvidenceSource.NAME,
                                    confidence = DetectionConfidence.MEDIUM,
                                    reason = "Device name is consistent with Axon Body Camera.",
                                    parsedValue = "BODY_CAMERA",
                                ),
                            ),
                    ),
                ),
            )
        val nearby =
            item(
                device(
                    DeviceSpec(
                        fingerprint = "airpods",
                        deviceType = DeviceType.HEADPHONES,
                        name = "AirPods",
                        vendorName = "Apple",
                    ),
                ),
            )
        val noise =
            item(
                device(
                    DeviceSpec(
                        fingerprint = "noise",
                        name = null,
                        vendorName = "Unknown Vendor",
                        rssi = -92,
                    ),
                ),
            )

        val sections = RadarUiSectionMapper.map(listOf(noise, nearby, publicSafety, suspicious, watchlist))

        assertEquals(
            listOf(
                RadarUiSectionType.WATCHLIST,
                RadarUiSectionType.SUSPICIOUS,
                RadarUiSectionType.PUBLIC_SAFETY,
                RadarUiSectionType.NEARBY,
                RadarUiSectionType.UNKNOWN_NOISE,
            ),
            sections.map { it.type },
        )
        assertEquals(listOf("watch"), sections[0].items.map { it.fingerprint })
        assertEquals(listOf("follow"), sections[1].items.map { it.fingerprint })
        assertEquals(listOf("axon"), sections[2].items.map { it.fingerprint })
        assertEquals(listOf("airpods"), sections[3].items.map { it.fingerprint })
        assertEquals(listOf("noise"), sections[4].items.map { it.fingerprint })
    }

    @Test
    fun `watchlist takes priority over public safety evidence`() {
        val item =
            item(
                device(
                    DeviceSpec(
                        fingerprint = "watch-axon",
                        isInWatchlist = true,
                        deviceType = DeviceType.BODY_CAMERA,
                        evidence =
                            listOf(
                                evidence(
                                    source = EvidenceSource.SERVICE_UUID,
                                    confidence = DetectionConfidence.HIGH,
                                    parsedValue = "BODY_CAMERA",
                                ),
                            ),
                    ),
                ),
            )

        val section = RadarUiSectionMapper.map(listOf(item)).single()

        assertEquals(RadarUiSectionType.WATCHLIST, section.type)
    }

    @Test
    fun `watchlist takes priority over user suppressed calibration`() {
        val item =
            item(
                device(
                    DeviceSpec(
                        fingerprint = "watch-safe",
                        isInWatchlist = true,
                        calibrationLabel = DeviceCalibrationLabel.KNOWN_SAFE,
                    ),
                ),
            )

        val section = RadarUiSectionMapper.map(listOf(item)).single()

        assertEquals(RadarUiSectionType.WATCHLIST, section.type)
    }

    @Test
    fun `generic motorola phone is nearby not public safety`() {
        val item =
            item(
                device(
                    DeviceSpec(
                        fingerprint = "phone",
                        deviceType = DeviceType.PHONE,
                        name = "moto g",
                        vendorName = "Motorola Mobility",
                        evidence =
                            listOf(
                                evidence(
                                    source = EvidenceSource.NAME,
                                    confidence = DetectionConfidence.LOW,
                                    reason = "Device advertised a Bluetooth name.",
                                    parsedValue = "moto g",
                                ),
                            ),
                    ),
                ),
            )

        val section = RadarUiSectionMapper.map(listOf(item)).single()

        assertEquals(RadarUiSectionType.NEARBY, section.type)
    }

    @Test
    fun `public safety device type without evidence remains nearby`() {
        val item =
            item(
                device(
                    DeviceSpec(
                        fingerprint = "body-camera-label-only",
                        deviceType = DeviceType.BODY_CAMERA,
                        name = "Camera",
                        vendorName = "Known Vendor",
                    ),
                ),
            )

        val section = RadarUiSectionMapper.map(listOf(item)).single()

        assertEquals(RadarUiSectionType.NEARBY, section.type)
    }

    @Test
    fun `tracker device type without evidence remains nearby`() {
        val item =
            item(
                device(
                    DeviceSpec(
                        fingerprint = "tracker-label-only",
                        deviceType = DeviceType.TRACKER,
                        name = "AirTag",
                        vendorName = "Apple",
                    ),
                ),
            )

        val section = RadarUiSectionMapper.map(listOf(item)).single()

        assertEquals(RadarUiSectionType.NEARBY, section.type)
    }

    @Test
    fun `known tracker evidence is routed to suspicious review section`() {
        val item =
            item(
                device(
                    DeviceSpec(
                        fingerprint = "airtag",
                        deviceType = DeviceType.TRACKER,
                        name = "AirTag",
                        evidence =
                            listOf(
                                evidence(
                                    source = EvidenceSource.NAME,
                                    confidence = DetectionConfidence.MEDIUM,
                                    reason =
                                        "Signal is consistent with a Bluetooth tracker accessory; " +
                                            "review movement history before acting.",
                                    parsedValue = DeviceType.TRACKER.name,
                                ),
                            ),
                    ),
                ),
            )

        val section = RadarUiSectionMapper.map(listOf(item)).single()

        assertEquals(RadarUiSectionType.SUSPICIOUS, section.type)
    }

    @Test
    fun `generic placeholder identity is routed to unknown noise`() {
        val item =
            item(
                device(
                    DeviceSpec(
                        fingerprint = "placeholder",
                        name = "Unknown Device",
                        vendorName = "Unknown Vendor",
                        predictedModel = "N/A",
                    ),
                ),
            )

        val section = RadarUiSectionMapper.map(listOf(item)).single()

        assertEquals(RadarUiSectionType.UNKNOWN_NOISE, section.type)
    }

    @Test
    fun `tracker evidence is routed to suspicious review section when device type is misleading`() {
        val item =
            item(
                device(
                    DeviceSpec(
                        fingerprint = "findmy",
                        deviceType = DeviceType.WEARABLE,
                        name = "Apple Watch",
                        evidence =
                            listOf(
                                evidence(
                                    source = EvidenceSource.RAW_PAYLOAD,
                                    confidence = DetectionConfidence.MEDIUM,
                                    reason =
                                        "Signal is consistent with a Bluetooth tracker accessory; " +
                                            "review movement history before acting.",
                                    parsedValue = DeviceType.AIRTAG.name,
                                ),
                            ),
                    ),
                ),
            )

        val section = RadarUiSectionMapper.map(listOf(item)).single()

        assertEquals(RadarUiSectionType.SUSPICIOUS, section.type)
    }

    @Test
    fun `user marked suspicious calibration is routed to suspicious review section`() {
        val item =
            item(
                device(
                    DeviceSpec(
                        fingerprint = "manual-suspicious",
                        name = null,
                        vendorName = "Unknown Vendor",
                        calibrationLabel = DeviceCalibrationLabel.SUSPICIOUS,
                        evidence =
                            listOf(
                                evidence(
                                    source = EvidenceSource.USER_CONFIRMATION,
                                    confidence = DetectionConfidence.MEDIUM,
                                    reason = "User marked this device as suspicious for future evidence review.",
                                    parsedValue = "Suspicious",
                                ),
                            ),
                    ),
                ),
            )

        val section = RadarUiSectionMapper.map(listOf(item)).single()

        assertEquals(RadarUiSectionType.SUSPICIOUS, section.type)
    }

    @Test
    fun `safe follow-me score at backend boundary remains nearby`() {
        val item =
            item(
                device(
                    DeviceSpec(
                        fingerprint = "score-boundary",
                        deviceType = DeviceType.HEADPHONES,
                        name = "Known headphones",
                        vendorName = "Known Vendor",
                        trackingStatus = TrackingStatus.SAFE,
                        followingScore = 50f,
                    ),
                ),
            )

        val section = RadarUiSectionMapper.map(listOf(item)).single()

        assertEquals(RadarUiSectionType.NEARBY, section.type)
    }

    @Test
    fun `follow-me score at suspicious backend threshold routes to suspicious review`() {
        val item =
            item(
                device(
                    DeviceSpec(
                        fingerprint = "score-suspicious",
                        deviceType = DeviceType.HEADPHONES,
                        name = "Nearby device",
                        vendorName = "Known Vendor",
                        trackingStatus = TrackingStatus.SAFE,
                        followingScore = 51f,
                    ),
                ),
            )

        val section = RadarUiSectionMapper.map(listOf(item)).single()

        assertEquals(RadarUiSectionType.SUSPICIOUS, section.type)
    }

    @Test
    fun `named samsung tv with low smarttag payload evidence remains nearby`() {
        val item =
            item(
                device(
                    DeviceSpec(
                        fingerprint = "tv",
                        deviceType = DeviceType.TV,
                        name = "[TV] Samsung Q60 Series (65)",
                        evidence =
                            listOf(
                                evidence(
                                    source = EvidenceSource.RAW_PAYLOAD,
                                    confidence = DetectionConfidence.LOW,
                                    reason = "Advertisement payload was decoded.",
                                    parsedValue = "Samsung SmartTag",
                                ),
                            ),
                    ),
                ),
            )

        val section = RadarUiSectionMapper.map(listOf(item)).single()

        assertEquals(RadarUiSectionType.NEARBY, section.type)
    }

    @Test
    fun `known safe calibration suppresses public safety evidence to noise`() {
        val item =
            item(
                device(
                    DeviceSpec(
                        fingerprint = "axon-safe",
                        deviceType = DeviceType.BODY_CAMERA,
                        calibrationLabel = DeviceCalibrationLabel.KNOWN_SAFE,
                        evidence =
                            listOf(
                                evidence(
                                    source = EvidenceSource.SERVICE_UUID,
                                    confidence = DetectionConfidence.HIGH,
                                    reason = "Service UUID is consistent with Axon Body Camera.",
                                    parsedValue = "BODY_CAMERA",
                                ),
                            ),
                    ),
                ),
            )

        val section = RadarUiSectionMapper.map(listOf(item)).single()

        assertEquals(RadarUiSectionType.UNKNOWN_NOISE, section.type)
    }

    @Test
    fun `false positive calibration suppresses tracker evidence to noise`() {
        val item =
            item(
                device(
                    DeviceSpec(
                        fingerprint = "tracker-false-positive",
                        deviceType = DeviceType.TRACKER,
                        calibrationLabel = DeviceCalibrationLabel.FALSE_POSITIVE,
                        evidence =
                            listOf(
                                evidence(
                                    source = EvidenceSource.RAW_PAYLOAD,
                                    confidence = DetectionConfidence.MEDIUM,
                                    reason =
                                        "Signal is consistent with a Bluetooth tracker accessory; " +
                                            "review movement history before acting.",
                                    parsedValue = DeviceType.TRACKER.name,
                                ),
                            ),
                    ),
                ),
            )

        val section = RadarUiSectionMapper.map(listOf(item)).single()

        assertEquals(RadarUiSectionType.UNKNOWN_NOISE, section.type)
    }

    @Test
    fun `public safety section description does not claim confirmed presence`() {
        assertEquals(
            "Signals consistent with public-safety or professional equipment, not a confirmed presence.",
            RadarUiSectionType.PUBLIC_SAFETY.description,
        )
    }

    @Test
    fun `section metadata gives each decision group a visible review tone`() {
        assertEquals("Watched", RadarUiSectionType.WATCHLIST.statusText)
        assertEquals(RadarUiColorToken.WARNING, RadarUiSectionType.WATCHLIST.tone)
        assertEquals("Review", RadarUiSectionType.SUSPICIOUS.statusText)
        assertEquals(RadarUiColorToken.SUSPICIOUS, RadarUiSectionType.SUSPICIOUS.tone)
        assertEquals("Evidence review", RadarUiSectionType.PUBLIC_SAFETY.statusText)
        assertEquals(RadarUiColorToken.WARNING, RadarUiSectionType.PUBLIC_SAFETY.tone)
        assertEquals("No attention", RadarUiSectionType.NEARBY.statusText)
        assertEquals(RadarUiColorToken.SAFE, RadarUiSectionType.NEARBY.tone)
        assertEquals("Low priority", RadarUiSectionType.UNKNOWN_NOISE.statusText)
        assertEquals(RadarUiColorToken.OUTLINE, RadarUiSectionType.UNKNOWN_NOISE.tone)
    }

    @Test
    fun `summary prioritizes suspicious movement over other attention sections`() {
        val watchlist = item(device(DeviceSpec(fingerprint = "watch", isInWatchlist = true)))
        val suspicious =
            item(
                device(
                    DeviceSpec(
                        fingerprint = "follow",
                        trackingStatus = TrackingStatus.SUSPICIOUS,
                        followingScore = 75f,
                    ),
                ),
            )
        val sections = RadarUiSectionMapper.map(listOf(watchlist, suspicious))

        val summary = RadarDecisionSummaryMapper.summarize(sections)

        assertEquals("Review tracker-like evidence", summary.headline)
        assertEquals("1 watchlist / 1 suspicious", summary.detail)
        assertEquals(RadarUiColorToken.SUSPICIOUS, summary.tone)
        assertEquals(2, summary.attentionCount)
    }

    @Test
    fun `summary treats watchlist visibility as warning not danger`() {
        val watchlist = item(device(DeviceSpec(fingerprint = "watch", isInWatchlist = true)))
        val sections = RadarUiSectionMapper.map(listOf(watchlist))

        val summary = RadarDecisionSummaryMapper.summarize(sections)

        assertEquals("Watchlist devices visible", summary.headline)
        assertEquals("1 watchlist", summary.detail)
        assertEquals(RadarUiColorToken.WARNING, summary.tone)
        assertEquals(1, summary.attentionCount)
    }

    @Test
    fun `summary treats public safety as evidence review not confirmed presence`() {
        val publicSafety =
            item(
                device(
                    DeviceSpec(
                        fingerprint = "axon",
                        deviceType = DeviceType.BODY_CAMERA,
                        name = "Axon Body 3",
                        evidence =
                            listOf(
                                evidence(
                                    source = EvidenceSource.NAME,
                                    confidence = DetectionConfidence.MEDIUM,
                                    reason = "Device name is consistent with Axon Body Camera.",
                                    parsedValue = "BODY_CAMERA",
                                ),
                            ),
                    ),
                ),
            )
        val sections = RadarUiSectionMapper.map(listOf(publicSafety))

        val summary = RadarDecisionSummaryMapper.summarize(sections)

        assertEquals("Public-safety-like signals need evidence review", summary.headline)
        assertEquals("1 public safety", summary.detail)
        assertEquals(RadarUiColorToken.WARNING, summary.tone)
        assertEquals(1, summary.attentionCount)
    }

    @Test
    fun `summary reports no attention signals for ordinary nearby devices`() {
        val nearby =
            item(
                device(
                    DeviceSpec(
                        fingerprint = "airpods",
                        deviceType = DeviceType.HEADPHONES,
                        name = "AirPods",
                        vendorName = "Apple",
                    ),
                ),
            )
        val sections = RadarUiSectionMapper.map(listOf(nearby))

        val summary = RadarDecisionSummaryMapper.summarize(sections)

        assertEquals("No attention signals", summary.headline)
        assertEquals("1 nearby", summary.detail)
        assertEquals(RadarUiColorToken.SAFE, summary.tone)
        assertEquals(0, summary.attentionCount)
    }

    private fun item(device: Device): RadarUiItem =
        RadarUiMapper.mapToUi(
            device = device,
            isNew = false,
            activeProbeMac = null,
        )

    private fun evidence(
        source: EvidenceSource,
        confidence: DetectionConfidence,
        reason: String = "Evidence reason",
        parsedValue: String? = null,
    ): DetectionEvidence =
        DetectionEvidence(
            source = source,
            confidence = confidence,
            reasonText = reason,
            timestamp = NOW,
            rawValue = null,
            parsedValue = parsedValue,
            isPassive = true,
        )

    private data class DeviceSpec(
        val fingerprint: String,
        val deviceType: DeviceType = DeviceType.UNKNOWN,
        val name: String? = "Device $fingerprint",
        val vendorName: String? = "Unknown Vendor",
        val trackingStatus: TrackingStatus = TrackingStatus.SAFE,
        val followingScore: Float = 0f,
        val isInWatchlist: Boolean = false,
        val rssi: Int = -55,
        val calibrationLabel: DeviceCalibrationLabel = DeviceCalibrationLabel.UNKNOWN,
        val predictedModel: String? = null,
        val evidence: List<DetectionEvidence> = emptyList(),
    )

    private fun device(spec: DeviceSpec): Device =
        Device(
            fingerprint = spec.fingerprint,
            macAddress = "AA:BB:CC:11:22:33",
            macAddressType = MacAddressType.PUBLIC,
            technology = "BLE",
            name = spec.name,
            deviceType = spec.deviceType,
            vendorName = spec.vendorName,
            predictedModel = spec.predictedModel,
            trackingStatus = spec.trackingStatus,
            followingScore = spec.followingScore,
            isSafeBeacon = false,
            isInWatchlist = spec.isInWatchlist,
            userAlias = null,
            userNotes = null,
            alertSound = false,
            alertVibration = false,
            calibrationLabel = spec.calibrationLabel,
            firstSeenAt = NOW,
            lastSeenAt = NOW,
            rssi = spec.rssi,
            encounterCount = 1,
            evidence = spec.evidence,
        )

    private companion object {
        private const val NOW = 1_789_000_000_000L
    }
}
