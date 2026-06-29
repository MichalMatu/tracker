package io.blueeye.core.decoders.parser.apple

import javax.inject.Inject
import javax.inject.Singleton

/** Parser for Apple Proximity Pairing / AirPods (Type 0x07). Based on FuriousMAC. */
@Singleton
class ProximityPairingParser
@Inject
constructor() {
    fun parse(data: ByteArray): AppleDeviceData? {
        if (data.isEmpty()) return null

        val subtype = data[0].toInt() and 0xFF
        val deviceName =
            when (subtype) {
                0x01 -> "AirPods (Gen 1)"
                0x02 -> "Apple Pencil"
                0x03 -> "AirPods Case"
                0x09 -> "AirPods (Gen 2)"
                0x0B -> "AirPods Pro"
                0x10 -> "Beats Solo Pro"
                0x13 -> "AirTag (Setup)"
                0x2A -> "AirPods Max"
                else -> "Proximity Device (0x%02X)".format(subtype)
            }

        return AppleDeviceData(
            deviceModel = deviceName,
            proximitySubtype = subtype,
        )
    }
}
