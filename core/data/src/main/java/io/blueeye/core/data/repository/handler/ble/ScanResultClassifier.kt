package io.blueeye.core.data.repository.handler.ble

import io.blueeye.core.data.classifier.DeviceClassifier
import io.blueeye.core.model.DeviceType
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Classifies the device based on all available BLE data.
 * Resolves conflicts between different classification sources.
 */
@Singleton
class ScanResultClassifier @Inject constructor(
    private val deviceClassifier: DeviceClassifier,
) {

    /**
     * Classifies the device type and resolves conflicts between:
     * - Direct scan classification (from appearance, service UUIDs)
     * - Vendor-specific classification (from manufacturer data)
     * - Existing device type (from database)
     */
    fun classify(ctx: ScanDataContext): ScanDataContext {
        // Classify device type from scan data
        // Classify device type from scan data
        ctx.deviceType = deviceClassifier.classifyBle(
            io.blueeye.core.data.classifier.model.BleClassificationInput(
                manufacturerRecords = ctx.manufacturerRecords(),
                serviceUuids = ctx.serviceUuids,
                serviceDataByUuid = ctx.serviceDataRecords(),
                appearance = ctx.appearance,
                deviceName = ctx.sanitizedName,
                vendorName = ctx.vendorName
            )
        )

        if (ctx.deviceType != DeviceType.UNKNOWN) {
            android.util.Log.d(
                TAG,
                "Classified ${ctx.mac} as ${ctx.deviceType} (mfgId=${ctx.manufacturerId}, rssi=${ctx.rssi})"
            )
        }

        // Calculate advertising interval if we have existing device
        calculateInterval(ctx)

        return ctx
    }

    /**
     * Resolves the final device type considering all sources.
     * Called after enrichment when we have vendor type information.
     */
    fun resolveType(ctx: ScanDataContext): DeviceType {
        val existing = ctx.existingDevice

        return if (existing != null) {
            DeviceTypeResolver.resolve(
                existingType = existing.deviceType,
                modelNumber = existing.modelNumber,
                vendorType = ctx.vendorDeviceType,
                scanType = ctx.deviceType,
                nameType = deviceClassifier.classifyByName(ctx.sanitizedName ?: ctx.name),
            )
        } else {
            DeviceTypeResolver.resolveForNew(
                vendorType = ctx.vendorDeviceType,
                scanType = ctx.deviceType,
                nameType = deviceClassifier.classifyByName(ctx.sanitizedName ?: ctx.name),
            )
        }
    }

    private fun calculateInterval(ctx: ScanDataContext) {
        val existing = ctx.existingDevice
        if (existing != null) {
            val diff = ctx.timestamp - existing.lastSeenAt
            // Allow up to 60s for interval (e.g. background Apple devices)
            ctx.advertisingInterval = if (diff in MIN_AD_INTERVAL_MS..MAX_AD_INTERVAL_MS) {
                diff
            } else {
                existing.advertisingIntervalMs
            }
        }
    }

    companion object {
        private const val TAG = "ScanResultClassifier"
        private const val MIN_AD_INTERVAL_MS = 20
        private const val MAX_AD_INTERVAL_MS = 60000
    }
}
