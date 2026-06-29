package io.blueeye.core.data.repository.handler.ble

import io.blueeye.core.data.classifier.pipeline.ModelClassifier
import io.blueeye.core.model.DeviceType

/**
 * Resolves the final device type using priority rules:
 * 1. Model-based classification (from explicit GATT connection) - highest priority
 * 2. Vendor strategy result - medium priority
 * 3. Scan-time classification (appearance, UUIDs) - lower priority
 * 4. Existing type - fallback
 */
object DeviceTypeResolver {
    /**
     * Determines the best device type from available sources.
     *
     * @param existingType The currently stored device type
     * @param modelNumber The model number from explicit GATT connection (if available)
     * @param vendorType Device type from vendor strategy decoding
     * @param scanType Device type from scan-time classification
     * @return The resolved device type with highest confidence
     */
    fun resolve(
        existingType: DeviceType,
        modelNumber: String?,
        vendorType: DeviceType,
        scanType: DeviceType,
        nameType: DeviceType,
    ): DeviceType {
        // 1. Model-based classification is the strongest identity signal.
        if (modelNumber != null) {
            val modelType = ModelClassifier.classify(modelNumber)
            if (modelType != DeviceType.UNKNOWN) return modelType
        }

        // 2. Candidate Selection
        // We have 3 candidates: Existing, Vendor, Scan.
        // We want the "Highest Value" type.
        // Rules:
        // - Laptop/Phone > Tracker/Wearable > Unknown
        // - Specific > Generic

        val candidates = listOf(existingType, vendorType, scanType)
        val resolved = candidates.maxByOrNull { getPriority(it) } ?: DeviceType.UNKNOWN
        return resolveExplicitNameConflict(nameType, resolved)
    }

    private fun getPriority(type: DeviceType): Int {
        return when (type) {
            DeviceType.LAPTOP, DeviceType.PHONE, DeviceType.TABLET -> 100 // High Value
            DeviceType.AUDIO_VIDEO, DeviceType.HEADPHONES, DeviceType.TV, DeviceType.PC, DeviceType.CONSOLE -> 80
            DeviceType.WEARABLE, DeviceType.WATCH -> 60
            DeviceType.TRACKER, DeviceType.AIRTAG, DeviceType.TILE, DeviceType.SAMSUNG_TAG -> 40 // "Find My" often reports as Tracker
            DeviceType.UNKNOWN -> 0
            else -> 20 // All other specific types (Printer, Camera, etc.)
        }
    }

    /** Simplified version for new devices (no existing type). */
    fun resolveForNew(
        vendorType: DeviceType,
        scanType: DeviceType,
        nameType: DeviceType,
    ): DeviceType {
        val resolved = listOf(vendorType, scanType).maxByOrNull { getPriority(it) } ?: DeviceType.UNKNOWN
        return resolveExplicitNameConflict(nameType, resolved)
    }

    private fun resolveExplicitNameConflict(
        nameType: DeviceType,
        resolvedType: DeviceType,
    ): DeviceType {
        if (nameType == DeviceType.UNKNOWN) return resolvedType
        if (resolvedType == DeviceType.UNKNOWN || resolvedType == nameType) return nameType

        return if (nameType in EXPLICIT_NAME_TYPES && resolvedType in CONSUMER_IDENTITY_TYPES) {
            nameType
        } else {
            resolvedType
        }
    }

    private val EXPLICIT_NAME_TYPES =
        setOf(
            DeviceType.HEADPHONES,
            DeviceType.SPEAKER,
            DeviceType.TV,
            DeviceType.PHONE,
            DeviceType.TABLET,
            DeviceType.LAPTOP,
            DeviceType.PC,
            DeviceType.CONSOLE,
            DeviceType.WEARABLE,
            DeviceType.WATCH,
            DeviceType.AIRTAG,
            DeviceType.TILE,
            DeviceType.SAMSUNG_TAG,
            DeviceType.TRACKER,
            DeviceType.TAG,
        )

    private val CONSUMER_IDENTITY_TYPES = EXPLICIT_NAME_TYPES + DeviceType.AUDIO_VIDEO
}
