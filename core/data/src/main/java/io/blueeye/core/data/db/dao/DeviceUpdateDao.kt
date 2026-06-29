package io.blueeye.core.data.db.dao

import androidx.room.Dao
import androidx.room.Query
import io.blueeye.core.model.DeviceType
import io.blueeye.core.model.TrackingStatus

@Dao
@Suppress("LongParameterList")
interface DeviceUpdateDao {
    @Query("UPDATE devices SET isPaired = :isPaired WHERE fingerprint = :fingerprint")
    suspend fun updateIsPaired(fingerprint: String, isPaired: Boolean)

    @Query(
        """
        UPDATE devices 
        SET lastSeenAt = :timestamp, encounterCount = encounterCount + 1
        WHERE fingerprint = :fingerprint
    """,
    )
    suspend fun updateLastSeen(
        fingerprint: String,
        timestamp: Long,
    )

    @Query("UPDATE devices SET isInWatchlist = :isWatchlisted WHERE fingerprint = :fingerprint")
    suspend fun setWatchlistStatus(
        fingerprint: String,
        isWatchlisted: Boolean,
    )

    @Query("UPDATE devices SET isTrackingEnabled = :isEnabled WHERE fingerprint = :fingerprint")
    suspend fun updateTrackingEnabled(
        fingerprint: String,
        isEnabled: Boolean,
    )

    @Query("UPDATE devices SET userAlias = :alias WHERE fingerprint = :fingerprint")
    suspend fun setUserAlias(
        fingerprint: String,
        alias: String?,
    )

    @Query(
        """
        UPDATE devices
        SET lastWatchlistReturnAlertAt = :timestamp,
            lastWatchlistReturnOfflineDurationMs = :offlineDurationMs
        WHERE fingerprint = :fingerprint
    """,
    )
    suspend fun recordWatchlistReturnAlert(
        fingerprint: String,
        timestamp: Long,
        offlineDurationMs: Long,
    )

    @Query(
        """
        UPDATE devices
        SET trackingStatus = :status,
            followingScore = :score,
            followMeExplanation = :explanation,
            followMeDurationScore = :durationScore,
            followMeRssiStabilityScore = :rssiStabilityScore,
            followMeDeviceTypeScore = :deviceTypeScore,
            followMeMacBehaviorScore = :macBehaviorScore,
            followMeEncounterScore = :encounterScore,
            followMeUserMoved = :userMoved,
            followMeBaselineDevice = :baselineDevice
        WHERE fingerprint = :fingerprint
        """,
    )
    suspend fun setTrackingStatus(
        fingerprint: String,
        status: TrackingStatus,
        score: Float,
        explanation: String?,
        durationScore: Int,
        rssiStabilityScore: Int,
        deviceTypeScore: Int,
        macBehaviorScore: Int,
        encounterScore: Int,
        userMoved: Boolean?,
        baselineDevice: Boolean?,
    )

    @Suppress("LongParameterList")
    @Query(
        """
        UPDATE devices
        SET connectionStatus = :status,
            connectionAttempts = :attempts,
            lastProbeTimestamp = :timestamp,
            lastRssi = CASE
                WHEN :status IN ('PROBING', 'RFCOMM_FAIL')
                    AND lastRssi = -50
                    AND technology = 'CLASSIC'
                    AND (lastDeviceName IS NULL OR TRIM(lastDeviceName) = '')
                    AND classOfDevice IS NULL
                THEN -100
                ELSE lastRssi
            END,
            modelNumber = CASE WHEN :model IS NOT NULL THEN :model ELSE modelNumber END,
            predictedModel = CASE WHEN :model IS NOT NULL THEN :model ELSE predictedModel END,
            serialNumber = CASE WHEN :serial IS NOT NULL THEN :serial ELSE serialNumber END,
            firmwareRevision = CASE WHEN :firmware IS NOT NULL THEN :firmware ELSE firmwareRevision END,
            hardwareRevision = CASE WHEN :hardware IS NOT NULL THEN :hardware ELSE hardwareRevision END,
            softwareRevision = CASE WHEN :software IS NOT NULL THEN :software ELSE softwareRevision END,
            manufacturerName = CASE WHEN :manufacturer IS NOT NULL THEN :manufacturer ELSE manufacturerName END,
            batteryLevel = CASE WHEN :battery IS NOT NULL THEN :battery ELSE batteryLevel END,
            gattServices = CASE WHEN :services IS NOT NULL THEN :services ELSE gattServices END,
            characteristicData = CASE WHEN :charData IS NOT NULL THEN :charData ELSE characteristicData END,
            probeError = CASE WHEN :error IS NOT NULL THEN :error ELSE probeError END,
            deviceType = CASE WHEN :newDeviceType = 'UNKNOWN' THEN deviceType ELSE :newDeviceType END
        WHERE fingerprint = :fingerprint
    """,
    )
    suspend fun updateProbeData(
        fingerprint: String,
        status: String,
        attempts: Int,
        timestamp: Long,
        model: String?,
        serial: String?,
        firmware: String?,
        hardware: String?,
        software: String?,
        manufacturer: String?,
        battery: Int?,
        services: String?,
        charData: String?,
        error: String?,
        newDeviceType: DeviceType,
    )

    @Suppress("LongParameterList")
    @Query(
        """
        UPDATE devices 
        SET 
            lastMacAddress = CASE WHEN :mac IS NOT NULL THEN :mac ELSE lastMacAddress END,
            lastSeenAt = :timestamp,
            lastRssi = :rssi,
            encounterCount = encounterCount + 1,
            technology = :technology,
            lastDeviceName = CASE WHEN :name IS NOT NULL THEN :name ELSE lastDeviceName END,
            vendorName = CASE WHEN :vendor IS NOT NULL THEN :vendor ELSE vendorName END,
            deviceType = CASE WHEN :newType != 'UNKNOWN' THEN :newType ELSE deviceType END,
            sensorData = CASE WHEN :sensor IS NOT NULL THEN :sensor ELSE sensorData END,
            txPower = CASE WHEN :tx IS NOT NULL THEN :tx ELSE txPower END,
            isConnectable = :connectable,
            carryoverReasonCode = CASE WHEN :carryoverReasonCode IS NOT NULL THEN :carryoverReasonCode ELSE carryoverReasonCode END,
            carryoverConfidence = CASE WHEN :carryoverReasonCode IS NOT NULL THEN :carryoverConfidence ELSE carryoverConfidence END,
            carryoverFeatures = CASE WHEN :carryoverFeatures IS NOT NULL THEN :carryoverFeatures ELSE carryoverFeatures END,
            primaryPhy = CASE WHEN :phy1 IS NOT NULL THEN :phy1 ELSE primaryPhy END,
            secondaryPhy = CASE WHEN :phy2 IS NOT NULL THEN :phy2 ELSE secondaryPhy END,
            advertisingIntervalMs = CASE WHEN :interval IS NOT NULL THEN :interval ELSE advertisingIntervalMs END,
            beaconType = CASE WHEN :beacon IS NOT NULL THEN :beacon ELSE beaconType END,
            lastRawData = CASE WHEN :rawData IS NOT NULL THEN :rawData ELSE lastRawData END,
            gattServices = CASE WHEN :services IS NOT NULL THEN :services ELSE gattServices END,
            probeError = CASE WHEN :probeError IS NOT NULL THEN :probeError ELSE probeError END
        WHERE fingerprint = :fingerprint
    """,
    )
    suspend fun updateScanData(
        fingerprint: String,
        mac: String?,
        timestamp: Long,
        rssi: Int,
        technology: String,
        name: String?,
        vendor: String?,
        newType: DeviceType,
        sensor: String?,
        tx: Int?,
        connectable: Boolean,
        carryoverReasonCode: String?,
        carryoverConfidence: Float,
        carryoverFeatures: String?,
        phy1: Int?,
        phy2: Int?,
        interval: Long?,
        beacon: String?,
        rawData: String?,
        services: String?,
        probeError: String?
    )

    @Query(
        """
        UPDATE devices 
        SET 
            lastDeviceName = CASE WHEN :name IS NOT NULL THEN :name ELSE lastDeviceName END,
            lastRssi = :rssi,
            lastSeenAt = :timestamp,
            classOfDevice = CASE WHEN :cod IS NOT NULL THEN :cod ELSE classOfDevice END,
            technology = :technology,
            vendorName = CASE WHEN :vendor IS NOT NULL THEN :vendor ELSE vendorName END,
            deviceType = CASE WHEN :deviceType != 'UNKNOWN' THEN :deviceType ELSE deviceType END,
            encounterCount = encounterCount + 1,
            advertisingIntervalMs = :interval,
            gattServices = CASE WHEN :services IS NOT NULL THEN :services ELSE gattServices END
        WHERE fingerprint = :fingerprint
    """,
    )
    suspend fun updateClassicScanData(
        fingerprint: String,
        name: String?,
        rssi: Int,
        timestamp: Long,
        cod: Int?,
        technology: String,
        vendor: String?,
        deviceType: DeviceType,
        interval: Long?,
        services: String?,
    )
}
