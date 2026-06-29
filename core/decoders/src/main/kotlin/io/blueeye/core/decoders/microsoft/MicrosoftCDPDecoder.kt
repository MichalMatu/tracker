package io.blueeye.core.decoders.microsoft

import io.blueeye.core.decoders.BleBeaconDecoder
import io.blueeye.core.decoders.BleBeaconScanInput
import io.blueeye.core.decoders.parser.microsoft.CdpParser
import io.blueeye.core.decoders.parser.microsoft.SwiftPairParser
import io.blueeye.core.model.SensorData
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Microsoft Generic Decoder (Manufacturer ID 0x0006).
 * Handles Swift Pair, CDP, and other Microsoft protocols.
 */
@Singleton
class MicrosoftCDPDecoder
@Inject
constructor(
    private val swiftPairParser: SwiftPairParser,
    private val cdpParser: CdpParser,
) : BleBeaconDecoder {
    override fun supports(input: BleBeaconScanInput): Boolean {
        val manufacturerId = input.manufacturerId
        return manufacturerId == 0x0006
    }

    override fun decode(input: BleBeaconScanInput): SensorData {
        val data = input.data ?: byteArrayOf()
        // Try Swift Pair
        val swiftPairData = swiftPairParser.parse(data)
        if (swiftPairData != null) {
            val beaconType = swiftPairData.deviceModel ?: "Swift Pair Device"
            var status = if (swiftPairData.isSwiftPair) "Ready to Pair" else "Advertising"

            if (swiftPairData.displayName != null) {
                status += " (${swiftPairData.displayName})"
            }

            return SensorData(
                sensorStatus = status,
                beaconType = beaconType,
                rawData = "SwiftPair: ${data.joinToString("") { "%02X".format(it) }}",
            )
        }

        // Try CDP
        val cdpData = cdpParser.parse(data)
        if (cdpData != null) {
            val beaconType = cdpData.deviceModel ?: "Windows Device"
            var status = "Connected Devices Platform"

            if (cdpData.cdpDeviceType != null) {
                status += " (Type: 0x${"%02X".format(cdpData.cdpDeviceType)})"
            }

            return SensorData(
                sensorStatus = status,
                beaconType = beaconType,
                rawData = "MS-CDP: ${data.joinToString("") { "%02X".format(it) }}",
            )
        }

        // Fallback
        return SensorData(
            sensorStatus = "Microsoft advertising beacon",
            beaconType = "Microsoft Device",
            rawData = "MS-0x0006: ${data.joinToString("") { "%02X".format(it) }}",
        )
    }
}
