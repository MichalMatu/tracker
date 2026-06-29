package io.blueeye.core.decoders.misc.sensor

import io.blueeye.core.decoders.BleBeaconDecoder
import io.blueeye.core.decoders.BleBeaconScanInput
import io.blueeye.core.model.SensorData
import javax.inject.Inject
import javax.inject.Singleton

/**
 * RuuviTag RAWv2 decoder (Data format 5). Theengs: RuuviTag_RAWv2_json.h Manufacturer ID: 0x0499,
 * starts with "990405"
 */
@Singleton
class RuuviTagDecoder
@Inject
constructor() : BleBeaconDecoder {
    override fun supports(input: BleBeaconScanInput): Boolean {
        val manufacturerId = input.manufacturerId
        val data = input.data
        // Manufacturer ID 0x0499 (Ruuvi Innovations)
        if (manufacturerId != 0x0499 || data == null) return false

        // Check for data format 3 (RAWv1) or 5 (RAWv2)
        if (data.isEmpty()) return false

        // In the project, 'data' is usually the payload AFTER the 2-byte manufacturer ID.
        val dataFormat = data[0].toInt() and 0xFF
        return dataFormat == 0x03 || dataFormat == 0x05
    }

    override fun decode(input: BleBeaconScanInput): SensorData {
        val data = input.data ?: byteArrayOf()
        if (data.isEmpty()) return SensorData(beaconType = "RuuviTag (Error)")

        val dataFormat = data[0].toInt() and 0xFF
        return when (dataFormat) {
            0x03 -> decodeRAWv1(data)
            0x05 -> decodeRAWv2(data)
            else -> SensorData(beaconType = "RuuviTag (Unknown Format $dataFormat)")
        }
    }

    private fun decodeRAWv1(data: ByteArray): SensorData {
        if (data.size < 14) return SensorData(beaconType = "RuuviTag RAWv1 (Short)")

        // Temperature: bytes 2-3 (hex 8-11), 2 bytes (integer + fraction? or just 2 bytes?)
        // Theengs uses bf_value_from_hex_data which means Byte[0].Byte[1] e.g. 19.45
        val tInt = data[2].toInt() and 0x7F
        val tFrac = data[3].toInt() and 0xFF
        var temp = tInt + (tFrac / 100.0)
        if ((data[2].toInt() and 0x80) != 0) temp = -temp

        // Humidity: byte 1 (hex 6-7), unsigned, /2.0
        val humidity = (data[1].toInt() and 0xFF) / 2.0

        // Pressure: bytes 4-5 (hex 12-15), + 50000, /100
        val pHigh = data[4].toInt() and 0xFF
        val pLow = data[5].toInt() and 0xFF
        val pressure = ((pHigh shl 8) or pLow + 50000) / 100.0

        // Acceleration: bytes 6-7, 8-9, 10-11
        val ax =
            ((data[6].toInt() and 0xFF shl 8) or (data[7].toInt() and 0xFF)).toShort() /
                1000.0 * 9.80665
        val ay =
            ((data[8].toInt() and 0xFF shl 8) or (data[9].toInt() and 0xFF)).toShort() /
                1000.0 * 9.80665
        val az =
            ((data[10].toInt() and 0xFF shl 8) or (data[11].toInt() and 0xFF)).toShort() /
                1000.0 * 9.80665

        // Voltage: bytes 12-13, /1000
        val vHigh = data[12].toInt() and 0xFF
        val vLow = data[13].toInt() and 0xFF
        val voltage = ((vHigh shl 8) or vLow) / 1000.0

        return SensorData(
            temperatureCelcius = temp,
            humidityPercent = humidity,
            pressureHpa = pressure,
            accelerationX = ax,
            accelerationY = ay,
            accelerationZ = az,
            voltageV = voltage,
            beaconType = "RuuviTag RAWv1",
            rawData = "Ruuvi V1: ${data.joinToString("") { "%02X".format(it) }}",
        )
    }

    private fun decodeRAWv2(data: ByteArray): SensorData {
        if (data.size < 24) return SensorData(beaconType = "RuuviTag RAWv2 (Short)")
        // ... existing RAWv2 logic moved here ...
        val tHigh = data[1].toInt() and 0xFF
        val tLow = data[2].toInt() and 0xFF
        val tempRaw = (tHigh shl 8) or tLow
        val temp = if (tempRaw != 0x8000) tempRaw / 200.0 else null

        val hHigh = data[3].toInt() and 0xFF
        val hLow = data[4].toInt() and 0xFF
        val humRaw = (hHigh shl 8) or hLow
        val humidity = if (humRaw != 0xFFFF) humRaw / 400.0 else null

        val pHigh = data[5].toInt() and 0xFF
        val pLow = data[6].toInt() and 0xFF
        val presRaw = (pHigh shl 8) or pLow
        val pressure = if (presRaw != 0xFFFF) (presRaw + 50000) / 100.0 else null

        val axRaw = ((data[7].toInt() and 0xFF shl 8) or (data[8].toInt() and 0xFF)).toShort()
        val accX = if (axRaw.toInt() != -32768) axRaw / 10000.0 * 9.80665 else null

        val ayRaw = ((data[9].toInt() and 0xFF shl 8) or (data[10].toInt() and 0xFF)).toShort()
        val accY = if (ayRaw.toInt() != -32768) ayRaw / 10000.0 * 9.80665 else null

        val azRaw = ((data[11].toInt() and 0xFF shl 8) or (data[12].toInt() and 0xFF)).toShort()
        val accZ = if (azRaw.toInt() != -32768) azRaw / 10000.0 * 9.80665 else null

        val pwrRaw = (data[13].toInt() and 0xFF shl 8) or (data[14].toInt() and 0xFF)
        val voltageRaw = pwrRaw shr 5
        val voltage = if (voltageRaw != 0x7FF) (voltageRaw + 1600) / 1000.0 else null

        val txPowerRaw = pwrRaw and 0x1F
        val txPower = if (txPowerRaw != 0x1F) txPowerRaw * 2 - 40 else null

        val movementCounter = data[15].toInt() and 0xFF
        val movementDetected = movementCounter > 0

        val batteryPercent = voltage?.let { ((it - 2.0) / 1.0 * 100).coerceIn(0.0, 100.0).toInt() }

        return SensorData(
            temperatureCelcius = temp,
            humidityPercent = humidity,
            pressureHpa = pressure,
            accelerationX = accX,
            accelerationY = accY,
            accelerationZ = accZ,
            voltageV = voltage,
            txPower = txPower,
            movementDetected = movementDetected,
            batteryLevel = batteryPercent,
            beaconType = "RuuviTag RAWv2",
            rawData = "Ruuvi V2: ${data.joinToString("") { "%02X".format(it) }}",
        )
    }
}
