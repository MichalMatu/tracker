package io.blueeye.core.data.repository

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import io.blueeye.core.data.repository.parser.GattCharacteristicParser
import io.blueeye.core.data.repository.parser.GattServiceParser
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Repository for resolving GATT Services and Characteristics. Loads data from service_uuids.json
 * and characteristic_uuids.json.
 */
@Singleton
class GattRepository
@Inject
constructor(
    @ApplicationContext private val context: Context,
    private val serviceParser: GattServiceParser,
    private val characteristicParser: GattCharacteristicParser,
) {
    // UUID -> Name
    private var serviceMap: Map<String, String> = emptyMap()
    private var characteristicMap: Map<String, String> = emptyMap()

    // Proprietary Services based on Reverse Engineering Report
    private val customKnownServices =
        mapOf(
            // Samsung
            "FD5A" to "Samsung SmartThings",
            "FE2C" to "Google Fast Pair / Samsung Quick Share",
            // Bose
            "FEBE" to "Bose Proprietary Audio Control",
            // Microsoft
            "CDP" to "Microsoft Connected Devices Platform", // Placeholder, actual UUIDs
            // dynamic but managed by CdeSvc
        )

    private var isLoaded = false
    private val loadMutex = Mutex()

    /** Initialize the repository by loading data sources. */
    suspend fun init(): Result<Unit> = runCatching {
        loadMutex.withLock {
            if (isLoaded) return@runCatching
            withContext(Dispatchers.IO) {
                // Load Services
                val serviceFile = java.io.File(context.filesDir, "service_uuids.json")
                val serviceStream =
                    if (serviceFile.exists()) {
                        java.io.FileInputStream(serviceFile)
                    } else {
                        context.assets.open("service_uuids.json")
                    }
                val parsedServices = serviceParser.parse(serviceStream)

                // Merge parsed services with custom knowledge (custom takes precedence or
                // supplements)
                serviceMap = parsedServices + customKnownServices

                // Load Characteristics
                val charFile = java.io.File(context.filesDir, "characteristic_uuids.json")
                val charStream =
                    if (charFile.exists()) {
                        java.io.FileInputStream(charFile)
                    } else {
                        context.assets.open("characteristic_uuids.json")
                    }
                characteristicMap = characteristicParser.parse(charStream)
            }
            isLoaded = true
        }
    }

    suspend fun reload(): Result<Unit> {
        loadMutex.withLock { isLoaded = false }
        return init()
    }

    fun isInitialized(): Boolean = isLoaded

    fun getServiceCount(): Int = serviceMap.size

    fun getCharacteristicCount(): Int = characteristicMap.size

    fun getServiceName(uuid: String): String? {
        val normalizedUuid = uuid.uppercase()
        // Try exact match first (Full UUID)
        val exactMatch = serviceMap[normalizedUuid]
        if (exactMatch != null) return exactMatch

        // Try 16-bit match (Proprietary/SIG Short UUIDs)
        // Standard format: 0000xxxx-0000-1000-8000-00805F9B34FB
        val isFullUuid = normalizedUuid.length == FULL_UUID_LENGTH &&
            normalizedUuid.startsWith(SIG_UUID_PREFIX) &&
            normalizedUuid.endsWith(SIG_UUID_SUFFIX)

        return if (isFullUuid) {
            val shortUuid = normalizedUuid.substring(SHORT_UUID_START, SHORT_UUID_END)
            serviceMap[shortUuid]
        } else {
            null
        }
    }

    private companion object {
        private const val FULL_UUID_LENGTH = 36
        private const val SIG_UUID_PREFIX = "0000"
        private const val SIG_UUID_SUFFIX = "-0000-1000-8000-00805F9B34FB"
        private const val SHORT_UUID_START = 4
        private const val SHORT_UUID_END = 8
    }
}
