package io.blueeye.core.decoders.misc

import io.blueeye.core.decoders.BleBeaconDecoder
import io.blueeye.core.decoders.BleBeaconScanInput
import io.blueeye.core.model.SensorData
import javax.inject.Inject

class Eq3EqivaDecoder
@Inject
constructor() : BleBeaconDecoder {
    override fun supports(input: BleBeaconScanInput): Boolean {
        val serviceUuids = input.serviceUuids
        // UUID provided in doc: 3e135142-654f-9090-134a-a6ff5bb77046
        return serviceUuids.any {
            it.equals("3e135142-654f-9090-134a-a6ff5bb77046", ignoreCase = true)
        }
    }

    override fun decode(input: BleBeaconScanInput): SensorData {
        return SensorData(beaconType = "eQ-3 Eqiva", sensorStatus = "Advertised")
    }
}
