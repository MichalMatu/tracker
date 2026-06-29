package io.blueeye.core.data.evidence

import io.blueeye.core.data.classifier.vendor.tactical.TacticalUuids
import io.blueeye.core.data.db.entity.DeviceEntity
import io.blueeye.core.data.mapper.toDomain
import io.blueeye.core.model.DetectionConfidence
import io.blueeye.core.model.DeviceCalibrationLabel
import io.blueeye.core.model.DeviceType
import io.blueeye.core.model.EvidenceProvenance
import io.blueeye.core.model.EvidenceSource
import io.blueeye.core.model.IdentityCarryoverVerdict
import io.blueeye.core.model.MacAddressType
import io.blueeye.core.model.TrackingStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DeviceEvidenceFactoryTest {
    @Test
    fun `name-only Axon evidence is capped at medium confidence`() {
        val evidence =
            DeviceEvidenceFactory.build(
                device(
                    mac = "AA:BB:CC:11:22:33",
                    name = "Axon Body 3",
                ),
            )

        val nameEvidence = evidence.single { it.source == EvidenceSource.NAME }

        assertEquals(DetectionConfidence.MEDIUM, nameEvidence.confidence)
        assertEquals("Axon Body 3", nameEvidence.rawValue)
        assertTrue(nameEvidence.reasonText.contains("consistent"))
        assertTrue(nameEvidence.reasonText.contains("Name-only signals are capped at medium confidence"))
        assertEquals("BODY_CAMERA: Axon Body Camera", nameEvidence.parsedValue)
        assertEquals(EvidenceProvenance.BLE_ADVERTISEMENT, nameEvidence.provenance)
    }

    @Test
    fun `Axon OUI evidence is capped at high confidence`() {
        val evidence =
            DeviceEvidenceFactory.build(
                device(mac = "00:25:DF:11:22:33"),
            )

        val ouiEvidence = evidence.single { it.source == EvidenceSource.OUI }

        assertEquals(DetectionConfidence.HIGH, ouiEvidence.confidence)
        assertEquals(
            "MAC OUI is consistent with Axon Enterprise, Inc.: " +
                "Body camera and connected safety sensor equipment.",
            ouiEvidence.reasonText,
        )
        assertTrue(ouiEvidence.parsedValue.orEmpty().contains("BODY_CAMERA"))
        assertEquals(EvidenceProvenance.DEVICE_REGISTRY, ouiEvidence.provenance)
    }

    @Test
    fun `Axon manufacturer id evidence is high confidence`() {
        val evidence =
            DeviceEvidenceFactory.build(
                device(
                    mac = "AA:BB:CC:11:22:33",
                    manufacturerId = TacticalUuids.AXON_COMPANY_ID,
                ),
            )

        val manufacturerEvidence = evidence.single { it.source == EvidenceSource.MANUFACTURER_ID }

        assertEquals(DetectionConfidence.HIGH, manufacturerEvidence.confidence)
        assertEquals("0x034D", manufacturerEvidence.rawValue)
        assertEquals(EvidenceProvenance.BLE_ADVERTISEMENT, manufacturerEvidence.provenance)
    }

    @Test
    fun `raw advertisement manufacturer records are all considered as evidence`() {
        val evidence =
            DeviceEvidenceFactory.build(
                device(
                    mac = "AA:BB:CC:11:22:33",
                    manufacturerId = APPLE_COMPANY_ID,
                    rawData = FLAGS_AD + APPLE_MANUFACTURER_AD + AXON_MANUFACTURER_AD,
                ),
            )

        val manufacturerEvidence = evidence.single { it.source == EvidenceSource.MANUFACTURER_ID }

        assertEquals(DetectionConfidence.HIGH, manufacturerEvidence.confidence)
        assertEquals("0x034D", manufacturerEvidence.rawValue)
        assertTrue(manufacturerEvidence.reasonText.contains("Axon"))
        assertEquals(EvidenceProvenance.BLE_ADVERTISEMENT, manufacturerEvidence.provenance)
    }

    @Test
    fun `Motorola service uuid evidence is high confidence`() {
        val evidence =
            DeviceEvidenceFactory.build(
                device(
                    mac = "AA:BB:CC:11:22:33",
                    services = TacticalUuids.MOTOROLA_SOLUTIONS_UUID,
                ),
            )

        val serviceEvidence = evidence.single { it.source == EvidenceSource.SERVICE_UUID }

        assertEquals(DetectionConfidence.HIGH, serviceEvidence.confidence)
        assertTrue(serviceEvidence.reasonText.contains("Motorola"))
        assertTrue(serviceEvidence.isPassive)
        assertEquals(EvidenceProvenance.BLE_ADVERTISEMENT, serviceEvidence.provenance)
    }

    @Test
    fun `active tactical service uuid evidence is marked active`() {
        val evidence =
            DeviceEvidenceFactory.build(
                device(
                    mac = "AA:BB:CC:11:22:33",
                    services = "fd8e:[]",
                ).copy(
                    connectionStatus = "PROBED",
                    lastProbeTimestamp = TIMESTAMP + ALERT_OFFSET_MS,
                ),
            )

        val serviceEvidence = evidence.single { it.source == EvidenceSource.SERVICE_UUID }

        assertEquals(DetectionConfidence.HIGH, serviceEvidence.confidence)
        assertFalse(serviceEvidence.isPassive)
        assertEquals(EvidenceProvenance.ACTIVE_GATT, serviceEvidence.provenance)
    }

    @Test
    fun `persisted classic service uuid list is visible as low confidence identity evidence`() {
        val evidence =
            DeviceEvidenceFactory.build(
                device(
                    mac = "AA:BB:CC:11:22:33",
                    services = CLASSIC_AUDIO_SERVICES,
                ).copy(technology = "CLASSIC"),
            )

        val serviceEvidence = evidence.single { it.source == EvidenceSource.SERVICE_UUID }

        assertEquals(DetectionConfidence.LOW, serviceEvidence.confidence)
        assertEquals(CLASSIC_AUDIO_SERVICES.lowercase().replace(" ", ""), serviceEvidence.rawValue)
        assertEquals("2 service UUIDs observed", serviceEvidence.parsedValue)
        assertTrue(serviceEvidence.reasonText.contains("opportunistic Classic SDP discovery"))
        assertTrue(serviceEvidence.reasonText.contains("identity context"))
        assertTrue(serviceEvidence.reasonText.contains("not as a risk signal"))
        assertTrue(serviceEvidence.isPassive)
        assertEquals(EvidenceProvenance.CLASSIC_SDP, serviceEvidence.provenance)
    }

    @Test
    fun `structured active service list is marked active service evidence`() {
        val evidence =
            DeviceEvidenceFactory.build(
                device(
                    mac = "AA:BB:CC:11:22:33",
                    services = "180a:[2a24,2a29]",
                ).copy(
                    connectionStatus = "PROBED",
                    lastProbeTimestamp = TIMESTAMP + ALERT_OFFSET_MS,
                ),
            )

        val serviceEvidence = evidence.single { it.source == EvidenceSource.SERVICE_UUID }

        assertEquals(DetectionConfidence.LOW, serviceEvidence.confidence)
        assertEquals("0000180a-0000-1000-8000-00805f9b34fb", serviceEvidence.rawValue)
        assertEquals("1 service UUID observed", serviceEvidence.parsedValue)
        assertFalse(serviceEvidence.isPassive)
        assertEquals(EvidenceProvenance.ACTIVE_GATT, serviceEvidence.provenance)
    }

    @Test
    fun `audio service uuid evidence explains headphones classification context`() {
        val evidence =
            DeviceEvidenceFactory.build(
                device(
                    mac = "AA:BB:CC:11:22:33",
                    services = LE_AUDIO_INPUT_CONTROL_SERVICE,
                ),
            )

        val serviceEvidence = evidence.single { it.source == EvidenceSource.SERVICE_UUID }

        assertEquals(DetectionConfidence.MEDIUM, serviceEvidence.confidence)
        assertEquals(LE_AUDIO_INPUT_CONTROL_SERVICE.lowercase(), serviceEvidence.rawValue)
        assertEquals(DeviceType.HEADPHONES.name, serviceEvidence.parsedValue)
        assertTrue(serviceEvidence.reasonText.contains("LE Audio Device"))
        assertTrue(serviceEvidence.reasonText.contains("classification context"))
        assertTrue(serviceEvidence.reasonText.contains("not as a risk signal"))
        assertTrue(serviceEvidence.isPassive)
        assertEquals(EvidenceProvenance.BLE_ADVERTISEMENT, serviceEvidence.provenance)
    }

    @Test
    fun `Cambridge Silicon Radio manufacturer id does not create Motorola evidence`() {
        val evidence =
            DeviceEvidenceFactory.build(
                device(
                    mac = "AA:BB:CC:11:22:33",
                    manufacturerId = CAMBRIDGE_SILICON_RADIO_ID,
                ),
            )

        assertTrue(evidence.none { it.source == EvidenceSource.MANUFACTURER_ID })
    }

    @Test
    fun `Samsung Clarus name stays generic name evidence`() {
        val evidence =
            DeviceEvidenceFactory.build(
                device(
                    mac = "AA:BB:CC:11:22:33",
                    name = "Samsung Clarus",
                ),
            )

        val nameEvidence = evidence.single { it.source == EvidenceSource.NAME }

        assertEquals(DetectionConfidence.LOW, nameEvidence.confidence)
        assertEquals("Samsung Clarus", nameEvidence.rawValue)
        assertEquals("Samsung Clarus", nameEvidence.parsedValue)
        assertTrue(nameEvidence.reasonText.contains("advertised a Bluetooth name"))
        assertEquals(EvidenceProvenance.BLE_ADVERTISEMENT, nameEvidence.provenance)
    }

    @Test
    fun `name evidence explains non tactical device type classification`() {
        val evidence =
            DeviceEvidenceFactory.build(
                device(
                    mac = "AA:BB:CC:11:22:33",
                    name = "Jabra Elite",
                ),
            )

        val nameEvidence = evidence.single { it.source == EvidenceSource.NAME }

        assertEquals(DetectionConfidence.LOW, nameEvidence.confidence)
        assertEquals("Jabra Elite", nameEvidence.rawValue)
        assertEquals(DeviceType.HEADPHONES.name, nameEvidence.parsedValue)
        assertTrue(nameEvidence.reasonText.contains("classification context"))
        assertTrue(nameEvidence.reasonText.contains("Names can be spoofed"))
        assertTrue(nameEvidence.reasonText.contains("not as a risk signal"))
        assertTrue(nameEvidence.isPassive)
        assertEquals(EvidenceProvenance.BLE_ADVERTISEMENT, nameEvidence.provenance)
    }

    @Test
    fun `predicted model evidence explains model identity context`() {
        val evidence =
            DeviceEvidenceFactory.build(
                device(mac = "AA:BB:CC:11:22:33").copy(
                    predictedModel = "AirPods Pro",
                ),
            )

        val modelEvidence = evidence.single { it.source == EvidenceSource.MODEL }

        assertEquals(DetectionConfidence.LOW, modelEvidence.confidence)
        assertEquals("AirPods Pro", modelEvidence.rawValue)
        assertEquals(DeviceType.HEADPHONES.name, modelEvidence.parsedValue)
        assertTrue(modelEvidence.reasonText.contains("identity context"))
        assertTrue(modelEvidence.reasonText.contains("not as a risk signal"))
        assertTrue(modelEvidence.isPassive)
        assertEquals(EvidenceProvenance.BLE_ADVERTISEMENT, modelEvidence.provenance)
    }

    @Test
    fun `raw service data uuid is considered as service evidence`() {
        val evidence =
            DeviceEvidenceFactory.build(
                device(
                    mac = "AA:BB:CC:11:22:33",
                    rawData = FLAGS_AD + MOTOROLA_SERVICE_DATA_AD,
                ),
            )

        val serviceEvidence = evidence.single { it.source == EvidenceSource.SERVICE_UUID }

        assertEquals(DetectionConfidence.HIGH, serviceEvidence.confidence)
        assertTrue(serviceEvidence.rawValue.orEmpty().contains("fd8e"))
        assertTrue(serviceEvidence.reasonText.contains("Motorola"))
        assertEquals(EvidenceProvenance.BLE_ADVERTISEMENT, serviceEvidence.provenance)
    }

    @Test
    fun `watchlist evidence can be critical only from watchlist source`() {
        val evidence =
            DeviceEvidenceFactory.build(
                device(
                    mac = "AA:BB:CC:11:22:33",
                    name = "Known headphones",
                ).copy(
                    isInWatchlist = true,
                    userAlias = "My headphones",
                ),
            )

        val criticalEvidence = evidence.filter { it.confidence == DetectionConfidence.CRITICAL }

        assertEquals(1, criticalEvidence.size)
        assertEquals(EvidenceSource.WATCHLIST, criticalEvidence.single().source)
        assertEquals(EvidenceProvenance.USER_ACTION, criticalEvidence.single().provenance)
    }

    @Test
    fun `random mac carryover is visible as low confidence identity evidence`() {
        val evidence =
            DeviceEvidenceFactory.build(
                device(mac = "7A:BB:CC:11:22:33").copy(
                    fingerprint = "AA:BB:CC:11:22:33",
                    macAddressType = MacAddressType.RANDOM,
                    carryoverReasonCode = "WEIGHTED_FEATURE_MATCH",
                    carryoverConfidence = 0.86f,
                    carryoverFeatures = "scorePct=86;payloadPct=92;rssiDiff=4",
                ),
            )

        val carryoverEvidence = evidence.single { it.source == EvidenceSource.IDENTITY_CARRYOVER }

        assertEquals(DetectionConfidence.LOW, carryoverEvidence.confidence)
        assertEquals("7A:BB:CC:11:22:33", carryoverEvidence.rawValue)
        assertEquals(
            "AA:BB:CC:11:22:33;reason=WEIGHTED_FEATURE_MATCH;confidence=86%",
            carryoverEvidence.parsedValue,
        )
        assertTrue(carryoverEvidence.isPassive)
        assertEquals(EvidenceProvenance.FOLLOW_ME_ANALYSIS, carryoverEvidence.provenance)
        assertTrue(carryoverEvidence.reasonText.contains("weighted BLE feature match"))
        assertTrue(carryoverEvidence.reasonText.contains("scorePct=86"))
        assertTrue(carryoverEvidence.reasonText.contains("identity continuity context"))
        assertTrue(carryoverEvidence.reasonText.contains("not as a risk signal"))
    }

    @Test
    fun `public mac mismatch does not create carryover evidence`() {
        val evidence =
            DeviceEvidenceFactory.build(
                device(mac = "AA:BB:CC:11:22:33").copy(
                    fingerprint = "stable-record",
                    macAddressType = MacAddressType.PUBLIC,
                ),
            )

        assertTrue(evidence.none { it.source == EvidenceSource.IDENTITY_CARRYOVER })
    }

    @Test
    fun `false carryover verdict is visible in identity evidence`() {
        val evidence =
            DeviceEvidenceFactory.build(
                device(mac = "7A:BB:CC:11:22:33").copy(
                    fingerprint = "AA:BB:CC:11:22:33",
                    macAddressType = MacAddressType.RANDOM,
                    carryoverReasonCode = "WEIGHTED_FEATURE_MATCH",
                    carryoverConfidence = 0.86f,
                    identityCarryoverVerdict = IdentityCarryoverVerdict.FALSE_MATCH,
                ),
            )

        val carryoverEvidence = evidence.single { it.source == EvidenceSource.IDENTITY_CARRYOVER }

        assertEquals(
            "AA:BB:CC:11:22:33;reason=WEIGHTED_FEATURE_MATCH;confidence=86%;verdict=FALSE_MATCH",
            carryoverEvidence.parsedValue,
        )
        assertTrue(carryoverEvidence.reasonText.contains("userVerdict=false match"))
        assertTrue(carryoverEvidence.reasonText.contains("do not trust merged history"))
        assertTrue(carryoverEvidence.reasonText.contains("not as a risk signal"))
    }

    @Test
    fun `inconclusive carryover verdict asks for more observations in identity evidence`() {
        val evidence =
            DeviceEvidenceFactory.build(
                device(mac = "7A:BB:CC:11:22:33").copy(
                    fingerprint = "AA:BB:CC:11:22:33",
                    macAddressType = MacAddressType.RANDOM,
                    carryoverReasonCode = "NAME_AND_RSSI_MATCH",
                    identityCarryoverVerdict = IdentityCarryoverVerdict.INCONCLUSIVE,
                ),
            )

        val carryoverEvidence = evidence.single { it.source == EvidenceSource.IDENTITY_CARRYOVER }

        assertEquals(
            "AA:BB:CC:11:22:33;reason=NAME_AND_RSSI_MATCH;verdict=INCONCLUSIVE",
            carryoverEvidence.parsedValue,
        )
        assertTrue(carryoverEvidence.reasonText.contains("userVerdict=inconclusive"))
        assertTrue(carryoverEvidence.reasonText.contains("needs more observations"))
    }

    @Test
    fun `watchlist return alert evidence uses persisted offline duration`() {
        val evidence =
            DeviceEvidenceFactory.build(
                device(
                    mac = "AA:BB:CC:11:22:33",
                    name = "Known headphones",
                ).copy(
                    isInWatchlist = true,
                    userAlias = "My headphones",
                    lastWatchlistReturnAlertAt = TIMESTAMP + ALERT_OFFSET_MS,
                    lastWatchlistReturnOfflineDurationMs = OFFLINE_DURATION_MS,
                ),
            )

        val watchlistEvidence = evidence.single { it.source == EvidenceSource.WATCHLIST }

        assertEquals(DetectionConfidence.CRITICAL, watchlistEvidence.confidence)
        assertEquals(TIMESTAMP + ALERT_OFFSET_MS, watchlistEvidence.timestamp)
        assertEquals("My headphones", watchlistEvidence.parsedValue)
        assertTrue(watchlistEvidence.reasonText.contains("returned after 120s offline"))
        assertEquals(EvidenceProvenance.BLE_ADVERTISEMENT, watchlistEvidence.provenance)
    }

    @Test
    fun `suspicious calibration label creates user confirmation evidence`() {
        val evidence =
            DeviceEvidenceFactory.build(
                device(mac = "AA:BB:CC:11:22:33").copy(
                    calibrationLabel = DeviceCalibrationLabel.SUSPICIOUS,
                ),
            )

        val userEvidence = evidence.single { it.source == EvidenceSource.USER_CONFIRMATION }

        assertEquals(DetectionConfidence.MEDIUM, userEvidence.confidence)
        assertEquals(DeviceCalibrationLabel.SUSPICIOUS.name, userEvidence.rawValue)
        assertEquals("Suspicious", userEvidence.parsedValue)
        assertTrue(userEvidence.reasonText.contains("User marked this device as suspicious"))
        assertEquals(EvidenceProvenance.USER_ACTION, userEvidence.provenance)
    }

    @Test
    fun `known safe calibration label stays low confidence user confirmation evidence`() {
        val evidence =
            DeviceEvidenceFactory.build(
                device(mac = "AA:BB:CC:11:22:33").copy(
                    calibrationLabel = DeviceCalibrationLabel.KNOWN_SAFE,
                ),
            )

        val userEvidence = evidence.single { it.source == EvidenceSource.USER_CONFIRMATION }

        assertEquals(DetectionConfidence.LOW, userEvidence.confidence)
        assertEquals(DeviceCalibrationLabel.KNOWN_SAFE.name, userEvidence.rawValue)
        assertEquals("Known safe", userEvidence.parsedValue)
        assertTrue(userEvidence.reasonText.contains("Follow-Me scoring are suppressed"))
        assertEquals(EvidenceProvenance.USER_ACTION, userEvidence.provenance)
    }

    @Test
    fun `unknown calibration label does not create user confirmation evidence`() {
        val evidence =
            DeviceEvidenceFactory.build(
                device(mac = "AA:BB:CC:11:22:33").copy(
                    calibrationLabel = DeviceCalibrationLabel.UNKNOWN,
                ),
            )

        assertTrue(evidence.none { it.source == EvidenceSource.USER_CONFIRMATION })
    }

    @Test
    fun `tracking evidence keeps follow me score separate from rssi pattern`() {
        val evidence =
            DeviceEvidenceFactory.build(
                device(mac = "AA:BB:CC:11:22:33").copy(
                    trackingStatus = TrackingStatus.SUSPICIOUS,
                    followingScore = 55f,
                    followMeExplanation =
                        "Seen for 12min while moving, " +
                            "RSSI stayed stable during movement window",
                    lastRssi = -61,
                    followMeDurationScore = 25,
                    followMeRssiStabilityScore = 18,
                    followMeEncounterScore = 4,
                    followMeUserMoved = true,
                    followMeBaselineDevice = false,
                ),
            )

        val trackingEvidence =
            evidence.single {
                it.source == EvidenceSource.FOLLOW_ME_SCORE &&
                    it.parsedValue == TrackingStatus.SUSPICIOUS.name
            }
        val durationEvidence = evidence.single { it.parsedValue == "DURATION_WHILE_MOVING" }
        val encounterEvidence = evidence.single { it.parsedValue == "REPEATED_ENCOUNTERS" }
        val rssiEvidence = evidence.single { it.source == EvidenceSource.RSSI_PATTERN }

        assertEquals(DetectionConfidence.MEDIUM, trackingEvidence.confidence)
        assertEquals(EvidenceProvenance.FOLLOW_ME_ANALYSIS, trackingEvidence.provenance)
        assertTrue(trackingEvidence.reasonText.contains("55/100"))
        assertTrue(trackingEvidence.reasonText.contains("Seen for 12min while moving"))
        assertTrue(trackingEvidence.reasonText.contains("RSSI stayed stable during movement window"))
        assertEquals("25", durationEvidence.rawValue)
        assertEquals(EvidenceProvenance.FOLLOW_ME_ANALYSIS, durationEvidence.provenance)
        assertEquals("4", encounterEvidence.rawValue)
        assertEquals(EvidenceProvenance.FOLLOW_ME_ANALYSIS, encounterEvidence.provenance)
        assertEquals(DetectionConfidence.MEDIUM, rssiEvidence.confidence)
        assertEquals("score=18;lastRssi=-61", rssiEvidence.rawValue)
        assertEquals("STABLE_RSSI_WITH_MOVEMENT", rssiEvidence.parsedValue)
        assertEquals(EvidenceProvenance.FOLLOW_ME_ANALYSIS, rssiEvidence.provenance)
        assertTrue(rssiEvidence.reasonText.contains("noisy proximity evidence"))
        assertFalse(rssiEvidence.reasonText.contains("55/100"))
    }

    @Test
    fun `follow me evidence uses structured components instead of parsing explanation`() {
        val evidence =
            DeviceEvidenceFactory.build(
                device(mac = "AA:BB:CC:11:22:33").copy(
                    trackingStatus = TrackingStatus.SUSPICIOUS,
                    followingScore = 60f,
                    followMeExplanation =
                        "Seen for 12min while moving, " +
                            "RSSI stayed stable during movement window",
                    followMeMacBehaviorScore = 10,
                    followMeRssiStabilityScore = 0,
                ),
            )

        val trackingEvidence =
            evidence.single {
                it.source == EvidenceSource.FOLLOW_ME_SCORE &&
                    it.parsedValue == TrackingStatus.SUSPICIOUS.name
            }
        val macEvidence = evidence.single { it.parsedValue == "MAC_ROTATION_WITH_STABLE_PAYLOAD" }

        assertEquals(DetectionConfidence.MEDIUM, trackingEvidence.confidence)
        assertTrue(trackingEvidence.reasonText.contains("RSSI stayed stable during movement window"))
        assertEquals("10", macEvidence.rawValue)
        assertEquals(EvidenceProvenance.FOLLOW_ME_ANALYSIS, macEvidence.provenance)
        assertTrue(evidence.none { it.source == EvidenceSource.RSSI_PATTERN })
    }

    @Test
    fun `movement and baseline suppression are structured follow me evidence`() {
        val evidence =
            DeviceEvidenceFactory.build(
                device(mac = "AA:BB:CC:11:22:33").copy(
                    trackingStatus = TrackingStatus.SAFE,
                    followingScore = 20f,
                    followMeExplanation = "Baseline device seen before movement - follow-me score suppressed",
                    followMeUserMoved = false,
                    followMeBaselineDevice = true,
                ),
            )

        val movementEvidence = evidence.single { it.parsedValue == "MOVEMENT_NOT_DETECTED" }
        val baselineEvidence = evidence.single { it.parsedValue == "BASELINE_DEVICE" }

        assertEquals(DetectionConfidence.LOW, movementEvidence.confidence)
        assertEquals("false", movementEvidence.rawValue)
        assertEquals(EvidenceProvenance.FOLLOW_ME_ANALYSIS, movementEvidence.provenance)
        assertEquals(DetectionConfidence.LOW, baselineEvidence.confidence)
        assertEquals("true", baselineEvidence.rawValue)
        assertEquals(EvidenceProvenance.FOLLOW_ME_ANALYSIS, baselineEvidence.provenance)
    }

    @Test
    fun `unavailable movement source is not reported as no movement detected`() {
        val evidence =
            DeviceEvidenceFactory.build(
                device(mac = "AA:BB:CC:11:22:33").copy(
                    trackingStatus = TrackingStatus.SAFE,
                    followingScore = 20f,
                    followMeExplanation = "Movement source unavailable - follow-me score suppressed",
                    followMeUserMoved = false,
                    followMeBaselineDevice = false,
                ),
            )

        val movementEvidence = evidence.single { it.parsedValue == "MOVEMENT_SOURCE_UNAVAILABLE" }

        assertEquals(DetectionConfidence.LOW, movementEvidence.confidence)
        assertEquals("false", movementEvidence.rawValue)
        assertEquals(EvidenceProvenance.FOLLOW_ME_ANALYSIS, movementEvidence.provenance)
        assertTrue(movementEvidence.reasonText.contains("Movement source was unavailable"))
        assertTrue(evidence.none { it.parsedValue == "MOVEMENT_NOT_DETECTED" })
    }

    @Test
    fun `known tracker type evidence is medium confidence and neutral`() {
        val evidence =
            DeviceEvidenceFactory.build(
                device(
                    mac = "AA:BB:CC:11:22:33",
                    name = "AirTag",
                ).copy(
                    deviceType = DeviceType.TRACKER,
                ),
            )

        val trackerEvidence = evidence.single { it.source == EvidenceSource.NAME }

        assertEquals(DetectionConfidence.MEDIUM, trackerEvidence.confidence)
        assertEquals("AirTag", trackerEvidence.rawValue)
        assertEquals(DeviceType.TRACKER.name, trackerEvidence.parsedValue)
        assertTrue(trackerEvidence.reasonText.contains("consistent with a Bluetooth tracker accessory"))
        assertTrue(trackerEvidence.reasonText.contains("review movement history"))
        assertEquals(EvidenceProvenance.BLE_ADVERTISEMENT, trackerEvidence.provenance)
    }

    @Test
    fun `tracker beacon evidence wins over misleading bluetooth name`() {
        val evidence =
            DeviceEvidenceFactory.build(
                device(
                    mac = "AA:BB:CC:11:22:33",
                    name = "Apple Watch",
                ).copy(
                    deviceType = DeviceType.WEARABLE,
                    beaconType = "Apple FindMy / AirTag",
                ),
            )

        val trackerEvidence = evidence.single { it.source == EvidenceSource.RAW_PAYLOAD }

        assertEquals(DetectionConfidence.MEDIUM, trackerEvidence.confidence)
        assertEquals("Apple FindMy / AirTag", trackerEvidence.rawValue)
        assertEquals(DeviceType.AIRTAG.name, trackerEvidence.parsedValue)
        assertTrue(trackerEvidence.reasonText.contains("consistent with a Bluetooth tracker accessory"))
        assertTrue(evidence.none { it.source == EvidenceSource.NAME })
    }

    @Test
    fun `samsung tag payload does not override named tv identity`() {
        val evidence =
            DeviceEvidenceFactory.build(
                device(
                    mac = "AA:BB:CC:11:22:33",
                    name = "[TV] Samsung Q60 Series (65)",
                ).copy(
                    deviceType = DeviceType.TV,
                    beaconType = "Samsung SmartTag",
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

    @Test
    fun `named samsung tv is not tracker evidence even when stale type is tag`() {
        val evidence =
            DeviceEvidenceFactory.build(
                device(
                    mac = "AA:BB:CC:11:22:33",
                    name = "[TV] Samsung 5 Series (32)",
                ).copy(
                    deviceType = DeviceType.TAG,
                    beaconType = "Samsung SmartTag",
                ),
            )

        assertTrue(
            evidence.none {
                it.confidence == DetectionConfidence.MEDIUM &&
                    it.reasonText.contains("Bluetooth tracker accessory")
            },
        )
    }

    @Test
    fun `domain device exposes deterministic evidence`() {
        val domainDevice =
            device(mac = "00:25:DF:11:22:33")
                .toDomain()

        assertTrue(domainDevice.evidence.any { it.source == EvidenceSource.OUI })
    }

    private fun device(
        mac: String,
        name: String? = null,
        manufacturerId: Int? = null,
        services: String? = null,
        rawData: String? = null,
    ): DeviceEntity =
        DeviceEntity(
            fingerprint = mac,
            lastMacAddress = mac,
            macAddressType = MacAddressType.PUBLIC,
            technology = "BLE",
            lastDeviceName = name,
            vendorName = null,
            manufacturerId = manufacturerId,
            firstSeenAt = TIMESTAMP,
            lastSeenAt = TIMESTAMP,
            encounterCount = 1,
            gattServices = services,
            lastRawData = rawData,
        )

    private companion object {
        const val TIMESTAMP = 1_789_000_000_000L
        const val APPLE_COMPANY_ID = 0x004C
        const val CAMBRIDGE_SILICON_RADIO_ID = 0x000A
        const val FLAGS_AD = "020106"
        const val APPLE_MANUFACTURER_AD = "05FF4C000102"
        const val AXON_MANUFACTURER_AD = "04FF4D0301"
        const val MOTOROLA_SERVICE_DATA_AD = "04168EFD01"
        const val ALERT_OFFSET_MS = 10_000L
        const val OFFLINE_DURATION_MS = 120_000L
        const val LE_AUDIO_INPUT_CONTROL_SERVICE = "00001843-0000-1000-8000-00805f9b34fb"
        const val CLASSIC_AUDIO_SERVICES =
            "0000110b-0000-1000-8000-00805f9b34fb, 0000110e-0000-1000-8000-00805f9b34fb"
    }
}
