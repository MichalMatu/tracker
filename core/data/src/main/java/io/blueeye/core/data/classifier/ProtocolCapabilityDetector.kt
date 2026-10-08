package io.blueeye.core.data.classifier

import io.blueeye.core.model.ProtocolCapability

/**
 * Independent BLE protocol detection. UUID-only observations are retained as capabilities
 * without promoting headphones, TVs or phones to physical tracker types.
 */
object ProtocolCapabilityDetector {
    fun fromAdvertisement(
        serviceUuids: List<String>,
        serviceDataByUuid: Map<String, ByteArray>,
    ): Set<ProtocolCapability> = buildSet {
        val observedUuids = serviceUuids + serviceDataByUuid.keys

        if (observedUuids.any { it.matchesShortUuid("fcb2") }) add(ProtocolCapability.DULT)
        if (observedUuids.any { it.matchesShortUuid("fd5a") }) add(ProtocolCapability.SMARTTHINGS_FIND)
        if (observedUuids.any { it.matchesShortUuid("fe2c") }) add(ProtocolCapability.FAST_PAIR)

        serviceDataByUuid.entries
            .filter { (uuid, _) -> uuid.matchesShortUuid("feaa") }
            .forEach { (_, frame) ->
                val frameType = frame.firstOrNull()?.toInt()?.and(0xFF)
                when {
                    frameType != null && frameType in setOf(0x40, 0x41) &&
                        frame.size in setOf(22, 34) ->
                        add(ProtocolCapability.FIND_HUB)
                    frameType != null && frameType in setOf(0x00, 0x10, 0x20, 0x30) ->
                        add(ProtocolCapability.EDDYSTONE)
                }
            }
    }

    /**
     * Rebuild capabilities from fields already stored in DeviceEntity. The persisted
     * beacon label is only used for frame-validated protocols; a bare FEAA UUID never
     * establishes Eddystone or Find Hub.
     */
    fun fromPersisted(
        beaconType: String?,
        gattServices: String?,
    ): Set<ProtocolCapability> = buildSet {
        val label = beaconType.orEmpty().lowercase()
        if (label.contains("find hub")) add(ProtocolCapability.FIND_HUB)
        if (label.contains("eddystone")) add(ProtocolCapability.EDDYSTONE)
        if (label.contains("dult")) add(ProtocolCapability.DULT)
        if (label.contains("smartthings find")) add(ProtocolCapability.SMARTTHINGS_FIND)

        val observedUuids = gattServices.orEmpty().split(",", ";")
            .map { it.substringBefore(":").trim() }
        if (observedUuids.any { it.matchesShortUuid("fcb2") }) add(ProtocolCapability.DULT)
        if (observedUuids.any { it.matchesShortUuid("fd5a") }) add(ProtocolCapability.SMARTTHINGS_FIND)
        if (observedUuids.any { it.matchesShortUuid("fe2c") }) add(ProtocolCapability.FAST_PAIR)
    }

    private fun String.matchesShortUuid(shortUuid: String): Boolean {
        val compact = lowercase().replace("-", "")
        return compact == shortUuid ||
            compact == "0000${shortUuid}00001000800000805f9b34fb"
    }
}
