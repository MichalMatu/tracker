package io.blueeye.core.data.repository.handler.ble

import android.util.Log
import io.blueeye.core.data.db.dao.SignalSampleDao
import io.blueeye.core.data.db.entity.SignalSampleEntity
import io.blueeye.core.location.LocationProvider
import io.blueeye.core.scanner.throttle.ScanThrottler
import javax.inject.Inject
import javax.inject.Singleton

internal enum class SignalSamplePersistenceOutcome {
    WRITTEN,
    THROTTLED,
    FAILED,
}

/**
 * Persists the time-series signal snapshot for one processed scan observation.
 *
 * Device row persistence and identity/merge policy intentionally stay in [DevicePersister]. This
 * component owns only sample throttling, snapshot construction, location attachment and the
 * explicit written/throttled/failed outcome used by Phase 3 ingest accounting.
 */
@Singleton
class SignalSamplePersister @Inject constructor(
    private val signalSampleDao: SignalSampleDao,
    private val scanThrottler: ScanThrottler,
    private val locationProvider: LocationProvider,
) {
    internal suspend fun persist(
        ctx: ScanDataContext,
        classifier: ScanResultClassifier,
    ): SignalSamplePersistenceOutcome {
        if (!scanThrottler.shouldWriteSample(ctx.mac, isPriorityDevice = ctx.isTactical)) {
            return SignalSamplePersistenceOutcome.THROTTLED
        }

        val location = locationProvider.getFreshCoordinates()
        val sample =
            SignalSampleEntity(
                deviceFingerprint = ctx.fingerprint,
                observedMac = ctx.mac,
                technology = ctx.technology,
                deviceName = ctx.sanitizedName ?: ctx.name,
                deviceType = classifier.resolveType(ctx).name,
                vendorName = ctx.probeManufacturer ?: ctx.vendorName,
                rssi = ctx.validRssi,
                timestamp = ctx.timestamp,
                latitude = location?.first,
                longitude = location?.second,
                locationAccuracy = location?.third,
                manufacturerId = ctx.manufacturerId,
                manufacturerDataHex = ctx.manufacturerData.toHexStringOrNull(),
                manufacturerDataByIdHex = ctx.manufacturerRecords().toManufacturerHexEntries(),
                serviceUuids = ctx.serviceUuids.toCsvOrNull(),
                serviceDataByUuidHex = ctx.serviceDataRecords().toServiceDataHexEntries(),
                appearance = ctx.appearance,
                txPower = ctx.txPower,
                isConnectable = ctx.isConnectable,
                primaryPhy = ctx.primaryPhy,
                secondaryPhy = ctx.secondaryPhy,
                advertisingIntervalMs = ctx.advertisingInterval,
                beaconType = ctx.beaconType,
                rawDataHex = ctx.rawDataHex,
                sensorData = SensorDataFormatter.format(ctx.sensorData),
                trackingStatus = ctx.trackingStatus.name,
                followingScore = ctx.followingScore,
                isTactical = ctx.isTactical,
                tacticalCategory = ctx.tacticalCategory,
                probeError = ctx.probeError,
            )

        return try {
            signalSampleDao.insert(sample)
            SignalSamplePersistenceOutcome.WRITTEN
        } catch (@Suppress("TooGenericExceptionCaught") error: Exception) {
            Log.w(TAG, "Sample insert failed: ${error.message}")
            SignalSamplePersistenceOutcome.FAILED
        }
    }

    private companion object {
        const val TAG = "SignalSamplePersister"
    }
}

private fun ByteArray?.toHexStringOrNull(): String? =
    this?.takeIf { it.isNotEmpty() }?.joinToString("") { byte -> "%02X".format(byte) }

private fun Map<Int, ByteArray>.toManufacturerHexEntries(): String? =
    entries
        .sortedBy { it.key }
        .mapNotNull { (key, value) ->
            value.toHexStringOrNull()?.let { hex -> "0x%04X=%s".format(key, hex) }
        }
        .joinToString(";")
        .ifBlank { null }

private fun Map<String, ByteArray>.toServiceDataHexEntries(): String? =
    entries
        .sortedBy { it.key }
        .mapNotNull { (key, value) ->
            value.toHexStringOrNull()?.let { hex -> "$key=$hex" }
        }
        .joinToString(";")
        .ifBlank { null }

private fun List<String>.toCsvOrNull(): String? =
    map { it.trim() }
        .filter { it.isNotBlank() }
        .distinct()
        .joinToString(",")
        .ifBlank { null }
