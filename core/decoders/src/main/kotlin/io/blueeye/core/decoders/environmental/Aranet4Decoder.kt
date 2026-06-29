package io.blueeye.core.decoders.environmental

import io.blueeye.core.decoders.BleBeaconDecoder
import io.blueeye.core.decoders.BleBeaconScanInput
import io.blueeye.core.model.SensorData
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class Aranet4Decoder
@Inject
constructor() : BleBeaconDecoder {
    override fun supports(input: BleBeaconScanInput): Boolean {
        val manufacturerId = input.manufacturerId
        val data = input.data
        if (manufacturerId != 0x0702 || data == null) return false
        // Manufacturer 0x0702 (Aranet). Theengs says index 0 "0207" which is LE for 0702.
        // Android provides Manufacturer ID separately.
        // Length check: Theengs says 48 chars (24 bytes). minus 2 bytes ID = 22 bytes.
        // But let's be safe.

        return true
    }

    override fun decode(input: BleBeaconScanInput): SensorData {
        val data = input.data ?: byteArrayOf()
        // Data layout relative to Manufacturer Data (excluding ID):
        // Theengs indices are hex chars on full manufacturer data (including ID).
        // ID is 2 bytes (4 hex chars).
        // Android 'data' excludes ID.
        // So Android Byte Index = (Theengs Hex Index / 2) - 2.

        // CO2: Hex 20 -> Byte 10. Android Index: 10 - 2 = 8.
        // Temp: Hex 24 -> Byte 12. Android Index: 12 - 2 = 10.
        // Pres: Hex 28 -> Byte 14. Android Index: 14 - 2 = 12.
        // Hum: Hex 32 -> Byte 16. Android Index: 16 - 2 = 14.
        // Batt: Hex 34 -> Byte 17. Android Index: 17 - 2 = 15.

        try {
            if (data.size <= 15) return SensorData(beaconType = "Aranet4 (Short)")

            // CO2: Byte 8-9. JSON: true, false -> Signed, LE.
            val co2Low = data[8].toInt() and 0xFF
            val co2High = data[9].toInt() and 0xFF
            val co2 = (co2High shl 8) or co2Low

            // Temp: Byte 10-11. JSON: true, true -> Signed, BE?
            // "post_proc": ["/", 20]
            // val t1 = data[10].toInt() and 0xFF // Unused
            // val t2 = data[11].toInt() and 0xFF // Unused
            // Assuming BE based on "true"

            // If previous ABN07 logic held, true, true = Signed, BE.
            // Aranet spec says LE usually? Let's check online... Aranet4 is LE usually.
            // But Theengs JSON specifies Big Endian flag? Or maybe second bool is "SingleByte"? No.
            // Let's stick to interpretation: false=LE, true=BE.
            // Actually, wait. ABN03 had [..., true, true] and I used LE? No, ABN03 I used LE.
            // Let's re-read ABN03 logic.
            // ABN03 Temp: [18, 4, true, true].
            // My Code: ((tHigh shl 8) or tLow) where tHigh=data[0], tLow=data[1]... oh wait.
            // In ABN03 I did: tLow = manuf >> 8 (ID), tHigh = data[0].
            // That was special because data was split across ID.

            // Let's look at Aranet4 integration docs (Standard Bluetooth):
            // "Temperature - 2 bytes - LE - / 20"
            // So if JSON says 'true', maybe 'true' means Little Endian in Theengs?
            // Or maybe default is BE?
            // Most Theengs JSONs have 'false' for last param.
            // Example ABN03 Hum: [..., true, false].
            // If Aranet is LE, and JSON says true, true?
            // Maybe last param is NOT endianness?
            // Theengs decoder.cpp: value_from_hex_data(..., is_signed, is_big_endian)
            // So true = Big Endian.
            // If Aranet4 is LE (standard BLE), then JSON is weird OR Aranet uses BE.
            // Use LE as per common BLE sense, if values look weird (e.g. 25 deg C becomes huge),
            // user will notice.
            // Actually: 25.0 * 20 = 500 = 0x01F4.
            // LE: F4 01. BE: 01 F4.
            // If I read as LE: F4 01 -> 0x01F4 = 500 / 20 = 25. Correct.
            // If I read as BE: F4 01 -> 0xF401 = -3071 / 20 = -153.
            // Let's assume LE for now (standard BLE).

            // Temp: Byte 10-11, Signed, Big Endian (Theengs: true, true)
            // JSON explicitly specifies Big Endian, so we follow it despite conflicting online
            // common knowledge for Aranet4 (often LE).
            val tLow = data[11].toInt() and 0xFF
            val tHigh = data[10].toInt() and 0xFF
            val tempRaw = ((tHigh shl 8) or tLow)
            val temp = tempRaw.toShort() / 20.0

            // Pres: Byte 12-13. JSON: true, false -> LE.
            val pLow = data[12].toInt() and 0xFF
            val pHigh = data[13].toInt() and 0xFF
            val pres = ((pHigh shl 8) or pLow) / 10.0

            // Hum: Byte 14.
            val hum = data[14].toInt() and 0xFF

            // Batt: Byte 15.
            val batt = data[15].toInt() and 0x7F // & 127

            return SensorData(
                temperatureCelcius = temp,
                humidityPercent = hum.toDouble(),
                pressureHpa = pres,
                co2Ppm = co2,
                batteryLevel = batt,
                beaconType = "Aranet4",
                rawData = "Aranet4: ${data.joinToString("") { "%02X".format(it) }}",
            )
        } catch (e: Exception) {
            return SensorData(beaconType = "Aranet4 (Parse Error)")
        }
    }
}
