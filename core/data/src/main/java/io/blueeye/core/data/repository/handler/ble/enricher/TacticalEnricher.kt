package io.blueeye.core.data.repository.handler.ble.enricher

import io.blueeye.core.data.classifier.tactical.TacticalProcessor
import io.blueeye.core.data.repository.handler.ble.ScanDataContext
import io.blueeye.core.model.DeviceType
import javax.inject.Inject

/** Enriches scan data with professional/public-safety signal information. */
class TacticalEnricher @Inject constructor(
    private val tacticalProcessor: TacticalProcessor
) : ScanEnricher {

    override fun enrich(ctx: ScanDataContext) {
        val tacticalResult = tacticalProcessor.process(
            mac = ctx.mac,
            rssi = ctx.rssi,
            manufacturerId = ctx.manufacturerId,
            manufacturerData = ctx.manufacturerData,
            manufacturerDataById = ctx.manufacturerRecords(),
            serviceUuids = ctx.serviceUuids,
            name = ctx.name,
            timestamp = ctx.timestamp,
        )

        ctx.isTactical = tacticalResult.isTactical
        ctx.tacticalEvidence = tacticalResult.evidence

        if (tacticalResult.isTactical) {
            if (ctx.vendorDeviceType == DeviceType.UNKNOWN && tacticalResult.deviceType != DeviceType.UNKNOWN) {
                ctx.vendorDeviceType = tacticalResult.deviceType
            }
            if (tacticalResult.beaconTypeStatus != null) {
                ctx.beaconType = tacticalResult.beaconTypeStatus
            }
            if (tacticalResult.categoryDescription != null && ctx.vendorModel == null) {
                ctx.tacticalCategory = tacticalResult.categoryDescription
                ctx.vendorModel = tacticalResult.categoryDescription
            }
        }
    }
}
