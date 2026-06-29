package io.blueeye.core.data.repository

import io.blueeye.core.data.db.dao.DeviceDao
import io.blueeye.core.data.mapper.toDomain
import io.blueeye.core.data.repository.handler.ble.BleScanHandler
import io.blueeye.core.data.repository.handler.classic.ClassicScanHandler
import io.blueeye.core.data.repository.handler.paired.ProbeResultHandler
import io.blueeye.core.data.utils.asResult
import io.blueeye.core.domain.repository.DeviceRepository
import io.blueeye.core.model.Device
import io.blueeye.core.model.DeviceCalibrationLabel
import io.blueeye.core.model.IdentityCarryoverVerdict
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

/** Implementacja repozytorium. Łączy bazę danych (Room) z warstwą domenową. */
@Singleton
@Suppress("TooManyFunctions")
class DeviceRepositoryImpl
@Inject
constructor(
    private val deviceDao: DeviceDao,
    private val deviceHistoryDataSource: DeviceHistoryDataSource,
    private val bleScanHandler: BleScanHandler,
    private val classicScanHandler: ClassicScanHandler,
    private val probeResultHandler: ProbeResultHandler,
    private val probeStateManager: ProbeStateManager,
) : DeviceRepository {
    override fun getAllDevices(): Flow<Result<List<Device>>> {
        return deviceDao.getAllDevicesFlow()
            .map { entities -> entities.toDomain() }
            .asResult()
    }

    override suspend fun getAllDevicesSync(): Result<List<Device>> = runCatching {
        deviceDao.getAllDevices().toDomain()
    }

    override fun getRecentDevices(sinceTimestamp: Long): Flow<Result<List<Device>>> {
        return deviceDao.getRecentDevicesFlow(sinceTimestamp)
            .map { entities -> entities.toDomain() }
            .asResult()
    }

    override fun getWatchlistDevices(): Flow<Result<List<Device>>> {
        return deviceDao.getWatchlistDevicesFlow()
            .map { entities -> entities.toDomain() }
            .asResult()
    }

    override fun searchDevices(query: String): Flow<Result<List<Device>>> {
        return deviceDao.searchDevices(query)
            .map { entities -> entities.toDomain() }
            .asResult()
    }

    override suspend fun getDeviceByFingerprint(fingerprint: String): Result<Device?> = runCatching {
        deviceDao.getByFingerprint(fingerprint)?.toDomain()
    }

    override fun getDeviceFlow(fingerprint: String): Flow<Result<Device?>> {
        return deviceDao.getFlowByFingerprint(fingerprint)
            .map { it?.toDomain() }
            .asResult()
    }

    override fun getSignalSamples(fingerprint: String): Flow<Result<List<io.blueeye.core.model.SignalSample>>> {
        return deviceHistoryDataSource.getSignalSamples(fingerprint)
            .asResult()
    }

    override fun getFollowMeHistory(fingerprint: String): Flow<Result<List<io.blueeye.core.model.FollowMeHistorySample>>> {
        return deviceHistoryDataSource.getFollowMeHistory(fingerprint)
            .asResult()
    }

    override fun getAlertEvidenceEvents(fingerprint: String): Flow<Result<List<io.blueeye.core.model.AlertEvidenceEvent>>> {
        return deviceHistoryDataSource.getAlertEvidenceEvents(fingerprint)
            .asResult()
    }

    override fun getRecentAlertEvidenceEvents(): Flow<Result<List<io.blueeye.core.model.AlertEvidenceEvent>>> {
        return deviceHistoryDataSource.getRecentAlertEvidenceEvents()
            .asResult()
    }

    override suspend fun updateDeviceConfig(
        fingerprint: String,
        config: io.blueeye.core.domain.repository.DeviceConfig,
    ): Result<Unit> = runCatching {
        deviceDao.getByFingerprint(fingerprint)?.let { device ->
            val updated =
                device.copy(
                    userAlias = config.alias,
                    userNotes = config.notes,
                    isSafeBeacon = config.isSafe,
                    alertSound = config.alertSound,
                    alertVibration = config.alertVibration,
                    isTrackingEnabled = config.isTrackingEnabled
                )
            deviceDao.update(updated)
        } ?: throw NoSuchElementException("Device not found: $fingerprint")
    }

    override suspend fun setIgnoredForTracking(fingerprint: String, ignored: Boolean): Result<Unit> = runCatching {
        deviceDao.setIgnoredForTracking(fingerprint, ignored)
    }

    override suspend fun setCalibrationLabel(
        fingerprint: String,
        label: DeviceCalibrationLabel,
    ): Result<Unit> = runCatching {
        deviceDao.setCalibrationLabel(fingerprint, label)
    }

    override suspend fun setIdentityCarryoverVerdict(
        fingerprint: String,
        verdict: IdentityCarryoverVerdict,
    ): Result<Unit> = runCatching {
        deviceDao.setIdentityCarryoverVerdict(fingerprint, verdict)
    }

    override suspend fun deleteDevice(fingerprint: String): Result<Unit> = runCatching {
        deviceDao.getByFingerprint(fingerprint)?.let { deviceDao.delete(it) }
    }

    override suspend fun handleScanResult(params: io.blueeye.core.domain.repository.ScanResultParams): Result<Unit> = runCatching {
        bleScanHandler.handle(
            io.blueeye.core.scanner.model.BleScanResultData(
                mac = params.mac,
                rssi = params.rssi,
                timestamp = params.timestamp,
                technology = params.technology,
                name = params.name,
                manufacturerId = params.manufacturerId,
                manufacturerData = params.manufacturerData,
                manufacturerDataById = params.manufacturerDataById,
                serviceUuids = params.serviceUuids,
                serviceDataByUuid = params.serviceDataByUuid,
                appearance = params.appearance,
                txPower = params.txPower,
                isConnectable = params.isConnectable,
                primaryPhy = params.primaryPhy,
                secondaryPhy = params.secondaryPhy,
                rawData = params.rawData,
            )
        )
    }

    override suspend fun handleClassicDiscovery(
        mac: String,
        name: String?,
        rssi: Int,
        classOfDevice: Int?,
        serviceUuids: List<String>,
    ): Result<Unit> = runCatching {
        classicScanHandler.handle(
            mac = mac,
            name = name,
            rssi = rssi,
            classOfDevice = classOfDevice,
            serviceUuids = serviceUuids,
        )
    }

    override suspend fun updateProbeData(
        fingerprint: String,
        params: io.blueeye.core.domain.repository.RepoProbeParams,
    ): Result<Unit> = runCatching {
        probeResultHandler.handle(
            fingerprint = fingerprint,
            params = params
        )
    }

    override suspend fun updateScanData(
        fingerprint: String,
        params: io.blueeye.core.domain.repository.RepoScanParams,
    ): Result<Unit> = runCatching {
        deviceDao.updateScanData(
            fingerprint = fingerprint,
            mac = params.mac,
            timestamp = params.timestamp,
            rssi = params.rssi,
            technology = params.technology,
            name = params.name,
            vendor = params.vendor,
            newType = params.newType,
            sensor = params.sensor,
            tx = params.tx,
            connectable = params.connectable,
            carryoverReasonCode = null,
            carryoverConfidence = 0f,
            carryoverFeatures = null,
            phy1 = params.phy1,
            phy2 = params.phy2,
            interval = params.interval,
            beacon = params.beacon,
            rawData = params.rawData,
            services = null,
            probeError = null,
        )
    }

    override suspend fun clearAllData(keepWatchlist: Boolean): Result<Unit> = runCatching {
        if (keepWatchlist) {
            deviceDao.deleteNonWatchlistDevices()
            deviceHistoryDataSource.deleteOrphanedHistory()
        } else {
            deviceDao.deleteAll()
            deviceHistoryDataSource.deleteAllHistory()
        }
    }

    override suspend fun deleteOldDevices(maxAgeMs: Long): Result<Int> = runCatching {
        val timestamp = System.currentTimeMillis() - maxAgeMs
        deviceDao.deleteOldDevices(timestamp)
    }

    override fun setActiveProbe(mac: String?) {
        probeStateManager.setActiveProbe(mac)
    }

    override fun getActiveProbe(): Flow<String?> {
        return probeStateManager.activeProbe
    }
}
