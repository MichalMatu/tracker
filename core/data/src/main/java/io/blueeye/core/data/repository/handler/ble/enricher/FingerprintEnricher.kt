package io.blueeye.core.data.repository.handler.ble.enricher

import android.util.Log
import io.blueeye.core.data.classifier.AppleIdentityConflictGuard
import io.blueeye.core.data.repository.handler.ble.ScanDataContext
import io.blueeye.core.data.tracker.analysis.SpoofingDetector
import io.blueeye.core.data.tracker.fingerprint.KnownDeviceFingerprints
import io.blueeye.core.model.DeviceType
import javax.inject.Inject

/**
 * Enriches scan data with known device fingerprints and checks for fingerprint-spam anomalies.
 */
class FingerprintEnricher @Inject constructor(
    private val spoofingDetector: SpoofingDetector,
) : ScanEnricher {

    companion object {
        private const val TAG = "DeviceEnricher"
    }

    override fun enrich(ctx: ScanDataContext) {
        detectFingerprint(ctx)
        applyFingerprintOverride(ctx)
    }

    private fun detectFingerprint(ctx: ScanDataContext) {
        val fingerprintModel = resolveFingerprintModel(ctx)
        if (fingerprintModel != null) {
            if (AppleIdentityConflictGuard.hasLabelConflict(ctx.sanitizedName ?: ctx.name, fingerprintModel)) {
                Log.d(
                    TAG,
                    "Ignoring conflicting known-device fingerprint for ${ctx.mac}: " +
                        "name=${ctx.sanitizedName ?: ctx.name}, model=$fingerprintModel",
                )
                return
            }

            ctx.fingerprintModel = fingerprintModel

            if (spoofingDetector.onDeviceIdentified(ctx.timestamp, fingerprintModel)) {
                Log.w(TAG, "Known-device fingerprint anomaly: ${spoofingDetector.getAnomalyDetails()}")
            } else {
                Log.v(TAG, "Known device fingerprint matched: $fingerprintModel (${ctx.mac})")
            }
        }
    }

    internal fun resolveFingerprintModel(ctx: ScanDataContext): String? =
        KnownDeviceFingerprints.identify(ctx.rawData)
            ?: KnownDeviceFingerprints.identify(ctx.serviceDataRecords(), ctx.manufacturerRecords())

    private fun applyFingerprintOverride(ctx: ScanDataContext) {
        val model = ctx.fingerprintModel
        if (model != null) {
            ctx.vendorModel = model
            if (isAudioDevice(model)) {
                ctx.vendorDeviceType = DeviceType.HEADPHONES
            }
        }
    }

    private fun isAudioDevice(model: String): Boolean {
        return model.contains("Buds") ||
            model.contains("Airpods") ||
            model.contains("Sony") ||
            model.contains("Bose")
    }
}
