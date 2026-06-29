package io.blueeye.core.data.classifier.pipeline

import io.blueeye.core.data.classifier.pipeline.CoDConstants.MAJOR_AUDIO_VIDEO
import io.blueeye.core.data.classifier.pipeline.CoDConstants.MAJOR_COMPUTER
import io.blueeye.core.data.classifier.pipeline.CoDConstants.MAJOR_PERIPHERAL
import io.blueeye.core.data.classifier.pipeline.CoDConstants.MAJOR_PHONE
import io.blueeye.core.data.classifier.pipeline.CoDConstants.MAJOR_UNCATEGORIZED
import io.blueeye.core.data.classifier.pipeline.CoDConstants.MAJOR_WEARABLE
import io.blueeye.core.data.classifier.pipeline.CoDConstants.MASK_MAJOR_CLASS
import io.blueeye.core.data.classifier.pipeline.CoDConstants.MASK_MINOR_CLASS
import io.blueeye.core.data.classifier.pipeline.CoDConstants.MASK_SERVICE_AUDIO
import io.blueeye.core.data.classifier.pipeline.CoDConstants.MASK_SERVICE_TELEPHONY
import io.blueeye.core.data.classifier.pipeline.CoDConstants.MINOR_AV_CAR_AUDIO
import io.blueeye.core.data.classifier.pipeline.CoDConstants.MINOR_AV_HANDSFREE
import io.blueeye.core.data.classifier.pipeline.CoDConstants.MINOR_AV_HEADPHONES
import io.blueeye.core.data.classifier.pipeline.CoDConstants.MINOR_AV_HEADSET
import io.blueeye.core.data.classifier.pipeline.CoDConstants.MINOR_AV_HIFI
import io.blueeye.core.data.classifier.pipeline.CoDConstants.MINOR_AV_LOUDSPEAKER
import io.blueeye.core.data.classifier.pipeline.CoDConstants.MINOR_AV_PORTABLE_AUDIO
import io.blueeye.core.data.classifier.pipeline.CoDConstants.MINOR_AV_SETTOP_BOX
import io.blueeye.core.data.classifier.pipeline.CoDConstants.MINOR_AV_VIDEO_DISPLAY_SPEAKER
import io.blueeye.core.data.classifier.pipeline.CoDConstants.MINOR_AV_VIDEO_MONITOR
import io.blueeye.core.data.classifier.pipeline.CoDConstants.MINOR_COMPUTER_DESKTOP
import io.blueeye.core.data.classifier.pipeline.CoDConstants.MINOR_COMPUTER_HANDHELD
import io.blueeye.core.data.classifier.pipeline.CoDConstants.MINOR_COMPUTER_LAPTOP
import io.blueeye.core.data.classifier.pipeline.CoDConstants.MINOR_COMPUTER_SERVER
import io.blueeye.core.data.classifier.pipeline.CoDConstants.MINOR_COMPUTER_WEARABLE
import io.blueeye.core.data.classifier.pipeline.CoDConstants.MINOR_PHONE_CELLULAR
import io.blueeye.core.data.classifier.pipeline.CoDConstants.MINOR_PHONE_CORDLESS
import io.blueeye.core.data.classifier.pipeline.CoDConstants.MINOR_PHONE_SMARTPHONE
import io.blueeye.core.data.classifier.pipeline.CoDConstants.MINOR_WEARABLE_GLASSES
import io.blueeye.core.data.classifier.pipeline.CoDConstants.MINOR_WEARABLE_WATCH
import io.blueeye.core.model.DeviceType

/**
 * Strategy for classifying Bluetooth devices based on Class of Device (CoD). Constants are defined
 * in [CoDConstants].
 */
object CoDClassifier {
    /**
     * Classify a Bluetooth device based on its Class of Device (CoD).
     *
     * @param classOfDevice The raw CoD value from BluetoothClass or discovery
     * @return The determined DeviceType
     */
    fun classify(classOfDevice: Int?): DeviceType {
        if (classOfDevice == null || classOfDevice == 0) {
            return DeviceType.UNKNOWN
        }

        val majorClass = classOfDevice and MASK_MAJOR_CLASS
        val minorClass = classOfDevice and MASK_MINOR_CLASS

        return when (majorClass) {
            MAJOR_PHONE -> classifyPhone(minorClass)
            MAJOR_COMPUTER -> classifyComputer(minorClass)
            MAJOR_AUDIO_VIDEO -> classifyAudioVideo(minorClass, classOfDevice)
            MAJOR_WEARABLE -> classifyWearable(minorClass)
            MAJOR_PERIPHERAL -> DeviceType.UNKNOWN // Keyboards, mice, etc.
            MAJOR_UNCATEGORIZED -> classifyUncategorized(classOfDevice)
            else -> DeviceType.UNKNOWN
        }
    }

    /** Classify Phone devices (Major Class 0x0200). */
    private fun classifyPhone(minorClass: Int): DeviceType {
        return when (minorClass) {
            MINOR_PHONE_CELLULAR, MINOR_PHONE_SMARTPHONE -> DeviceType.PHONE
            MINOR_PHONE_CORDLESS -> DeviceType.UNKNOWN // Landline phone
            else -> DeviceType.PHONE // Default to phone for this major class
        }
    }

    /**
     * Classify Computer devices (Major Class 0x0100). Note: Minor 0x18 (Wearable Computer) is often
     * used by smartwatches.
     */
    private fun classifyComputer(minorClass: Int): DeviceType {
        return when (minorClass) {
            MINOR_COMPUTER_LAPTOP -> DeviceType.LAPTOP
            MINOR_COMPUTER_DESKTOP -> DeviceType.LAPTOP // Treat as computer
            MINOR_COMPUTER_SERVER -> DeviceType.LAPTOP // Treat as computer
            MINOR_COMPUTER_WEARABLE -> DeviceType.WEARABLE // Smartwatch with Android
            MINOR_COMPUTER_HANDHELD -> DeviceType.PHONE // Tablet/PDA
            else -> DeviceType.LAPTOP // Default to laptop for computers
        }
    }

    /**
     * Classify Audio/Video devices (Major Class 0x0400). This is the most complex category - we
     * must be CONSERVATIVE to avoid misclassifying car audio systems or TVs as headphones.
     */
    private fun classifyAudioVideo(
        minorClass: Int,
        fullCoD: Int,
    ): DeviceType {
        return when (minorClass) {
            // SAFE: Definitely headphones/headsets
            MINOR_AV_HEADSET,
            MINOR_AV_HEADPHONES,
            -> DeviceType.HEADPHONES

            // RISKY: Hands-free could be car kit or headset
            MINOR_AV_HANDSFREE -> DeviceType.HEADPHONES // Accept with caution

            // SPEAKERS: Not headphones
            MINOR_AV_LOUDSPEAKER,
            MINOR_AV_PORTABLE_AUDIO,
            MINOR_AV_HIFI,
            -> DeviceType.UNKNOWN // Generic audio device

            // CAR: Definitely NOT headphones
            MINOR_AV_CAR_AUDIO -> DeviceType.CAR_AUDIO

            // VIDEO DEVICES: TVs, monitors
            MINOR_AV_VIDEO_MONITOR,
            MINOR_AV_VIDEO_DISPLAY_SPEAKER,
            MINOR_AV_SETTOP_BOX,
            -> DeviceType.TV
            else -> {
                // Fallback: Check service bits for audio capability
                if ((fullCoD and MASK_SERVICE_AUDIO) != 0) {
                    DeviceType.UNKNOWN // Generic audio, not confident it's headphones
                } else {
                    DeviceType.UNKNOWN
                }
            }
        }
    }

    /** Classify Wearable devices (Major Class 0x0700). */
    private fun classifyWearable(minorClass: Int): DeviceType {
        return when (minorClass) {
            MINOR_WEARABLE_WATCH -> DeviceType.WEARABLE
            MINOR_WEARABLE_GLASSES -> DeviceType.WEARABLE
            else -> DeviceType.WEARABLE // Default to wearable for this class
        }
    }

    /**
     * Handle Uncategorized devices (Major Class 0x1F00). Use Service Class bits as fallback for
     * classification.
     */
    private fun classifyUncategorized(fullCoD: Int): DeviceType {
        // Check service bits for hints
        val hasAudio = (fullCoD and MASK_SERVICE_AUDIO) != 0
        val hasTelephony = (fullCoD and MASK_SERVICE_TELEPHONY) != 0

        return when {
            hasAudio && hasTelephony -> DeviceType.HEADPHONES // Likely headset
            hasAudio -> DeviceType.UNKNOWN // Could be speaker or headphones
            hasTelephony -> DeviceType.PHONE
            else -> DeviceType.UNKNOWN
        }
    }
}
