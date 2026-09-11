package io.blueeye.core.data.repository.handler.ble

import android.util.Log
import io.blueeye.core.data.classifier.AppleIdentityConflictGuard
import io.blueeye.core.data.classifier.chipset.ChipsetIdentifier
import io.blueeye.core.data.db.dao.DeviceDao
import io.blueeye.core.data.db.dao.IdentityContinuityCandidateDao
import io.blueeye.core.data.db.entity.DeviceEntity
import io.blueeye.core.data.db.entity.IdentityContinuityCandidateEntity
import io.blueeye.core.data.repository.handler.common.DeviceTypePriorityHelper
import io.blueeye.core.data.util.NameUtils
import io.blueeye.core.scanner.throttle.ScanThrottler
import io.blueeye.core.scanner.throttle.ThrottleParams
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.abs

internal data class BlePersistenceOutcome(
    val deviceUpdated: Boolean,
    val deviceUpdateThrottled: Boolean,
    val signalSampleOutcome: SignalSamplePersistenceOutcome,
)

private data class DevicePersistenceStageOutcome(
    val shouldRecordFollowMeObservation: Boolean,
    val deviceUpdated: Boolean,
    val deviceUpdateThrottled: Boolean,
)

/**
 * Persists canonical device identity/scan state and Follow-Me tracking mutations.
 *
 * Signal-sample throttling and snapshot construction are delegated to [SignalSamplePersister];
 * scanner lifecycle and ingest accounting live outside this class.
 */
@Singleton
class DevicePersister @Inject constructor(
    private val deviceDao: DeviceDao,
    private val followMeObservationRecorder: FollowMeObservationRecorder,
    private val scanThrottler: ScanThrottler,
    private val priorityHelper: DeviceTypePriorityHelper,
    private val signalSamplePersister: SignalSamplePersister,
    private val identityContinuityCandidateDao: IdentityContinuityCandidateDao,
) {

    internal suspend fun persist(
        ctx: ScanDataContext,
        classifier: ScanResultClassifier,
    ): BlePersistenceOutcome {
        val existing = ctx.existingDevice
        val sensorDataString = SensorDataFormatter.format(ctx.sensorData)

        val stageOutcome =
            if (existing != null) {
                persistExistingDevice(ctx, existing, sensorDataString, classifier)
            } else {
                DevicePersistenceStageOutcome(
                    shouldRecordFollowMeObservation = persistNewDevice(ctx, sensorDataString, classifier),
                    deviceUpdated = true,
                    deviceUpdateThrottled = false,
                )
            }

        if (stageOutcome.shouldRecordFollowMeObservation) {
            followMeObservationRecorder.record(ctx)
        }
        persistIdentityCandidate(ctx)

        val signalSampleOutcome = signalSamplePersister.persist(ctx, classifier)
        return BlePersistenceOutcome(
            deviceUpdated = stageOutcome.deviceUpdated,
            deviceUpdateThrottled = stageOutcome.deviceUpdateThrottled,
            signalSampleOutcome = signalSampleOutcome,
        )
    }

    private suspend fun persistIdentityCandidate(ctx: ScanDataContext) {
        val candidate = ctx.identityCandidate ?: return
        if (ctx.fingerprint != ctx.mac || candidate.candidateFingerprint == ctx.fingerprint) return
        identityContinuityCandidateDao.insert(
            IdentityContinuityCandidateEntity(
                deviceFingerprint = ctx.fingerprint,
                candidateFingerprint = candidate.candidateFingerprint,
                timestamp = ctx.timestamp,
                reasonCode = candidate.evidence.reasonCode.name,
                confidence = candidate.evidence.confidence,
                featureSummary = candidate.evidence.featureSummary,
            ),
        )
    }

    private suspend fun persistExistingDevice(
        ctx: ScanDataContext,
        existing: DeviceEntity,
        sensorDataString: String?,
        classifier: ScanResultClassifier,
    ): DevicePersistenceStageOutcome {
        val candidateType = classifier.resolveType(ctx)
        val bestName = NameUtils.resolveBestName(existing.lastDeviceName, ctx.name ?: ctx.vendorModel ?: ctx.beaconType)
        val nameCorrectedType =
            AppleIdentityConflictGuard.preferredNameTypeForConflict(
                name = bestName,
                candidateType = existing.deviceType,
            )
        val resolvedType = nameCorrectedType ?: priorityHelper.resolveBetterType(existing.deviceType, candidateType)

        val hasNewName = ctx.name != null && ctx.name != existing.lastDeviceName
        val hasNewType = resolvedType != existing.deviceType
        val isTactical = ctx.isTactical

        val params = ThrottleParams(
            mac = ctx.mac,
            currentRssi = ctx.validRssi,
            hasNewName = hasNewName,
            hasNewType = hasNewType,
            isPriorityDevice = isTactical,
        )

        val trackingChanged = updateTrackingIfChanged(ctx, existing)

        if (!scanThrottler.shouldUpdateDevice(params)) {
            return DevicePersistenceStageOutcome(
                shouldRecordFollowMeObservation = trackingChanged,
                deviceUpdated = trackingChanged,
                deviceUpdateThrottled = true,
            )
        }

        val currentServices = ctx.serviceUuids.toSet()
        val existingServices = existing.gattServices?.split(",")?.toSet() ?: emptySet()
        val mergedServices = (existingServices + currentServices)
            .filter { it.isNotBlank() }
            .joinToString(",")

        val mergedTechnology = if (existing.technology.contains(ctx.technology)) {
            existing.technology
        } else {
            "${existing.technology} + ${ctx.technology}"
        }

        deviceDao.updateScanData(
            fingerprint = ctx.fingerprint,
            mac = ctx.mac,
            timestamp = ctx.timestamp,
            rssi = ctx.validRssi,
            technology = mergedTechnology,
            name = bestName,
            vendor = ctx.vendorName,
            newType = resolvedType,
            sensor = sensorDataString,
            tx = ctx.txPower,
            connectable = ctx.isConnectable,
            carryoverReasonCode = ctx.carryoverReasonCode,
            carryoverConfidence = ctx.carryoverConfidence,
            carryoverFeatures = ctx.carryoverFeatures,
            phy1 = ctx.primaryPhy,
            phy2 = ctx.secondaryPhy,
            interval = ctx.advertisingInterval,
            beacon = ctx.beaconType,
            rawData = ctx.rawDataHex,

            services = mergedServices.ifBlank { null },
            probeError = ctx.probeError,
        )

        mergeSecondaryRecordIfPresent(ctx, contextLabel = "scan update")
        return DevicePersistenceStageOutcome(
            shouldRecordFollowMeObservation = trackingChanged,
            deviceUpdated = true,
            deviceUpdateThrottled = false,
        )
    }

    private suspend fun persistNewDevice(
        ctx: ScanDataContext,
        sensorDataString: String?,
        classifier: ScanResultClassifier,
    ): Boolean {
        val rescueFingerprint = tryRescueCarryover(ctx)
        if (rescueFingerprint != null) {
            ctx.fingerprint = rescueFingerprint
            updateRescuedDevice(ctx, sensorDataString, classifier)
            return true
        } else {
            createNewDevice(ctx, sensorDataString, classifier)
            return followMeObservationRecorder.hasObservationValue(ctx)
        }
    }

    private suspend fun tryRescueCarryover(ctx: ScanDataContext): String? {
        val rawHex = ctx.rawDataHex
        if (rawHex == null || rawHex.length <= MIN_RAW_DATA_LEN) return null

        val rescueDevice = deviceDao.findDeviceByRawData(rawHex)
        val rescueFingerprint = if (rescueDevice == null || rescueDevice.fingerprint == ctx.fingerprint) {
            null
        } else {
            val allDuplicates = deviceDao.findAllDevicesByRawData(rawHex)
            if (allDuplicates.size > 1) {
                val oldest = allDuplicates.first()
                allDuplicates.drop(1).forEach { duplicate ->
                    try {
                        deviceDao.mergeDevices(oldest.fingerprint, duplicate.fingerprint)
                    } catch (@Suppress("TooGenericExceptionCaught") e: Exception) {
                        Log.e(
                            TAG,
                            "Failed to merge rescue candidate ${duplicate.fingerprint} into " +
                                "${oldest.fingerprint}: ${e.message}",
                        )
                    }
                }
                oldest.fingerprint
            } else {
                rescueDevice.fingerprint
            }
        }
        return rescueFingerprint
    }

    private suspend fun updateRescuedDevice(
        ctx: ScanDataContext,
        sensorDataString: String?,
        classifier: ScanResultClassifier,
    ) {
        val resolvedType = classifier.resolveType(ctx)
        val services = ctx.serviceUuids.joinToString(",")

        deviceDao.updateScanData(
            fingerprint = ctx.fingerprint,
            mac = ctx.mac,
            timestamp = ctx.timestamp,
            rssi = ctx.validRssi,
            technology = ctx.technology,
            name = ctx.name ?: ctx.probeModel ?: ctx.vendorModel,
            vendor = ctx.probeManufacturer ?: ctx.vendorName,
            newType = resolvedType,
            sensor = sensorDataString,
            tx = ctx.txPower,
            connectable = ctx.isConnectable,
            carryoverReasonCode = ctx.carryoverReasonCode,
            carryoverConfidence = ctx.carryoverConfidence,
            carryoverFeatures = ctx.carryoverFeatures,
            phy1 = ctx.primaryPhy,
            phy2 = ctx.secondaryPhy,
            interval = ctx.advertisingInterval,
            beacon = ctx.beaconType,
            rawData = ctx.rawDataHex,

            services = services.ifBlank { null },
            probeError = ctx.probeError,
        )

        // Rescued records do not carry the previous entity, so write follow-me fields unconditionally.
        deviceDao.setTrackingStatus(
            fingerprint = ctx.fingerprint,
            status = ctx.trackingStatus,
            score = ctx.followingScore,
            explanation = ctx.followMeExplanation,
            durationScore = ctx.followMeDurationScore,
            rssiStabilityScore = ctx.followMeRssiStabilityScore,
            deviceTypeScore = ctx.followMeDeviceTypeScore,
            macBehaviorScore = ctx.followMeMacBehaviorScore,
            encounterScore = ctx.followMeEncounterScore,
            userMoved = ctx.followMeUserMoved,
            baselineDevice = ctx.followMeBaselineDevice,
        )

        mergeSecondaryRecordIfPresent(ctx, contextLabel = "rescued scan")
    }

    private suspend fun createNewDevice(
        ctx: ScanDataContext,
        sensorDataString: String?,
        classifier: ScanResultClassifier,
    ) {
        val chipset = ChipsetIdentifier.getChipFamily(ctx.mac)
        val newDevice = DeviceEntity(
            fingerprint = ctx.fingerprint, lastMacAddress = ctx.mac, macAddressType = ctx.macAddressType,
            lastDeviceName = ctx.name ?: ctx.probeModel ?: ctx.vendorModel, vendorName = ctx.probeManufacturer ?: ctx.vendorName,
            deviceType = classifier.resolveType(ctx), technology = ctx.technology,
            manufacturerId = ctx.manufacturerId, lastRssi = ctx.validRssi, firstSeenAt = ctx.timestamp,
            lastSeenAt = ctx.timestamp, encounterCount = 1, sensorData = sensorDataString,
            txPower = ctx.txPower, isConnectable = ctx.isConnectable, primaryPhy = ctx.primaryPhy,
            secondaryPhy = ctx.secondaryPhy, predictedModel = ctx.probeModel ?: ctx.vendorModel ?: ctx.fingerprintModel,
            advertisingIntervalMs = ctx.advertisingInterval, beaconType = ctx.beaconType,
            lastRawData = ctx.rawDataHex, hardwareRevision = ctx.probeFirmware ?: chipset,
            trackingStatus = ctx.trackingStatus, followingScore = ctx.followingScore,
            followMeExplanation = ctx.followMeExplanation,
            carryoverReasonCode = ctx.carryoverReasonCode,
            carryoverConfidence = ctx.carryoverConfidence,
            carryoverFeatures = ctx.carryoverFeatures,
            followMeDurationScore = ctx.followMeDurationScore,
            followMeRssiStabilityScore = ctx.followMeRssiStabilityScore,
            followMeDeviceTypeScore = ctx.followMeDeviceTypeScore,
            followMeMacBehaviorScore = ctx.followMeMacBehaviorScore,
            followMeEncounterScore = ctx.followMeEncounterScore,
            followMeUserMoved = ctx.followMeUserMoved,
            followMeBaselineDevice = ctx.followMeBaselineDevice,

            gattServices = ctx.serviceUuids.joinToString(",").ifBlank { null },
            probeError = ctx.probeError,
        )
        deviceDao.upsert(newDevice)
    }

    private suspend fun updateTrackingIfChanged(
        ctx: ScanDataContext,
        existing: DeviceEntity,
    ): Boolean {
        val isStatusChanged = existing.trackingStatus != ctx.trackingStatus
        val isScoreChanged = abs(existing.followingScore - ctx.followingScore) > SCORE_UPDATE_DELTA
        val isExplanationChanged = existing.followMeExplanation != ctx.followMeExplanation
        val areComponentsChanged = existing.hasDifferentFollowMeComponents(ctx)

        val shouldUpdateTracking = listOf(
            isStatusChanged,
            isScoreChanged,
            isExplanationChanged,
            areComponentsChanged,
        ).any { it }

        if (shouldUpdateTracking) {
            deviceDao.setTrackingStatus(
                fingerprint = ctx.fingerprint,
                status = ctx.trackingStatus,
                score = ctx.followingScore,
                explanation = ctx.followMeExplanation,
                durationScore = ctx.followMeDurationScore,
                rssiStabilityScore = ctx.followMeRssiStabilityScore,
                deviceTypeScore = ctx.followMeDeviceTypeScore,
                macBehaviorScore = ctx.followMeMacBehaviorScore,
                encounterScore = ctx.followMeEncounterScore,
                userMoved = ctx.followMeUserMoved,
                baselineDevice = ctx.followMeBaselineDevice,
            )
        }
        return shouldUpdateTracking
    }

    private suspend fun mergeSecondaryRecordIfPresent(
        ctx: ScanDataContext,
        contextLabel: String,
    ) {
        if (ctx.mac == ctx.fingerprint) return

        val duplicate = deviceDao.getByFingerprint(ctx.mac)
        if (duplicate != null && duplicate.fingerprint != ctx.fingerprint) {
            Log.i(
                TAG,
                "Merging stale $contextLabel record ${ctx.mac} into ${ctx.fingerprint}",
            )
            try {
                deviceDao.mergeDevices(ctx.fingerprint, ctx.mac)
            } catch (@Suppress("TooGenericExceptionCaught") e: Exception) {
                Log.e(
                    TAG,
                    "Failed to merge stale $contextLabel record ${ctx.mac} into " +
                        "${ctx.fingerprint}: ${e.message}",
                )
            }
        }
    }

    companion object {
        private const val TAG = "DevicePersister"
        // Lowered from 32 to 20 to support packets with just Flags + Small MfgData
        // Example: Flags(3) + MfgData(12) = 15 bytes = 30 hex chars.
        private const val MIN_RAW_DATA_LEN = 20
        private const val SCORE_UPDATE_DELTA = 2.0f
    }
}

private fun DeviceEntity.hasDifferentFollowMeComponents(ctx: ScanDataContext): Boolean =
    followMeDurationScore != ctx.followMeDurationScore ||
        followMeRssiStabilityScore != ctx.followMeRssiStabilityScore ||
        followMeDeviceTypeScore != ctx.followMeDeviceTypeScore ||
        followMeMacBehaviorScore != ctx.followMeMacBehaviorScore ||
        followMeEncounterScore != ctx.followMeEncounterScore ||
        followMeUserMoved != ctx.followMeUserMoved ||
        followMeBaselineDevice != ctx.followMeBaselineDevice
