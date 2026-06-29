package io.blueeye.core.data.evidence

import io.blueeye.core.data.classifier.ble.BleAppearanceClassifier
import io.blueeye.core.data.db.entity.DeviceEntity
import io.blueeye.core.model.DetectionConfidence
import io.blueeye.core.model.DetectionEvidence
import io.blueeye.core.model.DeviceType
import io.blueeye.core.model.EvidenceProvenance
import io.blueeye.core.model.EvidenceSource

internal object AppearanceEvidenceFactory {
    fun build(
        device: DeviceEntity,
        advertisementEvidence: AdvertisementEvidenceParser.AdvertisementEvidence,
    ): DetectionEvidence? {
        val appearance = advertisementEvidence.appearance
        val appearanceDeviceType = BleAppearanceClassifier.classify(appearance)

        return if (appearance == null || appearanceDeviceType == DeviceType.UNKNOWN) {
            null
        } else {
            DetectionEvidence(
                source = EvidenceSource.APPEARANCE,
                confidence = DetectionConfidence.LOW,
                reasonText =
                    "BLE appearance value is consistent with ${appearanceDeviceType.name}. " +
                        "Use this as classification context, not as a risk signal.",
                timestamp = device.lastSeenAt,
                rawValue = appearance.toHexAppearance(),
                parsedValue = appearanceDeviceType.name,
                isPassive = true,
                provenance = EvidenceProvenance.BLE_ADVERTISEMENT,
            )
        }
    }
}

private fun Int.toHexAppearance(): String = "0x%04X".format(this)
