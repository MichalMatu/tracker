package io.blueeye.core.decoders.parser.generic

object AdRecordParser {
    fun parse(hexString: String): String {
        if (hexString.isEmpty()) return "Empty data"

        val sb = StringBuilder()
        val bytes = hexToBytes(hexString)
        var offset = 0

        while (offset < bytes.size) {
            // Length byte
            val length = bytes[offset].toInt() and 0xFF
            if (length == 0) {
                // End of significant data (padding zeros often found at end)
                break
            }

            if (offset + 1 + length > bytes.size) {
                sb.append("Error: Malformed packet (invalid length at offset $offset)\n")
                break
            }

            // Type byte
            val type = bytes[offset + 1].toInt() and 0xFF

            // Data bytes
            val data = ByteArray(length - 1)
            // Safety check for array copy
            if (length - 1 > 0) {
                System.arraycopy(bytes, offset + 2, data, 0, length - 1)
            }

            // Formatting
            val typeName = getAdTypeName(type)
            val dataHex = bytesToHex(data)

            sb.append("• [0x%02X] %s\n".format(type, typeName))
            sb.append("  Data: %s\n".format(dataHex))

            // Quick interpretation for common types
            val interpretation = interpretData(type, data)
            if (interpretation.isNotEmpty()) {
                sb.append("  Value: %s\n".format(interpretation))
            }

            sb.append("\n")

            // Move to next record
            offset += (1 + length)
        }

        return if (sb.isNotEmpty()) sb.toString().trim() else "No records found"
    }

    private fun getAdTypeName(type: Int): String {
        return when (type) {
            0x01 -> "Flags"
            0x02, 0x03 -> "16-bit Service UUIDs"
            0x04, 0x05 -> "32-bit Service UUIDs"
            0x06, 0x07 -> "128-bit Service UUIDs"
            0x08, 0x09 -> "Local Name"
            0x0A -> "Tx Power Level"
            0x16 -> "Service Data (16-bit)"
            0x20 -> "Service Data (32-bit)"
            0x21 -> "Service Data (128-bit)"
            0x19 -> "Appearance"
            0xFF -> "Manufacturer Specific Data"
            else -> "Unknown Type"
        }
    }

    private fun interpretData(
        type: Int,
        data: ByteArray,
    ): String {
        return try {
            when (type) {
                // Local Name
                0x08,
                0x09,
                -> String(data, Charsets.UTF_8).trim()

                // Tx Power
                0x0A -> if (data.isNotEmpty()) "${data[0].toInt()} dBm" else ""

                // Manufacturer Data (show Company ID)
                0xFF -> {
                    if (data.size >= 2) {
                        // Little Endian Company ID
                        val id = ((data[1].toInt() and 0xFF) shl 8) or (data[0].toInt() and 0xFF)
                        "Company ID: 0x%04X (%d)".format(id, id)
                    } else {
                        ""
                    }
                }

                // Flags
                0x01 -> {
                    if (data.isNotEmpty()) {
                        val flag = data[0].toInt()
                        val bits = mutableListOf<String>()
                        if ((flag and 0x01) != 0) bits.add("LE Limited")
                        if ((flag and 0x02) != 0) bits.add("LE General")
                        if ((flag and 0x04) != 0) bits.add("BR/EDR Not Supported")
                        bits.joinToString(", ")
                    } else {
                        ""
                    }
                }
                else -> ""
            }
        } catch (e: Exception) {
            "(Decoded error)"
        }
    }

    private fun hexToBytes(hex: String): ByteArray {
        val cleanHex = hex.replace(" ", "")
        val len = cleanHex.length
        val data = ByteArray(len / 2)
        var i = 0
        while (i < len) {
            data[i / 2] =
                (
                    (Character.digit(cleanHex[i], 16) shl 4) +
                        Character.digit(cleanHex[i + 1], 16)
                    )
                    .toByte()
            i += 2
        }
        return data
    }

    private fun bytesToHex(bytes: ByteArray): String {
        return bytes.joinToString("") { "%02X".format(it) }
    }
}
