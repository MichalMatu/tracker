package io.blueeye.core.decoders.samsung

import io.blueeye.core.decoders.BleBeaconDecoder
import io.blueeye.core.decoders.BleBeaconScanInput
import io.blueeye.core.decoders.parser.samsung.SamsungManufacturerParser
import io.blueeye.core.model.SensorData
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Decoder for Samsung devices (Manufacturer ID 0x0075).
 * Delegates to SamsungManufacturerParser for detailed parsing of SmartTags, Quick Share, etc.
 */
@Singleton
class SamsungGenericDecoder
@Inject
constructor(
    private val samsungManufacturerParser: SamsungManufacturerParser,
) : BleBeaconDecoder {
    override fun supports(input: BleBeaconScanInput): Boolean {
        val manufacturerId = input.manufacturerId
        return manufacturerId == 0x0075
    }

    override fun decode(input: BleBeaconScanInput): SensorData {
        val data = input.data ?: byteArrayOf()
        val samsungData = samsungManufacturerParser.parse(data)

        if (samsungData == null) {
            return SensorData(
                beaconType = "Samsung Device",
                rawData = "Samsung: ${data.joinToString("") { "%02X".format(it) }}",
                sensorStatus = "Unknown 0x0075 Data"
            )
        }

        // Map SamsungDeviceData to SensorData
        val modelName = samsungData.deviceModel ?: "Samsung Device"
        var status = ""

        if (samsungData.isSmartTag) {
            status += "SmartTag ID: ${samsungData.smartTagId} "
        }
        if (samsungData.isOfflineFinding) {
            status += "[Offline Finding] "
        }
        if (samsungData.isQuickShareVisible) {
            status += "[Quick Share] "
        }
        if (samsungData.privacyId != null) {
            status += "Privacy ID: ${samsungData.privacyId.joinToString("") { "%02X".format(it) }} "
        }

        return SensorData(
            beaconType = modelName,
            sensorStatus = status.trim(),
            rawData = "Samsung: ${data.joinToString("") { "%02X".format(it) }}"
        )
    }
}
