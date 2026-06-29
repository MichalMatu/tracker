package io.blueeye.core.data.repository.parser

import android.util.Log
import org.json.JSONArray
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class GattCharacteristicParser
@Inject
constructor() {
    companion object {
        private const val TAG = "GattCharParser"
    }

    fun parse(inputStream: java.io.InputStream): Map<String, String> {
        val map = HashMap<String, String>()
        try {
            val jsonString = inputStream.bufferedReader().use { it.readText() }
            val jsonArray = JSONArray(jsonString)

            for (i in 0 until jsonArray.length()) {
                val entry = jsonArray.getJSONObject(i)
                val uuid = entry.getString("uuid")
                val name = entry.getString("name")
                map[uuid] = name
            }
        } catch (@Suppress("TooGenericExceptionCaught") e: Exception) {
            Log.e(TAG, "Parsing failed", e)
        }
        return map
    }
}
