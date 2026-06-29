package io.blueeye.core.connectivity.resolver

import android.content.Context
import android.util.Log
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.decodeFromString
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class BleJsonParser @Inject constructor() {
    private val json = Json { ignoreUnknownKeys = true }

    fun parse(
        context: Context,
        fileName: String,
    ): List<BleJsonItem> {
        return try {
            context.assets.open(fileName).use { inputStream ->
                val content = inputStream.bufferedReader().readText()
                val list: List<BleJsonItem> = json.decodeFromString(content)
                Log.d("BleJsonParser", "Loaded ${list.size} items from $fileName")
                list
            }
        } catch (e: Exception) {
            when (e) {
                is IOException -> Log.e("BleJsonParser", "IO error loading $fileName", e)
                else -> Log.e("BleJsonParser", "Error parsing $fileName", e)
            }
            emptyList()
        }
    }

    @Serializable
    data class BleJsonItem(
        val name: String? = null,
        val uuid: String? = null,
        val identifier: String? = null,
        val source: String? = null,
    )
}
