package io.blueeye.core.data.repository.parser

import android.util.Log
import java.io.BufferedReader
import java.io.IOException
import java.io.InputStream
import java.io.InputStreamReader
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class OuiParser @Inject constructor() {
    fun parse(inputStream: InputStream): Map<String, String> {
        val map = HashMap<String, String>()
        try {
            BufferedReader(InputStreamReader(inputStream)).use { reader ->
                var line = reader.readLine()
                while (line != null) {
                    parseManufLine(line, map)
                    line = reader.readLine()
                }
            }
        } catch (e: IOException) {
            Log.e(TAG, "Parsing failed due to IO error", e)
        }
        return map
    }
    private fun parseManufLine(line: String, map: HashMap<String, String>) {
        if (line.isBlank() || line.startsWith("#")) return

        val parts = line.split(Regex("\\s+"), limit = SPLIT_LIMIT)
        if (parts.size >= 2) {
            val rawPrefix = parts[0]
            val cleanPrefix = rawPrefix.replace("[:\\-./]".toRegex(), "").uppercase()

            if (cleanPrefix.length >= OUI_LEN) {
                val ouiKey = cleanPrefix.substring(0, OUI_LEN)
                val vendorName = if (parts.size >= SPLIT_LIMIT && parts[2].isNotBlank()) parts[2] else parts[1]

                if (!map.containsKey(ouiKey)) {
                    map[ouiKey] = vendorName
                }
            }
        }
    }

    private companion object {
        private const val TAG = "OuiParser"
        private const val OUI_LEN = 6
        private const val SPLIT_LIMIT = 3
    }
}
