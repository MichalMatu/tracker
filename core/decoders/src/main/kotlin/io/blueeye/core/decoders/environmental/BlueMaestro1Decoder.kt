package io.blueeye.core.decoders.environmental

import io.blueeye.core.decoders.BleBeaconDecoder
import io.blueeye.core.decoders.BleBeaconScanInput
import io.blueeye.core.model.SensorData
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class BlueMaestro1Decoder
@Inject
constructor() : BleBeaconDecoder {
    override fun supports(input: BleBeaconScanInput): Boolean {
        val manufacturerId = input.manufacturerId
        val data = input.data
        // Manufacturer 0x0133. JSON 3301 -> LE 0133.
        if (manufacturerId != 0x0133 || data == null) return false

        // Length 24 hex = 12 bytes. payload data is 10 bytes (excluding ID).
        if (data.size < 10) return false

        // JSON Index 4 (Byte 2 of full manuf data) -> Byte 0 of payload data.
        // Wait.
        // Manuf Data: [33][01] [XX][XX] [0D]...
        // Indices:     0   1    2   3    4
        // So Byte 2 of payload (index 2)? No.
        // Payload[0] corresponds to index 2 (byte 2 of full?).
        // Let's trace.
        // Index 0,1 = ID.
        // Index 2,3 = Byte 0? No, bytes are 0..N. Hex is 2x bytes.
        // Hex Index 4 is start of 3rd byte (Byte Index 2).
        // [Byte0][Byte1][Byte2]...
        //   0-1    2-3    4-5
        // So Hex Index 4 is Byte Index 2.
        // Android payload excludes ID (Byte 0,1).
        // So Android Byte Index = 2 - 2 = 0.

        // So we check data[0] == 0x0D.
        return data[0] == 0x0D.toByte()
    }

    override fun decode(input: BleBeaconScanInput): SensorData {
        val data = input.data ?: byteArrayOf()
        try {
            // Batt: Hex 6 -> Byte 3. Android Index: 3 - 2 = 1.
            val batt = data[1].toInt() and 0xFF

            // Temp: Hex 16 -> Byte 8. Android Index: 8 - 2 = 6.
            // 2 bytes, BE (false, true) -> Signed? JSON "value_from_hex_data", ..., false, true.
            // Signed=false? Unsigned.
            // BE=true.
            val tHigh = data[6].toInt() and 0xFF
            val tLow = data[7].toInt() and 0xFF
            val tempRaw = (tHigh shl 8) or tLow
            val temp = tempRaw / 10.0

            return SensorData(
                temperatureCelcius = temp,
                batteryLevel = batt,
                beaconType = "Blue Maestro Tempo Disc",
                rawData = "BM1: ${data.joinToString("") { "%02X".format(it) }}",
            )
        } catch (e: Exception) {
            return SensorData(beaconType = "Blue Maestro (Parse Error)")
        }
    }
}
