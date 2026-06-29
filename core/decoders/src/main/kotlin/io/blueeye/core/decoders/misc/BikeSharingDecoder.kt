package io.blueeye.core.decoders.misc

import io.blueeye.core.decoders.BleBeaconDecoder
import io.blueeye.core.decoders.BleBeaconScanInput
import io.blueeye.core.model.SensorData
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Decoder for Bike Sharing Systems (e.g. oBike, Mobike).
 *
 * Based on: https://github.com/antoinet/obike
 * and generic bike sharing protocols.
 */
@Singleton
class BikeSharingDecoder
@Inject
constructor() : BleBeaconDecoder {
    override fun supports(input: BleBeaconScanInput): Boolean {
        val serviceUuids = input.serviceUuids
        val rawData = input.rawData
        if (rawData == null) return false

        val name = extractLocalName(rawData) ?: ""

        // oBike detection
        if (name.startsWith("oBike", ignoreCase = true)) return true

        // Mobike detection (often starts with 'Mobike' or has specific services)
        if (name.startsWith("Mobike", ignoreCase = true)) return true

        // Check for common bike sharing services
        // 0xFEE7 is often used by Tencent/WeChat related locks
        if (serviceUuids.any { it.contains("fee7", ignoreCase = true) }) {
            // Confirm with name if possible, or assume generic lock
            // Some conflicting devices might use FEE7, so be careful.
            // But for tracker purposes, flagging it is good.
            return true
        }

        return false
    }

    override fun decode(input: BleBeaconScanInput): SensorData {
        val rawData = input.rawData
        val name = rawData?.let { extractLocalName(it) } ?: "Smart Lock"

        var type = "Smart Lock"
        var status = "Locked" // Default assumption

        if (name.startsWith("oBike", ignoreCase = true)) {
            type = "oBike"
            // oBike often puts the bike ID in the name
        } else if (name.startsWith("Mobike", ignoreCase = true)) {
            type = "Mobike"
        }

        return SensorData(
            beaconType = type,
            sensorStatus = status,
            rawData = "Name: $name",
        )
    }

    private fun extractLocalName(rawData: ByteArray): String? {
        var i = 0
        while (i < rawData.size - 1) {
            val len = rawData[i].toInt() and 0xFF
            if (len == 0) break
            if (i + 1 + len > rawData.size) break
            val type = rawData[i + 1].toInt() and 0xFF
            if (type == 0x08 || type == 0x09) {
                return String(rawData.copyOfRange(i + 2, i + 1 + len), Charsets.UTF_8)
            }
            i += 1 + len
        }
        return null
    }
}
