package io.blueeye.core.connectivity.resolver

interface BluetoothNamesResolver {
    fun resolveServiceName(uuid: String): String

    fun resolveCharName(uuid: String): String
}
