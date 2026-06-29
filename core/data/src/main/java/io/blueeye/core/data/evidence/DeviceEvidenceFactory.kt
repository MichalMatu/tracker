package io.blueeye.core.data.evidence

import io.blueeye.core.alert.TacticalEvidenceFactory
import io.blueeye.core.data.classifier.ble.ServiceUuidClassifier
import io.blueeye.core.data.classifier.pipeline.NameClassifier
import io.blueeye.core.data.classifier.vendor.TacticalOuiRegistry
import io.blueeye.core.data.classifier.vendor.tactical.ConfidenceLevel
import io.blueeye.core.data.classifier.vendor.tactical.TacticalNameMatcher
import io.blueeye.core.data.db.entity.DeviceEntity
import io.blueeye.core.model.DetectionConfidence
import io.blueeye.core.model.DetectionEvidence
import io.blueeye.core.model.DeviceType
import io.blueeye.core.model.EvidenceProvenance
import io.blueeye.core.model.EvidenceSource
import io.blueeye.core.model.TrackingStatus

object DeviceEvidenceFactory {
    fun build(device: DeviceEntity): List<DetectionEvidence> {
        val evidence = mutableListOf<DetectionEvidence>()
        val advertisementEvidence = AdvertisementEvidenceParser.parse(device.lastRawData)

        addWatchlistEvidence(device, evidence)
        UserConfirmationEvidenceFactory.build(device)?.let(evidence::add)
        IdentityCarryoverEvidenceFactory.build(device)?.let(evidence::add)
        device.knownTrackerEvidence()?.let(evidence::add)
        addNameEvidence(device, evidence)
        ModelEvidenceFactory.build(device)?.let(evidence::add)
        AppearanceEvidenceFactory.build(device, advertisementEvidence)?.let(evidence::add)
        ClassOfDeviceEvidenceFactory.build(device)?.let(evidence::add)
        addOuiEvidence(device, evidence)
        addManufacturerEvidence(device, advertisementEvidence, evidence)
        addServiceUuidEvidence(device, advertisementEvidence, evidence)
        addRawPayloadEvidence(device, evidence)
        addTrackingEvidence(device, evidence)
        addProbeEvidence(device, evidence)

        return evidence.sortedWith(
            compareByDescending<DetectionEvidence> { it.confidence.priority }
                .thenBy { it.source.name }
                .thenBy { it.provenance.name }
                .thenBy { it.rawValue.orEmpty() },
        )
    }

    private fun addWatchlistEvidence(
        device: DeviceEntity,
        evidence: MutableList<DetectionEvidence>,
    ) {
        if (!device.isInWatchlist) return

        if (device.lastWatchlistReturnAlertAt > 0L && device.lastWatchlistReturnOfflineDurationMs > 0L) {
            evidence +=
                DetectionEvidence(
                    source = EvidenceSource.WATCHLIST,
                    confidence = DetectionConfidence.CRITICAL,
                    reasonText =
                        "User-selected watchlist device returned after " +
                            "${device.lastWatchlistReturnOfflineDurationMs / MILLIS_PER_SECOND}s offline.",
                    timestamp = device.lastWatchlistReturnAlertAt,
                    rawValue = device.fingerprint,
                    parsedValue = device.userAlias ?: device.lastDeviceName,
                    isPassive = true,
                    provenance = EvidenceProvenance.BLE_ADVERTISEMENT,
                )
            return
        }

        evidence +=
            DetectionEvidence(
                source = EvidenceSource.WATCHLIST,
                confidence = DetectionConfidence.CRITICAL,
                reasonText = "User watchlist match: this device was selected for alerts.",
                timestamp = device.lastSeenAt,
                rawValue = device.fingerprint,
                parsedValue = device.userAlias ?: device.lastDeviceName,
                isPassive = true,
                provenance = EvidenceProvenance.USER_ACTION,
            )
    }

    private fun addNameEvidence(
        device: DeviceEntity,
        evidence: MutableList<DetectionEvidence>,
    ) {
        val name =
            device.lastDeviceName
                ?.takeIf { it.isNotBlank() }
                ?.takeUnless { device.hasKnownTrackerSignal() }
                ?: return
        val tacticalNameMatch = TacticalNameMatcher.match(name)
        val nameDeviceType = NameClassifier.classify(name)

        val nameEvidence =
            when {
                tacticalNameMatch != null ->
                    TacticalEvidenceFactory.buildName(
                        match = tacticalNameMatch,
                        rawValue = name,
                        timestamp = device.lastSeenAt,
                    )
                nameDeviceType != DeviceType.UNKNOWN ->
                    DetectionEvidence(
                        source = EvidenceSource.NAME,
                        confidence = DetectionConfidence.LOW,
                        reasonText =
                            "Advertised Bluetooth name is consistent with ${nameDeviceType.name}. " +
                                "Names can be spoofed, so use this as classification context, not as a risk signal.",
                        timestamp = device.lastSeenAt,
                        rawValue = name,
                        parsedValue = nameDeviceType.name,
                        isPassive = true,
                        provenance = EvidenceProvenance.BLE_ADVERTISEMENT,
                    )
                else ->
                    DetectionEvidence(
                        source = EvidenceSource.NAME,
                        confidence = DetectionConfidence.LOW,
                        reasonText = "Device advertised a Bluetooth name.",
                        timestamp = device.lastSeenAt,
                        rawValue = name,
                        parsedValue = name,
                        isPassive = true,
                        provenance = EvidenceProvenance.BLE_ADVERTISEMENT,
                    )
            }

        evidence += nameEvidence
    }

    private fun addOuiEvidence(
        device: DeviceEntity,
        evidence: MutableList<DetectionEvidence>,
    ) {
        val mac = device.lastMacAddress ?: return
        val tacticalOui = TacticalOuiRegistry.lookup(mac) ?: return

        evidence +=
            DetectionEvidence(
                source = EvidenceSource.OUI,
                confidence = tacticalOui.confidence.toDetectionConfidence(),
                reasonText =
                    "MAC OUI is consistent with ${tacticalOui.vendorName}: " +
                        "${tacticalOui.description}.",
                timestamp = device.lastSeenAt,
                rawValue = mac,
                parsedValue = "${tacticalOui.category.name}: ${tacticalOui.description}",
                isPassive = true,
                provenance = EvidenceProvenance.DEVICE_REGISTRY,
            )
    }

    private fun addManufacturerEvidence(
        device: DeviceEntity,
        advertisementEvidence: AdvertisementEvidenceParser.AdvertisementEvidence,
        evidence: MutableList<DetectionEvidence>,
    ) {
        val manufacturerIds = buildList {
            device.manufacturerId?.let(::add)
            addAll(advertisementEvidence.manufacturerIds)
        }.distinct().sorted()

        manufacturerIds.forEach { manufacturerId ->
            val match = TacticalOuiRegistry.matchByManufacturerId(manufacturerId) ?: return@forEach

            evidence +=
                DetectionEvidence(
                    source = EvidenceSource.MANUFACTURER_ID,
                    confidence = DetectionConfidence.HIGH,
                    reasonText =
                        "Manufacturer ID ${manufacturerId.toHexCompanyId()} is consistent with " +
                            "${match.second}.",
                    timestamp = device.lastSeenAt,
                    rawValue = manufacturerId.toHexCompanyId(),
                    parsedValue = match.first.name,
                    isPassive = true,
                    provenance = EvidenceProvenance.BLE_ADVERTISEMENT,
                )
        }
    }

    private fun addServiceUuidEvidence(
        device: DeviceEntity,
        advertisementEvidence: AdvertisementEvidenceParser.AdvertisementEvidence,
        evidence: MutableList<DetectionEvidence>,
    ) {
        val observations =
            ServiceUuidEvidenceParser.observations(
                device = device,
                advertisementUuids = advertisementEvidence.serviceUuids,
            )
        if (observations.isEmpty()) return

        observations
            .groupBy { observation -> observation.provenance }
            .toSortedMap(compareBy { provenance -> provenance.name })
            .forEach { (provenance, provenanceObservations) ->
                addServiceUuidEvidenceForProvenance(
                    serviceUuids = provenanceObservations.map { observation -> observation.uuid }
                        .distinct()
                        .sorted(),
                    provenance = provenance,
                    device = device,
                    evidence = evidence,
                )
            }
    }

    private fun addRawPayloadEvidence(
        device: DeviceEntity,
        evidence: MutableList<DetectionEvidence>,
    ) {
        if (device.hasKnownTrackerSignal()) return

        val parsedPayload = device.beaconType?.takeIf { it.isNotBlank() } ?: return

        evidence +=
            DetectionEvidence(
                source = EvidenceSource.RAW_PAYLOAD,
                confidence = DetectionConfidence.LOW,
                reasonText = "Advertisement payload was decoded.",
                timestamp = device.lastSeenAt,
                rawValue = device.lastRawData,
                parsedValue = parsedPayload,
                isPassive = true,
                provenance = EvidenceProvenance.BLE_ADVERTISEMENT,
            )
    }

    private fun addTrackingEvidence(
        device: DeviceEntity,
        evidence: MutableList<DetectionEvidence>,
    ) {
        if (device.trackingStatus == TrackingStatus.SAFE && device.followingScore <= 0f) return

        val explanation = device.followMeExplanation?.takeIf { it.isNotBlank() }
        val reason = if (explanation == null) {
            "Follow-Me score is ${device.followingScore.toInt()}/100 with status ${device.trackingStatus.name}."
        } else {
            "Follow-Me score is ${device.followingScore.toInt()}/100: $explanation."
        }
        val followMeConfidence =
            when (device.trackingStatus) {
                TrackingStatus.SAFE -> DetectionConfidence.LOW
                TrackingStatus.SUSPICIOUS -> DetectionConfidence.MEDIUM
                TrackingStatus.DANGEROUS -> DetectionConfidence.HIGH
            }

        evidence +=
            DetectionEvidence(
                source = EvidenceSource.FOLLOW_ME_SCORE,
                confidence = followMeConfidence,
                reasonText = reason,
                timestamp = device.lastSeenAt,
                rawValue = device.followingScore.toString(),
                parsedValue = device.trackingStatus.name,
                isPassive = true,
                provenance = EvidenceProvenance.FOLLOW_ME_ANALYSIS,
            )

        device.movementSuppressionEvidence(explanation)?.let(evidence::add)

        if (device.followMeBaselineDevice == true) {
            evidence +=
                DetectionEvidence(
                    source = EvidenceSource.FOLLOW_ME_SCORE,
                    confidence = DetectionConfidence.LOW,
                    reasonText = "Device was seen before movement, so baseline suppression reduced Follow-Me scoring.",
                    timestamp = device.lastSeenAt,
                    rawValue = true.toString(),
                    parsedValue = "BASELINE_DEVICE",
                    isPassive = true,
                    provenance = EvidenceProvenance.FOLLOW_ME_ANALYSIS,
                )
        }

        if (device.followMeDurationScore > 0) {
            evidence +=
                DetectionEvidence(
                    source = EvidenceSource.FOLLOW_ME_SCORE,
                    confidence = followMeConfidence,
                    reasonText = "Observation duration while moving contributed to the Follow-Me score.",
                    timestamp = device.lastSeenAt,
                    rawValue = device.followMeDurationScore.toString(),
                    parsedValue = "DURATION_WHILE_MOVING",
                    isPassive = true,
                    provenance = EvidenceProvenance.FOLLOW_ME_ANALYSIS,
                )
        }

        if (device.followMeMacBehaviorScore > 0) {
            evidence +=
                DetectionEvidence(
                    source = EvidenceSource.FOLLOW_ME_SCORE,
                    confidence = followMeConfidence,
                    reasonText = "MAC rotation correlated with stable payload contributed to the Follow-Me score.",
                    timestamp = device.lastSeenAt,
                    rawValue = device.followMeMacBehaviorScore.toString(),
                    parsedValue = "MAC_ROTATION_WITH_STABLE_PAYLOAD",
                    isPassive = true,
                    provenance = EvidenceProvenance.FOLLOW_ME_ANALYSIS,
                )
        }

        if (device.followMeEncounterScore > 0) {
            evidence +=
                DetectionEvidence(
                    source = EvidenceSource.FOLLOW_ME_SCORE,
                    confidence = followMeConfidence,
                    reasonText = "Repeated encounters contributed to the Follow-Me score.",
                    timestamp = device.lastSeenAt,
                    rawValue = device.followMeEncounterScore.toString(),
                    parsedValue = "REPEATED_ENCOUNTERS",
                    isPassive = true,
                    provenance = EvidenceProvenance.FOLLOW_ME_ANALYSIS,
                )
        }

        if (device.followMeRssiStabilityScore > 0) {
            evidence +=
                DetectionEvidence(
                    source = EvidenceSource.RSSI_PATTERN,
                    confidence =
                        if (device.trackingStatus == TrackingStatus.SAFE) {
                            DetectionConfidence.LOW
                        } else {
                            DetectionConfidence.MEDIUM
                        },
                    reasonText =
                        "RSSI stability contributed to the Follow-Me score. " +
                            "Treat this as noisy proximity evidence, not distance.",
                    timestamp = device.lastSeenAt,
                    rawValue = "score=${device.followMeRssiStabilityScore};lastRssi=${device.lastRssi}",
                    parsedValue = "STABLE_RSSI_WITH_MOVEMENT",
                    isPassive = true,
                    provenance = EvidenceProvenance.FOLLOW_ME_ANALYSIS,
                )
        }
    }

    private fun addProbeEvidence(
        device: DeviceEntity,
        evidence: MutableList<DetectionEvidence>,
    ) {
        when (device.connectionStatus) {
            "PROBED" ->
                evidence += activeProbeEvidence(
                    device = device,
                    source = EvidenceSource.GATT_PROBE,
                    reason = "Active GATT probe returned device information.",
                    provenance = EvidenceProvenance.ACTIVE_GATT,
                )
            "RFCOMM_OK", "RFCOMM_FAIL", "FAILED", "FAILED_PERMANENT" ->
                evidence += activeProbeEvidence(
                    device = device,
                    source = EvidenceSource.RFCOMM_PROBE,
                    reason = "Active RFCOMM probe changed connection status.",
                    provenance = EvidenceProvenance.ACTIVE_RFCOMM,
                )
        }
    }

    private fun activeProbeEvidence(
        device: DeviceEntity,
        source: EvidenceSource,
        reason: String,
        provenance: EvidenceProvenance,
    ): DetectionEvidence =
        DetectionEvidence(
            source = source,
            confidence = DetectionConfidence.MEDIUM,
            reasonText = reason,
            timestamp = device.lastProbeTimestamp.takeIf { it > 0L } ?: device.lastSeenAt,
            rawValue = device.connectionStatus,
            parsedValue = listOfNotNull(
                device.manufacturerName,
                device.modelNumber,
                device.batteryLevel?.let { "$it%" },
            )
                .joinToString(" ")
                .ifBlank { null },
            isPassive = false,
            provenance = provenance,
        )

    private const val MILLIS_PER_SECOND = 1_000L
}

private fun DeviceEntity.movementSuppressionEvidence(explanation: String?): DetectionEvidence? {
    if (followMeUserMoved != false) return null

    val movementSourceUnavailable =
        explanation?.contains("Movement source unavailable", ignoreCase = true) == true

    return DetectionEvidence(
        source = EvidenceSource.FOLLOW_ME_SCORE,
        confidence = DetectionConfidence.LOW,
        reasonText =
            if (movementSourceUnavailable) {
                "Movement source was unavailable, so movement-based Follow-Me components were suppressed."
            } else {
                "Movement was not detected, so movement-based Follow-Me components were suppressed."
            },
        timestamp = lastSeenAt,
        rawValue = false.toString(),
        parsedValue =
            if (movementSourceUnavailable) {
                "MOVEMENT_SOURCE_UNAVAILABLE"
            } else {
                "MOVEMENT_NOT_DETECTED"
            },
        isPassive = true,
        provenance = EvidenceProvenance.FOLLOW_ME_ANALYSIS,
    )
}

private fun ConfidenceLevel.toDetectionConfidence(): DetectionConfidence =
    when (this) {
        ConfidenceLevel.CRITICAL -> DetectionConfidence.HIGH
        ConfidenceLevel.HIGH -> DetectionConfidence.HIGH
        ConfidenceLevel.MEDIUM -> DetectionConfidence.MEDIUM
    }

private fun addServiceUuidEvidenceForProvenance(
    serviceUuids: List<String>,
    provenance: EvidenceProvenance,
    device: DeviceEntity,
    evidence: MutableList<DetectionEvidence>,
) {
    val match = TacticalOuiRegistry.matchByServiceUuid(serviceUuids)
    if (match != null) {
        evidence +=
            DetectionEvidence(
                source = EvidenceSource.SERVICE_UUID,
                confidence = DetectionConfidence.HIGH,
                reasonText =
                    "Service UUID observed via ${provenance.reasonText} is consistent with " +
                        "${match.second}.",
                timestamp = device.lastSeenAt,
                rawValue = serviceUuids.joinToString(","),
                parsedValue = match.first.name,
                isPassive = provenance.isPassiveEvidence,
                provenance = provenance,
            )
        return
    }

    val classification = ServiceUuidClassifier.classify(serviceUuids)
    if (classification.deviceType != DeviceType.UNKNOWN) {
        evidence +=
            DetectionEvidence(
                source = EvidenceSource.SERVICE_UUID,
                confidence = DetectionConfidence.MEDIUM,
                reasonText =
                    "Bluetooth service UUIDs observed via ${provenance.reasonText} are consistent with " +
                        "${classification.serviceName ?: classification.deviceType.name}. " +
                        "Use this as classification context, not as a risk signal.",
                timestamp = device.lastSeenAt,
                rawValue = serviceUuids.joinToString(","),
                parsedValue = classification.deviceType.name,
                isPassive = provenance.isPassiveEvidence,
                provenance = provenance,
            )
        return
    }

    evidence +=
        DetectionEvidence(
            source = EvidenceSource.SERVICE_UUID,
            confidence = DetectionConfidence.LOW,
            reasonText =
                "Bluetooth service UUIDs were observed via ${provenance.reasonText}. " +
                    "Use this as identity context, not as a risk signal.",
            timestamp = device.lastSeenAt,
            rawValue = serviceUuids.joinToString(","),
            parsedValue = ServiceUuidEvidenceParser.countText(serviceUuids.size),
            isPassive = provenance.isPassiveEvidence,
            provenance = provenance,
        )
}

private fun Int.toHexCompanyId(): String = "0x%04X".format(this)

private data class ServiceUuidObservation(
    val uuid: String,
    val provenance: EvidenceProvenance,
)

private object ServiceUuidEvidenceParser {
    fun observations(
        device: DeviceEntity,
        advertisementUuids: List<String>,
    ): List<ServiceUuidObservation> {
        val advertisementObservations =
            advertisementUuids
                .map { uuid -> uuid.toFullBluetoothUuid() }
                .distinct()
                .map { uuid ->
                    ServiceUuidObservation(
                        uuid = uuid,
                        provenance = EvidenceProvenance.BLE_ADVERTISEMENT,
                    )
                }
        val advertisedUuidSet = advertisementObservations.map { observation -> observation.uuid }.toSet()

        return (advertisementObservations + parsePersisted(device, advertisedUuidSet))
            .distinctBy { observation -> observation.uuid to observation.provenance }
    }

    private fun parsePersisted(
        device: DeviceEntity,
        advertisedUuidSet: Set<String>,
    ): List<ServiceUuidObservation> {
        val services = device.gattServices?.takeIf { it.isNotBlank() }
        return when {
            services == null -> emptyList()
            services.contains(structuredServiceMarker) -> parseStructuredServices(services)
            else ->
                services.split(uuidListSeparator)
                    .map { entry -> entry.trim() }
                    .filter { entry -> entry.isUuidLike() }
                    .map { entry -> entry.toFullBluetoothUuid() }
                    .distinct()
                    .map { uuid ->
                        ServiceUuidObservation(
                            uuid = uuid,
                            provenance = device.persistedUuidProvenance(uuid, advertisedUuidSet),
                        )
                    }
        }
    }

    private fun parseStructuredServices(services: String): List<ServiceUuidObservation> =
        services.split(structuredServiceSeparator)
            .map { entry -> entry.substringBefore(serviceCharacteristicSeparator).trim() }
            .filter { serviceUuid -> serviceUuid.isUuidLike() }
            .map { serviceUuid ->
                ServiceUuidObservation(
                    uuid = serviceUuid.toFullBluetoothUuid(),
                    provenance = EvidenceProvenance.ACTIVE_GATT,
                )
            }
            .distinctBy { observation -> observation.uuid }

    private fun DeviceEntity.persistedUuidProvenance(
        uuid: String,
        advertisedUuidSet: Set<String>,
    ): EvidenceProvenance =
        when {
            uuid in advertisedUuidSet -> EvidenceProvenance.BLE_ADVERTISEMENT
            technology.contains(CLASSIC_TECHNOLOGY, ignoreCase = true) -> EvidenceProvenance.CLASSIC_SDP
            else -> EvidenceProvenance.BLE_ADVERTISEMENT
        }

    fun countText(count: Int): String =
        if (count == 1) {
            "1 service UUID observed"
        } else {
            "$count service UUIDs observed"
        }

    private fun String.isUuidLike(): Boolean =
        length == shortUuidHexLength ||
            length == shortServiceUuidHexLength ||
            contains(uuidGroupSeparator)

    private fun String.toFullBluetoothUuid(): String =
        when (length) {
            shortUuidHexLength -> "0000$this-0000-1000-8000-00805f9b34fb"
            shortServiceUuidHexLength -> "$this-0000-1000-8000-00805f9b34fb"
            else -> this
        }.lowercase()

    private const val CLASSIC_TECHNOLOGY = "CLASSIC"
    private const val structuredServiceSeparator = ";"
    private const val structuredServiceMarker = ":["
    private const val serviceCharacteristicSeparator = ":"
    private const val uuidListSeparator = ","
    private const val uuidGroupSeparator = "-"
    private const val shortUuidHexLength = 4
    private const val shortServiceUuidHexLength = 8
}

private val EvidenceProvenance.reasonText: String
    get() =
        when (this) {
            EvidenceProvenance.BLE_ADVERTISEMENT -> "BLE advertisement"
            EvidenceProvenance.CLASSIC_DISCOVERY -> "Classic Bluetooth discovery"
            EvidenceProvenance.CLASSIC_SDP -> "opportunistic Classic SDP discovery"
            EvidenceProvenance.ACTIVE_GATT -> "active GATT service discovery"
            EvidenceProvenance.ACTIVE_RFCOMM -> "active RFCOMM probe"
            EvidenceProvenance.USER_ACTION -> "user action"
            EvidenceProvenance.FOLLOW_ME_ANALYSIS -> "Follow-Me analysis"
            EvidenceProvenance.DEVICE_REGISTRY -> "device registry lookup"
            EvidenceProvenance.UNKNOWN -> "an unknown source"
        }

private val EvidenceProvenance.isPassiveEvidence: Boolean
    get() =
        when (this) {
            EvidenceProvenance.ACTIVE_GATT,
            EvidenceProvenance.ACTIVE_RFCOMM,
            -> false
            EvidenceProvenance.UNKNOWN,
            EvidenceProvenance.BLE_ADVERTISEMENT,
            EvidenceProvenance.CLASSIC_DISCOVERY,
            EvidenceProvenance.CLASSIC_SDP,
            EvidenceProvenance.USER_ACTION,
            EvidenceProvenance.FOLLOW_ME_ANALYSIS,
            EvidenceProvenance.DEVICE_REGISTRY,
            -> true
        }

private val DetectionConfidence.priority: Int
    get() =
        when (this) {
            DetectionConfidence.LOW -> 1
            DetectionConfidence.MEDIUM -> 2
            DetectionConfidence.HIGH -> 3
            DetectionConfidence.CRITICAL -> 4
        }

private fun DeviceEntity.knownTrackerEvidence(): DetectionEvidence? {
    if (!hasKnownTrackerSignal()) return null

    val name = lastDeviceName?.takeIf { it.isNotBlank() }
    val trackerPayload = beaconType?.takeIf { it.isKnownTrackerBeacon() }
    val source =
        when {
            trackerPayload != null -> EvidenceSource.RAW_PAYLOAD
            name != null -> EvidenceSource.NAME
            else -> EvidenceSource.RAW_PAYLOAD
        }

    return DetectionEvidence(
        source = source,
        confidence = DetectionConfidence.MEDIUM,
        reasonText =
            "Signal is consistent with a Bluetooth tracker accessory; " +
                "review movement history before acting.",
        timestamp = lastSeenAt,
        rawValue = trackerPayload ?: name ?: beaconType ?: lastRawData,
        parsedValue = trackerDeviceType().name,
        isPassive = true,
        provenance = EvidenceProvenance.BLE_ADVERTISEMENT,
    )
}

private fun DeviceEntity.hasKnownTrackerSignal(): Boolean {
    val trackerBeacon = beaconType
    val hasStrongNonTrackerIdentity =
        !lastDeviceName.isNullOrBlank() &&
            deviceType in STRONG_NON_TRACKER_DEVICE_TYPES
    val hasNonTrackerName = lastDeviceName.isKnownNonTrackerName()
    if (hasNonTrackerName) return false

    return isKnownTrackerType() ||
        (
            trackerBeacon != null &&
                trackerBeacon.isKnownTrackerBeacon() &&
                !hasStrongNonTrackerIdentity
        )
}

private fun DeviceEntity.isKnownTrackerType(): Boolean =
    deviceType in KNOWN_TRACKER_TYPES

private fun DeviceEntity.trackerDeviceType(): DeviceType {
    val trackerBeacon = beaconType.orEmpty()
    return when {
        isKnownTrackerType() -> deviceType
        AIRTAG_TRACKER_KEYWORDS.any { trackerBeacon.contains(it, ignoreCase = true) } -> DeviceType.AIRTAG
        TILE_TRACKER_KEYWORDS.any { trackerBeacon.contains(it, ignoreCase = true) } -> DeviceType.TILE
        SAMSUNG_TAG_TRACKER_KEYWORDS.any { trackerBeacon.contains(it, ignoreCase = true) } -> DeviceType.SAMSUNG_TAG
        else -> DeviceType.TRACKER
    }
}

private fun String?.isKnownTrackerBeacon(): Boolean =
    this != null && TRACKER_BEACON_KEYWORDS.any { keyword -> contains(keyword, ignoreCase = true) }

private fun String?.isKnownNonTrackerName(): Boolean {
    if (isNullOrBlank()) return false

    return NON_TRACKER_NAME_KEYWORDS.any { keyword -> contains(keyword, ignoreCase = true) }
}

private val KNOWN_TRACKER_TYPES =
    setOf(
        DeviceType.AIRTAG,
        DeviceType.TILE,
        DeviceType.SAMSUNG_TAG,
        DeviceType.TAG,
        DeviceType.TRACKER,
    )

private val STRONG_NON_TRACKER_DEVICE_TYPES =
    setOf(
        DeviceType.CAR_AUDIO,
        DeviceType.HEADPHONES,
        DeviceType.PHONE,
        DeviceType.LAPTOP,
        DeviceType.TV,
        DeviceType.PC,
        DeviceType.CONSOLE,
        DeviceType.TABLET,
        DeviceType.SPEAKER,
        DeviceType.CAR,
        DeviceType.SMART_HOME,
        DeviceType.AUDIO,
        DeviceType.AUDIO_VIDEO,
    )

private val AIRTAG_TRACKER_KEYWORDS = listOf("airtag", "find my", "findmy")
private val TILE_TRACKER_KEYWORDS = listOf("tile")
private val SAMSUNG_TAG_TRACKER_KEYWORDS = listOf("smarttag", "samsung tag")
private val TRACKER_BEACON_KEYWORDS =
    AIRTAG_TRACKER_KEYWORDS +
        TILE_TRACKER_KEYWORDS +
        SAMSUNG_TAG_TRACKER_KEYWORDS

private val NON_TRACKER_NAME_KEYWORDS =
    listOf(
        "[tv]",
        " tv",
        "tv ",
        "television",
        "samsung 5 series",
        "samsung q",
        "signage",
        "monitor",
        "display",
        "webos",
    )
