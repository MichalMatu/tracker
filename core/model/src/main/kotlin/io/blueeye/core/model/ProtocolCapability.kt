package io.blueeye.core.model

/**
 * Protocols observed on a device. These describe advertised capabilities, not physical
 * hardware identity, ownership or evidence that a device is following the user.
 */
enum class ProtocolCapability {
    FIND_HUB,
    DULT,
    SMARTTHINGS_FIND,
    EDDYSTONE,
    FAST_PAIR,
}
