package io.blueeye.core.decoders.xiaomi

import io.blueeye.core.decoders.BleBeaconDecoder
import io.blueeye.core.decoders.BleBeaconScanInput
import io.blueeye.core.model.SensorData
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Xiaomi LYWSD03MMC / MJWSD05MMC TH Sensor decoder. Supports ATC, PVVX, and PVVX_DECR firmware
 * formats. Theengs: LYWSD03MMC_json.h Service UUID: 181A
 */
@Singleton
class XiaomiLYWSD03Decoder
@Inject
constructor() : BleBeaconDecoder {
    override fun supports(input: BleBeaconScanInput): Boolean {
        val rawData = input.rawData
        if (rawData == null) return false

        // Check directly for Service Data in raw payload
        // Many devices don't advertise the UUID in the "Service UUIDs" list but include it in Service Data key.
        val serviceData181A = findServiceData(rawData, 0x181A)
        val serviceDataFCD2 = findServiceData(rawData, 0xFCD2)

        if (serviceData181A == null && serviceDataFCD2 == null) return false

        if (serviceData181A != null) {
            // ATC: 11, 13 bytes; PVVX: 15 bytes; PVVX_DECR: 6 bytes; PVVX_ENCR: 11 bytes
            return serviceData181A.size in listOf(6, 11, 13, 15)
        } else {
            // FCD2 (BTHome)
            // BTHome has variable length depending on enabled sensors (Legacy/Precise/etc)
            // Minimum: DeviceInfo(1) + ObjectID(1) + Value(1) = 3 bytes

            // Exclude Shelly devices (they use FCD2 but have their own decoders)
            val localName = extractLocalName(rawData)
            if (localName != null && localName.startsWith("SB", ignoreCase = true)) {
                return false // Let Shelly decoders handle this
            }

            return serviceDataFCD2!!.size >= 3
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

    override fun decode(input: BleBeaconScanInput): SensorData {
        val mac = input.mac
        val rawData = input.rawData
        if (rawData == null) return SensorData(beaconType = "Xiaomi LYWSD03 (Error)")

        val serviceData181A = findServiceData(rawData, 0x181A)
        val serviceDataFCD2 = findServiceData(rawData, 0xFCD2)

        try {
            if (serviceData181A != null) {
                return when (serviceData181A.size) {
                    11 -> decodeATC11(serviceData181A)
                    13 -> decodeATC(serviceData181A)
                    15 -> decodePVVX(serviceData181A)
                    6 -> decodePVVXDecr(serviceData181A)
                    else ->
                        SensorData(
                            beaconType = "Xiaomi LYWSD03 (${serviceData181A.size} bytes)",
                            rawData =
                            "Unknown: ${serviceData181A.joinToString("") { "%02X".format(it) }}",
                        )
                }
            } else if (serviceDataFCD2 != null) {
                return decodeBTHome(serviceDataFCD2)
            }
            return SensorData(beaconType = "Xiaomi LYWSD03 (No Data)")
        } catch (e: Exception) {
            android.util.Log.e(
                "XiaomiDecoder",
                "Parsing Failed for $mac (Raw: ${rawData.joinToString("") { "%02X".format(it) }})",
                e
            )
            return SensorData(beaconType = "Xiaomi LYWSD03 (Parse Error)")
        }
    }

    private fun decodeATC(data: ByteArray): SensorData {
        // ATC format:
        // Bytes 0-5: MAC
        // Bytes 6-7: Temp (BE, /10)
        // Byte 8: Humidity
        // Byte 9: Battery %
        // Bytes 10-11: Voltage (LE, /1000)

        val tHigh = data[6].toInt() and 0xFF
        val tLow = data[7].toInt() and 0xFF
        val temp = ((tHigh shl 8) or tLow) / 10.0

        val humidity = (data[8].toInt() and 0xFF).toDouble()

        val battery = data[9].toInt() and 0xFF

        val vLow = data[10].toInt() and 0xFF
        val vHigh = data[11].toInt() and 0xFF
        val voltage = ((vHigh shl 8) or vLow) / 1000.0

        return SensorData(
            temperatureCelcius = temp,
            humidityPercent = humidity,
            batteryLevel = battery,
            voltageV = voltage,
            beaconType = "Xiaomi LYWSD03MMC (ATC)",
            rawData = "ATC: ${data.joinToString("") { "%02X".format(it) }}",
        )
    }

    private fun decodeATC11(data: ByteArray): SensorData {
        // ATC 11-byte format (no voltage):
        // Bytes 0-5: MAC
        // Bytes 6-7: Temp (BE, signed, /10)
        // Byte 8: Humidity
        // Byte 9: Battery %
        // Byte 10: Frame counter

        val tHigh = data[6].toInt() and 0xFF
        val tLow = data[7].toInt() and 0xFF
        val tempRaw = ((tHigh shl 8) or tLow).toShort()
        val temp = tempRaw / 10.0

        val humidity = (data[8].toInt() and 0xFF).toDouble()
        val battery = data[9].toInt() and 0xFF

        return SensorData(
            temperatureCelcius = temp,
            humidityPercent = humidity,
            batteryLevel = battery,
            beaconType = "Xiaomi LYWSD03MMC (ATC)",
            rawData = "ATC11: ${data.joinToString("") { "%02X".format(it) }}",
        )
    }

    private fun decodePVVX(data: ByteArray): SensorData {
        // PVVX format (custom firmware):
        // Bytes 0-5: MAC (reversed)
        // Bytes 6-7: Temp (Signed, LE, /100) - FIXED: was incorrectly BE
        // Bytes 8-9: Humidity (Signed, LE, /100)
        // Bytes 10-11: Voltage (LE, mV)
        // Byte 12: Battery %
        // Byte 13: Counter
        // Byte 14: Flags

        android.util.Log.d("XiaomiDecoder", "PVVX Raw: ${data.joinToString("") { "%02X".format(it) }}")

        // Temperature: Little Endian signed int16, /100
        val tLow = data[6].toInt() and 0xFF
        val tHigh = data[7].toInt() and 0xFF
        val tempRaw = ((tHigh shl 8) or tLow).toShort()
        val temp = tempRaw / 100.0

        android.util.Log.d("XiaomiDecoder", "PVVX Temp: bytes=[${data[6]}, ${data[7]}] raw=$tempRaw val=$temp")

        // Humidity: Little Endian signed int16, /100
        val hLow = data[8].toInt() and 0xFF
        val hHigh = data[9].toInt() and 0xFF
        val humRaw = ((hHigh shl 8) or hLow).toShort()
        val humidity = humRaw / 100.0

        // Voltage: Little Endian uint16, mV
        val vLow = data[10].toInt() and 0xFF
        val vHigh = data[11].toInt() and 0xFF
        val voltage = ((vHigh shl 8) or vLow) / 1000.0

        val battery = data[12].toInt() and 0xFF

        return SensorData(
            temperatureCelcius = temp,
            humidityPercent = humidity,
            batteryLevel = battery,
            voltageV = voltage,
            beaconType = "Xiaomi LYWSD03MMC (PVVX)",
            rawData = "PVVX: ${data.joinToString("") { "%02X".format(it) }}",
        )
    }

    private fun decodePVVXDecr(data: ByteArray): SensorData {
        // PVVX_DECR format (decrypted):
        // Bytes 0-1: Temp (Signed, BE, /100)
        // Bytes 2-3: Humidity (Signed, LE, /100)
        // Byte 4: Battery %

        val tHigh = data[0].toInt() and 0xFF
        val tLow = data[1].toInt() and 0xFF
        val tempRaw = ((tHigh shl 8) or tLow).toShort()
        val temp = tempRaw / 100.0

        val hLow = data[2].toInt() and 0xFF
        val hHigh = data[3].toInt() and 0xFF
        val humRaw = ((hHigh shl 8) or hLow).toShort()
        val humidity = humRaw / 100.0

        val battery = data[4].toInt() and 0xFF

        return SensorData(
            temperatureCelcius = temp,
            humidityPercent = humidity.toDouble(),
            batteryLevel = battery,
            beaconType = "Xiaomi LYWSD03MMC (PVVX Decr)",
            rawData = "PVVX_DECR: ${data.joinToString("") { "%02X".format(it) }}",
        )
    }

    private fun decodeBTHome(data: ByteArray): SensorData = BtHomeParser.decode(data)

    private fun findServiceData(
        rawData: ByteArray,
        uuid16: Int,
    ): ByteArray? {
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
                    val foundUuid = (uHigh shl 8) or uLow
                    if (foundUuid == uuid16) {
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
