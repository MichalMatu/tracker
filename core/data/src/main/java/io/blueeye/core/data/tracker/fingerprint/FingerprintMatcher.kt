package io.blueeye.core.data.tracker.fingerprint

import io.blueeye.core.scanner.analysis.BleBinaryConstants
import java.util.Locale
import io.blueeye.core.data.tracker.fingerprint.FingerprintDefinitions as Defs

/**
 * Helper object to offload matching logic from [KnownDeviceFingerprints].
 */
internal object FingerprintMatcher {

    private const val FAST_PAIR_ID_LEN = 3
    private const val FAST_PAIR_HEX_LEN = 6

    fun checkServiceData(serviceDataMap: Map<String, ByteArray>?): String? {
        if (serviceDataMap == null) return null

        return checkTile(serviceDataMap)
            ?: checkEddystone(serviceDataMap)
            ?: checkFastPair(serviceDataMap)
            ?: checkCommonServices(serviceDataMap)
    }

    private fun checkTile(map: Map<String, ByteArray>): String? =
        if (map.containsUuid(Defs.TILE_SERVICE_UUID)) "Tile Device" else null

    private fun checkEddystone(map: Map<String, ByteArray>): String? {
        val eddystoneData = map.valueForUuid(Defs.EDDYSTONE_SERVICE_UUID) ?: return null

        return if (eddystoneData.isEmpty()) {
            "Eddystone Beacon"
        } else {
            when (eddystoneData[0].toInt() and BleBinaryConstants.MASK_BYTE) {
                Defs.EDDYSTONE_FRAME_UID -> "Eddystone Beacon (UID)"
                Defs.EDDYSTONE_FRAME_URL -> "Eddystone Beacon (URL)"
                Defs.EDDYSTONE_FRAME_TLM -> "Eddystone Beacon (TLM)"
                Defs.EDDYSTONE_FRAME_EID -> "Eddystone Beacon (EID)"
                else -> "Eddystone Beacon"
            }
        }
    }

    private fun checkFastPair(map: Map<String, ByteArray>): String? {
        val fastPairMatch = map.entries.firstOrNull { (uuid, data) ->
            uuid.contains(Defs.FAST_PAIR_SERVICE_UUID_SHORT, ignoreCase = true) && data.size >= FAST_PAIR_ID_LEN
        } ?: return null

        val prefix = fastPairMatch.value.joinToString("") { "%02x".format(it) }
            .uppercase(Locale.ROOT)
            .take(FAST_PAIR_HEX_LEN)

        return Defs.FAST_PAIR_MODELS[prefix]
    }

    private fun checkCommonServices(map: Map<String, ByteArray>): String? {
        return when {
            map.containsUuid(Defs.ALEXA_AMA_SERVICE_UUID) -> "Alexa Accessory"
            map.containsUuid(Defs.EXPOSURE_NOTIFICATION_SERVICE_UUID) -> "Exposure Notification"
            else -> null
        }
    }

    private fun Map<String, ByteArray>.containsUuid(uuid: String): Boolean =
        keys.any { it.matchesUuid(uuid) }

    private fun Map<String, ByteArray>.valueForUuid(uuid: String): ByteArray? =
        entries.firstOrNull { (key, _) -> key.matchesUuid(uuid) }?.value

    private fun String.matchesUuid(uuid: String): Boolean =
        equals(uuid, ignoreCase = true) || contains(uuid, ignoreCase = true)
}
