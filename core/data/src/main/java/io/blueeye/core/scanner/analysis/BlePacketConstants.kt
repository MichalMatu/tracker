package io.blueeye.core.scanner.analysis

/**
 * Constants for BLE Packet analysis.
 */
object BlePacketConstants {
    // Multipliers
    const val ADV_INTERVAL_UNIT = 0.625
    const val CONN_INTERVAL_UNIT = 1.25

    // Flags AD Type Bits
    const val FLAG_LE_LIMITED_DISC = 0x01
    const val FLAG_LE_GENERAL_DISC = 0x02
    const val FLAG_BR_EDR_NOT_SUPPORTED = 0x04
    const val FLAG_SIMULTANEOUS_LE_BR_CONTROLLER = 0x08
    const val FLAG_SIMULTANEOUS_LE_BR_HOST = 0x10

    // OOB Flags AD Type Bits
    const val OOB_FLAG_DATA_PRESENT = 0x01
    const val OOB_FLAG_LE_SUPPORTED_HOST = 0x02
    const val OOB_FLAG_SIMULTANEOUS_LE_BR_HOST = 0x04
    const val OOB_FLAG_RANDOM_ADDRESS = 0x08

    // LE Role Values
    const val ROLE_PERIPHERAL_ONLY = 0x00
    const val ROLE_CENTRAL_ONLY = 0x01
    const val ROLE_PERIPHERAL_PREFERRED = 0x02
    const val ROLE_CENTRAL_PREFERRED = 0x03

    // Lengths and Offsets
    const val ADDR_LEN = 6
    const val LE_ADDR_LEN = 7
    const val CONN_INT_LEN = 4
    const val ADV_INT_LEN = 2

    const val OFS_ADDR_TYPE = 6

    // Ranges for AD Types
    const val AD_TYPE_STANDARD_MIN = 0x01
    const val AD_TYPE_BLOCK_A_MAX = 0x16
    const val AD_TYPE_BLOCK_B_MIN = 0x17
    const val AD_TYPE_BLOCK_B_MAX = 0x24
}
