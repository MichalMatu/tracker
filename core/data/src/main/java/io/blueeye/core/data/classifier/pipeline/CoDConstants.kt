package io.blueeye.core.data.classifier.pipeline

/**
 * Constants for Class of Device (CoD) classification.
 *
 * CoD is a 24-bit field transmitted during Bluetooth Classic discovery. Structure (Format Type #1):
 * - Bits 0-1: Format Type (always 00 for standard classification)
 * - Bits 2-7: Minor Device Class (interpretation depends on Major Class)
 * - Bits 8-12: Major Device Class
 * - Bits 13-23: Service Classes (independent capability flags)
 *
 * Reference: Bluetooth SIG Assigned Numbers, Section 2.8
 */
object CoDConstants {
    // ========== BIT MASKS ==========

    /** Mask to extract Major Device Class (bits 8-12) */
    const val MASK_MAJOR_CLASS = 0x1F00

    /** Mask to extract Minor Device Class (bits 2-7) */
    const val MASK_MINOR_CLASS = 0x00FC

    /** Mask to check Audio service bit (bit 21) */
    const val MASK_SERVICE_AUDIO = 0x200000

    /** Mask to check Telephony service bit (bit 22) */
    const val MASK_SERVICE_TELEPHONY = 0x400000

    /** Mask to check Rendering service bit (bit 18) */
    const val MASK_SERVICE_RENDERING = 0x040000

    // ========== MAJOR DEVICE CLASSES ==========

    const val MAJOR_COMPUTER = 0x0100
    const val MAJOR_PHONE = 0x0200
    const val MAJOR_AUDIO_VIDEO = 0x0400
    const val MAJOR_PERIPHERAL = 0x0500
    const val MAJOR_WEARABLE = 0x0700
    const val MAJOR_UNCATEGORIZED = 0x1F00

    // ========== MINOR DEVICE CLASSES (shifted left by 2) ==========

    // Phone Minor Classes
    const val MINOR_PHONE_CELLULAR = 0x04
    const val MINOR_PHONE_CORDLESS = 0x08
    const val MINOR_PHONE_SMARTPHONE = 0x0C

    // Computer Minor Classes
    const val MINOR_COMPUTER_DESKTOP = 0x04
    const val MINOR_COMPUTER_SERVER = 0x08
    const val MINOR_COMPUTER_LAPTOP = 0x0C
    const val MINOR_COMPUTER_HANDHELD = 0x10
    const val MINOR_COMPUTER_WEARABLE = 0x18

    // Audio/Video Minor Classes
    const val MINOR_AV_HEADSET = 0x04
    const val MINOR_AV_HANDSFREE = 0x08
    const val MINOR_AV_MICROPHONE = 0x10
    const val MINOR_AV_LOUDSPEAKER = 0x14
    const val MINOR_AV_HEADPHONES = 0x18
    const val MINOR_AV_PORTABLE_AUDIO = 0x1C
    const val MINOR_AV_CAR_AUDIO = 0x20
    const val MINOR_AV_SETTOP_BOX = 0x24
    const val MINOR_AV_HIFI = 0x28
    const val MINOR_AV_VCR = 0x2C
    const val MINOR_AV_VIDEO_CAMERA = 0x30
    const val MINOR_AV_CAMCORDER = 0x34
    const val MINOR_AV_VIDEO_MONITOR = 0x38
    const val MINOR_AV_VIDEO_DISPLAY_SPEAKER = 0x3C

    // Wearable Minor Classes
    const val MINOR_WEARABLE_WATCH = 0x04
    const val MINOR_WEARABLE_PAGER = 0x08
    const val MINOR_WEARABLE_JACKET = 0x0C
    const val MINOR_WEARABLE_HELMET = 0x10
    const val MINOR_WEARABLE_GLASSES = 0x14
}
