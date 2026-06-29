package io.blueeye.core.decoders.apple

import io.blueeye.core.decoders.BleBeaconDecoder
import io.blueeye.core.decoders.BleBeaconScanInput
import io.blueeye.core.decoders.parser.apple.AppleContinuityParser
import io.blueeye.core.decoders.parser.apple.AppleDeviceData
import io.blueeye.core.model.SensorData
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Apple Continuity / Generic "Apple Device" Decoder.
 * Delegates parsing to AppleContinuityParser.
 */
@Singleton
class AppleGenericDecoder
@Inject
constructor(
    private val appleContinuityParser: AppleContinuityParser
) : BleBeaconDecoder {
    override val priority: Int = -100

    override fun supports(input: BleBeaconScanInput): Boolean {
        val manufacturerId = input.manufacturerId
        val data = input.data
        // Apple ID = 0x004C
        if (manufacturerId != 0x004C) return false
        if (data == null || data.size < 8) return false

        // Exclude packets handled by specialized decoders:

        // 1. AirPods (Proximity Pair - Type 0x07)
        if (data[0] == 0x07.toByte() && data.size >= 25) {
            return false // Let AppleAirPodsDecoder handle this
        }

        // 2. Apple Watch specific pattern (Type 0x10, Len 0x05, Status 0x98/0x18)
        if (data.size >= 9 && data[0] == 0x10.toByte() && data[1] == 0x05.toByte()) {
            val statusByte = data[8].toInt() and 0xFF
            if (statusByte == 0x98 || statusByte == 0x18) {
                return false // Let AppleWatchDecoder handle this
            }
        }

        // 3. iBeacon (Type 0x02 0x15) - handled by IBeaconDecoder or BM2/BM6
        if (data.size >= 2 && data[0] == 0x02.toByte() && data[1] == 0x15.toByte()) {
            return false
        }

        return true
    }

    override fun decode(input: BleBeaconScanInput): SensorData {
        val mac = input.mac
        val data = input.data ?: byteArrayOf()
        if (data.isEmpty()) {
            return SensorData(beaconType = "Apple Device", rawData = "No data")
        }

        val hexData = data.joinToString("") { "%02X".format(it) }
        val appleData = appleContinuityParser.parse(data)

        if (appleData == null) {
            // Fallback for unknown structure (though parse usually returns if any TLV is found)
            return SensorData(
                beaconType = "Apple Device",
                rawData = "Apple: $hexData",
            )
        }

        return mapToSensorData(appleData, hexData, mac)
    }

    private fun mapToSensorData(
        data: AppleDeviceData,
        hexData: String,
        mac: String
    ): SensorData {
        val statusList = mutableListOf<String>()

        // Prioritize status info
        if (!data.nearbyActionDescription.isNullOrEmpty()) {
            statusList.add(data.nearbyActionDescription)
        }
        if (!data.airDropMode.isNullOrEmpty()) {
            statusList.add("AirDrop: ${data.airDropMode}")
        }
        if (data.homeKitStatus != null) {
            val isPairable = (data.homeKitStatus and 0x01) != 0
            statusList.add(if (isPairable) "Pairable" else "Not Pairable")
        }
        if (data.airPlayFlags != null) {
            val pinRequired = (data.airPlayFlags and 0x08) != 0
            statusList.add(if (pinRequired) "PIN Required" else "Open / No PIN")
        }
        if (data.batteryLevelLeft != null || data.batteryLevelRight != null || data.batteryLevelCase != null) {
            val bats = listOfNotNull(
                data.batteryLevelLeft?.let { "L:$it%" },
                data.batteryLevelRight?.let { "R:$it%" },
                data.batteryLevelCase?.let { "C:$it%" }
            ).joinToString(" ")
            statusList.add("Bat: $bats")
        }
        if (data.handoffIv != null) {
            statusList.add("Encrypted Activity (Seq ${data.handoffIv and 0xFF})")
        }

        // FindMy Key Reconstruction
        if (data.findMyKey != null) {
            if (data.findMyKey.size == 22) {
                val macHex = mac.replace(":", "").uppercase()
                // Only meaningful if MAC is 6 bytes (12 hex chars)
                if (macHex.length == 12) {
                    val keyHex = data.findMyKey.joinToString("") { "%02X".format(it) }
                    statusList.add("Public Key: $macHex$keyHex")
                } else {
                    statusList.add("FindMy (Invalid MAC)")
                }
            } else {
                statusList.add("FindMy Device")
            }
        }

        // Tethering
        if (data.tetheringType != null) {
            // If specific tethering info is needed in status, add here.
            // deviceModel covers the main "Instant Hotspot" text.
        }

        val consolidatedStatus = statusList.joinToString(", ")

        return SensorData(
            beaconType = data.deviceModel ?: "Apple Device",
            sensorStatus = if (consolidatedStatus.isNotEmpty()) consolidatedStatus else null,
            rawData = "Apple: $hexData"
        )
    }
}
