package io.blueeye.core.data.classifier.tactical

import io.blueeye.core.alert.TacticalAlertRequest
import io.blueeye.core.alert.TacticalAlertService
import io.blueeye.core.data.classifier.vendor.tactical.ConfidenceLevel
import io.blueeye.core.data.classifier.vendor.tactical.TacticalOuiInfo
import io.blueeye.core.data.classifier.vendor.tactical.TacticalUuids
import io.blueeye.core.model.DetectionConfidence
import io.blueeye.core.model.DetectionEvidence
import io.blueeye.core.model.DeviceType
import io.blueeye.core.model.EvidenceProvenance
import io.blueeye.core.model.EvidenceSource
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.verifyNoInteractions

class TacticalProcessorTest {
    private val tacticalAlertService: TacticalAlertService = mock()
    private val processor = TacticalProcessor(tacticalAlertService)

    @Test
    fun `Axon OUI is user-facing high confidence body camera signal`() {
        val result =
            processor.process(
                mac = "00:25:DF:11:22:33",
                rssi = -52,
                manufacturerId = null,
                manufacturerData = null,
                serviceUuids = emptyList(),
                name = null,
                timestamp = NOW,
            )

        assertTrue(result.isTactical)
        assertEquals(DeviceType.BODY_CAMERA, result.deviceType)
        assertEquals("Signal consistent with body camera equipment", result.beaconTypeStatus)
        assertNeutral(result.beaconTypeStatus)

        val alert = captureAlert()
        assertEquals(ConfidenceLevel.HIGH, alert.match.confidence)
        assertEquals(EvidenceSource.OUI, alert.source)
        assertEquals("0025DF", alert.rawValue)
        assertEquals(NOW, alert.evidence.timestamp)
        assertEquals(EvidenceProvenance.DEVICE_REGISTRY, alert.evidence.provenance)
        assertEquals(EvidenceSource.OUI, result.evidence.single().source)
        assertEquals(EvidenceProvenance.DEVICE_REGISTRY, result.evidence.single().provenance)
    }

    @Test
    fun `name-only Axon signal emits medium confidence name evidence`() {
        val result =
            processor.process(
                mac = "AA:BB:CC:11:22:33",
                rssi = -58,
                manufacturerId = null,
                manufacturerData = null,
                serviceUuids = emptyList(),
                name = "Axon Body 3",
                timestamp = NOW,
            )

        assertTrue(result.isTactical)
        assertEquals(DeviceType.BODY_CAMERA, result.deviceType)
        assertEquals("Signal consistent with Axon Body Camera", result.beaconTypeStatus)
        assertNeutral(result.beaconTypeStatus)

        val evidence = result.evidence.single()
        assertEquals(EvidenceSource.NAME, evidence.source)
        assertEquals(DetectionConfidence.MEDIUM, evidence.confidence)
        assertEquals("Axon Body 3", evidence.rawValue)
        assertTrue(evidence.reasonText.contains("Name-only signals are capped at medium confidence"))

        val alert = captureAlert()
        assertEquals(ConfidenceLevel.MEDIUM, alert.match.confidence)
        assertEquals(EvidenceSource.NAME, alert.source)
        assertEquals("Axon Body 3", alert.rawValue)
        assertEquals(EvidenceSource.NAME, alert.evidence.source)
    }

    @Test
    fun `loose Axon name does not emit tactical evidence`() {
        val result =
            processor.process(
                mac = "AA:BB:CC:11:22:33",
                rssi = -58,
                manufacturerId = null,
                manufacturerData = null,
                serviceUuids = emptyList(),
                name = "Axon Speaker",
                timestamp = NOW,
            )

        assertFalse(result.isTactical)
        assertEquals(DeviceType.UNKNOWN, result.deviceType)
        assertNull(result.beaconTypeStatus)
        assertTrue(result.evidence.isEmpty())
        verifyNoInteractions(tacticalAlertService)
    }

    @Test
    fun `Axon body camera payload maps to body camera type`() {
        val result =
            processor.process(
                mac = "AA:BB:CC:11:22:33",
                rssi = -47,
                manufacturerId = null,
                manufacturerData =
                    byteArrayOf(
                        0x4D,
                        0x03,
                        0x01,
                        0x00,
                        0x12,
                        0x34,
                        0x56,
                        0x78,
                        0x64,
                    ),
                serviceUuids = emptyList(),
                name = null,
                timestamp = NOW,
            )

        assertTrue(result.isTactical)
        assertEquals(DeviceType.BODY_CAMERA, result.deviceType)
        assertEquals("Signal consistent with Axon Enterprise payload [IDLE]", result.beaconTypeStatus)
        assertNeutral(result.beaconTypeStatus)

        val alert = captureAlert()
        assertEquals(EvidenceSource.RAW_PAYLOAD, alert.source)
        assertEquals("0x034D", alert.rawValue)
        assertEquals(EvidenceProvenance.BLE_ADVERTISEMENT, alert.evidence.provenance)
        assertEquals(EvidenceSource.RAW_PAYLOAD, result.evidence.single().source)
        assertEquals(EvidenceProvenance.BLE_ADVERTISEMENT, result.evidence.single().provenance)
    }

    @Test
    fun `Motorola V300 payload maps to body camera type`() {
        val result =
            processor.process(
                mac = "AA:BB:CC:11:22:33",
                rssi = -47,
                manufacturerId = null,
                manufacturerData =
                    byteArrayOf(
                        0x08,
                        0x00,
                        0x03,
                        0x00,
                        0x12,
                        0x34,
                        0x56,
                        0x78,
                        0x00,
                        0x00,
                        0x01,
                    ),
                serviceUuids = emptyList(),
                name = null,
                timestamp = NOW,
            )

        assertTrue(result.isTactical)
        assertEquals(DeviceType.BODY_CAMERA, result.deviceType)
        assertEquals("Signal consistent with Motorola Solutions payload [ACTIVE_STATE]", result.beaconTypeStatus)
        assertNeutral(result.beaconTypeStatus)
    }

    @Test
    fun `Axon vehicle payload maps to vehicle router without police label`() {
        val result =
            processor.process(
                mac = "AA:BB:CC:11:22:33",
                rssi = -47,
                manufacturerId = null,
                manufacturerData =
                    byteArrayOf(
                        0x4D,
                        0x03,
                        0x03,
                        0x00,
                        0x12,
                        0x34,
                        0x56,
                        0x78,
                        0x64,
                    ),
                serviceUuids = emptyList(),
                name = null,
                timestamp = NOW,
            )

        assertTrue(result.isTactical)
        assertEquals(DeviceType.VEHICLE_ROUTER, result.deviceType)
        assertEquals("Signal consistent with Axon Enterprise payload [IDLE]", result.beaconTypeStatus)
        assertNeutral(result.beaconTypeStatus)

        val alert = captureAlert()
        assertEquals(ConfidenceLevel.HIGH, alert.match.confidence)
        assertEquals(EvidenceSource.RAW_PAYLOAD, alert.source)
        assertEquals("0x034D", alert.rawValue)
        assertEquals(EvidenceProvenance.BLE_ADVERTISEMENT, alert.evidence.provenance)
        assertEquals(EvidenceSource.RAW_PAYLOAD, result.evidence.single().source)
        assertEquals(EvidenceProvenance.BLE_ADVERTISEMENT, result.evidence.single().provenance)
    }

    @Test
    fun `Axon Android manufacturer payload decodes when company id is separate`() {
        val result =
            processor.process(
                mac = "AA:BB:CC:11:22:33",
                rssi = -47,
                manufacturerId = TacticalUuids.AXON_COMPANY_ID,
                manufacturerData = byteArrayOf(
                    0x03,
                    0x00,
                    0x12,
                    0x34,
                    0x56,
                    0x78,
                    0x64,
                ),
                serviceUuids = emptyList(),
                name = null,
                timestamp = NOW,
            )

        assertTrue(result.isTactical)
        assertEquals(DeviceType.VEHICLE_ROUTER, result.deviceType)
        assertEquals("Signal consistent with Axon Enterprise payload [IDLE]", result.beaconTypeStatus)
        assertNeutral(result.beaconTypeStatus)

        val alert = captureAlert()
        assertEquals(EvidenceSource.RAW_PAYLOAD, alert.source)
        assertEquals("0x034D", alert.rawValue)
        assertEquals(EvidenceProvenance.BLE_ADVERTISEMENT, alert.evidence.provenance)
        assertEquals(EvidenceSource.RAW_PAYLOAD, result.evidence.single().source)
        assertEquals(EvidenceProvenance.BLE_ADVERTISEMENT, result.evidence.single().provenance)
    }

    @Test
    fun `non-first Axon manufacturer record still decodes tactical payload`() {
        val result =
            processor.process(
                mac = "AA:BB:CC:11:22:33",
                rssi = -47,
                manufacturerId = APPLE_COMPANY_ID,
                manufacturerData = byteArrayOf(0x01, 0x02),
                manufacturerDataById = mapOf(
                    APPLE_COMPANY_ID to byteArrayOf(0x01, 0x02),
                    TacticalUuids.AXON_COMPANY_ID to byteArrayOf(
                        0x03,
                        0x00,
                        0x12,
                        0x34,
                        0x56,
                        0x78,
                        0x64,
                    ),
                ),
                serviceUuids = emptyList(),
                name = null,
                timestamp = NOW,
            )

        assertTrue(result.isTactical)
        assertEquals(DeviceType.VEHICLE_ROUTER, result.deviceType)
        assertEquals("Signal consistent with Axon Enterprise payload [IDLE]", result.beaconTypeStatus)
        assertNeutral(result.beaconTypeStatus)

        val alert = captureAlert()
        assertEquals(EvidenceSource.RAW_PAYLOAD, alert.source)
        assertEquals("0x034D", alert.rawValue)
        assertEquals(EvidenceProvenance.BLE_ADVERTISEMENT, alert.evidence.provenance)
        assertEquals(EvidenceSource.RAW_PAYLOAD, result.evidence.single().source)
        assertEquals(EvidenceProvenance.BLE_ADVERTISEMENT, result.evidence.single().provenance)
    }

    @Test
    fun `tactical manufacturer id fallback uses BLE advertisement provenance`() {
        val result =
            processor.process(
                mac = "AA:BB:CC:11:22:33",
                rssi = -61,
                manufacturerId = TacticalUuids.MOTOROLA_COMPANY_ID,
                manufacturerData = byteArrayOf(),
                serviceUuids = emptyList(),
                name = null,
                timestamp = NOW,
            )

        assertTrue(result.isTactical)
        assertEquals(DeviceType.TACTICAL_RADIO, result.deviceType)
        assertEquals("Signal consistent with Motorola (Radio)", result.beaconTypeStatus)
        assertNeutral(result.beaconTypeStatus)

        val alert = captureAlert()
        assertEquals(ConfidenceLevel.HIGH, alert.match.confidence)
        assertEquals(EvidenceSource.MANUFACTURER_ID, alert.source)
        assertEquals("0x0008", alert.rawValue)
        assertEquals(EvidenceProvenance.BLE_ADVERTISEMENT, alert.evidence.provenance)
        assertEquals(EvidenceSource.MANUFACTURER_ID, result.evidence.single().source)
        assertEquals(EvidenceProvenance.BLE_ADVERTISEMENT, result.evidence.single().provenance)
    }

    @Test
    fun `Motorola service uuid maps to tactical radio with neutral label`() {
        val result =
            processor.process(
                mac = "AA:BB:CC:11:22:33",
                rssi = -61,
                manufacturerId = null,
                manufacturerData = null,
                serviceUuids = listOf(TacticalUuids.MOTOROLA_SOLUTIONS_UUID),
                name = null,
                timestamp = NOW,
            )

        assertTrue(result.isTactical)
        assertEquals(DeviceType.TACTICAL_RADIO, result.deviceType)
        assertEquals("Signal consistent with Motorola Solutions (FD8E)", result.beaconTypeStatus)
        assertNeutral(result.beaconTypeStatus)

        val alert = captureAlert()
        assertEquals(ConfidenceLevel.HIGH, alert.match.confidence)
        assertEquals(EvidenceSource.SERVICE_UUID, alert.source)
        assertEquals(TacticalUuids.MOTOROLA_SOLUTIONS_UUID, alert.rawValue)
        assertEquals(EvidenceSource.SERVICE_UUID, result.evidence.single().source)
        assertEquals(EvidenceProvenance.BLE_ADVERTISEMENT, result.evidence.single().provenance)
    }

    @Test
    fun `Cambridge Silicon Radio manufacturer id is not tactical Motorola evidence`() {
        val result =
            processor.process(
                mac = "AA:BB:CC:11:22:33",
                rssi = -61,
                manufacturerId = CAMBRIDGE_SILICON_RADIO_ID,
                manufacturerData = byteArrayOf(0x01, 0x02, 0x03),
                serviceUuids = emptyList(),
                name = null,
                timestamp = NOW,
            )

        assertFalse(result.isTactical)
        assertEquals(DeviceType.UNKNOWN, result.deviceType)
        assertNull(result.beaconTypeStatus)
        verifyNoInteractions(tacticalAlertService)
    }

    @Test
    fun `attack speaker name does not emit tactical name evidence`() {
        val result =
            processor.process(
                mac = "AA:BB:CC:11:22:33",
                rssi = -61,
                manufacturerId = null,
                manufacturerData = null,
                serviceUuids = emptyList(),
                name = "Attack Speaker",
                timestamp = NOW,
            )

        assertFalse(result.isTactical)
        assertEquals(DeviceType.UNKNOWN, result.deviceType)
        assertNull(result.beaconTypeStatus)
        assertTrue(result.evidence.isEmpty())
        verifyNoInteractions(tacticalAlertService)
    }

    @Test
    fun `loose SIG BDX family words do not emit tactical name evidence`() {
        listOf("Sierra Speaker", "Whiskey Speaker", "Kilo Keyboard").forEach { name ->
            val result =
                processor.process(
                    mac = "AA:BB:CC:11:22:33",
                    rssi = -61,
                    manufacturerId = null,
                    manufacturerData = null,
                    serviceUuids = emptyList(),
                    name = name,
                    timestamp = NOW,
                )

            assertFalse("$name should not be tactical", result.isTactical)
            assertEquals(DeviceType.UNKNOWN, result.deviceType)
            assertNull(result.beaconTypeStatus)
            assertTrue(result.evidence.isEmpty())
        }
        verifyNoInteractions(tacticalAlertService)
    }

    @Test
    fun `SIG model name remains medium confidence smart weapon signal`() {
        val result =
            processor.process(
                mac = "AA:BB:CC:11:22:33",
                rssi = -61,
                manufacturerId = null,
                manufacturerData = null,
                serviceUuids = emptyList(),
                name = "Sig Kilo3K",
                timestamp = NOW,
            )

        assertTrue(result.isTactical)
        assertEquals(DeviceType.SMART_WEAPON, result.deviceType)
        assertEquals("Signal consistent with Sig Sauer BDX", result.beaconTypeStatus)
        assertNeutral(result.beaconTypeStatus)

        val evidence = result.evidence.single()
        assertEquals(EvidenceSource.NAME, evidence.source)
        assertEquals(DetectionConfidence.MEDIUM, evidence.confidence)
        assertEquals("Sig Kilo3K", evidence.rawValue)
        assertTrue(evidence.reasonText.contains("Name-only signals are capped at medium confidence"))

        val alert = captureAlert()
        assertEquals(ConfidenceLevel.MEDIUM, alert.match.confidence)
        assertEquals(EvidenceSource.NAME, alert.source)
        assertEquals("Sig Kilo3K", alert.rawValue)
    }

    @Test
    fun `consumer Samsung Clarus name is suppressed as tactical audio evidence`() {
        val result =
            processor.process(
                mac = "AA:BB:CC:11:22:33",
                rssi = -61,
                manufacturerId = null,
                manufacturerData = null,
                serviceUuids = emptyList(),
                name = "Samsung Clarus",
                timestamp = NOW,
            )

        assertFalse(result.isTactical)
        assertEquals(DeviceType.UNKNOWN, result.deviceType)
        assertNull(result.beaconTypeStatus)
        assertTrue(result.evidence.isEmpty())
        verifyNoInteractions(tacticalAlertService)
    }

    private fun captureAlert(): CapturedAlert {
        val requestCaptor = argumentCaptor<TacticalAlertRequest>()
        verify(tacticalAlertService).onDeviceDetected(requestCaptor.capture())
        val request = requestCaptor.firstValue
        return CapturedAlert(
            match = request.match,
            source = request.evidenceSource,
            rawValue = request.rawEvidenceValue,
            evidence = request.evidence ?: error("Expected tactical evidence"),
        )
    }

    private data class CapturedAlert(
        val match: TacticalOuiInfo,
        val source: EvidenceSource,
        val rawValue: String,
        val evidence: DetectionEvidence,
    )

    private fun assertNeutral(label: String?) {
        val text = label.orEmpty().lowercase()
        assertFalse(text.contains("police"))
        assertFalse(text.contains("law enforcement"))
        assertFalse(text.contains("alarm"))
        assertFalse(text.contains("nearby"))
        assertFalse(text.contains("⚠"))
        assertFalse(text.contains("🚨"))
    }

    private companion object {
        const val APPLE_COMPANY_ID = 0x004C
        const val CAMBRIDGE_SILICON_RADIO_ID = 0x000A
        const val NOW = 1_789_000_000_000L
    }
}
