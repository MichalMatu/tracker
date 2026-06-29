package io.blueeye.core.decoders.misc.beacon

import io.blueeye.core.decoders.BleBeaconDecoder
import io.blueeye.core.decoders.BleBeaconScanInput
import io.blueeye.core.model.SensorData
import java.nio.ByteBuffer
import java.nio.ByteOrder
import javax.inject.Inject
import javax.inject.Singleton

/** MikroTik TG-BT5 tags decoder. Theengs: Mikrotik_json.h Manufacturer ID: 0x094F */
@Singleton
class MikrotikDecoder
@Inject
constructor() : BleBeaconDecoder {
    override fun supports(input: BleBeaconScanInput): Boolean {
        val manufacturerId = input.manufacturerId
        val data = input.data
        // Manufacturer ID 0x094F (LE "4f09")
        if (manufacturerId != 0x094F || data == null) return false

        // Condition: starts with 01 00 (after ID) and length 40 hex = 20 bytes
        // But 'data' usually has the ID stripped if passed from some providers,
        // or included if raw from manufacturer data.
        // Theengs 'data' source for manufacturerdata includes the ID.
        // So byte 0,1 is 4F 09, byte 2,3 is 01 00.
        if (data.size != 20) return false

        return (data[2].toInt() and 0xFF) == 0x01 && (data[3].toInt() and 0xFF) == 0x00
    }

    override fun decode(input: BleBeaconScanInput): SensorData {
        val data = input.data ?: byteArrayOf()
        if (data.size < 20) return SensorData(beaconType = "MikroTik (Short)")

        try {
            val buffer = ByteBuffer.wrap(data)

            // accx: byte 6, 2 bytes signed, BE
            buffer.order(ByteOrder.BIG_ENDIAN)
            val accX = buffer.getShort(6) / 256.0
            val accY = buffer.getShort(8) / 256.0
            val accZ = buffer.getShort(10) / 256.0

            // temp: byte 12, 2 bytes signed, BE
            val temp = buffer.getShort(12) / 256.0

            // uptime: byte 14, 4 bytes unsigned, LE
            buffer.order(ByteOrder.LITTLE_ENDIAN)
            val uptime = buffer.getInt(14).toLong() and 0xFFFFFFFFL

            // flags: byte 18
            val flags = data[18].toInt() and 0xFF
            val reed = (flags and 0x01) != 0
            val tilt = (flags and 0x02) != 0
            val fall = (flags and 0x04) != 0
            val impactX = (flags and 0x08) != 0
            val impactY = (flags and 0x10) != 0
            val impactZ = (flags and 0x20) != 0

            val statusList = mutableListOf<String>()
            if (reed) statusList.add("Reed")
            if (tilt) statusList.add("Tilt")
            if (fall) statusList.add("Fall")
            if (impactX || impactY || impactZ) statusList.add("Impact")

            // batt: byte 19, & 127
            val battery = data[19].toInt() and 0x7F

            return SensorData(
                temperatureCelcius = temp,
                batteryLevel = battery,
                accelerationX = accX,
                accelerationY = accY,
                accelerationZ = accZ,
                sensorStatus = statusList.joinToString(", ").ifEmpty { "Idle" },
                beaconType = "MikroTik TG-BT5",
                rawData =
                "uptime: ${uptime}s, Mikrotik: ${data.joinToString("") { "%02X".format(it) }}",
            )
        } catch (e: Exception) {
            return SensorData(beaconType = "MikroTik (Parse Error)")
        }
    }
}
