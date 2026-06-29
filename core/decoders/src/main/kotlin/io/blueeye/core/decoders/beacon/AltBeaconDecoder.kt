package io.blueeye.core.decoders.beacon

import io.blueeye.core.decoders.BleBeaconDecoder
import io.blueeye.core.decoders.BleBeaconScanInput
import io.blueeye.core.model.SensorData
import java.nio.ByteBuffer
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

/**
 * AltBeacon Decoder.
 * Reference: https://github.com/RadiusNetworks/altbeacon-reference
 * Format:
 * [0-1]  AltBeacon Code (0xBEAC)
 * [2-17] ID1 (16-byte UUID)
 * [18-19] ID2 (2-byte Major)
 * [20-21] ID3 (2-byte Minor)
 * [22]   Reference RSSI
 * [23]   Reserved
 */
@Singleton
class AltBeaconDecoder
@Inject
constructor() : BleBeaconDecoder {
    override val priority: Int = -100

    override fun supports(input: BleBeaconScanInput): Boolean {
        val data = input.data
        if (data == null || data.size < 24) return false

        // Check for 0xBEAC preamble
        // In Android "data" (from SparseArray), the manufacturer ID is stripped.
        // So index 0 is the start of the AltBeacon payload.
        return data[0] == 0xBE.toByte() && data[1] == 0xAC.toByte()
    }

    override fun decode(input: BleBeaconScanInput): SensorData {
        val data = input.data ?: byteArrayOf()
        try {
            // ID1: 16-byte UUID (Big Endian)
            val uuidBytes = data.copyOfRange(2, 18)
            val bb = ByteBuffer.wrap(uuidBytes)
            val high = bb.long
            val low = bb.long
            val uuid = UUID(high, low).toString()

            // ID2: Major (2 bytes, Big Endian)
            val major = ((data[18].toInt() and 0xFF) shl 8) or (data[19].toInt() and 0xFF)

            // ID3: Minor (2 bytes, Big Endian)
            val minor = ((data[20].toInt() and 0xFF) shl 8) or (data[21].toInt() and 0xFF)

            // Ref RSSI (1 byte, Signed)
            val refRssi = data[22].toInt()

            // Reserved (1 byte)
            // val reserved = data[23].toInt() and 0xFF // Unused

            return SensorData(
                beaconType = "AltBeacon",
                sensorStatus = "Active",
                // We hijack 'rawData' field to store the nice summary for now,
                // or ideally we update SensorData to have 'beaconInfo'
                // but for compatibility with current UI we use rawData text.
                rawData = "UUID: $uuid\nMaj: $major, Min: $minor, Ref: $refRssi dBm"
            )
        } catch (e: Exception) {
            return SensorData(beaconType = "AltBeacon (Parse Error)")
        }
    }
}
