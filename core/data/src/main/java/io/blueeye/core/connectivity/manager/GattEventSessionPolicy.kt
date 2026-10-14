package io.blueeye.core.connectivity.manager

/** Ignore events emitted before a GATT session was replaced or closed. */
internal object GattEventSessionPolicy {
    fun isCurrent(eventEpoch: Long, currentEpoch: Long): Boolean = eventEpoch == currentEpoch
}
