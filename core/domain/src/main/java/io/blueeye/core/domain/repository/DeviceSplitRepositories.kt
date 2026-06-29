package io.blueeye.core.domain.repository

import io.blueeye.core.model.AlertEvidenceEvent
import io.blueeye.core.model.Device
import io.blueeye.core.model.DeviceCalibrationLabel
import io.blueeye.core.model.IdentityCarryoverVerdict
import kotlinx.coroutines.flow.Flow

/**
 * Interface for querying devices.
 */
interface DeviceQueryRepository {
    fun getAllDevices(): Flow<Result<List<Device>>>

    suspend fun getAllDevicesSync(): Result<List<Device>>

    fun getRecentDevices(sinceTimestamp: Long): Flow<Result<List<Device>>>

    fun getWatchlistDevices(): Flow<Result<List<Device>>>

    fun searchDevices(query: String): Flow<Result<List<Device>>>

    suspend fun getDeviceByFingerprint(fingerprint: String): Result<Device?>

    fun getDeviceFlow(fingerprint: String): Flow<Result<Device?>>

    fun getSignalSamples(fingerprint: String): Flow<Result<List<io.blueeye.core.model.SignalSample>>>

    fun getFollowMeHistory(fingerprint: String): Flow<Result<List<io.blueeye.core.model.FollowMeHistorySample>>>

    fun getActiveProbe(): Flow<String?>
}

/**
 * Interface for querying durable evidence tied to device alerts.
 */
interface DeviceAlertEvidenceRepository {
    fun getAlertEvidenceEvents(fingerprint: String): Flow<Result<List<AlertEvidenceEvent>>>

    fun getRecentAlertEvidenceEvents(): Flow<Result<List<AlertEvidenceEvent>>>
}

/**
 * Interface for managing device settings and lifecycle.
 */
interface DeviceManageRepository {
    suspend fun updateDeviceConfig(
        fingerprint: String,
        config: DeviceConfig
    ): Result<Unit>

    suspend fun setIgnoredForTracking(
        fingerprint: String,
        ignored: Boolean
    ): Result<Unit>

    suspend fun setCalibrationLabel(
        fingerprint: String,
        label: DeviceCalibrationLabel
    ): Result<Unit>

    suspend fun setIdentityCarryoverVerdict(
        fingerprint: String,
        verdict: IdentityCarryoverVerdict
    ): Result<Unit>

    suspend fun deleteDevice(fingerprint: String): Result<Unit>

    suspend fun clearAllData(keepWatchlist: Boolean = true): Result<Unit>

    suspend fun deleteOldDevices(maxAgeMs: Long): Result<Int>
}

/**
 * Interface for scanner-driven updates.
 */
interface DeviceScanRepository {
    suspend fun handleScanResult(params: ScanResultParams): Result<Unit>

    suspend fun handleClassicDiscovery(
        mac: String,
        name: String?,
        rssi: Int,
        classOfDevice: Int?,
        serviceUuids: List<String> = emptyList()
    ): Result<Unit>

    suspend fun updateProbeData(
        fingerprint: String,
        params: RepoProbeParams
    ): Result<Unit>

    suspend fun updateScanData(
        fingerprint: String,
        params: RepoScanParams
    ): Result<Unit>

    fun setActiveProbe(mac: String?)
}
