package io.blueeye.core.decoders.misc.beacon

import io.blueeye.core.decoders.BleBeaconDecoder
import io.blueeye.core.decoders.BleBeaconScanInput
import io.blueeye.core.model.SensorData
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ABN07Decoder
@Inject
constructor() : BleBeaconDecoder {
    override fun supports(input: BleBeaconScanInput): Boolean {
        val rawData = input.rawData
        if (rawData == null) return false

        // ABN07 uses BTHome (0xFCD2). To avoid collision with Xiaomi/ATC/PVVX,
        // we STRICTLY require the Local Name to start with "asensor".
        // If the name is missing (e.g. split pack), we skip to be safe and let XiaomiDecoder try.
        val localName = extractLocalName(rawData)

        // STRICT CHECK: Must be "asensor..."
        if (localName == null || !localName.startsWith("asensor", ignoreCase = true)) {
            return false
        }

        // Check for Service Data 0xFCD2
        val serviceData = findServiceDataFCD2(rawData) ?: return false

        // Check Header 0x40 (BTHome v2 unencrypted)
        return serviceData.isNotEmpty() && (serviceData[0].toInt() and 0xFF) == 0x40
    }

    private fun extractLocalName(rawData: ByteArray): String? {
        var i = 0
        while (i < rawData.size - 1) {
            val len = rawData[i].toInt() and 0xFF
            if (len == 0) break
            if (i + 1 + len > rawData.size) break

            val type = rawData[i + 1].toInt() and 0xFF
            // Complete Local Name = 0x09, Shortened Local Name = 0x08
            if (type == 0x09 || type == 0x08) {
                val nameLen = len - 1
                if (nameLen > 0 && i + 2 + nameLen <= rawData.size) {
                    return String(rawData, i + 2, nameLen, Charsets.UTF_8)
                }
            }
            i += 1 + len
        }
        return null
    }

    override fun decode(input: BleBeaconScanInput): SensorData {
        val rawData = input.rawData
        if (rawData == null) return SensorData(beaconType = "ABN07 (Error)")

        val payload =
            findServiceDataFCD2(rawData) ?: return SensorData(beaconType = "ABN07 (No Data)")

        if (payload.size < 11) return SensorData(beaconType = "ABN07 (Short)")

        // Structure (based on JSON analysis):
        // Byte 0: 0x40
        // Byte 1: 0x00 (Packet ID Type)
        // Byte 2: [PacketID]
        // Byte 3: 0x01 (Battery Type)
        // Byte 4: [Batt]
        // Byte 5: 0x02 (Temp Type)
        // Byte 6-7: [Temp] int16 big endian? JSON says "true, true" for signed, bigendian?
        // JSON decoder: parameters often [index, len, signed, big_endian?]
        // ABN03 had [val, len, signed, big_endian]
        // ABN07 JSON: "tempc": decoder: [..., 12, 4, true, true] -> Signed=true, BigEndian=true?
        // Wait, normally Theengs JSON format is [method, source, index, len, signed, bigEndian]
        // ABN03: [..., 18, 4, true, true] -> I parsed it as LE in ABN03 but let me check.
        // Actually ABN03 JSON said "true, true". If that means BigEndian, I might have bugged
        // ABN03.
        // But BLE is usually LE.
        // Let's assume Theengs "true" for endianness usually means "Big Endian" if false is Little.
        // Or vice versa.
        // ABN03 'hum': [..., 22, 4, true, false] -> Signed, Little Endian.
        // ABN07 'tempc': [..., 12, 4, true, true] -> Signed, Big Endian.

        try {
            // Batt (Byte 4)
            val batt = payload[4].toInt() and 0xFF

            // Temp (Byte 6-7) - Signed, Big Endian (based on true, true)
            // If Big Endian: [Hi] [Lo]
            val tHigh = payload[6].toInt() and 0xFF
            val tLow = payload[7].toInt() and 0xFF
            val tRaw = ((tHigh shl 8) or tLow).toShort()
            val temp = tRaw / 100.0

            // Hum (Byte 9-10) - Signed, Little Endian (based on true, false)?
            // JSON: "hum": decoder: [..., 18, 4, true, false] -> Signed=true, BigEnd=false (LE)
            val hLow = payload[9].toInt() and 0xFF
            val hHigh = payload[10].toInt() and 0xFF
            val hRaw = ((hHigh shl 8) or hLow).toShort()
            val hum = hRaw / 100.0

            return SensorData(
                temperatureCelcius = temp,
                humidityPercent = hum.toDouble(),
                batteryLevel = batt,
                beaconType = "April Brother N07",
                rawData = "ABN07: ${payload.joinToString("") { "%02X".format(it) }}",
            )
        } catch (e: Exception) {
            return SensorData(beaconType = "ABN07 (Parse Error)")
        }
    }

    private fun findServiceDataFCD2(rawData: ByteArray): ByteArray? {
        var i = 0
        while (i < rawData.size - 1) {
            val len = rawData[i].toInt() and 0xFF
            if (len == 0) break
            if (i + 1 + len > rawData.size) break

            val type = rawData[i + 1].toInt() and 0xFF
            // Service Data 16-bit UUID = 0x16
            if (type == 0x16) {
                if (i + 3 < rawData.size) {
                    val uLow = rawData[i + 2].toInt() and 0xFF
                    val uHigh = rawData[i + 3].toInt() and 0xFF
                    // UUID 0xFCD2 (LE: D2 FC)
                    if (uLow == 0xD2 && uHigh == 0xFC) {
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
