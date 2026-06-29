package io.blueeye.core.decoders.misc.health

import io.blueeye.core.decoders.BleBeaconDecoder
import io.blueeye.core.decoders.BleBeaconScanInput
import io.blueeye.core.model.SensorData
import javax.inject.Inject
import kotlin.experimental.and

class LaicaPS7200LDecoder
@Inject
constructor() : BleBeaconDecoder {
    override fun supports(input: BleBeaconScanInput): Boolean {
        val data = input.data
        // Laica PS7200L broadcasts weight in the Manufacturer ID field (first 2 bytes).
        // Payload pattern: [WeightHi][WeightLo][0x02][Var][0x86][0xFF][0xFF][0x21][Var][0xAA]
        // This corresponds to Manufacturer Data:
        // ID: [WeightHi][WeightLo] (parsed as Little Endian by Android usually, so WeightLo goes to
        // MSB)
        // Data: [0x02][Var][0x86][0xFF][0xFF][0x21][Var][0xAA]
        // Or similar.
        // The text example: 09 ff 02 f8 02 47 86 ff ff 21 93 aa
        // Length 09 (8 bytes payload? No, 09 usually includes Type).
        // If 09 is length of structure: 1 byte type + 8 bytes data.
        // Data: 02 f8 02 47 86 ff ff 21. Total 8 bytes.
        // Wait, where is 93 aa? Maybe next structure.
        // If so, Data passed to supports is rest after ID.
        // ID: 02 f8 (2 bytes)
        // Rest: 02 47 86 ff ff 21 (6 bytes).
        // Constant check:
        // byte 2: 0x86
        // byte 3: 0xFF
        // byte 4: 0xFF
        // byte 5: 0x21

        if (data == null || data.size < 6) return false

        // Check constants at offset 2 (0x86) -> index 2
        // Based on: 02(0) 47(1) 86(2) ff(3) ff(4) 21(5)
        if (data[2] != 0x86.toByte() ||
            data[3] != 0xFF.toByte() ||
            data[4] != 0xFF.toByte() ||
            data[5] != 0x21.toByte()
        ) {
            return false
        }

        return true
    }

    override fun decode(input: BleBeaconScanInput): SensorData {
        val manufacturerId = input.manufacturerId
        // Decode weight from manufacturerId
        // Assuming Android parsed standard LE: ID = (Byte1 << 8) | Byte0
        // But device sends BE: Byte0 = High, Byte1 = Low.
        // We want (Byte0 << 8) | Byte1.
        // Byte0 = ID & 0xFF. Byte1 = ID >> 8.
        // So reconstructed BE = (Byte0 << 8) | Byte1 = ((ID & 0xFF) << 8) | ((ID >> 8) & 0xFF)

        val weightRaw =
            if (manufacturerId != null) {
                ((manufacturerId and 0xFF) shl 8) or ((manufacturerId ushr 8) and 0xFF)
            } else {
                0
            }

        val weightKg = weightRaw / 10.0

        return SensorData(
            weightKg = weightKg,
            beaconType = "Laica PS7200L",
            sensorStatus = "Active",
        )
    }
}
