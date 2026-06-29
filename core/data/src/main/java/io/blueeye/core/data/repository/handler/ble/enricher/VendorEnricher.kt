package io.blueeye.core.data.repository.handler.ble.enricher

import android.util.Log
import io.blueeye.core.data.classifier.AppleIdentityConflictGuard
import io.blueeye.core.data.classifier.vendor.VendorStrategyFactory
import io.blueeye.core.data.repository.handler.ble.ScanDataContext
import io.blueeye.core.model.DeviceType
import javax.inject.Inject

/**
 * Enriches scan data using vendor-specific strategies (Apple, Samsung, etc.).
 */
class VendorEnricher @Inject constructor(
    private val vendorStrategyFactory: VendorStrategyFactory
) : ScanEnricher {

    override fun enrich(ctx: ScanDataContext) {
        decodeVendorData(ctx)
        detectBeaconTypeFallback(ctx)
    }

    private fun decodeVendorData(ctx: ScanDataContext) {
        val vendorResult = vendorStrategyFactory.decode(
            ctx.manufacturerRecords(),
            ctx.serviceUuids,
            ctx.name,
        )

        if (vendorResult != null) {
            if (AppleIdentityConflictGuard.preferredNameTypeForConflict(
                    name = ctx.sanitizedName ?: ctx.name,
                    candidateType = vendorResult.deviceType,
                ) != null
            ) {
                Log.d(
                    TAG,
                    "Ignoring conflicting Apple vendor payload for ${ctx.mac}: " +
                        "name=${ctx.sanitizedName ?: ctx.name}, model=${vendorResult.modelName}",
                )
                return
            }
            if (vendorResult.deviceType != DeviceType.UNKNOWN) {
                ctx.vendorDeviceType = vendorResult.deviceType
            }
            if (vendorResult.modelName != null) {
                ctx.vendorModel = vendorResult.modelName
            }
            if (vendorResult.extraInfo != null && ctx.beaconType == null) {
                ctx.beaconType = vendorResult.extraInfo
            }
        }
    }

    private fun detectBeaconTypeFallback(ctx: ScanDataContext) {
        // Fallback logic for Eddystone
        if (ctx.beaconType == null) {
            if (ctx.serviceUuids.contains("0000feaa-0000-1000-8000-00805f9b34fb")) {
                ctx.beaconType = "Eddystone"
            }
        }
    }

    private companion object {
        private const val TAG = "VendorEnricher"
    }
}
