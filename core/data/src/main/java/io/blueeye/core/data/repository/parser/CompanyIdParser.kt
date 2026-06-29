package io.blueeye.core.data.repository.parser

import android.util.Log
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject
import java.io.IOException
import java.io.InputStream
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class CompanyIdParser @Inject constructor() {
    companion object {
        private const val TAG = "CompanyIdParser"
    }

    fun parse(inputStream: InputStream): Map<Int, String> {
        val map = HashMap<Int, String>()
        try {
            val json = inputStream.bufferedReader().use { it.readText() }
            if (json.trim().startsWith("[")) {
                parseArray(json, map)
            } else {
                parseObject(json, map)
            }
        } catch (e: JSONException) {
            Log.e(TAG, "JSON parsing failed", e)
        } catch (e: IOException) {
            Log.e(TAG, "IO error during parsing", e)
        }
        return map
    }

    private fun parseArray(json: String, map: HashMap<Int, String>) {
        val array = JSONArray(json)
        for (i in 0 until array.length()) {
            val entry = array.getJSONObject(i)
            map[entry.getInt("code")] = entry.getString("name")
        }
    }

    private fun parseObject(json: String, map: HashMap<Int, String>) {
        val root = JSONObject(json)
        if (root.has("_default")) {
            val defaultObj = root.getJSONObject("_default")
            val keys = defaultObj.keys()
            while (keys.hasNext()) {
                parseEntry(defaultObj, keys.next(), map)
            }
        }
    }

    private fun parseEntry(defaultObj: JSONObject, key: String, map: HashMap<Int, String>) {
        val item = defaultObj.getJSONObject(key)
        val idStr = item.optString("id")
        val name = item.optString("mfg")
        try {
            val code = if (idStr.startsWith("0x")) Integer.decode(idStr) else idStr.toInt()
            map[code] = name
        } catch (@Suppress("SwallowedException") e: NumberFormatException) {
            // Ignore malformed IDs
        }
    }
}
