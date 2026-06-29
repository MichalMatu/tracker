package io.blueeye.core.decoders.misc.beacon

import io.blueeye.core.decoders.BleBeaconDecoder
import io.blueeye.core.decoders.BleBeaconScanInput
import io.blueeye.core.model.SensorData
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ABN03Decoder
@Inject
constructor() : BleBeaconDecoder {
    override fun supports(input: BleBeaconScanInput): Boolean {
        val rawData = input.rawData
        if (rawData == null) return false

        // We need to look for Service Data with UUID 0xAB03
        // Theengs logic: condition: [servicedata, =, 30, index, 0, ab03]
        // This usually means the decoder expects the Service Data payload to start with AB03
        // In standard Android scan record parsing:
        // Service Data - 16 bit UUID (Type 0x16)
        // [Len] [0x16] [UUID_LO] [UUID_HI] [Data...]
        // The "servicedata" in Theengs often includes the UUID bytes if it comes from the unified
        // decoder concept,
        // OR it matches specifically the device spec.
        // Let's search for 0xAB03 service data in the raw buffer.

        return findServiceDataAB03(rawData) != null
    }

    override fun decode(input: BleBeaconScanInput): SensorData {
        val rawData = input.rawData
        if (rawData == null) return SensorData(beaconType = "ABN03 (Error)")

        val serviceData =
            findServiceDataAB03(rawData) ?: return SensorData(beaconType = "ABN03 (No Data)")

        // Layout based on ABN03_json.h
        // Size: 15 bytes (30 hex chars)
        // [0-1]: UUID (AB 03) - included because Theengs condition 'index 0 ab03' matched it.
        // Actually, 'findServiceDataAB03' usually returns the data AFTER the UUID if we follow
        // standard Android convention,
        // BUT Theengs condition says 'index 0 = ab03'.
        // This implies Theengs treats the UUID as part of the 'servicedata' blob it checks.
        // If my 'findServiceDataAB03' returns just the payload, we need to adjust indices.

        // The JS decoder indices:
        // MAC: hex index 4 -> byte index 2 (skips 0,1 which are UUID)
        // Batt: hex index 16 -> byte index 8
        // Temp: hex index 18 -> byte index 9
        // Hum: hex index 22 -> byte index 11
        // Lux: hex index 26 -> byte index 13

        // If 'serviceData' contains ONLY the payload (without UUID), we shift indices by -2.
        // Payload size should be 15 - 2 = 13 bytes.

        // Let's assume 'serviceData' is just the payload (excluding UUID).
        // Then:
        // MAC starts at 0
        // Batt at 6
        // Temp at 7
        // Hum at 9
        // Lux at 11

        try {
            if (serviceData.size < 13) return SensorData(beaconType = "ABN03 (Short)")

            // Battery (Byte 6)
            val batt = serviceData[6].toInt() and 0xFF

            // Temp (Byte 7-8), Signed, Big Endian (based on Theengs 'true, true')
            val tHigh = serviceData[7].toInt() and 0xFF
            val tLow = serviceData[8].toInt() and 0xFF
            val tRaw = ((tHigh shl 8) or tLow).toShort()
            val temp = tRaw / 8.0

            // Hum (Byte 9-10), LE, /2
            val hLow = serviceData[9].toInt() and 0xFF
            val hHigh = serviceData[10].toInt() and 0xFF
            val hRaw = (hHigh shl 8) or hLow
            val hum = hRaw / 2.0

            // Lux (Byte 11-12), LE
            val lLow = serviceData[11].toInt() and 0xFF
            val lHigh = serviceData[12].toInt() and 0xFF
            val lux = ((lHigh shl 8) or lLow).toDouble()

            return SensorData(
                temperatureCelcius = temp,
                humidityPercent = hum,
                batteryLevel = batt,
                illuminanceLux = lux,
                beaconType = "April Brother N03",
                rawData = "ABN03 Payload: ${serviceData.joinToString("") { "%02X".format(it) }}",
            )
        } catch (e: Exception) {
            return SensorData(beaconType = "ABN03 (Parse Error)")
        }
    }

    private fun findServiceDataAB03(rawData: ByteArray): ByteArray? {
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
                    // UUID 0xAB03. In packet it's Little Endian: 03 AB
                    // So uLow should be 03, uHigh should be AB
                    if (uLow == 0x03 && uHigh == 0xAB) {
                        // Found it.
                        // Payload is from i+4 to i+1+len
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
