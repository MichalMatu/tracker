package io.blueeye.core.decoders.misc.tracker

import io.blueeye.core.decoders.BleBeaconDecoder
import io.blueeye.core.decoders.BleBeaconScanInput
import io.blueeye.core.model.SensorData
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Chipolo Tracker Decoder. Based on sandeepmistry/node-chipolo and Theengs research. Service UUID:
 * 0xFE2C (sometimes) or strict 128-bit UUID.
 */
@Singleton
class ChipoloDecoder @Inject constructor() : BleBeaconDecoder {
    override fun supports(input: BleBeaconScanInput): Boolean {
        val serviceUuids = input.serviceUuids
        val rawData = input.rawData
        // Check for Chipolo 128-bit Service UUID
        // Suffix: d54e8938944fd483774f33f8
        serviceUuids.forEach { uuid ->
            if (uuid.replace("-", "").lowercase().endsWith("d54e8938944fd483774f33f8")) {
                return true
            }
        }

        // Or check local name
        val name = rawData?.let { extractLocalName(it) }
        if (name == "Chipolo") return true

        return false
    }

    override fun decode(input: BleBeaconScanInput): SensorData {
        val rawData = input.rawData
        // Chipolo advertising data is minimal usually.
        // Service Data: c01005 (3 bytes)
        // c0 = state?
        // 10 = ?
        // 05 = Color? (05 is white?)

        // Node-chipolo output:
        // Service Data: c01005

        // Let's try to extract service data for the Chipolo UUID if present
        // But 'data' argument here corresponds to Manufacturer Data usually.
        // We might need to look at rawData if we want Service Data.

        var status = "Active"
        var extra = ""

        if (rawData != null) {
            val serviceData = findServiceData(rawData)
            if (serviceData != null && serviceData.size >= 3) {
                val b0 = serviceData[0].toInt() and 0xFF
                val b1 = serviceData[1].toInt() and 0xFF
                val b2 = serviceData[2].toInt() and 0xFF

                extra = "SD: %02X%02X%02X".format(b0, b1, b2)

                // Helper from node-chipolo doesn't explicitly decode these bits in advertisement,
                // but connects to read characteristics.
            }
        }

        return SensorData(beaconType = "Chipolo Tracker", sensorStatus = status, rawData = extra)
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

    private fun findServiceData(rawData: ByteArray): ByteArray? {
        // Look for Service Data associated with Chipolo UUID
        // This is tricky as UUID is 128-bit.
        // Usually Service Data triggers on 16-bit UUIDs.
        // Chipolo specific 128-bit Service Data?
        // From node-chipolo log:
        // "here is my service data: c01005"
        // It doesn't say FOR which UUID.
        // But valid 128-bit Service Data AD type is 0x21 (Service Data - 128-bit UUID)

        var i = 0
        while (i < rawData.size - 1) {
            val len = rawData[i].toInt() and 0xFF
            if (len == 0) break
            if (i + 1 + len > rawData.size) break

            val type = rawData[i + 1].toInt() and 0xFF

            // 0x21 = Service Data - 128-bit UUID
            if (type == 0x21) {
                // UUID (16 bytes) + Data
                if (len > 17) {
                    // Check UUID match
                    // UUID in packet is LE.
                    // Chipolo UUID LE: d5 4e 89 38 94 4f d4 83 77 4f 33 f8 d6 85 10 45
                    val uuidBytes = rawData.copyOfRange(i + 2, i + 18)
                    // Simple check of first few bytes or suffix
                    // Suffix in LE is at END? No, suffix is high bytes.
                    // UUID: 4510...
                    // LE: ... 10 45
                    if (uuidBytes[15] == 0x45.toByte() && uuidBytes[14] == 0x10.toByte()) {
                        return rawData.copyOfRange(i + 18, i + 1 + len)
                    }
                }
            }
            i += 1 + len
        }
        return null
    }
}
