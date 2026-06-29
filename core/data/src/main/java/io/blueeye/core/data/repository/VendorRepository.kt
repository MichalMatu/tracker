package io.blueeye.core.data.repository

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Repository for resolving Bluetooth device vendor names.
 */
@Singleton
class VendorRepository @Inject constructor(
    @ApplicationContext private val context: Context,
    private val companyIdParser: io.blueeye.core.data.repository.parser.CompanyIdParser,
    private val ouiParser: io.blueeye.core.data.repository.parser.OuiParser,
) {
    private var companyIdMap: Map<Int, String> = emptyMap()
    private var ouiMap: Map<String, String> = emptyMap()
    private var isLoaded = false
    private val loadMutex = Mutex()

    companion object {
        private const val OUI_LEN = 6
        private const val MAC_MIN = 8

        private val PUBLIC_OUIS_RPA = setOf(
            "404CCA", "485519", "483FDA", "543204", "58CF79", "600194", "6055F9", "68B6B3",
            "48B4", "546009", "4006A0", "44783E", "4098AD", "441793", "48437C", "4447CC", "544A16"
        )
    }

    suspend fun init(): Result<Unit> = runCatching {
        loadMutex.withLock {
            if (isLoaded) return@runCatching
            withContext(Dispatchers.IO) {
                val cFile = java.io.File(context.filesDir, "company_ids.json")
                val cStream = if (cFile.exists()) {
                    java.io.FileInputStream(
                        cFile
                    )
                } else {
                    context.assets.open("company_ids.json")
                }
                companyIdMap = companyIdParser.parse(cStream)

                val oFile = java.io.File(context.filesDir, "manuf.txt")
                val oStream = if (oFile.exists()) java.io.FileInputStream(oFile) else context.assets.open("manuf.txt")
                ouiMap = ouiParser.parse(oStream)
            }
            isLoaded = true
        }
    }

    suspend fun reload(): Result<Unit> {
        loadMutex.withLock { isLoaded = false }
        return init()
    }

    fun getVendorName(mac: String, manufacturerId: Int?): String? {
        return manufacturerId?.let { companyIdMap[it] } ?: resolveOui(mac)
    }

    private fun resolveOui(mac: String): String? {
        val macClean = mac.replace(":", "").uppercase()
        if (macClean.length < OUI_LEN) return null

        val validPrefix = macClean.substring(0, OUI_LEN)
        return ouiMap[validPrefix]
    }

    fun isInitialized(): Boolean = isLoaded

    fun hasOui(mac: String): Boolean {
        if (mac.length < MAC_MIN) return false
        val clean = mac.replace(":", "").uppercase()
        return if (clean.length >= OUI_LEN) {
            val oui = clean.substring(0, OUI_LEN)
            ouiMap.containsKey(oui) || oui in PUBLIC_OUIS_RPA
        } else {
            false
        }
    }

    fun getCompanyIdCount(): Int = companyIdMap.size
    fun getOuiCount(): Int = ouiMap.size
}
