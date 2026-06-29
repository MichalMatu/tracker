package io.blueeye.core.data.repository.handler.paired

import io.blueeye.core.data.classifier.DeviceClassifier
import io.blueeye.core.data.classifier.pipeline.ModelClassifier
import io.blueeye.core.data.db.dao.DeviceDao
import io.blueeye.core.model.DeviceType
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ProbeResultHandler @Inject constructor(
    private val deviceDao: DeviceDao,
    private val deviceClassifier: DeviceClassifier,
) {
    companion object {
        private const val MIN_SERVICES_LEN = 20
    }

    suspend fun handle(
        fingerprint: String,
        params: io.blueeye.core.domain.repository.RepoProbeParams
    ) {
        val model = params.model
        val services = params.services
        // 1. Refine Device Type & Model
        val (refinedType, refinedModel) = refineClassification(model, services)

        // 2. Persist Probe Data
        deviceDao.updateProbeData(
            fingerprint = fingerprint,
            status = params.status,
            attempts = params.attempts,
            timestamp = params.timestamp,
            model = refinedModel ?: model,
            serial = params.serial,
            firmware = params.firmware,
            hardware = params.hardware,
            software = params.software,
            manufacturer = params.manufacturer,
            battery = params.battery,
            services = params.services,
            charData = params.charData,
            error = params.error,
            newDeviceType = refinedType,
        )

        // 3. GATT Correlation (Merge devices if GATT structure matches)
        services?.let {
            if (it.length > MIN_SERVICES_LEN) { // Basic sanity check for non-empty service list
                attemptGattCorrelation(fingerprint, it)
            }
        }
    }

    private fun refineClassification(
        model: String?,
        services: String?
    ): Pair<DeviceType, String?> {
        var newType = ModelClassifier.classify(model)
        var newModel = model

        // Try explicit GATT classification
        val gattResult = deviceClassifier.classifyByGattServices(services, model)
        if (gattResult != null) {
            if (gattResult.deviceType != DeviceType.UNKNOWN) {
                newType = gattResult.deviceType
            }
            if (gattResult.modelName != null) {
                newModel = gattResult.modelName
            }
        }
        return Pair(newType, newModel)
    }

    private suspend fun attemptGattCorrelation(fingerprint: String, services: String) {
        val matchingDevice = deviceDao.findDeviceByGattServices(services, fingerprint)
        if (matchingDevice != null) {
             try {
                // Merge this device into the older one (matchingDevice is the original)
                deviceDao.mergeDevices(
                    targetFingerprint = matchingDevice.fingerprint,
                    duplicateFingerprint = fingerprint,
                )
            } catch (e: Exception) {
                android.util.Log.e("ProbeResultHandler", "Failed to merge ${fingerprint} into ${matchingDevice.fingerprint}: ${e.message}")
            }
        }
    }
}
