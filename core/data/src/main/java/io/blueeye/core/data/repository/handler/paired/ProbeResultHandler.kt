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
    suspend fun handle(
        fingerprint: String,
        params: io.blueeye.core.domain.repository.RepoProbeParams,
    ) {
        val model = params.model
        val services = params.services
        val (refinedType, refinedModel) = refineClassification(model, services)

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

        // Never merge identities solely because GATT service lists match. Generic services such as
        // Device Information and Battery are shared by many unrelated products.
    }

    private fun refineClassification(
        model: String?,
        services: String?,
    ): Pair<DeviceType, String?> {
        var newType = ModelClassifier.classify(model)
        var newModel = model
        val gattResult = deviceClassifier.classifyByGattServices(services, model)

        if (gattResult != null) {
            if (newType == DeviceType.UNKNOWN && gattResult.deviceType != DeviceType.UNKNOWN) {
                newType = gattResult.deviceType
            }
            if (gattResult.modelName != null) {
                newModel = gattResult.modelName
            }
        }
        return Pair(newType, newModel)
    }
}
