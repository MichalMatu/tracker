package io.blueeye.core.scanner.analysis

/**
 * Constants for BLE Advertisement Data Types (AD Types).
 */
object BleAdTypes {
    const val FLAGS = 0x01
    const val UUID16_INCOMPLETE = 0x02
    const val UUID16_COMPLETE = 0x03
    const val UUID32_INCOMPLETE = 0x04
    const val UUID32_COMPLETE = 0x05
    const val UUID128_INCOMPLETE = 0x06
    const val UUID128_COMPLETE = 0x07
    const val NAME_SHORT = 0x08
    const val NAME_COMPLETE = 0x09
    const val TX_POWER = 0x0A
    const val CLASS_OF_DEVICE = 0x0D
    const val SIMPLE_PAIRING_HASH = 0x0E
    const val SIMPLE_PAIRING_RANDOMIZER = 0x0F
    const val DEVICE_ID = 0x10
    const val OOB_FLAGS = 0x11
    const val SLAVE_CONN_INTERVAL = 0x12
    const val SOLICITATION_UUID16 = 0x14
    const val SOLICITATION_UUID128 = 0x15
    const val SERVICE_DATA_16 = 0x16
    const val PUBLIC_TARGET_ADDR = 0x17
    const val RANDOM_TARGET_ADDR = 0x18
    const val RAND_ADDR_TYPE = 0x18 // Alias for RANDOM_TARGET_ADDR
    const val APPEARANCE = 0x19
    const val ADVERTISING_INTERVAL = 0x1A
    const val LE_DEVICE_ADDR = 0x1B
    const val LE_ROLE = 0x1C
    const val SOLICITATION_UUID32 = 0x1F
    const val SERVICE_DATA_32 = 0x20
    const val SERVICE_DATA_128 = 0x21
    const val URI = 0x24
    const val INDOOR_POSITIONING = 0x25
    const val TRANSPORT_DISCOVERY = 0x26
    const val PB_ADV = 0x29
    const val MESH_MESSAGE = 0x2A
    const val MESH_BEACON = 0x2B
    const val INFO_DATA_3D = 0x3D
    const val MANUFACTURER_SPECIFIC = 0xFF

    private val NAMES = mapOf(
        FLAGS to "Flags",
        UUID16_INCOMPLETE to "Incomplete 16-bit Service UUIDs",
        UUID16_COMPLETE to "Complete 16-bit Service UUIDs",
        UUID32_INCOMPLETE to "Incomplete 32-bit Service UUIDs",
        UUID32_COMPLETE to "Complete 32-bit Service UUIDs",
        UUID128_INCOMPLETE to "Incomplete 128-bit Service UUIDs",
        UUID128_COMPLETE to "Complete 128-bit Service UUIDs",
        NAME_SHORT to "Shortened Local Name",
        NAME_COMPLETE to "Complete Local Name",
        TX_POWER to "Tx Power Level",
        CLASS_OF_DEVICE to "Class of Device",
        SIMPLE_PAIRING_HASH to "Simple Pairing Hash C-192",
        SIMPLE_PAIRING_RANDOMIZER to "Simple Pairing Randomizer R-192",
        DEVICE_ID to "Device ID",
        OOB_FLAGS to "OOB Flags",
        SLAVE_CONN_INTERVAL to "Slave Connection Interval Range",
        SOLICITATION_UUID16 to "List of 16-bit Service Solicitation UUIDs",
        SOLICITATION_UUID128 to "List of 128-bit Service Solicitation UUIDs",
        SERVICE_DATA_16 to "Service Data - 16-bit UUID",
        PUBLIC_TARGET_ADDR to "Public Target Address",
        RANDOM_TARGET_ADDR to "Random Target Address",
        APPEARANCE to "Appearance",
        ADVERTISING_INTERVAL to "Advertising Interval",
        LE_DEVICE_ADDR to "LE Bluetooth Device Address",
        LE_ROLE to "LE Role",
        SOLICITATION_UUID32 to "List of 32-bit Service Solicitation UUIDs",
        SERVICE_DATA_32 to "Service Data - 32-bit UUID",
        SERVICE_DATA_128 to "Service Data - 128-bit UUID",
        URI to "URI",
        INDOOR_POSITIONING to "Indoor Positioning",
        TRANSPORT_DISCOVERY to "Transport Discovery Data",
        PB_ADV to "PB-ADV",
        MESH_MESSAGE to "Mesh Message",
        MESH_BEACON to "Mesh Beacon",
        INFO_DATA_3D to "3D Information Data",
        MANUFACTURER_SPECIFIC to "Manufacturer Specific Data"
    )

    fun getName(type: Int): String = NAMES[type] ?: "Unknown (0x%02X)".format(type)
}
