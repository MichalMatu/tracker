package io.blueeye.core.model

sealed class DeviceConnectionState {
    data object Disconnected : DeviceConnectionState()

    data object Connecting : DeviceConnectionState()

    data class Connected(
        val deviceName: String,
        val macAddress: String,
    ) : DeviceConnectionState()

    data class Error(
        val message: String,
    ) : DeviceConnectionState()
}
