package io.blueeye.core.decoders.bose

import io.blueeye.core.decoders.BleBeaconDecoder
import io.blueeye.core.decoders.BleBeaconScanInput
import io.blueeye.core.model.SensorData
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Bose BLE Advertising Decoder.
 *
 * Decodes BLE ManufacturerData from Bose devices with Manufacturer ID 0x009E (158).
 *
 * NOTE: Rich data like battery level and noise cancellation settings are NOT available in BLE
 * advertisements - they require an active RFCOMM connection which is handled by the
 * RfcommProbeService and BoseRfcommHandler.
 *
 * This decoder extracts what IS available in BLE advertising packets:
 * - Product identification (from manufacturer data structure)
 * - Basic device presence and signal strength
 *
 * For full Bose device data, see:
 * - core/scanner/rfcomm/handlers/BoseRfcommHandler.kt (RFCOMM protocol)
 * - core/scanner/rfcomm/RfcommProbeService.kt (explicit active probing infrastructure)
 */
@Singleton
class BoseDecoder
@Inject
constructor() : BleBeaconDecoder {
    override fun supports(input: BleBeaconScanInput): Boolean {
        val manufacturerId = input.manufacturerId
        // Bose Company ID is 0x009E (158)
        return manufacturerId == BOSE_MANUFACTURER_ID
    }

    override fun decode(input: BleBeaconScanInput): SensorData {
        val data = input.data ?: byteArrayOf()
        if (data.isEmpty()) {
            return SensorData(beaconType = "Bose Device")
        }

        val hexDump = data.joinToString(" ") { "%02X".format(it) }

        // Attempt to identify product type from manufacturer data
        val productInfo = identifyProduct(data)

        return SensorData(beaconType = productInfo, rawData = "Bose MfgData: $hexDump")
    }

    /**
     * Attempts to identify Bose product type from manufacturer data.
     *
     * Bose uses various data formats. Common patterns observed:
     * - First bytes often contain product/model identifier
     * - Some bytes may indicate connection state or features
     *
     * This mapping should be expanded as more devices are analyzed.
     */
    private fun identifyProduct(data: ByteArray): String {
        if (data.isEmpty()) return "Bose Device"

        // Check data length and patterns
        return when {
            // Try to detect by data length and patterns
            data.size >= 2 -> {
                val b0 = data[0].toInt() and 0xFF
                val b1 = data[1].toInt() and 0xFF

                // Known patterns (to be expanded with real data)
                when {
                    // 0x23 = 35 decimal. Strong heuristic for QC 35.
                    b0 == 0x00 && b1 == 0x23 -> "Bose QC 35"
                    // 0x47 = 71 (NC 700?)
                    b0 == 0x00 && b1 == 0x47 -> "Bose NC 700"
                    b0 == 0x00 && data.size >= 3 -> "Bose Audio (Active)"
                    b0 in 0x01..0x0F -> "Bose Headphones"
                    b0 in 0x10..0x1F -> "Bose Speaker"
                    else -> "Bose Device (${"%02X".format(b0)}${"%02X".format(b1)})"
                }
            }
            else -> "Bose Device"
        }
    }

    companion object {
        /** Bose Bluetooth SIG Company ID */
        const val BOSE_MANUFACTURER_ID = 0x009E // 158 decimal

        /** Bose proprietary service UUID (used by QC35, NC700, etc.) */
        const val BOSE_SERVICE_UUID = "0000FEBE-0000-1000-8000-00805F9B34FB"
    }
}
