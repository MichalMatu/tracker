package io.blueeye.core.data.evidence

import io.blueeye.core.data.classifier.pipeline.ModelClassifier
import io.blueeye.core.data.db.entity.DeviceEntity
import io.blueeye.core.model.DetectionConfidence
import io.blueeye.core.model.DetectionEvidence
import io.blueeye.core.model.DeviceType
import io.blueeye.core.model.EvidenceProvenance
import io.blueeye.core.model.EvidenceSource

object ModelEvidenceFactory {
    fun build(device: DeviceEntity): DetectionEvidence? {
        val model =
            device.predictedModel
                ?.takeIf { it.isNotBlank() }
                ?.takeUnless { it == device.lastDeviceName }
                ?: return null
        val modelDeviceType = ModelClassifier.classify(model)
        val provenance = device.modelEvidenceProvenance(model)

        return DetectionEvidence(
            source = EvidenceSource.MODEL,
            confidence = DetectionConfidence.LOW,
            reasonText =
                "Device model metadata is consistent with ${modelDeviceType.displayName(model)}. " +
                    "Use this as identity context, not as a risk signal.",
            timestamp = device.lastSeenAt,
            rawValue = model,
            parsedValue = modelDeviceType.takeUnless { it == DeviceType.UNKNOWN }?.name ?: model,
            isPassive = provenance.isPassiveEvidence,
            provenance = provenance,
        )
    }
}

private fun DeviceEntity.modelEvidenceProvenance(model: String): EvidenceProvenance =
    if (model == modelNumber && lastProbeTimestamp > 0L) {
        EvidenceProvenance.ACTIVE_GATT
    } else {
        EvidenceProvenance.BLE_ADVERTISEMENT
    }

private fun DeviceType.displayName(fallback: String): String =
    if (this == DeviceType.UNKNOWN) {
        fallback
    } else {
        name
    }

private val EvidenceProvenance.isPassiveEvidence: Boolean
    get() =
        when (this) {
            EvidenceProvenance.ACTIVE_GATT,
            EvidenceProvenance.ACTIVE_RFCOMM,
            -> false
            EvidenceProvenance.UNKNOWN,
            EvidenceProvenance.BLE_ADVERTISEMENT,
            EvidenceProvenance.CLASSIC_DISCOVERY,
            EvidenceProvenance.CLASSIC_SDP,
            EvidenceProvenance.USER_ACTION,
            EvidenceProvenance.FOLLOW_ME_ANALYSIS,
            EvidenceProvenance.DEVICE_REGISTRY,
            -> true
        }
