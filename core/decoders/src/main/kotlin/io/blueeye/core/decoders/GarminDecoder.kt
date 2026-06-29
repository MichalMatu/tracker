package io.blueeye.core.decoders

import io.blueeye.core.model.DeviceType

/**
 * Decoder for Garmin BLE Manufacturer Data.
 *
 * Garmin Company ID: 0x0087
 *
 * Based on joker.lua Wireshark dissector by Guillaume Celosia & Mathieu Cunche (INRIA).
 * Source: https://github.com/gcelosia/joker
 */
object GarminDecoder {
    const val MANUFACTURER_ID_GARMIN = 0x0087

    data class GarminInfo(
        val deviceType: DeviceType,
        val modelId: Int? = null,
        val modelName: String? = null,
    )

    // Known Model IDs from joker.lua
    private val knownModels = mapOf(
        0x0657 to "Forerunner 620",
        0x0660 to "Forerunner 220",
        0x06e5 to "Forerunner 920",
        0x072c to "Edge 1000",
        0x075d to "Vivoki",
        0x0773 to "Vivoactive",
        0x07a4 to "Vivosmart",
        0x07c4 to "Epix",
        0x0802 to "Fenix 3/Quatix 3",
        0x0857 to "Vivosmart",
        0x0863 to "Edge 25",
        0x0864 to "Forerunner 25",
        0x0869 to "Forerunner 225",
        0x086c to "Forerunner 630",
        0x086d to "Forerunner 230",
        0x086e to "Forerunner 735XT",
        0x0870 to "Vivoactive",
        0x08da to "Approach S20",
        0x08f4 to "Approach X40",
        0x08f5 to "Fenix 3",
        0x0921 to "Vivoactive HR",
        0x092b to "Vivosmart HR+",
        0x092c to "Vivosmart HR",
        0x0939 to "Vivosmart HR",
        0x096d to "Fenix 3 HR",
        0x097f to "Forerunner 235",
        0x09c7 to "Forerunner 35",
        0x09f0 to "Fenix 5s",
        0x0a2c to "Fenix 5x/Tactix Charlie",
        0x0a2e to "Vivofit",
        0x0a3e to "Vivosmart 3",
        0x0a3f to "Vivosport",
        0x0a60 to "Approach S60",
        0x0a83 to "Forerunner 935",
        0x0a89 to "Fenix 5",
        0x0a8c to "Vivoactive 3",
        0x0ad4 to "Vivomove HR",
        0x0aed to "Fenix 5s APAC",
        0x0b46 to "Forerunner 645",
        0x0b4b to "Forerunner 30",
        // Extended models (newer devices)
        0x0b97 to "Fenix 5 Plus",
        0x0bc4 to "Instinct",
        0x0be6 to "Forerunner 245",
        0x0c17 to "Venu",
        0x0c3d to "Fenix 6S",
        0x0c3e to "Fenix 6",
        0x0c3f to "Fenix 6X",
        0x0c84 to "Forerunner 945",
        0x0d61 to "Venu 2",
        0x0dc5 to "Fenix 7",
        0x0e02 to "Forerunner 955",
        0x0e4a to "Forerunner 265",
        0x0e5c to "Fenix 7 Pro",
    )

    /**
     * Decode Garmin Manufacturer Data.
     * @param manufacturerData The manufacturer data bytes (after Company ID).
     */
    fun decode(manufacturerData: ByteArray): GarminInfo? {
        if (manufacturerData.size < 2) return null

        // Model ID is 16-bit, Big Endian (as per joker.lua)
        val modelId = ((manufacturerData[0].toInt() and 0xFF) shl 8) or
            (manufacturerData[1].toInt() and 0xFF)

        val modelName = knownModels[modelId]

        val deviceType = when {
            modelName?.contains("Forerunner") == true -> DeviceType.FITNESS
            modelName?.contains("Fenix") == true -> DeviceType.WATCH
            modelName?.contains("Vivo") == true -> DeviceType.WEARABLE
            modelName?.contains("Venu") == true -> DeviceType.WATCH
            modelName?.contains("Instinct") == true -> DeviceType.WATCH
            modelName?.contains("Edge") == true -> DeviceType.FITNESS // Bike computer
            modelName?.contains("Approach") == true -> DeviceType.WEARABLE // Golf
            modelName?.contains("Epix") == true -> DeviceType.WATCH
            else -> DeviceType.WEARABLE
        }

        return GarminInfo(
            deviceType = deviceType,
            modelId = modelId,
            modelName = modelName ?: "Garmin (0x${modelId.toString(16).uppercase().padStart(4, '0')})"
        )
    }

    /**
     * Get a human-readable summary.
     */
    fun getSummary(info: GarminInfo): String {
        return info.modelName ?: "Garmin Device"
    }
}
