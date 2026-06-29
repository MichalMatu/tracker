package io.blueeye.core.decoders

import io.blueeye.core.model.SensorData
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.experimental.and

/**
 * Decoder based on Theengs Decoder project for broader interoperability. Currently supports:
 * - Xiaomi / Mijia (Service UUID 0xFE95)
 */
@Singleton
class TheengsInteroperableDecoder
@Inject
constructor() : BleBeaconDecoder {
    override fun supports(input: BleBeaconScanInput): Boolean {
        val serviceUuids = input.serviceUuids
        // Support Xiaomi (0xFE95)
        return serviceUuids.any { it.contains("fe95", true) }
    }

    override fun decode(input: BleBeaconScanInput): SensorData {
        val rawData = input.rawData
        if (rawData == null) return SensorData(beaconType = "Xiaomi (Unknown Data)")

        // Parse Raw Data to find Service Data 0xFE95
        val serviceData = findServiceData(rawData, 0xFE95)

        if (serviceData != null) {
            return decodeXiaomi(serviceData)
        }

        return SensorData(beaconType = "Xiaomi Device")
    }

    private fun findServiceData(
        rawData: ByteArray,
        uuid16: Int,
    ): ByteArray? {
        var i = 0
        while (i < rawData.size - 1) {
            val len = rawData[i].toInt() and 0xFF
            if (len == 0) break // End of AD structures
            if (i + 1 + len > rawData.size) break

            val type = rawData[i + 1].toInt() and 0xFF
            // Service Data 16-bit UUID = 0x16
            if (type == 0x16) {
                // Check UUID
                if (i + 3 < rawData.size) {
                    val uLow = rawData[i + 2].toInt() and 0xFF
                    val uHigh = rawData[i + 3].toInt() and 0xFF
                    val foundUuid = (uHigh shl 8) or uLow

                    if (foundUuid == uuid16) {
                        // Found it! Return payload (skipping Len, Type, UUID)
                        // Payload length = Len - 1 (Type) - 2 (UUID)
                        val payloadLen = len - 3
                        if (payloadLen > 0) {
                            return rawData.copyOfRange(i + 4, i + 4 + payloadLen)
                        }
                    }
                }
            }
            i += 1 + len
        }
        return null
    }

    private fun decodeXiaomi(payload: ByteArray): SensorData {
        // Xiaomi Payload Structure (MiBeacon V2/V3/V4/V5)
        // [FrameControl:2] [AssertID:2] ...

        if (payload.size < 5) return SensorData(beaconType = "Xiaomi (Inv Len)")

        val frameControl = payload.copyOfRange(0, 2)
        // Bit 0-3: Version (not reliable alone)
        // Bit 4: MAC included?
        // Bit 5: Capability included?
        // Bit 6: Object included? (The actual sensor data)

        val fc1 = (frameControl[0].toInt() and 0xFF)
        // val fc2 = (frameControl[1].toInt() and 0xFF) // Unused

        val hasMac = (fc1 and 0x10) != 0
        val hasCap = (fc1 and 0x20) != 0
        val hasObj = (fc1 and 0x40) != 0

        var offset = 2
        // Product ID
        offset += 2
        // Sequence Counter
        offset += 1

        if (hasMac) offset += 6
        if (hasCap) offset += 1

        if (hasObj && offset < payload.size) {
            // Object(s) loop
            // [Type:1] [Len:1] [Val...]

            // Common Objects:
            // 0x1004: Temperature
            // 0x1006: Humidity
            // 0x100A: Battery

            // Note: In BLE payload, object ID is usually 2 bytes LE?
            // Actually structure is [ID:2] [Data...] or [Type:1] [Len:1]?
            // MiBeacon V2/3 uses [Type:1] [Len:1] [Data...] inside the Object container?
            // No, usually it's [ID_LO] [ID_HI] [LEN] [VAL...]
            // e.g. 04 10 02 [T_LO] [T_HI] -> ID 0x1004 (Temp), Len 2

            // Simplification: Scan for known IDs

            var temp: Double? = null
            var hum: Double? = null
            var batt: Int? = null

            while (offset < payload.size - 2) {
                val idLow = payload[offset].toInt() and 0xFF
                val idHigh = payload[offset + 1].toInt() and 0xFF
                val objId = (idHigh shl 8) or idLow

                // If payload structure valid, next byte is usually data?
                // Or Len?
                // Standard format: [ID:2] [Value...] (length implicit by ID?)
                // Or [ID:2] [Len:1] [Value...]
                // Let's assume [ID:2] [Len:1] if varying.

                // However, common Xiaomi temp sensors (LYWSD03MMC) often use encryption binding.
                // Unencrypted ones (e.g. Cleargrass) usually use [ID:2] [Len:1] [Val]

                // Quick check if next byte is reasonable length (e.g. < 10)
                if (offset + 2 >= payload.size) break

                // Heuristic: Check common ID
                if (objId == 0x1004) { // Temp
                    // Expect 2 bytes
                    // offset+2 is likely data start in some versions, or length.
                    // Let's assume Len byte is present for safety, often is.
                    // But some docs say V5 is fixed.

                    // Let's try heuristic scan from offset
                    // If we match 04 10 (LE for 1004), let's look at next bytes
                    // Usually: 04 10 02 T1 T2

                    if (payload.size >= offset + 5 && payload[offset + 2].toInt() == 2) {
                        val tRaw =
                            ((payload[offset + 4].toInt() and 0xFF) shl 8) or
                                (payload[offset + 3].toInt() and 0xFF)
                        temp = tRaw / 10.0
                        offset += 5 // 2(ID) + 1(Len) + 2(Val)
                        continue
                    }
                }

                if (objId == 0x1006) { // Hum
                    // 06 10 02 H1 H2
                    if (payload.size >= offset + 5 && payload[offset + 2].toInt() == 2) {
                        val hRaw =
                            ((payload[offset + 4].toInt() and 0xFF) shl 8) or
                                (payload[offset + 3].toInt() and 0xFF)
                        hum = hRaw / 10.0
                        offset += 5
                        continue
                    }
                }

                if (objId == 0x100A) { // Bat
                    // 0A 10 01 B1
                    if (payload.size >= offset + 4 && payload[offset + 2].toInt() == 1) {
                        batt = payload[offset + 3].toInt() and 0xFF
                        offset += 4
                        continue
                    }
                }

                offset++ // Safety increment if no match
            }

            return SensorData(
                temperatureCelcius = temp,
                humidityPercent = hum,
                batteryLevel = batt,
                beaconType = "Xiaomi/Mijia Sensor",
                rawData = "Xiaomi FE95",
            )
        }

        return SensorData(beaconType = "Xiaomi (Encrypted/Empty)")
    }
}
