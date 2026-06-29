package io.blueeye.core.data.repository.handler.classic

import io.blueeye.core.data.classifier.DeviceClassifier
import io.blueeye.core.data.db.dao.DeviceDao
import io.blueeye.core.data.repository.VendorRepository
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ClassicScanHandler
@Inject
constructor(
    private val deviceDao: DeviceDao,
    private val vendorRepository: VendorRepository,
    private val persister: ClassicDevicePersister,
    private val deviceClassifier: DeviceClassifier,
) {
    suspend fun handle(
        mac: String,
        name: String?,
        rssi: Int,
        classOfDevice: Int?,
        serviceUuids: List<String> = emptyList(),
    ) {
        // Create context
        val ctx = ClassicScanDataContext.fromScan(mac, name, rssi, classOfDevice, serviceUuids)

        // Step 1: Resolve vendor name

        // Step 1: Resolve vendor name
        ctx.vendorName = vendorRepository.getVendorName(mac, null)

        // Step 2: Load existing device
        ctx.existingDevice = deviceDao.getByFingerprint(ctx.fingerprint)

        // Step 3: Classify device type
        ctx.deviceType = deviceClassifier.classify(classOfDevice, name, ctx.vendorName)

        // Step 4: Persist
        persister.persist(ctx)
    }
}
