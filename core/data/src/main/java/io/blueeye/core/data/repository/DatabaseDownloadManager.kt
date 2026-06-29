package io.blueeye.core.data.repository

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.BufferedInputStream
import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class DatabaseDownloadManager
@Inject
constructor(
    @ApplicationContext private val context: Context,
) {
    // Standard URLs
    companion object {
        private const val BASE_URL =
            "https://raw.githubusercontent.com/NordicSemiconductor/Bluetooth-Numbers-Database/master/v1"
        const val COMPANY_ID_URL = "$BASE_URL/company_ids.json"
        const val SERVICE_UUIDS_URL = "$BASE_URL/service_uuids.json"
        const val CHAR_UUIDS_URL = "$BASE_URL/characteristic_uuids.json"
        const val OUI_URL = "https://www.wireshark.org/download/automated/data/manuf"

        private const val DOWNLOAD_TIMEOUT_MS = 15000
        private const val BUFFER_SIZE = 4096
    }

    suspend fun downloadCompanyIds(): Boolean = downloadFile(COMPANY_ID_URL, "company_ids.json")

    suspend fun downloadServiceUuids(): Boolean = downloadFile(SERVICE_UUIDS_URL, "service_uuids.json")

    suspend fun downloadCharUuids(): Boolean = downloadFile(CHAR_UUIDS_URL, "characteristic_uuids.json")

    suspend fun downloadOui(): Boolean = downloadFile(OUI_URL, "manuf.txt")

    private suspend fun downloadFile(
        urlString: String,
        fileName: String,
    ): Boolean {
        return withContext(Dispatchers.IO) {
            var connection: HttpURLConnection? = null
            var inputStream: BufferedInputStream? = null
            var outputStream: FileOutputStream? = null

            try {
                val url = URL(urlString)
                connection = url.openConnection() as HttpURLConnection
                connection.connectTimeout = DOWNLOAD_TIMEOUT_MS
                connection.readTimeout = DOWNLOAD_TIMEOUT_MS
                connection.requestMethod = "GET"
                connection.connect()

                if (connection.responseCode != HttpURLConnection.HTTP_OK) {
                    return@withContext false
                }

                val tempFile = File(context.filesDir, "temp_$fileName")
                inputStream = BufferedInputStream(connection.inputStream)
                outputStream = FileOutputStream(tempFile)

                val data = ByteArray(BUFFER_SIZE)
                var count: Int
                while (inputStream.read(data).also { count = it } != -1) {
                    outputStream.write(data, 0, count)
                }
                outputStream.flush()

                // Close streams before moving
                outputStream.close()
                inputStream.close()
                outputStream = null
                inputStream = null

                // Move temp to final
                val finalFile = File(context.filesDir, fileName)
                if (finalFile.exists()) {
                    finalFile.delete()
                }
                if (tempFile.renameTo(finalFile)) {
                    return@withContext true
                } else {
                    // Fallback copy if rename fails (e.g. across filesystems, though unlikely here)
                    tempFile.copyTo(finalFile, overwrite = true)
                    tempFile.delete()
                    return@withContext true
                }
            } catch (@Suppress("TooGenericExceptionCaught", "SwallowedException") e: Exception) {
                // Log and return failure
                // android.util.Log.e("DatabaseDownload", "Download failed for $urlString", e)
                return@withContext false
            } finally {
                try {
                    outputStream?.close()
                    inputStream?.close()
                } catch (@Suppress("TooGenericExceptionCaught", "SwallowedException") e: Exception) {
                    // Ignore close exceptions
                }
                connection?.disconnect()
            }
        }
    }
}
