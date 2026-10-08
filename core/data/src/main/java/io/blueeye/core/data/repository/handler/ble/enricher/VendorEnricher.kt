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

    @Suppress("MagicNumber")
    private fun detectBeaconTypeFallback(ctx: ScanDataContext) {
        val feaaData =
            ctx.serviceDataRecords()
                .entries
                .firstOrNull { (uuid, _) -> uuid.contains("feaa", ignoreCase = true) }
                ?.value
        val frame = feaaData?.firstOrNull()?.toInt()?.and(0xFF)

        // A validated Find Hub frame wins over generic "Sony", "Samsung" and FEAA labels.
        // This is a protocol capability, not an assertion that the hardware is a tracker.
        if (frame in setOf(0x40, 0x41) && feaaData?.size in setOf(22, 34)) {
            ctx.beaconType = "Google Find Hub"
            return
        }

        if (ctx.beaconType != null) return

        ctx.beaconType =
            when {
                frame in setOf(0x00, 0x10, 0x20, 0x30) -> "Eddystone"
                ctx.serviceDataRecords().keys.any { it.contains("fcb2", ignoreCase = true) } ->
                    "DULT location-enabled"
                ctx.serviceUuids.any { it.contains("fcb2", ignoreCase = true) } ->
                    "DULT location-enabled"
                ctx.serviceUuids.any { it.contains("fd5a", ignoreCase = true) } ->
                    "SmartThings Find"
                else -> null
            }
    }

    private companion object {
        private const val TAG = "VendorEnricher"
    }
}
