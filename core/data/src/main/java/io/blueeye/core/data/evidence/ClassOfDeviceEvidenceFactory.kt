package io.blueeye.core.data.evidence

import io.blueeye.core.data.classifier.pipeline.CoDClassifier
import io.blueeye.core.data.db.entity.DeviceEntity
import io.blueeye.core.model.DetectionConfidence
import io.blueeye.core.model.DetectionEvidence
import io.blueeye.core.model.DeviceType
import io.blueeye.core.model.EvidenceProvenance
import io.blueeye.core.model.EvidenceSource

internal object ClassOfDeviceEvidenceFactory {
    fun build(device: DeviceEntity): DetectionEvidence? {
        val classOfDevice = device.classOfDevice
        val classDeviceType = CoDClassifier.classify(classOfDevice)

        return if (classOfDevice == null || classDeviceType == DeviceType.UNKNOWN) {
            null
        } else {
            DetectionEvidence(
                source = EvidenceSource.CLASS_OF_DEVICE,
                confidence = DetectionConfidence.LOW,
                reasonText =
                    "Classic Bluetooth Class of Device is consistent with ${classDeviceType.name}. " +
                        "Use this as classification context, not as a risk signal.",
                timestamp = device.lastSeenAt,
                rawValue = classOfDevice.toHexClassOfDevice(),
                parsedValue = classDeviceType.name,
                isPassive = true,
                provenance = EvidenceProvenance.CLASSIC_DISCOVERY,
            )
        }
    }
}

private fun Int.toHexClassOfDevice(): String = "0x%06X".format(this)
