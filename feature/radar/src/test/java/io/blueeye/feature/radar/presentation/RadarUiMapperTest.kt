package io.blueeye.feature.radar.presentation

import io.blueeye.core.model.DetectionConfidence
import io.blueeye.core.model.DetectionEvidence
import io.blueeye.core.model.Device
import io.blueeye.core.model.DeviceType
import io.blueeye.core.model.EvidenceProvenance
import io.blueeye.core.model.EvidenceSource
import io.blueeye.core.model.MacAddressType
import io.blueeye.core.model.TrackingStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class RadarUiMapperTest {
    @Test
    fun `maps strongest evidence to confidence and primary reason`() {
        val item =
            RadarUiMapper.mapToUi(
                device =
                    device(
                        evidence =
                            listOf(
                                evidence(EvidenceSource.NAME, DetectionConfidence.MEDIUM, "Name matched Axon pattern"),
                                evidence(
                                    EvidenceSource.SERVICE_UUID,
                                    DetectionConfidence.HIGH,
                                    "Service UUID is consistent with Axon Body Camera.",
                                    provenance = EvidenceProvenance.BLE_ADVERTISEMENT,
                                ).copy(
                                    rawValue = "0000fd8e-0000-1000-8000-00805f9b34fb",
                                    parsedValue = "BODY_CAMERA",
                                ),
                            ),
                    ),
                isNew = false,
                activeProbeMac = null,
            )

        val evidenceInfo = item.evidenceInfo ?: error("Expected evidence info")

        assertEquals("High confidence", evidenceInfo.confidenceText)
        assertEquals(RadarUiColorToken.SUSPICIOUS, evidenceInfo.confidenceColor)
        assertEquals("Source: Service - BLE ad", evidenceInfo.primarySourceText)
        assertEquals("Service UUID is consistent with Axon Body Camera.", evidenceInfo.primaryReasonText)
        assertEquals(
            "Value: 0000fd8e-0000-1000-8000-00805f9b34fb -> BODY_CAMERA",
            evidenceInfo.primaryValueText,
        )
    }

    @Test
    fun `evidence chips include source and passive active mode`() {
        val item =
            RadarUiMapper.mapToUi(
                device =
                    device(
                        evidence =
                            listOf(
                                evidence(
                                    source = EvidenceSource.GATT_PROBE,
                                    confidence = DetectionConfidence.MEDIUM,
                                    reason = "Probe returned info",
                                    isPassive = false,
                                ),
                                evidence(
                                    source = EvidenceSource.WATCHLIST,
                                    confidence = DetectionConfidence.CRITICAL,
                                    reason = "Watchlist device returned",
                                ),
                                evidence(
                                    source = EvidenceSource.USER_CONFIRMATION,
                                    confidence = DetectionConfidence.MEDIUM,
                                    reason = "User marked this device as suspicious",
                                ),
                            ),
                    ),
                isNew = false,
                activeProbeMac = null,
            )

        val evidenceInfo = item.evidenceInfo ?: error("Expected evidence info")
        val chipText = evidenceInfo.chips.map { it.text }

        assertEquals(listOf("Watchlist passive", "GATT active", "User verdict"), chipText)
    }

    @Test
    fun `identity carryover evidence is compact on radar card`() {
        val item =
            RadarUiMapper.mapToUi(
                device =
                    device(
                        evidence =
                            listOf(
                                evidence(
                                    source = EvidenceSource.IDENTITY_CARRYOVER,
                                    confidence = DetectionConfidence.LOW,
                                    reason =
                                        "Rotating Bluetooth address was correlated with an existing device record. " +
                                            "Matcher context: match=Apple shadow advertisement; " +
                                            "features=scorePct=100;rssiDiff=0;timeDeltaMs=10. " +
                                            "Use this as identity continuity context, not as a risk signal.",
                                    provenance = EvidenceProvenance.FOLLOW_ME_ANALYSIS,
                                ).copy(
                                    rawValue = "55:19:63:4D:86:8D",
                                    parsedValue = "68:F6:7D:C0:E5:2C;reason=APPLE_SHADOW;confidence=100%",
                                ),
                            ),
                    ),
                isNew = false,
                activeProbeMac = null,
            )

        val evidenceInfo = item.evidenceInfo ?: error("Expected evidence info")

        assertEquals(
            "Rotating Bluetooth address matched an existing device record; review identity continuity in details.",
            evidenceInfo.primaryReasonText,
        )
        assertEquals("Alias: 55:19:63:4D:86:8D -> 68:F6:7D:C0:E5:2C", evidenceInfo.primaryValueText)
        assertFalse(evidenceInfo.primaryReasonText.contains("scorePct"))
    }

    @Test
    fun `service evidence chips expose provenance source`() {
        val item =
            RadarUiMapper.mapToUi(
                device =
                    device(
                        evidence =
                            listOf(
                                evidence(
                                    source = EvidenceSource.SERVICE_UUID,
                                    confidence = DetectionConfidence.MEDIUM,
                                    reason = "Service UUID came from BLE advertisement",
                                    provenance = EvidenceProvenance.BLE_ADVERTISEMENT,
                                ),
                                evidence(
                                    source = EvidenceSource.SERVICE_UUID,
                                    confidence = DetectionConfidence.MEDIUM,
                                    reason = "Service UUID came from Classic SDP",
                                    provenance = EvidenceProvenance.CLASSIC_SDP,
                                ),
                                evidence(
                                    source = EvidenceSource.SERVICE_UUID,
                                    confidence = DetectionConfidence.MEDIUM,
                                    reason = "Service UUID came from active GATT",
                                    isPassive = false,
                                    provenance = EvidenceProvenance.ACTIVE_GATT,
                                ),
                            ),
                    ),
                isNew = false,
                activeProbeMac = null,
            )

        val evidenceInfo = item.evidenceInfo ?: error("Expected evidence info")
        val chipText = evidenceInfo.chips.map { it.text }

        assertEquals("Source: Service - BLE ad", evidenceInfo.primarySourceText)
        assertEquals(listOf("Service BLE ad", "Service Classic SDP", "Service active GATT"), chipText)
    }

    @Test
    fun `critical evidence is capped to high confidence in user facing radar text`() {
        val item =
            RadarUiMapper.mapToUi(
                device =
                    device(
                        evidence =
                            listOf(
                                evidence(
                                    source = EvidenceSource.WATCHLIST,
                                    confidence = DetectionConfidence.CRITICAL,
                                    reason = "Watchlist device returned after 120s offline.",
                                ),
                            ),
                    ),
                isNew = false,
                activeProbeMac = null,
            )

        val evidenceInfo = item.evidenceInfo ?: error("Expected evidence info")

        assertEquals("High confidence", evidenceInfo.confidenceText)
        assertFalse(evidenceInfo.confidenceText.contains("Critical"))
        assertEquals(RadarUiColorToken.SUSPICIOUS, evidenceInfo.confidenceColor)
        assertEquals(RadarUiColorToken.SUSPICIOUS, evidenceInfo.chips.single().color)
    }

    @Test
    fun `device without evidence shows neutral no attention evidence info`() {
        val item =
            RadarUiMapper.mapToUi(
                device = device(evidence = emptyList()),
                isNew = false,
                activeProbeMac = null,
            )

        val evidenceInfo = item.evidenceInfo ?: error("Expected neutral evidence info")

        assertEquals("No attention evidence", evidenceInfo.confidenceText)
        assertEquals(RadarUiColorToken.OUTLINE, evidenceInfo.confidenceColor)
        assertEquals("Source: none", evidenceInfo.primarySourceText)
        assertEquals(
            "No medium-or-higher confidence evidence is available for this device.",
            evidenceInfo.primaryReasonText,
        )
        assertEquals(null, evidenceInfo.primaryValueText)
        assertEquals(listOf("No evidence"), evidenceInfo.chips.map { it.text })
    }

    @Test
    fun `maps last seen and rssi text for radar cards`() {
        val item =
            RadarUiMapper.mapToUi(
                device =
                    device(
                        evidence = emptyList(),
                        lastSeenAt = System.currentTimeMillis() - LAST_SEEN_GAP_MS,
                    ),
                isNew = false,
                activeProbeMac = null,
            )

        assertEquals("-55 dBm", item.signalInfo.rssiText)
        assertEquals("1m", item.signalInfo.timeSinceSeen)
    }

    @Test
    fun `generic placeholder identity hides mac address from radar title`() {
        val item =
            RadarUiMapper.mapToUi(
                device =
                    device(
                        evidence = emptyList(),
                        name = "Unknown Device",
                        vendorName = "Unknown Vendor",
                        predictedModel = "N/A",
                    ),
                isNew = false,
                activeProbeMac = null,
            )

        assertEquals("Unknown BLE device", item.displayName)
    }

    @Test
    fun `active probe override uses neutral probing label`() {
        val item =
            RadarUiMapper.mapToUi(
                device = device(evidence = emptyList()),
                isNew = false,
                activeProbeMac = "AA:BB:CC:11:22:33",
            )

        assertEquals("PROBING", item.connectionInfo.text)
        assertFalse(item.connectionInfo.text.contains("\u26A1"))
    }

    private fun evidence(
        source: EvidenceSource,
        confidence: DetectionConfidence,
        reason: String,
        isPassive: Boolean = true,
        provenance: EvidenceProvenance = EvidenceProvenance.UNKNOWN,
    ): DetectionEvidence =
        DetectionEvidence(
            source = source,
            confidence = confidence,
            reasonText = reason,
            timestamp = NOW,
            rawValue = null,
            parsedValue = null,
            isPassive = isPassive,
            provenance = provenance,
        )

    private fun device(
        evidence: List<DetectionEvidence>,
        lastSeenAt: Long = NOW,
        name: String? = "Axon Body 3",
        vendorName: String? = "Unknown Vendor",
        predictedModel: String? = null,
    ): Device =
        Device(
            fingerprint = "AA:BB:CC:11:22:33",
            macAddress = "AA:BB:CC:11:22:33",
            macAddressType = MacAddressType.PUBLIC,
            technology = "BLE",
            name = name,
            deviceType = DeviceType.UNKNOWN,
            vendorName = vendorName,
            predictedModel = predictedModel,
            trackingStatus = TrackingStatus.SAFE,
            followingScore = 0f,
            isSafeBeacon = false,
            isInWatchlist = false,
            userAlias = null,
            userNotes = null,
            alertSound = false,
            alertVibration = false,
            firstSeenAt = NOW,
            lastSeenAt = lastSeenAt,
            rssi = -55,
            encounterCount = 1,
            evidence = evidence,
        )

    private companion object {
        private const val NOW = 1_789_000_000_000L
        private const val LAST_SEEN_GAP_MS = 65_000L
    }
}
