package io.blueeye.core.decoders.beacon

import io.blueeye.core.decoders.BleBeaconDecoder
import io.blueeye.core.decoders.BleBeaconScanInput
import io.blueeye.core.model.SensorData
import javax.inject.Inject

/**
 * Decoder for Google Eddystone beacon format (UID, URL, TLM).
 * Reference: https://github.com/google/eddystone/blob/master/protocol-specification.md
 */
class EddystoneDecoder @Inject constructor() : BleBeaconDecoder {
    override val priority: Int = -100

    companion object {
        const val EDDYSTONE_SERVICE_UUID = "feaa"
        const val FRAME_UID = 0x00.toByte()
        const val FRAME_URL = 0x10.toByte()
        const val FRAME_TLM = 0x20.toByte()
        const val FRAME_EID = 0x30.toByte()
    }

    override fun supports(input: BleBeaconScanInput): Boolean {
        val serviceUuids = input.serviceUuids
        return serviceUuids.any { it.contains(EDDYSTONE_SERVICE_UUID, ignoreCase = true) }
    }

    @OptIn(ExperimentalStdlibApi::class)
    override fun decode(input: BleBeaconScanInput): SensorData? {
        val rawData = input.rawData
        if (rawData == null) return null

        // Find Service Data for FEAA
        // Raw iteration is needed because 'data' param usually contains Manufacturer Data only
        var index = 0
        while (index < rawData.size) {
            val length = rawData[index].toInt() and 0xFF
            if (length == 0) break
            if (index + 1 >= rawData.size) break

            val type = rawData[index + 1].toInt() and 0xFF

            // Service Data - 16-bit UUID (0x16)
            if (type == 0x16) {
                if (index + 4 > rawData.size) break
                val uuid = ((rawData[index + 3].toInt() and 0xFF) shl 8) or (rawData[index + 2].toInt() and 0xFF)

                if (uuid == 0xFEAA) {
                    val payload = rawData.copyOfRange(index + 4, index + 1 + length)
                    if (payload.isNotEmpty()) {
                        return parseEddystonePayload(payload)
                    }
                }
            }
            index += length + 1
        }

        return null
    }

    private fun parseEddystonePayload(payload: ByteArray): SensorData? {
        val frameType = payload[0]

        return when (frameType) {
            FRAME_UID -> {
                // UID Frame: Type(1) + Tx(1) + Namespace(10) + Instance(6)
                if (payload.size < 18) return null
                val txPower = payload[1].toInt()
                val namespace = payload.copyOfRange(2, 12).toHexString()
                val instance = payload.copyOfRange(12, 18).toHexString()

                SensorData(
                    beaconType = "Eddystone UID: $namespace-$instance",
                    txPower = txPower,
                    rawData = payload.toHexString()
                )
            }
            FRAME_URL -> {
                // URL Frame: Type(1) + Tx(1) + Scheme(1) + URL(0-17)
                if (payload.size < 4) return null
                val txPower = payload[1].toInt()
                val scheme = decodeScheme(payload[2])
                val url = decodeUrl(payload.copyOfRange(3, payload.size))

                val fullUrl = scheme + url

                SensorData(
                    beaconType = "Eddystone URL: $fullUrl",
                    txPower = txPower,
                    rawData = payload.toHexString()
                )
            }
            FRAME_TLM -> {
                // TLM Frame: Type(1) + Ver(1) + Bat(2) + Temp(2) + Count(4) + Time(4)
                if (payload.size < 14) return null
                val batteryMv = ((payload[2].toInt() and 0xFF) shl 8) or (payload[3].toInt() and 0xFF)
                val tempRaw = ((payload[4].toInt() and 0xFF) shl 8) or (payload[5].toInt() and 0xFF)
                // Temp is 8.8 fixed point
                val tempC = tempRaw / 256.0

                SensorData(
                    beaconType = "Eddystone TLM",
                    temperatureCelcius = tempC,
                    batteryLevel = (batteryMv / 1000.0 * 100 / 3.3).toInt().coerceIn(0, 100), // Approx %
                    voltageV = batteryMv / 1000.0,
                    rawData = payload.toHexString()
                )
            }
            else -> null
        }
    }

    private fun decodeScheme(scheme: Byte): String {
        return when (scheme.toInt()) {
            0x00 -> "http://www."
            0x01 -> "https://www."
            0x02 -> "http://"
            0x03 -> "https://"
            else -> "unknown://"
        }
    }

    private fun decodeUrl(bytes: ByteArray): String {
        val sb = StringBuilder()
        for (b in bytes) {
            when (b.toInt()) {
                0x00 -> sb.append(".com/")
                0x01 -> sb.append(".org/")
                0x02 -> sb.append(".edu/")
                0x03 -> sb.append(".net/")
                0x04 -> sb.append(".info/")
                0x05 -> sb.append(".biz/")
                0x06 -> sb.append(".gov/")
                0x07 -> sb.append(".com")
                0x08 -> sb.append(".org")
                0x09 -> sb.append(".edu")
                0x0A -> sb.append(".net")
                0x0B -> sb.append(".info")
                0x0C -> sb.append(".biz")
                0x0D -> sb.append(".gov")
                else -> if (b >= 0x21 && b <= 0x7E) {
                    sb.append(b.toInt().toChar())
                }
            }
        }
        return sb.toString()
    }

    private fun decodeUptime(payload: ByteArray): Long {
        if (payload.size < 14) return 0
        // Time is bytes 10-13 (big endian)
        return ((payload[10].toLong() and 0xFF) shl 24) or
            ((payload[11].toLong() and 0xFF) shl 16) or
            ((payload[12].toLong() and 0xFF) shl 8) or
            (payload[13].toLong() and 0xFF)
                // Result is in 0.1 seconds resolution
                .div(10)
    }

    @OptIn(ExperimentalStdlibApi::class)
    private fun ByteArray.toHexString(): String = joinToString("") { "%02x".format(it) }
}
