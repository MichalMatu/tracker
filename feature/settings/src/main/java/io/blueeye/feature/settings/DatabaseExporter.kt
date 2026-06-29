package io.blueeye.feature.settings

import io.blueeye.core.domain.evidence.DetectionEvidenceClassifier
import io.blueeye.core.domain.repository.ActiveCollectionRepository
import io.blueeye.core.domain.repository.DeviceRepository
import io.blueeye.core.domain.repository.SettingsPreferencesRepository
import io.blueeye.core.domain.repository.SignalSampleRepository
import io.blueeye.core.model.AlertEvidenceEvent
import io.blueeye.core.model.DetectionEvidence
import io.blueeye.core.model.Device
import io.blueeye.core.model.DeviceCalibrationLabel
import io.blueeye.core.model.FollowMeHistorySample
import io.blueeye.core.model.IdentityCarryoverVerdict
import io.blueeye.core.model.SignalSample
import kotlinx.coroutines.flow.first
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonObjectBuilder
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class DatabaseExporter
    @Inject
    constructor(
        private val deviceRepository: DeviceRepository,
        private val signalSampleRepository: SignalSampleRepository,
        private val settingsPreferencesRepository: SettingsPreferencesRepository,
        private val activeCollectionRepository: ActiveCollectionRepository,
    ) {
        private val json = Json { prettyPrint = true }

        @Suppress("TooGenericExceptionCaught", "SwallowedException")
        suspend fun export(): String? {
            return try {
                val devicesResult = deviceRepository.getAllDevicesSync()
                val devices = devicesResult.getOrDefault(emptyList())
                val samples = signalSampleRepository.getAllSignalSamples().getOrDefault(emptyList())
                val exportDate = System.currentTimeMillis()
                val sessionSettings = settingsPreferencesRepository.session.first()
                val sessionLabel = sessionSettings.calibrationLabel
                val sessionStartedAt = sessionSettings.startedAt
                val sessionNotes = sessionSettings.notes
                val activeCollectionEnabled = activeCollectionRepository.autoActiveProbeEnabled.first()
                val sessionDevices =
                    if (sessionStartedAt > 0L) {
                        devices.filter { it.lastSeenAt >= sessionStartedAt }
                    } else {
                        emptyList()
                    }
                val sessionSamples =
                    if (sessionStartedAt > 0L) {
                        samples.filter { it.timestamp >= sessionStartedAt }
                    } else {
                        emptyList()
                    }
                val sessionFollowMeObservations =
                    loadSessionFollowMeObservations(
                        devices = sessionDevices,
                        sessionStartedAt = sessionStartedAt,
                    )
                val sessionAlertEvidenceEvents =
                    loadSessionAlertEvidenceEvents(
                        devices = sessionDevices,
                        sessionStartedAt = sessionStartedAt,
                    )

                val root =
                    DatabaseExportJsonMapper.buildExport(
                        DatabaseExportData(
                            devices = devices,
                            samples = samples,
                            session =
                                DatabaseExportSessionData(
                                    devices = sessionDevices,
                                    samples = sessionSamples,
                                    label = sessionLabel,
                                    startedAt = sessionStartedAt,
                                    notes = sessionNotes,
                                    activeCollectionEnabled = activeCollectionEnabled,
                                    followMeObservations = sessionFollowMeObservations,
                                    alertEvidenceEvents = sessionAlertEvidenceEvents,
                                ),
                            exportDate = exportDate,
                        ),
                    )
                json.encodeToString(root)
            } catch (e: Exception) {
                null
            }
        }

        private suspend fun loadSessionFollowMeObservations(
            devices: List<Device>,
            sessionStartedAt: Long,
        ): List<SessionFollowMeObservation> =
            devices
                .flatMap { device ->
                    deviceRepository.getFollowMeHistory(device.fingerprint)
                        .first()
                        .getOrDefault(emptyList())
                        .filter { observation -> isInSession(observation.timestamp, sessionStartedAt) }
                        .map { observation ->
                            SessionFollowMeObservation(
                                deviceFingerprint = device.fingerprint,
                                observation = observation,
                            )
                        }
                }
                .sortedBy { observation -> observation.observation.timestamp }

        private suspend fun loadSessionAlertEvidenceEvents(
            devices: List<Device>,
            sessionStartedAt: Long,
        ): List<AlertEvidenceEvent> =
            devices
                .flatMap { device ->
                    deviceRepository.getAlertEvidenceEvents(device.fingerprint)
                        .first()
                        .getOrDefault(emptyList())
                        .filter { event -> isInSession(event.timestamp, sessionStartedAt) }
                }
                .sortedBy { event -> event.timestamp }
    }

internal data class DatabaseExportData(
    val devices: List<Device>,
    val samples: List<SignalSample>,
    val session: DatabaseExportSessionData,
    val exportDate: Long,
)

internal data class DatabaseExportSessionData(
    val devices: List<Device>,
    val samples: List<SignalSample>,
    val label: DeviceCalibrationLabel,
    val startedAt: Long,
    val notes: String,
    val activeCollectionEnabled: Boolean,
    val followMeObservations: List<SessionFollowMeObservation>,
    val alertEvidenceEvents: List<AlertEvidenceEvent>,
)

internal data class SessionFollowMeObservation(
    val deviceFingerprint: String,
    val observation: FollowMeHistorySample,
)

internal object DatabaseExportJsonMapper {
    fun buildExport(data: DatabaseExportData): JsonObject =
        buildJsonObject {
            put("schemaVersion", SCHEMA_VERSION)
            put("exportDate", data.exportDate)
            put("deviceCount", data.devices.size)
            put("sampleCount", data.samples.size)
            putSampleQuality(data.samples)
            put(
                "privacyNotice",
                buildJsonObject {
                    put("containsMacAddresses", data.devices.any { it.macAddress.isNotBlank() })
                    put(
                        "containsGpsSamples",
                        data.samples.any { sample ->
                            sample.latitude != null ||
                                sample.longitude != null ||
                                sample.locationAccuracy != null
                        },
                    )
                    put(
                        "containsRawPayloads",
                        data.devices.any { !it.lastRawData.isNullOrBlank() } ||
                            data.samples.any(SessionSignalSampleExportQuality::hasRawPayloadData),
                    )
                    put(
                        "containsActiveProbeData",
                        data.devices.any(SessionActiveProbeSummaryCalculator::hasActiveProbeData),
                    )
                    put(
                        "handling",
                        "Treat as sensitive local telemetry. Share only with trusted reviewers.",
                    )
                },
            )
            put(
                "session",
                mapSession(
                    session = data.session,
                    exportDate = data.exportDate,
                ),
            )
            put("devices", JsonArray(data.devices.map(::mapDevice)))
            put("signalSamples", JsonArray(data.samples.map(::mapSample)))
        }

    private fun mapSession(
        session: DatabaseExportSessionData,
        exportDate: Long,
    ): JsonObject =
        buildJsonObject {
            val activeProbeSummary = SessionActiveProbeSummaryCalculator.calculate(session.devices)

            put("label", session.label.name)
            put("notes", session.notes)
            put("activeCollectionEnabled", session.activeCollectionEnabled)
            put("startedAt", session.startedAt)
            put("exportedAt", exportDate)
            put(
                "durationMs",
                SessionDurationCalculator.observedDurationMs(
                    startedAt = session.startedAt,
                    devices = session.devices,
                    samples = session.samples,
                    followMeObservations = session.followMeObservations,
                    alertEvidenceEvents = session.alertEvidenceEvents,
                ),
            )
            put("deviceCount", session.devices.size)
            put("sampleCount", session.samples.size)
            putSampleQuality(session.samples)
            put("followMeObservationCount", session.followMeObservations.size)
            put("alertEvidenceEventCount", session.alertEvidenceEvents.size)
            put("activeProbeDataDeviceCount", activeProbeSummary.dataDeviceCount)
            put("activeProbeStatusCounts", activeProbeSummary.statusCountsJson())
            put("decodedSignalDeviceCount", SessionDecodedSignalExportMapper.countDevices(session.devices))
            put("decodedSignalKindCounts", SessionDecodedSignalExportMapper.kindCounts(session.devices))
            put(
                "alertEvidenceEventTypeCounts",
                SessionHistoryExportJsonMapper.eventTypeCounts(session.alertEvidenceEvents),
            )
            put("rssiQuality", RssiQualityAnalyzer.analyze(session.samples).toJson())
            put("evidenceCount", session.devices.sumOf { it.evidence.size })
            put("attentionEvidenceCount", session.devices.countDeviceAttentionEvidence())
            put("reviewReadiness", session.reviewReadiness())
            put("calibrationCounts", session.devices.enumCounts<DeviceCalibrationLabel> { it.calibrationLabel })
            put(
                "identityCarryoverVerdictCounts",
                session.devices.enumCounts<IdentityCarryoverVerdict> { it.identityCarryoverVerdict },
            )
            put("reviewCategoryCounts", session.devices.sessionExportReviewCategoryCounts())
            put(
                "reviewDeviceQueue",
                JsonArray(
                    SessionReviewDeviceQueueBuilder.build(
                        devices = session.devices,
                        samples = session.samples,
                        alertEvidenceEvents = session.alertEvidenceEvents,
                    ).map(::mapReviewDeviceQueueItem),
                ),
            )
            put(
                "deviceSummaries",
                JsonArray(
                    session.devices
                        .sortedWith(
                            compareByDescending<Device> { it.evidence.strongestConfidencePriority() }
                                .thenByDescending { it.followingScore }
                                .thenByDescending { it.lastSeenAt },
                        )
                        .map { device ->
                            mapSessionDevice(
                                device = device,
                                samples = session.samples.filter { it.deviceFingerprint == device.fingerprint },
                                followMeObservations =
                                    session.followMeObservations
                                        .filter { it.deviceFingerprint == device.fingerprint },
                                alertEvidenceEvents =
                                    session.alertEvidenceEvents
                                        .filter { it.deviceFingerprint == device.fingerprint },
                            )
                        },
                ),
            )
            put(
                "followMeObservations",
                JsonArray(
                    session.followMeObservations.map(
                        SessionHistoryExportJsonMapper::mapFollowMeObservation,
                    ),
                ),
            )
            put(
                "alertEvidenceEvents",
                JsonArray(
                    session.alertEvidenceEvents.map(
                        SessionHistoryExportJsonMapper::mapAlertEvidenceEvent,
                    ),
                ),
            )
            put(
                "decodedSignals",
                SessionDecodedSignalExportMapper.decodedSignals(session.devices),
            )
            put(
                "deviceFingerprints",
                JsonArray(session.devices.map { device -> JsonPrimitive(device.fingerprint) }),
            )
        }

    private fun JsonObjectBuilder.putSampleQuality(samples: List<SignalSample>) {
        put("samplesWithGps", SessionSignalSampleExportQuality.countGpsSamples(samples))
        put("samplesWithRawPayloads", SessionSignalSampleExportQuality.countRawPayloadSamples(samples))
        put("samplesWithScanMetadata", SessionSignalSampleExportQuality.countScanMetadataSamples(samples))
    }

    private fun mapReviewDeviceQueueItem(item: SessionReviewDeviceQueueItem): JsonObject =
        buildJsonObject {
            put("fingerprint", item.fingerprint)
            put("displayName", item.displayName)
            put("reasonText", item.reasonText)
            put("actionText", item.actionText)
            put(
                "decisions",
                JsonArray(item.decisions.map(::mapReviewDeviceQueueDecision)),
            )
        }

    private fun mapReviewDeviceQueueDecision(decision: SessionReviewDeviceQueueDecision): JsonObject =
        buildJsonObject {
            put("kind", decision.kind)
            put("text", decision.text)
            put("calibrationLabel", decision.deviceCalibrationLabel?.name)
            put("identityCarryoverVerdict", decision.identityCarryoverVerdict?.name)
            put("watchlistTrackingEnabled", decision.watchlistTrackingEnabled)
        }

    private fun mapSessionDevice(
        device: Device,
        samples: List<SignalSample>,
        followMeObservations: List<SessionFollowMeObservation>,
        alertEvidenceEvents: List<AlertEvidenceEvent>,
    ): JsonObject {
        val reviewCategory = device.sessionExportReviewCategory()
        return buildJsonObject {
            put("fingerprint", device.fingerprint)
            put("displayName", device.getDisplayName())
            put("mac", device.macAddress)
            put("technology", device.technology)
            put("deviceType", device.deviceType.name)
            put("trackingStatus", device.trackingStatus.name)
            put("followingScore", device.followingScore)
            put("calibrationLabel", device.calibrationLabel.name)
            put("identityCarryoverVerdict", device.identityCarryoverVerdict.name)
            put("isInWatchlist", device.isInWatchlist)
            put("isIgnoredForTracking", device.isIgnoredForTracking)
            put("lastSeen", device.lastSeenAt)
            put("rssi", device.rssi)
            put("encounterCount", device.encounterCount)
            put("evidenceCount", device.evidence.size)
            put("attentionEvidenceCount", device.evidence.countEvidenceAttention())
            put("reviewCategory", reviewCategory.name)
            put("reviewReason", reviewCategory.reasonText)
            put("reviewAction", device.sessionExportReviewAction(reviewCategory))
            put("strongestEvidence", device.evidence.strongestEvidence()?.let(::mapEvidence) ?: JsonNull)
            put("followMeObservationCount", followMeObservations.size)
            put("alertEvidenceEventCount", alertEvidenceEvents.size)
            put(
                "latestFollowMeObservation",
                followMeObservations.maxByOrNull { it.observation.timestamp }
                    ?.let(SessionHistoryExportJsonMapper::mapFollowMeObservation) ?: JsonNull,
            )
            put(
                "latestAlertEvidenceEvent",
                alertEvidenceEvents.maxByOrNull { it.timestamp }
                    ?.let(SessionHistoryExportJsonMapper::mapAlertEvidenceEvent) ?: JsonNull,
            )
            put(
                "evidenceSources",
                JsonArray(
                    device.evidence
                        .map { evidence -> evidence.source.name }
                        .distinct()
                        .map(::JsonPrimitive),
                ),
            )
            put("decodedSignal", SessionDecodedSignalExportMapper.decodedSignal(device))
            put("sampleStats", mapSampleStats(samples))
        }
    }

    private fun mapDevice(device: Device): JsonObject =
        buildJsonObject {
            put("fingerprint", device.fingerprint)
            put("mac", device.macAddress)
            put("macAddressType", device.macAddressType.name)
            put("technology", device.technology)
            put("name", device.name)
            put("deviceType", device.deviceType.name)
            put("vendor", device.vendorName)
            put("model", device.modelNumber)
            put("predictedModel", device.predictedModel)
            put("trackingStatus", device.trackingStatus.name)
            put("followingScore", device.followingScore)
            put("isInWatchlist", device.isInWatchlist)
            put("isSafeBeacon", device.isSafeBeacon)
            put("calibrationLabel", device.calibrationLabel.name)
            put("identityCarryoverVerdict", device.identityCarryoverVerdict.name)
            put("userAlias", device.userAlias)
            put("userNotes", device.userNotes)
            put("alertSound", device.alertSound)
            put("alertVibration", device.alertVibration)
            put("isTrackingEnabled", device.isTrackingEnabled)
            put("isIgnoredForTracking", device.isIgnoredForTracking)
            put("rssi", device.rssi)
            put("firstSeen", device.firstSeenAt)
            put("lastSeen", device.lastSeenAt)
            put("encounterCount", device.encounterCount)
            put("sensorData", device.sensorData)
            put("txPower", device.txPower)
            put("isConnectable", device.isConnectable)
            put("primaryPhy", device.primaryPhy)
            put("secondaryPhy", device.secondaryPhy)
            put("advertisingInterval", device.advertisingIntervalMs)
            put("beaconType", device.beaconType)
            put("connectionStatus", device.connectionStatus)
            put("connectionAttempts", device.connectionAttempts)
            put("lastProbeTimestamp", device.lastProbeTimestamp)
            put("manufacturer", device.manufacturerName)
            put("serial", device.serialNumber)
            put("firmware", device.firmwareRevision)
            put("hardware", device.hardwareRevision)
            put("software", device.softwareRevision)
            put("battery", device.batteryLevel)
            put("gattServices", device.gattServices)
            put("characteristicData", device.characteristicData)
            put("evidenceCount", device.evidence.size)
            put("evidence", JsonArray(device.evidence.map(::mapEvidence)))
            put("rawData", device.lastRawData)
            put("probeError", device.probeError)
        }

    internal fun mapEvidence(evidence: DetectionEvidence): JsonObject =
        buildJsonObject {
            put("source", evidence.source.name)
            put("confidence", evidence.confidence.name)
            put("reasonText", evidence.reasonText)
            put("timestamp", evidence.timestamp)
            put("rawValue", evidence.rawValue)
            put("parsedValue", evidence.parsedValue)
            put("isPassive", evidence.isPassive)
            put("provenance", evidence.provenance.name)
        }

    private fun mapSample(sample: SignalSample): JsonObject =
        buildJsonObject {
            put("deviceFingerprint", sample.deviceFingerprint)
            put("observedMac", sample.observedMac)
            put("technology", sample.technology)
            put("deviceName", sample.deviceName)
            put("deviceType", sample.deviceType)
            put("vendorName", sample.vendorName)
            put("rssi", sample.rssi)
            put("timestamp", sample.timestamp)
            putNullableNumber("latitude", sample.latitude)
            putNullableNumber("longitude", sample.longitude)
            putNullableNumber("locationAccuracy", sample.locationAccuracy)
            putNullableNumber("manufacturerId", sample.manufacturerId)
            put("manufacturerDataHex", sample.manufacturerDataHex)
            put("manufacturerDataByIdHex", sample.manufacturerDataByIdHex)
            put("serviceUuids", sample.serviceUuids)
            put("serviceDataByUuidHex", sample.serviceDataByUuidHex)
            putNullableNumber("appearance", sample.appearance)
            putNullableNumber("txPower", sample.txPower)
            put("isConnectable", sample.isConnectable)
            putNullableNumber("primaryPhy", sample.primaryPhy)
            putNullableNumber("secondaryPhy", sample.secondaryPhy)
            putNullableNumber("advertisingIntervalMs", sample.advertisingIntervalMs)
            put("beaconType", sample.beaconType)
            put("rawDataHex", sample.rawDataHex)
            put("sensorData", sample.sensorData)
            putNullableNumber("classOfDevice", sample.classOfDevice)
            put("trackingStatus", sample.trackingStatus)
            putNullableNumber("followingScore", sample.followingScore)
            put("isTactical", sample.isTactical)
            put("tacticalCategory", sample.tacticalCategory)
            put("probeError", sample.probeError)
        }

    private const val SCHEMA_VERSION = 19
}

private val SessionReviewDeviceQueueDecision.kind: String
    get() =
        when {
            identityCarryoverVerdict != null -> "IDENTITY_CARRYOVER"
            watchlistTrackingEnabled != null -> "WATCHLIST_TRACKING"
            else -> "DEVICE_CALIBRATION"
        }

private fun DatabaseExportSessionData.reviewReadiness(): JsonObject =
    SessionReviewReadinessCalculator.calculate(
        SessionReviewReadinessInput(
            label = label,
            notes = notes,
            deviceCount = devices.size,
            sampleCount = samples.size,
            attentionEvidenceCount = devices.countDeviceAttentionEvidence(),
            activeCollection =
                SessionReviewActiveCollection(
                    enabled = activeCollectionEnabled,
                    dataDeviceCount = SessionActiveProbeSummaryCalculator.calculate(devices).dataDeviceCount,
                ),
        ),
    ).toJson()

private fun List<Device>.countDeviceAttentionEvidence(): Int =
    sumOf { device ->
        device.evidence.countEvidenceAttention()
    }

private fun List<DetectionEvidence>.countEvidenceAttention(): Int {
    return count { evidence ->
        DetectionEvidenceClassifier.isAttentionEvidence(evidence)
    }
}

private inline fun <reified T : Enum<T>> List<Device>.enumCounts(crossinline selectValue: (Device) -> T): JsonObject =
    buildJsonObject {
        enumValues<T>().forEach { value ->
            put(value.name, count { selectValue(it) == value })
        }
    }

private fun mapSampleStats(samples: List<SignalSample>): JsonObject =
    buildJsonObject {
        put("sampleCount", samples.size)
        putNullableNumber("firstSampleAt", samples.minOfOrNull { it.timestamp })
        putNullableNumber("lastSampleAt", samples.maxOfOrNull { it.timestamp })
        putNullableNumber("minRssi", samples.minOfOrNull { it.rssi })
        putNullableNumber("maxRssi", samples.maxOfOrNull { it.rssi })
        putNullableNumber("averageRssi", samples.averageRssi())
        put("rssiQuality", RssiQualityAnalyzer.analyze(samples).toJson())
        put("rssiTrend", RssiTrendAnalyzer.analyze(samples).toJson())
        put("samplesWithGps", samples.count { it.latitude != null && it.longitude != null })
    }

private fun List<SignalSample>.averageRssi(): Double? =
    if (isEmpty()) {
        null
    } else {
        sumOf { it.rssi }.toDouble() / size
    }

private fun List<DetectionEvidence>.strongestConfidencePriority(): Int =
    strongestEvidence()?.let { DetectionEvidenceClassifier.confidencePriority(it.confidence) } ?: 0

private fun isInSession(
    timestamp: Long,
    sessionStartedAt: Long,
): Boolean = sessionStartedAt > 0L && timestamp >= sessionStartedAt

private fun List<DetectionEvidence>.strongestEvidence(): DetectionEvidence? =
    maxWithOrNull(
        compareBy<DetectionEvidence> { DetectionEvidenceClassifier.confidencePriority(it.confidence) }
            .thenBy { it.timestamp },
    )

private fun JsonObjectBuilder.putNullableNumber(
    key: String,
    value: Number?,
) {
    if (value == null) {
        put(key, JsonNull)
    } else {
        put(key, value)
    }
}
