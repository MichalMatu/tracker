package io.blueeye.core.data.classifier

import io.blueeye.core.model.ProtocolCapability

/**
 * Independent BLE protocol detection. Protocols describe observed capabilities and never
 * determine physical device type, ownership or tracking risk by themselves.
 */
object ProtocolCapabilityDetector {
    fun fromAdvertisement(
        serviceUuids: List<String>,
        serviceDataByUuid: Map<String, ByteArray>,
    ): Set<ProtocolCapability> =
        buildSet {
            val observedUuids = serviceUuids + serviceDataByUuid.keys

            if (observedUuids.any { it.matchesShortUuid(UUID_DULT) }) add(ProtocolCapability.DULT)
            if (observedUuids.any { it.matchesShortUuid(UUID_SMARTTHINGS_FIND) }) {
                add(ProtocolCapability.SMARTTHINGS_FIND)
            }
            if (observedUuids.any { it.matchesShortUuid(UUID_FAST_PAIR) }) add(ProtocolCapability.FAST_PAIR)

            serviceDataByUuid.entries
                .filter { (uuid, _) -> uuid.matchesShortUuid(UUID_FEAA) }
                .forEach { (_, frame) ->
                    val frameType = frame.firstOrNull()?.toInt()?.and(BYTE_MASK)
                    when {
                        frameType in FIND_HUB_FRAME_TYPES && frame.size in FIND_HUB_FRAME_LENGTHS ->
                            add(ProtocolCapability.FIND_HUB)
                        frameType in EDDYSTONE_FRAME_TYPES ->
                            add(ProtocolCapability.EDDYSTONE)
                    }
                }
        }

    /**
     * Rebuild capabilities from already persisted metadata. A bare FEAA UUID is intentionally
     * insufficient to reconstruct Eddystone or Find Hub because the discriminating frame is gone.
     */
    fun fromPersisted(
        beaconType: String?,
        gattServices: String?,
    ): Set<ProtocolCapability> =
        buildSet {
            val label = beaconType.orEmpty().lowercase()
            if (label.contains("find hub")) add(ProtocolCapability.FIND_HUB)
            if (label.contains("eddystone")) add(ProtocolCapability.EDDYSTONE)
            if (label.contains("dult")) add(ProtocolCapability.DULT)
            if (label.contains("smartthings find")) add(ProtocolCapability.SMARTTHINGS_FIND)

            val observedUuids =
                gattServices.orEmpty()
                    .split(",", ";")
                    .map { it.substringBefore(":").trim() }
            if (observedUuids.any { it.matchesShortUuid(UUID_DULT) }) add(ProtocolCapability.DULT)
            if (observedUuids.any { it.matchesShortUuid(UUID_SMARTTHINGS_FIND) }) {
                add(ProtocolCapability.SMARTTHINGS_FIND)
            }
            if (observedUuids.any { it.matchesShortUuid(UUID_FAST_PAIR) }) add(ProtocolCapability.FAST_PAIR)
        }

    private fun String.matchesShortUuid(shortUuid: String): Boolean {
        val compact = lowercase().replace("-", "")
        return compact == shortUuid || compact == "0000${shortUuid}00001000800000805f9b34fb"
    }

    private const val BYTE_MASK = 0xFF
    private const val UUID_DULT = "fcb2"
    private const val UUID_SMARTTHINGS_FIND = "fd5a"
    private const val UUID_FAST_PAIR = "fe2c"
    private const val UUID_FEAA = "feaa"
    private val FIND_HUB_FRAME_TYPES = setOf(0x40, 0x41)
    private val FIND_HUB_FRAME_LENGTHS = setOf(22, 34)
    private val EDDYSTONE_FRAME_TYPES = setOf(0x00, 0x10, 0x20, 0x30)
}
