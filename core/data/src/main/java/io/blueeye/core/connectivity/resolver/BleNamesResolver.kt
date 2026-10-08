package io.blueeye.core.connectivity.resolver

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class BleNamesResolver @Inject constructor(
    @ApplicationContext private val context: Context,
    private val jsonParser: BleJsonParser,
) : BluetoothNamesResolver {
    private val serviceMap = mutableMapOf<String, String>()
    private val characteristicMap = mutableMapOf<String, String>()

    private companion object {
        const val FULL_UUID_LEN = 36
        const val DASH_POS = 8
        const val SHORT_UUID_START = 4
        const val SHORT_UUID_END = 8
        const val BASE_UUID = "-0000-1000-8000-00805f9b34fb"
        const val DULT_NON_OWNER_SERVICE = "15190001-12f4-c226-88ed-2ac5579f2a85"
        const val DULT_NON_OWNER_CHARACTERISTIC = "8e0c0001-1d68-fb92-bf61-48377421680e"
    }

    init {
        loadData()
    }

    private fun loadData() {
        jsonParser.parse(context, "service_uuids.json").forEach { item ->
            if (item.uuid != null && item.name != null) {
                serviceMap[item.uuid.lowercase()] = item.name
                serviceMap[toFullUuid(item.uuid).lowercase()] = item.name
            }
        }
        jsonParser.parse(context, "characteristic_uuids.json").forEach { item ->
            if (item.uuid != null && item.name != null) {
                characteristicMap[item.uuid.lowercase()] = item.name
                characteristicMap[toFullUuid(item.uuid).lowercase()] = item.name
            }
        }
    }

    override fun resolveServiceName(uuid: String): String {
        val lower = uuid.lowercase()
        if (lower == DULT_NON_OWNER_SERVICE) return "DULT Non-owner Accessory Information"
        val mappedName = serviceMap[lower] ?: if (lower.length == FULL_UUID_LEN && lower[DASH_POS] == '-') {
            serviceMap[lower.substring(SHORT_UUID_START, SHORT_UUID_END)]
        } else {
            null
        }

        return mappedName ?: "Unknown Service"
    }

    override fun resolveCharName(uuid: String): String {
        val lower = uuid.lowercase()
        if (lower == DULT_NON_OWNER_CHARACTERISTIC) return "DULT Non-owner Control (write/indicate)"
        val mappedName = characteristicMap[lower]
            ?: if (lower.length == FULL_UUID_LEN && lower[DASH_POS] == '-') {
                characteristicMap[lower.substring(SHORT_UUID_START, SHORT_UUID_END)]
            } else {
                null
            }

        return mappedName ?: "Unknown Char"
    }

    private fun toFullUuid(shortUuid: String): String = when (shortUuid.length) {
        SHORT_UUID_START -> "0000$shortUuid$BASE_UUID"
        else -> shortUuid
    }
}
