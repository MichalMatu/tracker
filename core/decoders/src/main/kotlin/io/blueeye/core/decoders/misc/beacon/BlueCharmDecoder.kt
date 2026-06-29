package io.blueeye.core.decoders.misc.beacon

import io.blueeye.core.decoders.BleBeaconDecoder
import io.blueeye.core.decoders.BleBeaconScanInput
import io.blueeye.core.model.SensorData
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class BlueCharmDecoder
@Inject
constructor() : BleBeaconDecoder {
    override fun supports(input: BleBeaconScanInput): Boolean {
        val rawData = input.rawData
        if (rawData == null) return false

        // Check for Service Data with UUID FEAA (Eddystone)
        // Payload starts with 21 01 0B or 21 00 0B
        val payload = findServiceDataFEAA(rawData) ?: return false

        if (payload.size < 13) return false

        // 21 01 0B or 21 00 0B
        if (payload[0] != 0x21.toByte()) return false
        if (payload[2] != 0x0B.toByte()) return false
        if (payload[1] != 0x01.toByte() && payload[1] != 0x00.toByte()) return false

        return true
    }

    override fun decode(input: BleBeaconScanInput): SensorData {
        val rawData = input.rawData
        if (rawData == null) return SensorData(beaconType = "BlueCharm (Error)")
        val payload =
            findServiceDataFEAA(rawData)
                ?: return SensorData(beaconType = "BlueCharm (No Data)")

        try {
            // Volt (Index 3, 2 bytes LE) in Volts
            val vLow = payload[3].toInt() and 0xFF
            val vHigh = payload[4].toInt() and 0xFF
            val voltRaw = (vHigh shl 8) or vLow
            val volts = voltRaw / 1000.0

            // Map volts to percent. 3V ~ 100%, 2V ~ 0%
            val batt =
                if (volts >= 3.0) {
                    100
                } else if (volts <= 2.0) {
                    0
                } else {
                    ((volts - 2.0) * 100).toInt()
                }

            // Temp (Index 5, 1 byte BE?) - JSON: 10, 2, false, true -> Signed?, BE. Wait, len 2 hex
            // = 1 byte.
            val tempRaw = payload[5].toInt() and 0xFF // BE 1 byte is just the byte

            // Cal (Index 6, 1 byte LE)
            val calRaw = payload[6].toInt() and 0xFF
            val calVal =
                (calRaw / 256.0) // Formula from JSON seems weird: /256 * 100 > 0 / 100. Roughly
            // part fraction.
            // Let's assume temp = tempRaw + calVal.

            val temp = tempRaw.toDouble() + calVal

            // Acc (Index 7, 2 bytes BE)
            val ax =
                (((payload[7].toInt() shl 8) or (payload[8].toInt() and 0xFF)).toShort())
                    .toDouble()
            val ay =
                (((payload[9].toInt() shl 8) or (payload[10].toInt() and 0xFF)).toShort())
                    .toDouble()
            val az =
                (((payload[11].toInt() shl 8) or (payload[12].toInt() and 0xFF)).toShort())
                    .toDouble()

            return SensorData(
                temperatureCelcius = temp,
                batteryLevel = batt,
                accelerationX = ax,
                accelerationY = ay,
                accelerationZ = az,
                beaconType = "BlueCharm Beacon",
                rawData = "BC08: ${payload.joinToString("") { "%02X".format(it) }}",
            )
        } catch (e: Exception) {
            return SensorData(beaconType = "BlueCharm (Parse Error)")
        }
    }

    private fun findServiceDataFEAA(rawData: ByteArray): ByteArray? {
        var i = 0
        while (i < rawData.size - 1) {
            val len = rawData[i].toInt() and 0xFF
            if (len == 0) break
            if (i + 1 + len > rawData.size) break

            val type = rawData[i + 1].toInt() and 0xFF
            if (type == 0x16) {
                if (i + 3 < rawData.size) {
                    val uLow = rawData[i + 2].toInt() and 0xFF
                    val uHigh = rawData[i + 3].toInt() and 0xFF
                    // FEAA = AA FE (LE)
                    if (uLow == 0xAA && uHigh == 0xFE) {
                        val payloadLen = len - 3
                        if (payloadLen > 0) {
                            return rawData.copyOfRange(i + 4, i + 4 + payloadLen)
                        }
                    }
                }
            }
            i += 1 + len
        }
        return null
    }
}
