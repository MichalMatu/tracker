package io.blueeye.core.decoders

import io.blueeye.core.decoders.parser.generic.ServiceDataExtractor
import io.blueeye.core.model.SensorData
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class GoogleFastPairBeaconDecoder
@Inject
constructor() : BleBeaconDecoder {
    override fun supports(input: BleBeaconScanInput): Boolean {
        val rawData = input.rawData
        val serviceData = ServiceDataExtractor.extract16(rawData)
        return serviceData.containsKey(GoogleFastPairDecoder.SERVICE_UUID)
    }

    override fun decode(input: BleBeaconScanInput): SensorData {
        val data = input.data ?: byteArrayOf()
        val rawData = input.rawData
        val serviceData = ServiceDataExtractor.extract16(rawData)
        val payload = serviceData[GoogleFastPairDecoder.SERVICE_UUID]
            ?: return SensorData(beaconType = "Fast Pair", sensorStatus = "Missing service data")

        val info = GoogleFastPairDecoder.decode(payload)
            ?: return SensorData(beaconType = "Fast Pair", sensorStatus = "Unparsed")

        // We keep the detailed multi-battery info in sensorStatus (SensorData has only single batteryLevel).
        val summary = GoogleFastPairDecoder.getSummary(info)

        return SensorData(
            beaconType = "Fast Pair",
            batteryLevel = info.batteryLevel,
            sensorStatus = summary,
            rawData = payload.toHex(),
        )
    }

    private fun ByteArray.toHex(): String = joinToString("") { "%02X".format(it) }
}
