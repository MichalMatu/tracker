package io.blueeye.core.decoders.parser.apple

import javax.inject.Inject
import javax.inject.Singleton

/** Parser for Apple Nearby Info (Type 0x10). Based on FuriousMAC / Reverse Engineering. */
@Singleton
class NearbyInfoParser
@Inject
constructor() {
    fun parse(data: ByteArray): AppleDeviceData? {
        if (data.size < 1) return null

        val status = data[0].toInt() and 0xFF

        // Upper Nibble: Activity/State (High 4 bits)
        val activity = (status shr 4) and 0x0F
        val actionDescription = getActionDescription(activity)

        // The lower nibble belongs to Nearby Info protocol/status semantics. It is not reliable
        // physical-model evidence and must not be promoted to deviceModel.

        // FuriousMAC: Status Flags (often in next byte, low nibble)
        // 0001 Primary Device (Y/N)
        // 0010 Unknown
        // 0100 AirDropReceiving (On/Off)
        // 1000 Not Used
        val statusFlags =
            if (data.size > 1) {
                data[1].toInt() and 0x0F
            } else {
                null
            }

        return AppleDeviceData(
            deviceModel = null,
            statusFlags = status,
            nearbyActionCode = activity,
            nearbyActionDescription = actionDescription,
            nearbyStatusFlags = statusFlags,
        )
    }

    private fun getActionDescription(action: Int): String? {
        return when (action) {
            0x00 -> "Idle"
            0x01 -> "Audio Activity"
            0x03 -> "Locked Screen"
            0x06 -> "Watch: Screen Off"
            0x07 -> "Transition Phase"
            0x09 -> "Watch: Music"
            0x0A -> "Locked Screen (Inform Watch)"
            0x0B -> "Active User"
            0x0C -> "Laptop: Lid Open"
            0x0D -> "User in Vehicle"
            0x0E -> "PhoneCall/FaceTime"
            else -> "Unknown Activity (0x${"%02X".format(action)})"
        }
    }
}
