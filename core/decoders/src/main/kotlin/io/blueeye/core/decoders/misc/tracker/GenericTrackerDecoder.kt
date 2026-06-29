package io.blueeye.core.decoders.misc.tracker

import io.blueeye.core.decoders.BleBeaconDecoder
import io.blueeye.core.decoders.BleBeaconScanInput
import io.blueeye.core.model.SensorData
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Generic Tracker decoders (Nut, Nutale, iTAG, Tag-It, Tile, Theengs) and VCHON. Theengs:
 * tracker_json.h, VCH6003_json.h
 */
@Singleton
class GenericTrackerDecoder
@Inject
constructor() : BleBeaconDecoder {
    override fun supports(input: BleBeaconScanInput): Boolean {
        val serviceUuids = input.serviceUuids
        val rawData = input.rawData
        if (rawData == null) return false
        val name = extractLocalName(rawData)?.lowercase() ?: ""

        if (name == "nut") return true
        if (name == "nutale") return true
        if (name == "itag") return true
        if (name == "tag-it") return true
        if (name == "tile") return true
        if (serviceUuids.any { it.contains("feed") || it.contains("feec") || it.contains("fd84") }) {
            return true
        }

        return false
    }

    override fun decode(input: BleBeaconScanInput): SensorData {
        val rawData = input.rawData
        val name = rawData?.let { extractLocalName(it) } ?: "Tracker"

        try {
            val brand =
                when {
                    name.lowercase() == "nut" -> "Nut Smart Tracker"
                    name.lowercase() == "nutale" -> "Nutale Tracker"
                    name.lowercase() == "itag" -> "iTAG Tracker"
                    name.lowercase().contains("tile") -> "Tile Tracker"
                    else -> "Generic Smart Tracker"
                }

            return SensorData(
                beaconType = brand,
                sensorStatus = "Active",
                rawData = "Tracker: $name",
            )
        } catch (e: Exception) {
            return SensorData(beaconType = "Tracker (Parse Error)")
        }
    }

    private fun extractInt(
        data: ByteArray,
        start: Int,
        len: Int,
    ): Int {
        var res = 0
        for (i in 0 until len) {
            if (start + i < data.size) {
                res = (res shl 8) or (data[start + i].toInt() and 0xFF)
            }
        }
        return res
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
