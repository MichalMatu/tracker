package io.blueeye.core.decoders.inkbird

import io.blueeye.core.decoders.BleBeaconDecoder
import io.blueeye.core.decoders.BleBeaconScanInput
import io.blueeye.core.model.SensorData
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Inkbird iBBQ BBQ Thermometer decoder. Supports IBT-2X(S), IBT-4XS, IBT-6XS models. Theengs:
 * IBT_2X_json.h, IBT_4XS_json.h, IBT_6XS_SOLIS6_json.h
 */
@Singleton
class InkbirdIBBQDecoder
@Inject
constructor() : BleBeaconDecoder {
    override fun supports(input: BleBeaconScanInput): Boolean {
        val data = input.data
        val rawData = input.rawData
        if (data == null || rawData == null) return false

        // Common headers: 00000000 or 01000000
        if (data.size < 14) return false
        val header =
            (data[0].toInt() and 0xFF).toLong() shl
                24 or
                ((data[1].toInt() and 0xFF).toLong() shl 16) or
                ((data[2].toInt() and 0xFF).toLong() shl 8) or
                (data[3].toInt() and 0xFF).toLong()

        if (header != 0L && header != 0x01000000L) return false

        val localName = extractLocalName(rawData)
        return localName?.startsWith("iBBQ") == true ||
            localName?.startsWith("xBBQ") == true ||
            data.size >= 14
    }

    override fun decode(input: BleBeaconScanInput): SensorData {
        val data = input.data ?: byteArrayOf()
        if (data.size < 12) return SensorData(beaconType = "Inkbird iBBQ (Short)")

        try {
            // Temperature mapping in iBBQ:
            // Probes start at byte 10 (hex index 20)
            // Each probe is 2 bytes, BE, Signed, /10
            // Offset check: Theengs says 20,4 for P1? (Wait, hex 20 is byte 10. 4 hex chars = 2
            // bytes)

            val temps = mutableListOf<Double?>()
            // Typically devices have 2, 4 or 6 probes
            val maxProbes = (data.size - 10) / 2

            for (i in 0 until maxProbes) {
                val offset = 10 + (i * 2)
                if (offset + 1 < data.size) {
                    val low = data[offset].toInt() and 0xFF
                    val high = data[offset + 1].toInt() and 0xFF

                    if (low == 0xFF && high == 0xFF) {
                        temps.add(null)
                    } else {
                        val tempRaw = ((high shl 8) or low).toShort()
                        if (tempRaw == (-1).toShort()) {
                            temps.add(null)
                        } else {
                            temps.add(tempRaw / 10.0)
                        }
                    }
                }
            }

            val activeProbes =
                temps.mapIndexedNotNull { index, temp ->
                    temp?.let { "P${index + 1}: %.1f°C".format(it) }
                }

            val statusStr =
                if (activeProbes.isNotEmpty()) {
                    activeProbes.joinToString(", ")
                } else {
                    "No probes connected"
                }

            return SensorData(
                temperatureCelcius = temps.firstOrNull(),
                sensorStatus = statusStr,
                beaconType = "Inkbird iBBQ Thermometer",
                rawData =
                "iBBQ (${temps.size}p): ${data.joinToString("") { "%02X".format(it) }}",
            )
        } catch (e: Exception) {
            return SensorData(beaconType = "Inkbird iBBQ (Parse Error)")
        }
    }

    private fun extractLocalName(rawData: ByteArray): String? {
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
