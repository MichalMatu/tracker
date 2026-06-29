package io.blueeye.core.decoders.parser.apple

import javax.inject.Inject
import javax.inject.Singleton

/** Parser for Apple AirDrop (Type 0x05) packets. Based on Hexway Apple BLEee research. */
@Singleton
class AirDropParser
@Inject
constructor() {
    fun parse(data: ByteArray): AppleDeviceData? {
        // AirDrop payload usually 18 bytes?
        // 0x05 [Length] [Zeros or Hashes]
        if (data.size < 8) return null

        val hash = data.copyOfRange(0, 8) // First 8 bytes relevant for identification

        // Hexway research:
        // If all zeros -> "AirDrop discoverable / Everyone"
        // If hash -> "Contacts Only" (hash of phone/email)
        val isAllZeros = hash.all { it == 0.toByte() }

        // Currently we don't have the contact book to match the hash against,
        // but we can expose the hash for fingerprinting.

        return AppleDeviceData(
            deviceModel = "Apple Device (AirDrop)",
            airDropHash = hash,
            airDropMode = if (isAllZeros) "Everyone" else "Contacts Only",
        )
    }
}
