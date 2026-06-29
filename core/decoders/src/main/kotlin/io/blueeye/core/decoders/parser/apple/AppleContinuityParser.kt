package io.blueeye.core.decoders.parser.apple

import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AppleContinuityParser
@Inject
constructor(
    private val nearbyInfoParser: NearbyInfoParser,
    private val nearbyActionParser: NearbyActionParser,
    private val handoffParser: HandoffParser,
    private val proximityPairingParser: ProximityPairingParser,
    private val tetheringTargetParser: TetheringTargetParser,
    private val tetheringSourceParser: TetheringSourceParser,
    private val findMyParser: FindMyParser,
    private val airDropParser: AirDropParser,
    private val airPrintParser: AirPrintParser,
    private val homeKitParser: HomeKitParser,
    private val heySiriParser: HeySiriParser,
    private val airPlayParser: AirPlayParser,
    private val magicSwitchParser: MagicSwitchParser,
) {
    fun parse(manufacturerData: ByteArray?): AppleDeviceData? {
        if (manufacturerData == null || manufacturerData.isEmpty()) return null

        var bestData: AppleDeviceData? = null

        var i = 0
        while (i < manufacturerData.size - 1) {
            val type = manufacturerData[i].toInt() and 0xFF
            val length = manufacturerData[i + 1].toInt() and 0xFF

            if (i + 2 + length > manufacturerData.size) break

            val valueStart = i + 2
            val valueEnd = valueStart + length
            val valueBytes = manufacturerData.copyOfRange(valueStart, valueEnd)

            val parsed =
                when (type) {
                    0x10 -> nearbyInfoParser.parse(valueBytes)
                    0x0F -> nearbyActionParser.parse(valueBytes)
                    0x0C -> handoffParser.parse(valueBytes)
                    0x07 -> proximityPairingParser.parse(valueBytes)
                    0x0D -> tetheringTargetParser.parse(valueBytes)
                    0x0E -> tetheringSourceParser.parse(valueBytes)
                    0x12 -> findMyParser.parse(valueBytes)
                    0x05 -> airDropParser.parse(valueBytes)
                    0x03 -> airPrintParser.parse(valueBytes)
                    0x06 -> homeKitParser.parse(valueBytes)
                    0x08 -> heySiriParser.parse(valueBytes)
                    0x09 -> airPlayParser.parse(valueBytes)
                    0x0B -> magicSwitchParser.parse(valueBytes)
                    else -> null
                }

            // Merge results (simple overwrite or first-win strategy? Apple devices can send
            // multiple types)
            // e.g. Nearby Info + Handoff in one packet
            if (parsed != null) {
                // If we already have data, try to merge key fields
                if (bestData == null) {
                    bestData = parsed
                } else {
                    bestData =
                        bestData.copy(
                            deviceModel = parsed.deviceModel ?: bestData.deviceModel,
                            findMyKey = parsed.findMyKey ?: bestData.findMyKey,
                            airDropHash = parsed.airDropHash ?: bestData.airDropHash,
                            airDropMode = parsed.airDropMode ?: bestData.airDropMode,
                            statusFlags = parsed.statusFlags ?: bestData.statusFlags,
                            nearbyStatusFlags =
                            parsed.nearbyStatusFlags
                                ?: bestData.nearbyStatusFlags,
                            nearbyActionCode =
                            parsed.nearbyActionCode
                                ?: bestData.nearbyActionCode,
                            nearbyActionDescription =
                            parsed.nearbyActionDescription
                                ?: bestData.nearbyActionDescription,
                            wifiSsidHashPrefix =
                            parsed.wifiSsidHashPrefix
                                ?: bestData.wifiSsidHashPrefix,
                            handoffIv = parsed.handoffIv ?: bestData.handoffIv,
                            proximitySubtype =
                            parsed.proximitySubtype
                                ?: bestData.proximitySubtype,
                            tetheringType = parsed.tetheringType ?: bestData.tetheringType,
                            tetheringPayload =
                            parsed.tetheringPayload
                                ?: bestData.tetheringPayload,
                            homeKitStatus = parsed.homeKitStatus ?: bestData.homeKitStatus,
                            airPlayFlags = parsed.airPlayFlags ?: bestData.airPlayFlags,
                        )
                }
            }

            i += 2 + length
        }

        return bestData
    }
}
