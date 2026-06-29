package io.blueeye.core.decoders.govee

import io.blueeye.core.decoders.BleBeaconDecoder
import io.blueeye.core.decoders.BleBeaconScanInput
import io.blueeye.core.model.SensorData
import java.nio.ByteBuffer
import java.nio.ByteOrder
import javax.inject.Inject

class GoveeH5074Decoder @Inject constructor() : BleBeaconDecoder {

    override fun supports(input: BleBeaconScanInput): Boolean {
        val manufacturerId = input.manufacturerId
        val rawData = input.rawData
        // H5074 uses manufacturerId 0xEC88
        if (manufacturerId != 0xEC88) return false

        // Strict check: Must have name "Govee_H5074" to avoid conflict with H5072/H5075
        val name = extractLocalName(rawData)
        return name?.startsWith("Govee_H5074") == true
    }

    override fun decode(input: BleBeaconScanInput): SensorData {
        val data = input.data ?: byteArrayOf()
        // H5074 format payload (after 0xEC88 ID):
        // Byte 0: 0x00 (padding)
        // Byte 1-2: Temp (Little Endian, div 100)
        // Byte 3-4: Hum (Little Endian, div 100)
        // Byte 5: Battery

        try {
            if (data.size < 6) return SensorData()

            val bb = ByteBuffer.wrap(data)
            bb.order(ByteOrder.LITTLE_ENDIAN)

            // Offset 1 because data[0] is padding/version
            val tempRaw = bb.getShort(1)
            val humRaw = bb.getShort(3)
            val battery = bb.get(5).toInt()

            val temp = tempRaw / 100.0
            val hum = humRaw / 100.0

            return SensorData(
                beaconType = "Govee H5074",
                temperatureCelcius = temp,
                humidityPercent = hum,
                batteryLevel = battery,
                rawData = "H5074: ${data.joinToString("") { "%02X".format(it) }}"
            )
        } catch (e: Exception) {
            return SensorData(beaconType = "Govee H5074 (Error)")
        }
    }

    private fun extractLocalName(rawData: ByteArray?): String? {
        if (rawData == null) return null
        var i = 0
        while (i < rawData.size - 1) {
            val len = rawData[i].toInt() and 0xFF
            if (len == 0) break
            if (i + 1 + len > rawData.size) break

            val type = rawData[i + 1].toInt() and 0xFF
            if (type == 0x08 || type == 0x09) {
                val nameBytes = rawData.copyOfRange(i + 2, i + 1 + len)
                return String(nameBytes, Charsets.UTF_8)
            }
            i += 1 + len
        }
        return null
    }
}
