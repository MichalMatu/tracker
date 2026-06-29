package io.blueeye.core.data.repository.handler.classic

import android.util.Log
import io.blueeye.core.data.db.dao.DeviceDao
import io.blueeye.core.data.db.dao.SignalSampleDao
import io.blueeye.core.data.db.entity.DeviceEntity
import io.blueeye.core.data.db.entity.SignalSampleEntity
import io.blueeye.core.data.repository.handler.common.DeviceTypePriorityHelper
import io.blueeye.core.location.LocationProvider
import io.blueeye.core.model.DeviceType
import io.blueeye.core.model.MacAddressType
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.abs

@Singleton
class ClassicDevicePersister @Inject constructor(
    private val deviceDao: DeviceDao,
    private val signalSampleDao: SignalSampleDao,
    private val priorityHelper: DeviceTypePriorityHelper,
    private val locationProvider: LocationProvider,
) {
    private companion object {
        const val TAG = "ClassicDevicePersister"
        const val LEGACY_RFCOMM_FALLBACK_RSSI = -50
        const val APPLE_COMPANY_ID = 76
        const val APPLE_SHADOW_LOOKBACK_MS = 30_000L
        const val UUID_MATCH_LOOKBACK_MS = 300_000L
        const val APPLE_SHADOW_RSSI_TOLERANCE = 10
        const val UUID_MATCH_RSSI_TOLERANCE = 15
    }

    suspend fun persist(ctx: ClassicScanDataContext) {
        val existing = ctx.existingDevice

        if (existing != null) {
            persistExistingDevice(ctx, existing)
        } else {
            persistNewDevice(ctx)
        }

        // Record signal sample
        recordSignalSample(ctx)
    }

    private suspend fun persistExistingDevice(
        ctx: ClassicScanDataContext,
        existing: DeviceEntity,
    ) {
        if (!ctx.name.isNullOrBlank()) {
            val name = ctx.name
            val altName = name.alternateApostropheName()

            val bestMatch = deviceDao.getByNameOrAlt(name, altName)

            if (bestMatch != null && bestMatch.fingerprint != existing.fingerprint) {
                Log.i(
                    TAG,
                    "Merging duplicate classic record ${existing.fingerprint} into name match " +
                        "${bestMatch.fingerprint} for $name",
                )

                persistExistingDevice(ctx, bestMatch)
                ctx.fingerprint = bestMatch.fingerprint

                deviceDao.delete(existing)
                return
            }
        }

        val isGenericApple = ctx.hasMeasuredRssi &&
            existing.vendorName.isAppleVendor() &&
            existing.lastDeviceName.isGenericAppleName()

        if (isGenericApple) {
            val candidates = deviceDao.getRecentDevicesSnapshot(ctx.timestamp - APPLE_SHADOW_LOOKBACK_MS)
            val match = candidates.find { candidate ->
                val isCandidateApple = candidate.vendorName.isAppleVendor() || candidate.manufacturerId == APPLE_COMPANY_ID
                val rssiDiff = abs(candidate.lastRssi - ctx.validRssi)
                candidate.fingerprint != existing.fingerprint &&
                    isCandidateApple &&
                    rssiDiff <= APPLE_SHADOW_RSSI_TOLERANCE
            }

            if (match != null) {
                Log.i(
                    TAG,
                    "Merging generic Apple classic record ${existing.fingerprint} into signal match " +
                        "${match.fingerprint}; rssiDiff=${abs(match.lastRssi - ctx.validRssi)}",
                )
                persistExistingDevice(ctx, match)
                ctx.fingerprint = match.fingerprint
                deviceDao.delete(existing)
                return
            }
        }

        val candidateType = if (ctx.deviceType != DeviceType.UNKNOWN) {
            ctx.deviceType
        } else {
            DeviceType.UNKNOWN
        }

        val updatedType = priorityHelper.resolveBetterType(existing.deviceType, candidateType)

        val interval = if (ctx.timestamp > existing.lastSeenAt) {
            (ctx.timestamp - existing.lastSeenAt)
        } else {
            existing.advertisingIntervalMs
        }

        ctx.advertisingInterval = interval

        val finalRssi = resolveFinalRssi(ctx, existing)

        val mergedTechnology = if (existing.technology.contains("CLASSIC")) {
            existing.technology
        } else {
            "${existing.technology} + CLASSIC"
        }
        val mergedServices = existing.gattServices.mergeServiceUuids(ctx.serviceUuids)

        val newName = ctx.name
        val currentName = existing.lastDeviceName

        val isNewGeneric = newName.isGenericAppleName()
        val isCurrentSpecific = !currentName.isNullOrBlank() && !currentName.isGenericAppleName()

        val finalName = if (isNewGeneric && isCurrentSpecific) {
            currentName
        } else {
            newName ?: currentName
        }

        deviceDao.updateClassicScanData(
            fingerprint = existing.fingerprint,
            name = finalName,
            rssi = finalRssi,
            timestamp = ctx.timestamp,
            cod = ctx.classOfDevice,
            technology = mergedTechnology,
            vendor = ctx.vendorName,
            deviceType = updatedType,
            interval = interval,
            services = mergedServices,
        )
    }

    private suspend fun persistNewDevice(ctx: ClassicScanDataContext) {
        val sameNameDevice = if (!ctx.name.isNullOrBlank()) {
            val name = ctx.name
            val altName = name.alternateApostropheName()

            deviceDao.getByNameOrAlt(name, altName)
        } else {
            null
        }

        if (sameNameDevice != null) {
            Log.i(
                TAG,
                "Merging classic scan ${ctx.mac} into existing name match ${sameNameDevice.fingerprint}",
            )
            persistExistingDevice(ctx, sameNameDevice)
            ctx.fingerprint = sameNameDevice.fingerprint
            return
        }

        if (ctx.hasMeasuredRssi && ctx.serviceUuids.isNotEmpty()) {
            val candidates = deviceDao.getRecentDevicesSnapshot(ctx.timestamp - UUID_MATCH_LOOKBACK_MS)
            val uuidMatch = candidates.find { candidate ->
                val candidateServices = candidate.gattServices?.split(",")?.toSet() ?: emptySet()
                val hasOverlap = ctx.serviceUuids.any { it in candidateServices }
                val rssiDiff = abs(candidate.lastRssi - ctx.validRssi)
                hasOverlap &&
                    rssiDiff <= UUID_MATCH_RSSI_TOLERANCE &&
                    candidate.fingerprint != ctx.fingerprint
            }

            if (uuidMatch != null) {
                Log.i(
                    TAG,
                    "Merging classic scan ${ctx.mac} into UUID match ${uuidMatch.fingerprint}; " +
                        "rssiDiff=${abs(uuidMatch.lastRssi - ctx.validRssi)}",
                )
                persistExistingDevice(ctx, uuidMatch)
                ctx.fingerprint = uuidMatch.fingerprint
                return
            }
        }

        val isGenericApple = ctx.hasMeasuredRssi &&
            ctx.vendorName.isAppleVendor() &&
            ctx.name.isGenericAppleName()

        if (isGenericApple) {
            val candidates = deviceDao.getRecentDevicesSnapshot(ctx.timestamp - APPLE_SHADOW_LOOKBACK_MS)
            val match = candidates.find { candidate ->
                val isCandidateApple = candidate.vendorName.isAppleVendor() || candidate.manufacturerId == APPLE_COMPANY_ID
                val rssiDiff = abs(candidate.lastRssi - ctx.validRssi)
                isCandidateApple && rssiDiff <= APPLE_SHADOW_RSSI_TOLERANCE
            }

            if (match != null) {
                Log.i(
                    TAG,
                    "Merging generic Apple classic scan ${ctx.mac} into signal match ${match.fingerprint}; " +
                        "rssiDiff=${abs(match.lastRssi - ctx.validRssi)}",
                )
                persistExistingDevice(ctx, match)
                ctx.fingerprint = match.fingerprint
                return
            }
        }

        val newDevice = DeviceEntity(
            fingerprint = ctx.fingerprint,
            lastMacAddress = ctx.mac,
            macAddressType = MacAddressType.PUBLIC,
            lastDeviceName = ctx.name,
            classOfDevice = ctx.classOfDevice,
            vendorName = ctx.vendorName,
            deviceType = ctx.deviceType,
            technology = "CLASSIC",
            lastRssi = ctx.validRssi,
            firstSeenAt = ctx.timestamp,
            lastSeenAt = ctx.timestamp,
            encounterCount = 1,
            gattServices = ctx.serviceUuids.toPersistedServices(),
        )

        deviceDao.upsert(newDevice)
    }

    private fun resolveFinalRssi(
        ctx: ClassicScanDataContext,
        existing: DeviceEntity,
    ): Int {
        if (ctx.hasMeasuredRssi) {
            return ctx.validRssi
        }

        return if (existing.hasLegacyRfcommFallbackRssi()) {
            ClassicScanDataContext.RSSI_DEFAULT
        } else {
            existing.lastRssi
        }
    }

    private fun DeviceEntity.hasLegacyRfcommFallbackRssi(): Boolean {
        return lastRssi == LEGACY_RFCOMM_FALLBACK_RSSI &&
            connectionStatus == "RFCOMM_FAIL" &&
            technology == "CLASSIC" &&
            lastDeviceName.isNullOrBlank()
    }

    private suspend fun recordSignalSample(ctx: ClassicScanDataContext) {
        if (!ctx.hasMeasuredRssi) {
            return
        }

        val location = locationProvider.getFreshCoordinates()
        val sample = SignalSampleEntity(
            deviceFingerprint = ctx.fingerprint,
            observedMac = ctx.mac,
            technology = "CLASSIC",
            deviceName = ctx.name,
            deviceType = ctx.deviceType.name,
            vendorName = ctx.vendorName,
            rssi = ctx.validRssi,
            timestamp = ctx.timestamp,
            latitude = location?.first,
            longitude = location?.second,
            locationAccuracy = location?.third,
            serviceUuids = ctx.serviceUuids.toPersistedServices(),
            classOfDevice = ctx.classOfDevice,
        )
        try {
            signalSampleDao.insert(sample)
        } catch (@Suppress("TooGenericExceptionCaught") e: Exception) {
            Log.w(TAG, "Sample insert failed for ${ctx.fingerprint}: ${e.message}")
        }
    }
}

private fun String.alternateApostropheName(): String =
    when {
        contains("'") -> replace("'", "’")
        contains("’") -> replace("’", "'")
        else -> this
    }

private fun String?.isAppleVendor(): Boolean =
    this?.contains("Apple") == true || this == "Apple"

private fun String?.isGenericAppleName(): Boolean =
    this == "Apple, Inc. Device" || this == "Apple Device" || isNullOrBlank()

private fun String?.mergeServiceUuids(newServices: List<String>): String? =
    (toServiceUuidList() + newServices)
        .map { it.trim() }
        .filter { it.isNotBlank() }
        .distinct()
        .joinToString(",")
        .ifBlank { null }

private fun String?.toServiceUuidList(): List<String> =
    this
        ?.split(",")
        ?.map { it.trim() }
        ?.filter { it.isNotBlank() }
        ?: emptyList()

private fun List<String>.toPersistedServices(): String? =
    map { it.trim() }
        .filter { it.isNotBlank() }
        .distinct()
        .joinToString(",")
        .ifBlank { null }
