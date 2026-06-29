package io.blueeye.core.data.repository.handler.ble.enricher

import android.util.Log
import io.blueeye.core.data.classifier.AppleIdentityConflictGuard
import io.blueeye.core.data.classifier.BeaconDecoderManager
import io.blueeye.core.data.repository.handler.ble.ScanDataContext
import javax.inject.Inject

/**
 * Enriches scan data with sensor readings (temperature, humidity, etc.).
 */
class SensorEnricher @Inject constructor(
    private val beaconDecoderManager: BeaconDecoderManager
) : ScanEnricher {

    override fun enrich(ctx: ScanDataContext) {
        val decodedSensorData =
            beaconDecoderManager.decode(
                mac = ctx.mac,
                manufacturerRecords = ctx.manufacturerRecords(),
                serviceUuids = ctx.serviceUuids,
                rawData = ctx.rawData,
            )

        if (AppleIdentityConflictGuard.hasLabelConflict(
                name = ctx.sanitizedName ?: ctx.name,
                label = decodedSensorData?.beaconType,
            )
        ) {
            Log.d(
                TAG,
                "Ignoring conflicting Apple beacon payload for ${ctx.mac}: " +
                    "name=${ctx.sanitizedName ?: ctx.name}, beacon=${decodedSensorData?.beaconType}",
            )
            ctx.sensorData = null
            return
        }

        ctx.sensorData = decodedSensorData

        // Use beacon type from sensor data if available
        if (ctx.sensorData?.beaconType != null) {
            ctx.beaconType = ctx.sensorData?.beaconType
        }

        if (ctx.sensorData != null) {
            Log.d(TAG, "Decoded: ${ctx.sensorData?.rawData} | Type: ${ctx.beaconType}")
        }
    }

    private companion object {
        private const val TAG = "DeviceEnricher"
    }
}
