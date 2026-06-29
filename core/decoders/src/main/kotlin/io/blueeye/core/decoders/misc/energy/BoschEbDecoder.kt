package io.blueeye.core.decoders.misc.energy

import io.blueeye.core.decoders.BleBeaconDecoder
import io.blueeye.core.decoders.BleBeaconScanInput
import io.blueeye.core.model.SensorData
import javax.inject.Inject

class BoschEbDecoder @Inject constructor() : BleBeaconDecoder {

    override fun supports(input: BleBeaconScanInput): Boolean {
        val data = input.data
        if (data == null || data.size < 4) return false

        // Protocol signature: Start byte 0x30
        if (data[0] != 0x30.toByte()) return false

        // Heuristic check for known tags (2nd byte of payload, after Length)
        // 30 (Start) XX (Len) T1 (Tag1) T2 (Tag2)
        // Check if data[2] is 0x98 or 0x80
        val tagPrefix = data[2].toInt() and 0xFF
        return tagPrefix == 0x98 || tagPrefix == 0x80
    }

    override fun decode(input: BleBeaconScanInput): SensorData {
        val data = input.data ?: byteArrayOf()
        var speedKmH: Double? = null
        var cadenceRpm: Double? = null
        var powerWatts: Int? = null
        var battery: Int? = null
        var assistMode: String? = null

        try {
            var i = 0
            while (i < data.size) {
                // Must start with 0x30
                if (data[i] != 0x30.toByte()) break
                // Bounds check for Length byte
                if (i + 1 >= data.size) break

                val len = data[i + 1].toInt() and 0xFF
                // Payload is from i+2 to i+2+len
                val payloadStart = i + 2
                if (payloadStart + len > data.size) break

                // Need at least 2 bytes for Tag
                if (len < 2) {
                    i += 2 + len
                    continue
                }

                val b1 = data[payloadStart].toInt() and 0xFF
                val b2 = data[payloadStart + 1].toInt() and 0xFF
                val tag = (b1 shl 8) or b2

                // Variable length value starts after Tag (2 bytes) and Type (1 byte, usually 0x08)
                // Sometimes Type is missing?
                // Example: 30-02-98-5B (Len 2. Tag 985B. End). No Type, No Value (Value 0).

                var value = 0
                if (len > 3) {
                    // Assume Type is at payloadStart + 2 (1 byte)
                    // Value starts at payloadStart + 3
                    val valueOffset = payloadStart + 3
                    val parseResult = parseVarInt(data, valueOffset, payloadStart + len)
                    value = parseResult
                }

                when (tag) {
                    0x982D -> speedKmH = value / 10.0
                    0x985A -> cadenceRpm = value / 2.0
                    0x985B -> powerWatts = value // Human Power
                    0x985D -> {
                        // Motor Power. If Human Power is set, maybe sum?
                        // Or prefer Human. For now just overwrite if > 0
                        if (powerWatts == null || powerWatts == 0) powerWatts = value
                    }
                    0x8088 -> battery = value
                    0x9809 -> assistMode = "Mode $value"
                }

                i += 2 + len
            }
        } catch (e: Exception) {
            // Parsing error
        }

        val infoParts = mutableListOf<String>()
        if (speedKmH != null) infoParts.add("Speed: ${speedKmH}km/h")
        if (powerWatts != null) infoParts.add("Pwr: ${powerWatts}W")
        if (cadenceRpm != null) infoParts.add("Cad: ${cadenceRpm}rpm")
        if (assistMode != null) infoParts.add(assistMode)

        val statusStr = if (infoParts.isNotEmpty()) infoParts.joinToString(", ") else null

        return SensorData(
            beaconType = "Bosch eBike",
            batteryLevel = battery,
            sensorStatus = statusStr
            // Note: We could add specific fields to SensorData for Speed/Power in the future
        )
    }

    private fun parseVarInt(data: ByteArray, start: Int, end: Int): Int {
        var value = 0
        var shift = 0
        var i = start
        while (i < end) {
            val b = data[i].toInt()
            value = value or ((b and 0x7F) shl shift)
            i++
            shift += 7
            if ((b and 0x80) == 0) break
        }
        return value
    }
}
