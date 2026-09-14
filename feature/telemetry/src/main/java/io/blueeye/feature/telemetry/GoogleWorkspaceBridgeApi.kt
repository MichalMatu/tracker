package io.blueeye.feature.telemetry

import android.content.Context
import android.util.Base64
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

internal data class BridgeTestResult(
    val driveFileId: String,
    val driveFileName: String,
    val gmailMessageId: String,
)

@Singleton
internal class GoogleWorkspaceBridgeApi
    @Inject
    constructor(
        @ApplicationContext private val context: Context,
    ) {
        private val json = Json { ignoreUnknownKeys = true }

        suspend fun fetchAccountEmail(accessToken: String): Result<String> =
            runCatching {
                withContext(Dispatchers.IO) {
                    val response = executeJsonRequest(
                        method = "GET",
                        url = GOOGLE_USERINFO_URL,
                        accessToken = accessToken,
                    )
                    json.parseToJsonElement(response)
                        .jsonObject["email"]
                        ?.jsonPrimitive
                        ?.content
                        ?.takeIf(String::isNotBlank)
                        ?: error("Google userinfo response did not include an email address")
                }
            }

        suspend fun runBridgeTest(
            accessToken: String,
            accountEmail: String,
        ): Result<BridgeTestResult> =
            runCatching {
                withContext(Dispatchers.IO) {
                    val rootFolderId = findOrCreateFolder(
                        accessToken = accessToken,
                        parentId = null,
                        name = "BlueEye",
                        role = ROOT_ROLE,
                    )
                    val testFolderId = findOrCreateFolder(
                        accessToken = accessToken,
                        parentId = rootFolderId,
                        name = "test",
                        role = TEST_FOLDER_ROLE,
                    )
                    val artifact = createBridgeTestArtifact(
                        accessToken = accessToken,
                        parentId = testFolderId,
                    )
                    val gmailMessageId = sendBatchReadyMessage(
                        accessToken = accessToken,
                        accountEmail = accountEmail,
                        artifact = artifact,
                    )
                    BridgeTestResult(
                        driveFileId = artifact.id,
                        driveFileName = artifact.name,
                        gmailMessageId = gmailMessageId,
                    )
                }
            }

        private fun findOrCreateFolder(
            accessToken: String,
            parentId: String?,
            name: String,
            role: String,
        ): String {
            val existing = findFolder(accessToken, parentId, role)
            if (existing != null) return existing

            val metadata =
                buildJsonObject {
                    put("name", name)
                    put("mimeType", DRIVE_FOLDER_MIME)
                    putJsonObject("appProperties") {
                        put(APP_ROLE_KEY, role)
                    }
                    if (parentId != null) {
                        put("parents", buildJsonArray { add(JsonPrimitive(parentId)) })
                    }
                }
            val response = executeJsonRequest(
                method = "POST",
                url = "$DRIVE_FILES_URL?fields=id,name",
                accessToken = accessToken,
                body = metadata.toString().toByteArray(StandardCharsets.UTF_8),
                contentType = JSON_CONTENT_TYPE,
            )
            return json.parseToJsonElement(response).jsonObject.requireString("id")
        }

        private fun findFolder(
            accessToken: String,
            parentId: String?,
            role: String,
        ): String? {
            val query =
                buildString {
                    append("trashed = false")
                    append(" and mimeType = '")
                    append(DRIVE_FOLDER_MIME)
                    append("'")
                    append(" and appProperties has { key='")
                    append(APP_ROLE_KEY)
                    append("' and value='")
                    append(role)
                    append("' }")
                    if (parentId != null) {
                        append(" and '")
                        append(parentId)
                        append("' in parents")
                    }
                }
            val encodedQuery = URLEncoder.encode(query, StandardCharsets.UTF_8.name())
            val encodedFields = URLEncoder.encode("files(id,name)", StandardCharsets.UTF_8.name())
            val response = executeJsonRequest(
                method = "GET",
                url = "$DRIVE_FILES_URL?q=$encodedQuery&spaces=drive&pageSize=10&fields=$encodedFields",
                accessToken = accessToken,
            )
            return json.parseToJsonElement(response)
                .jsonObject["files"]
                ?.jsonArray
                ?.firstOrNull()
                ?.jsonObject
                ?.get("id")
                ?.jsonPrimitive
                ?.content
        }

        private fun createBridgeTestArtifact(
            accessToken: String,
            parentId: String,
        ): DriveArtifact {
            val now = System.currentTimeMillis()
            val testId = UUID.randomUUID().toString()
            val fileName = "bridge-test-$now.json"
            val payload =
                buildJsonObject {
                    put("schemaVersion", 1)
                    put("kind", "bridge_test")
                    put("testId", testId)
                    put("createdAtEpochMs", now)
                    put("appPackage", context.packageName)
                    put("appVersion", appVersionName())
                }.toString()
            val metadata =
                buildJsonObject {
                    put("name", fileName)
                    put("mimeType", JSON_MIME_TYPE)
                    put("parents", buildJsonArray { add(JsonPrimitive(parentId)) })
                    putJsonObject("appProperties") {
                        put(APP_ROLE_KEY, TEST_ARTIFACT_ROLE)
                        put(TEST_ID_KEY, testId)
                    }
                }

            val boundary = "blueeye_${UUID.randomUUID()}"
            val multipartBody = multipartBody(boundary, metadata, payload)
            val response = executeJsonRequest(
                method = "POST",
                url = "$DRIVE_UPLOAD_URL?uploadType=multipart&fields=id,name",
                accessToken = accessToken,
                body = multipartBody,
                contentType = "multipart/related; boundary=$boundary",
            )
            val responseObject = json.parseToJsonElement(response).jsonObject
            return DriveArtifact(
                id = responseObject.requireString("id"),
                name = responseObject.requireString("name"),
                testId = testId,
                createdAtEpochMs = now,
            )
        }

        private fun sendBatchReadyMessage(
            accessToken: String,
            accountEmail: String,
            artifact: DriveArtifact,
        ): String {
            val safeEmail = accountEmail.replace("\r", "").replace("\n", "")
            val subject = "BLUEEYE/BATCH_READY type=bridge_test"
            val body =
                buildString {
                    appendLine("schemaVersion=1")
                    appendLine("kind=bridge_test")
                    appendLine("testId=${artifact.testId}")
                    appendLine("createdAtEpochMs=${artifact.createdAtEpochMs}")
                    appendLine("driveFileId=${artifact.id}")
                    appendLine("driveFileName=${artifact.name}")
                }
            val mime =
                buildString {
                    append("From: ").append(safeEmail).append("\r\n")
                    append("To: ").append(safeEmail).append("\r\n")
                    append("Subject: ").append(subject).append("\r\n")
                    append("MIME-Version: 1.0\r\n")
                    append("Content-Type: text/plain; charset=UTF-8\r\n")
                    append("\r\n")
                    append(body)
                }
            val encoded = Base64.encodeToString(
                mime.toByteArray(StandardCharsets.UTF_8),
                Base64.URL_SAFE or Base64.NO_WRAP or Base64.NO_PADDING,
            )
            val requestBody = buildJsonObject { put("raw", encoded) }.toString()
            val response = executeJsonRequest(
                method = "POST",
                url = GMAIL_SEND_URL,
                accessToken = accessToken,
                body = requestBody.toByteArray(StandardCharsets.UTF_8),
                contentType = JSON_CONTENT_TYPE,
            )
            return json.parseToJsonElement(response).jsonObject.requireString("id")
        }

        private fun multipartBody(
            boundary: String,
            metadata: JsonObject,
            payload: String,
        ): ByteArray {
            val content =
                buildString {
                    append("--").append(boundary).append("\r\n")
                    append("Content-Type: application/json; charset=UTF-8\r\n\r\n")
                    append(metadata).append("\r\n")
                    append("--").append(boundary).append("\r\n")
                    append("Content-Type: application/json; charset=UTF-8\r\n\r\n")
                    append(payload).append("\r\n")
                    append("--").append(boundary).append("--\r\n")
                }
            return content.toByteArray(StandardCharsets.UTF_8)
        }

        private fun executeJsonRequest(
            method: String,
            url: String,
            accessToken: String,
            body: ByteArray? = null,
            contentType: String? = null,
        ): String {
            val connection = URL(url).openConnection() as HttpURLConnection
            try {
                connection.requestMethod = method
                connection.connectTimeout = CONNECT_TIMEOUT_MS
                connection.readTimeout = READ_TIMEOUT_MS
                connection.setRequestProperty("Authorization", "Bearer $accessToken")
                connection.setRequestProperty("Accept", "application/json")
                if (body != null) {
                    connection.doOutput = true
                    connection.setFixedLengthStreamingMode(body.size)
                    if (contentType != null) {
                        connection.setRequestProperty("Content-Type", contentType)
                    }
                    connection.outputStream.use { output -> output.write(body) }
                }

                val status = connection.responseCode
                val responseStream =
                    if (status in HTTP_OK_MIN..HTTP_OK_MAX) {
                        connection.inputStream
                    } else {
                        connection.errorStream
                    }
                val response = responseStream?.bufferedReader()?.use { it.readText() }.orEmpty()
                if (status !in HTTP_OK_MIN..HTTP_OK_MAX) {
                    throw GoogleWorkspaceHttpException(status, response.take(MAX_ERROR_BODY_CHARS))
                }
                return response
            } finally {
                connection.disconnect()
            }
        }

        @Suppress("DEPRECATION")
        private fun appVersionName(): String {
            return runCatching {
                context.packageManager.getPackageInfo(context.packageName, 0).versionName
            }.getOrNull().orEmpty().ifBlank { "unknown" }
        }

        private data class DriveArtifact(
            val id: String,
            val name: String,
            val testId: String,
            val createdAtEpochMs: Long,
        )

        private class GoogleWorkspaceHttpException(
            status: Int,
            body: String,
        ) : IllegalStateException("Google API request failed with HTTP $status: $body")

        private companion object {
            const val GOOGLE_USERINFO_URL = "https://www.googleapis.com/oauth2/v2/userinfo"
            const val DRIVE_FILES_URL = "https://www.googleapis.com/drive/v3/files"
            const val DRIVE_UPLOAD_URL = "https://www.googleapis.com/upload/drive/v3/files"
            const val GMAIL_SEND_URL = "https://gmail.googleapis.com/gmail/v1/users/me/messages/send"
            const val DRIVE_FOLDER_MIME = "application/vnd.google-apps.folder"
            const val JSON_MIME_TYPE = "application/json"
            const val JSON_CONTENT_TYPE = "application/json; charset=UTF-8"
            const val APP_ROLE_KEY = "blueeyeBridgeRole"
            const val TEST_ID_KEY = "blueeyeBridgeTestId"
            const val ROOT_ROLE = "bridge_root"
            const val TEST_FOLDER_ROLE = "bridge_test_folder"
            const val TEST_ARTIFACT_ROLE = "bridge_test_artifact"
            const val CONNECT_TIMEOUT_MS = 15_000
            const val READ_TIMEOUT_MS = 20_000
            const val HTTP_OK_MIN = 200
            const val HTTP_OK_MAX = 299
            const val MAX_ERROR_BODY_CHARS = 2_000
        }
    }

private fun JsonObject.requireString(key: String): String =
    get(key)?.jsonPrimitive?.content?.takeIf(String::isNotBlank)
        ?: error("Google API response is missing '$key'")
