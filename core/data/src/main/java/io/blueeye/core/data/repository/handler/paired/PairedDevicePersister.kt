package io.blueeye.core.data.repository.handler.paired

import io.blueeye.core.data.db.dao.DeviceDao
import io.blueeye.core.data.db.entity.DeviceEntity
import io.blueeye.core.model.DeviceType
import io.blueeye.core.model.MacAddressType
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class PairedDevicePersister @Inject constructor(
    private val deviceDao: DeviceDao,
) {

    suspend fun persist(ctx: PairedScanDataContext) {
        val existing = ctx.existingDevice

        if (existing != null) {
            persistExistingDevice(ctx, existing)
        } else {
            persistNewDevice(ctx)
        }
    }

    private suspend fun persistExistingDevice(
        ctx: PairedScanDataContext,
        existing: DeviceEntity,
    ) {
        // Enriched sensor data (e.g. Battery)
        val sensorInfo = if (ctx.batteryLevel != null) "Bat: ${ctx.batteryLevel}%" else existing.sensorData

        deviceDao.updateScanData(
            fingerprint = ctx.fingerprint,
            mac = ctx.mac,
            timestamp = ctx.timestamp,
            rssi = existing.lastRssi, // Keep last RSSI
            technology = ctx.technology,
            name = ctx.name ?: existing.lastDeviceName,
            vendor = ctx.vendorName,
            newType = if (ctx.deviceType != DeviceType.UNKNOWN) ctx.deviceType else existing.deviceType,
            sensor = sensorInfo,
            tx = existing.txPower,
            connectable = true, // Paired means connectable usually
            carryoverReasonCode = null,
            carryoverConfidence = 0f,
            carryoverFeatures = null,
            phy1 = existing.primaryPhy,
            phy2 = existing.secondaryPhy,
            interval = existing.advertisingIntervalMs,
            beacon = existing.beaconType,
            rawData = existing.lastRawData,
            services = null,
            probeError = null,
        )

        // Update isPaired flag
        deviceDao.updateIsPaired(ctx.fingerprint, true)
    }

    private suspend fun persistNewDevice(ctx: PairedScanDataContext) {
        val sensorInfo = if (ctx.batteryLevel != null) "Bat: ${ctx.batteryLevel}%" else null

        val newDevice = DeviceEntity(
            fingerprint = ctx.fingerprint,
            lastMacAddress = ctx.mac,
            macAddressType = MacAddressType.PUBLIC, // Bonded are usually Public identity
            lastDeviceName = ctx.name,
            vendorName = ctx.vendorName,
            deviceType = ctx.deviceType,
            technology = ctx.technology,
            manufacturerId = null,
            lastRssi = -100, // Unknown
            firstSeenAt = ctx.timestamp,
            lastSeenAt = ctx.timestamp,
            encounterCount = 1,
            isPaired = true,
            isConnectable = true,
            classOfDevice = ctx.deviceClass,
            hardwareRevision = ctx.chipsetInfo,
            sensorData = sensorInfo,
            batteryLevel = ctx.batteryLevel
        )

        deviceDao.upsert(newDevice)
    }
}
