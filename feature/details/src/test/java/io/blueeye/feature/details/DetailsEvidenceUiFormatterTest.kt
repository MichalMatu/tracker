package io.blueeye.feature.details

import io.blueeye.core.model.DetectionConfidence
import io.blueeye.core.model.DetectionEvidence
import io.blueeye.core.model.EvidenceProvenance
import io.blueeye.core.model.EvidenceSource
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class DetailsEvidenceUiFormatterTest {
    @Test
    fun `empty state explains absence of attention evidence without claiming safety`() {
        val emptyState = DetailsEvidenceUiFormatter.emptyState()

        assertEquals("No attention evidence", emptyState.title)
        assertEquals("Passive scan review", emptyState.modeText)
        assertEquals(
            "No medium-or-higher confidence evidence is available for this device.",
            emptyState.detail,
        )
    }

    @Test
    fun `formats evidence with raw parsed timestamp and passive mode`() {
        val items =
            DetailsEvidenceUiFormatter.format(
                evidence =
                    listOf(
                        evidence(
                            source = EvidenceSource.SERVICE_UUID,
                            confidence = DetectionConfidence.HIGH,
                            reasonText = "Service UUID is consistent with Axon Body Camera.",
                        ).copy(
                            rawValue = "0000fd8e-0000-1000-8000-00805f9b34fb",
                            parsedValue = "BODY_CAMERA",
                        ),
                    ),
                timestampFormatter = { "observed:$it" },
            )

        val item = items.single()

        assertEquals("Service UUID", item.sourceText)
        assertEquals("High confidence", item.confidenceText)
        assertEquals("Passive scan", item.modeText)
        assertEquals("Service UUID is consistent with Axon Body Camera.", item.reasonText)
        assertEquals("observed:$TIMESTAMP", item.observedAtText)
        assertEquals("0000fd8e-0000-1000-8000-00805f9b34fb", item.rawValueText)
        assertEquals("BODY_CAMERA", item.parsedValueText)
    }

    @Test
    fun `formats neutral service uuid identity evidence`() {
        val items =
            DetailsEvidenceUiFormatter.format(
                evidence =
                    listOf(
                        evidence(
                            source = EvidenceSource.SERVICE_UUID,
                            confidence = DetectionConfidence.LOW,
                            reasonText =
                                "Bluetooth service UUIDs were observed. Use this as identity context, " +
                                    "not as a risk signal.",
                        ).copy(
                            rawValue = "0000110b-0000-1000-8000-00805f9b34fb",
                            parsedValue = "1 service UUID observed",
                        ),
                    ),
                timestampFormatter = { "observed:$it" },
            )

        val item = items.single()

        assertEquals("Service UUID", item.sourceText)
        assertEquals("Low confidence", item.confidenceText)
        assertEquals("Passive scan", item.modeText)
        assertEquals("0000110b-0000-1000-8000-00805f9b34fb", item.rawValueText)
        assertEquals("1 service UUID observed", item.parsedValueText)
    }

    @Test
    fun `formats service uuid provenance as evidence mode`() {
        val items =
            DetailsEvidenceUiFormatter.format(
                evidence =
                    listOf(
                        evidence(
                            source = EvidenceSource.SERVICE_UUID,
                            confidence = DetectionConfidence.LOW,
                            reasonText =
                                "Bluetooth service UUIDs were observed via opportunistic " +
                                    "Classic SDP discovery.",
                        ).copy(provenance = EvidenceProvenance.CLASSIC_SDP),
                        evidence(
                            source = EvidenceSource.SERVICE_UUID,
                            confidence = DetectionConfidence.LOW,
                            reasonText = "Bluetooth service UUIDs were observed via active GATT service discovery.",
                        ).copy(
                            provenance = EvidenceProvenance.ACTIVE_GATT,
                            isPassive = false,
                        ),
                    ),
                timestampFormatter = { it.toString() },
            )

        assertEquals("Active GATT", items[0].modeText)
        assertEquals("Classic SDP (opportunistic)", items[1].modeText)
    }

    @Test
    fun `sorts strongest evidence first and names watchlist critical as alert`() {
        val items =
            DetailsEvidenceUiFormatter.format(
                evidence =
                    listOf(
                        evidence(EvidenceSource.NAME, DetectionConfidence.MEDIUM, "Name matched"),
                        evidence(EvidenceSource.WATCHLIST, DetectionConfidence.CRITICAL, "Watchlist match"),
                    ),
                timestampFormatter = { it.toString() },
            )

        assertEquals("Watchlist", items.first().sourceText)
        assertEquals("Watchlist alert", items.first().confidenceText)
    }

    @Test
    fun `caps critical confidence label for non watchlist evidence`() {
        val items =
            DetailsEvidenceUiFormatter.format(
                evidence =
                    listOf(
                        evidence(EvidenceSource.USER_CONFIRMATION, DetectionConfidence.CRITICAL, "User confirmed"),
                    ),
                timestampFormatter = { it.toString() },
            )

        assertEquals("High confidence", items.single().confidenceText)
        assertFalse(items.single().confidenceText.contains("Critical"))
    }

    @Test
    fun `formats user confirmation as user verdict mode`() {
        val items =
            DetailsEvidenceUiFormatter.format(
                evidence =
                    listOf(
                        evidence(
                            source = EvidenceSource.USER_CONFIRMATION,
                            confidence = DetectionConfidence.MEDIUM,
                            reasonText = "User marked this device as suspicious for future evidence review.",
                        ),
                    ),
                timestampFormatter = { it.toString() },
            )

        val item = items.single()

        assertEquals("User confirmation", item.sourceText)
        assertEquals("User verdict", item.modeText)
    }

    @Test
    fun `formats identity carryover evidence`() {
        val items =
            DetailsEvidenceUiFormatter.format(
                evidence =
                    listOf(
                        evidence(
                            source = EvidenceSource.IDENTITY_CARRYOVER,
                            confidence = DetectionConfidence.LOW,
                            reasonText = "Rotating Bluetooth address was correlated with an existing device record.",
                        ).copy(
                            rawValue = "7A:BB:CC:11:22:33",
                            parsedValue = "AA:BB:CC:11:22:33",
                            provenance = EvidenceProvenance.FOLLOW_ME_ANALYSIS,
                        ),
                    ),
                timestampFormatter = { it.toString() },
            )

        val item = items.single()

        assertEquals("Identity continuity", item.sourceText)
        assertEquals("Low confidence", item.confidenceText)
        assertEquals("Follow-Me analysis", item.modeText)
        assertEquals("7A:BB:CC:11:22:33", item.rawValueText)
        assertEquals("AA:BB:CC:11:22:33", item.parsedValueText)
    }

    @Test
    fun `formats active evidence and empty raw values`() {
        val items =
            DetailsEvidenceUiFormatter.format(
                evidence =
                    listOf(
                        evidence(
                            source = EvidenceSource.GATT_PROBE,
                            confidence = DetectionConfidence.MEDIUM,
                            reasonText = "Active GATT probe returned device information.",
                        ).copy(isPassive = false),
                    ),
                timestampFormatter = { it.toString() },
            )

        val item = items.single()

        assertEquals("GATT probe", item.sourceText)
        assertEquals("Active probe", item.modeText)
        assertEquals("None", item.rawValueText)
        assertEquals("None", item.parsedValueText)
    }

    private fun evidence(
        source: EvidenceSource,
        confidence: DetectionConfidence,
        reasonText: String,
    ): DetectionEvidence =
        DetectionEvidence(
            source = source,
            confidence = confidence,
            reasonText = reasonText,
            timestamp = TIMESTAMP,
            rawValue = null,
            parsedValue = null,
            isPassive = true,
        )

    private companion object {
        private const val TIMESTAMP = 1_789_000_000_000L
    }
}
