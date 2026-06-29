package io.blueeye.core.decoders

import io.blueeye.core.model.SensorData
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AltBeaconDecoder
@Inject
constructor() : BleBeaconDecoder {
    override fun supports(input: BleBeaconScanInput): Boolean {
        val data = input.data
        // Android ScanRecord gives manufacturerId separately; "data" is the bytes AFTER the company ID.
        // AltBeacon begins with 0xBEAC (big endian) right after the company ID.
        if (data == null || data.size < 2) return false
        return (data[0].toInt() and 0xFF) == 0xBE && (data[1].toInt() and 0xFF) == 0xAC
    }

    override fun decode(input: BleBeaconScanInput): SensorData {
        val data = input.data ?: byteArrayOf()
        // Layout (after company ID):
        // [0-1]=0xBEAC, [2-21]=BeaconId(20), [22]=RefRSSI, [23]=RSVD
        if (data.size < 24) {
            return SensorData(
                beaconType = "AltBeacon",
                sensorStatus = "Malformed (len=${data.size})",
                rawData = data.toHex(),
            )
        }

        val beaconId = data.copyOfRange(2, 22).toHex()
        val refRssi = data[22].toInt() // signed
        val rsvd = data[23].toInt() and 0xFF

        return SensorData(
            beaconType = "AltBeacon",
            sensorStatus = "BeaconId=$beaconId RefRSSI=${refRssi}dBm RSVD=0x${rsvd.toString(
                16
            ).uppercase().padStart(2, '0')}",
            rawData = data.toHex(),
        )
    }

    private fun ByteArray.toHex(): String = joinToString("") { "%02X".format(it) }
}
