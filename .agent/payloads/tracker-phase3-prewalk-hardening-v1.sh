#!/usr/bin/env bash
set -euo pipefail

EXPECTED_HEAD="3a1ab7554bec9eca5fedff7d727665f5188af876"
REPO="MichalMatu/tracker"

git fetch origin main agent-control >/dev/null
CURRENT="$(git rev-parse origin/main)"
echo "source_head=$CURRENT"
if [[ "$CURRENT" != "$EXPECTED_HEAD" ]]; then
  echo "ERROR: origin/main moved; expected $EXPECTED_HEAD" >&2
  exit 20
fi

git checkout main >/dev/null 2>&1 || git checkout -b main origin/main
git reset --hard origin/main >/dev/null
if [[ -n "$(git status --porcelain)" ]]; then
  echo "ERROR: worktree not clean before patch" >&2
  exit 21
fi

python3 - <<'PY'
from pathlib import Path

# 1) Correct active foreground-service types for long screen-off BLE + location collection.
p = Path("core/data/src/main/java/io/blueeye/service/ScannerService.kt")
s = p.read_text()
old = """                    android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_CONNECTED_DEVICE,\n"""
new = """                    android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_CONNECTED_DEVICE or\n                        android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION,\n"""
if old not in s:
    raise SystemExit("ScannerService foreground type marker not found")
s = s.replace(old, new, 1)
p.write_text(s)

# 2) Add a reusable diagnostics append fragment for a streamed root object.
p = Path("feature/settings/src/main/java/io/blueeye/feature/settings/FieldMvpExportAppend.kt")
s = p.read_text()
marker = """internal fun Writer.writeWithFieldMvpDiagnostics(\n"""
helper = """internal fun fieldMvpDiagnosticsAppendFragment(uiState: SettingsUiState): String {\n    val decoratedEmptyObject = \"{}\".withFieldMvpDiagnostics(uiState)\n    return decoratedEmptyObject.substring(1, decoratedEmptyObject.length - 1)\n}\n\n"""
if marker not in s:
    raise SystemExit("FieldMvp writer marker not found")
if "fieldMvpDiagnosticsAppendFragment" not in s:
    s = s.replace(marker, helper + marker, 1)
old_fragment = """    val decoratedEmptyObject = \"{}\".withFieldMvpDiagnostics(uiState)\n    val diagnosticsFragment = decoratedEmptyObject.substring(1, decoratedEmptyObject.length - 1)\n"""
new_fragment = """    val diagnosticsFragment = fieldMvpDiagnosticsAppendFragment(uiState)\n"""
if old_fragment not in s:
    raise SystemExit("FieldMvp existing fragment construction not found")
s = s.replace(old_fragment, new_fragment, 1)
p.write_text(s)

# 3) Refactor DatabaseExporter data loading and add file streaming that never materializes
#    the top-level devices/signalSamples arrays or final export String.
p = Path("feature/settings/src/main/java/io/blueeye/feature/settings/DatabaseExporter.kt")
s = p.read_text()
if "import java.io.File\n" not in s:
    s = s.replace("import io.blueeye.core.model.SignalSample\n", "import io.blueeye.core.model.SignalSample\nimport java.io.File\nimport java.io.Writer\n", 1)
if "import kotlinx.coroutines.Dispatchers\n" not in s:
    s = s.replace("import kotlinx.coroutines.flow.first\n", "import kotlinx.coroutines.Dispatchers\nimport kotlinx.coroutines.flow.first\nimport kotlinx.coroutines.withContext\n", 1)

start = s.index('        @Suppress("TooGenericExceptionCaught", "SwallowedException")\n        suspend fun export(): String? {')
end = s.index('        private suspend fun loadSessionFollowMeObservations(', start)
new_export = '''        suspend fun export(): String? =\n            withContext(Dispatchers.Default) {\n                runCatching {\n                    json.encodeToString(DatabaseExportJsonMapper.buildExport(loadData()))\n                }.getOrNull()\n            }\n\n        suspend fun exportToFile(\n            file: File,\n            trailingRootFragmentProvider: () -> String = { "" },\n        ): Boolean =\n            withContext(Dispatchers.IO) {\n                runCatching {\n                    val data = loadData()\n                    file.bufferedWriter(Charsets.UTF_8).use { writer ->\n                        DatabaseExportJsonMapper.writeExport(\n                            data = data,\n                            writer = writer,\n                            json = json,\n                            trailingRootFragmentProvider = trailingRootFragmentProvider,\n                        )\n                    }\n                }.isSuccess\n            }\n\n        private suspend fun loadData(): DatabaseExportData {\n            val devices = deviceRepository.getAllDevicesSync().getOrDefault(emptyList())\n            val samples = signalSampleRepository.getAllSignalSamples().getOrDefault(emptyList())\n            val exportDate = System.currentTimeMillis()\n            val sessionSettings = settingsPreferencesRepository.session.first()\n            val sessionLabel = sessionSettings.calibrationLabel\n            val sessionStartedAt = sessionSettings.startedAt\n            val sessionNotes = sessionSettings.notes\n            val activeCollectionEnabled = activeCollectionRepository.autoActiveProbeEnabled.first()\n            val sessionDevices =\n                if (sessionStartedAt > 0L) {\n                    devices.filter { it.lastSeenAt >= sessionStartedAt }\n                } else {\n                    emptyList()\n                }\n            val sessionSamples =\n                if (sessionStartedAt > 0L) {\n                    samples.filter { it.timestamp >= sessionStartedAt }\n                } else {\n                    emptyList()\n                }\n            val sessionFollowMeObservations =\n                loadSessionFollowMeObservations(\n                    devices = sessionDevices,\n                    sessionStartedAt = sessionStartedAt,\n                )\n            val sessionAlertEvidenceEvents =\n                loadSessionAlertEvidenceEvents(\n                    devices = sessionDevices,\n                    sessionStartedAt = sessionStartedAt,\n                )\n\n            return DatabaseExportData(\n                devices = devices,\n                samples = samples,\n                session =\n                    DatabaseExportSessionData(\n                        devices = sessionDevices,\n                        samples = sessionSamples,\n                        label = sessionLabel,\n                        startedAt = sessionStartedAt,\n                        notes = sessionNotes,\n                        activeCollectionEnabled = activeCollectionEnabled,\n                        followMeObservations = sessionFollowMeObservations,\n                        alertEvidenceEvents = sessionAlertEvidenceEvents,\n                    ),\n                exportDate = exportDate,\n            )\n        }\n\n'''
s = s[:start] + new_export + s[end:]

obj_start = s.index("internal object DatabaseExportJsonMapper {")
map_session = s.index("    private fun mapSession(", obj_start)
new_mapper_head = '''internal object DatabaseExportJsonMapper {\n    fun buildExport(data: DatabaseExportData): JsonObject =\n        buildJsonObject {\n            buildExportMetadata(data).forEach { (key, value) -> put(key, value) }\n            put("devices", JsonArray(data.devices.map(::mapDevice)))\n            put("signalSamples", JsonArray(data.samples.map(::mapSample)))\n        }\n\n    fun writeExport(\n        data: DatabaseExportData,\n        writer: Writer,\n        json: Json,\n        trailingRootFragmentProvider: () -> String = { "" },\n    ) {\n        val metadata = json.encodeToString(buildExportMetadata(data))\n        val closingBrace = metadata.lastIndexOf('}')\n        check(closingBrace >= 0) { "Export metadata is not a JSON object" }\n\n        var prefixEnd = closingBrace\n        while (prefixEnd > 0 && metadata[prefixEnd - 1].isWhitespace()) {\n            prefixEnd -= 1\n        }\n\n        writer.write(metadata, 0, prefixEnd)\n        writer.write(",\\n  \\\"devices\\\": [")\n        writeJsonArray(writer, data.devices, json, ::mapDevice)\n        writer.write("\\n  ],\\n  \\\"signalSamples\\\": [")\n        writeJsonArray(writer, data.samples, json, ::mapSample)\n        writer.write("\\n  ]")\n\n        val trailingRootFragment = trailingRootFragmentProvider()\n        if (trailingRootFragment.isNotBlank()) {\n            writer.write(trailingRootFragment)\n        }\n        writer.write("\\n}")\n    }\n\n    private fun <T> writeJsonArray(\n        writer: Writer,\n        values: List<T>,\n        json: Json,\n        mapper: (T) -> JsonObject,\n    ) {\n        values.forEachIndexed { index, value ->\n            if (index > 0) writer.write(",")\n            writer.write("\\n    ")\n            writer.write(json.encodeToString(mapper(value)))\n        }\n    }\n\n    private fun buildExportMetadata(data: DatabaseExportData): JsonObject =\n        buildJsonObject {\n            put("schemaVersion", SCHEMA_VERSION)\n            put("exportDate", data.exportDate)\n            put("deviceCount", data.devices.size)\n            put("sampleCount", data.samples.size)\n            putSampleQuality(data.samples)\n            put(\n                "privacyNotice",\n                buildJsonObject {\n                    put("containsMacAddresses", data.devices.any { it.macAddress.isNotBlank() })\n                    put(\n                        "containsGpsSamples",\n                        data.samples.any { sample ->\n                            sample.latitude != null ||\n                                sample.longitude != null ||\n                                sample.locationAccuracy != null\n                        },\n                    )\n                    put(\n                        "containsRawPayloads",\n                        data.devices.any { !it.lastRawData.isNullOrBlank() } ||\n                            data.samples.any(SessionSignalSampleExportQuality::hasRawPayloadData),\n                    )\n                    put(\n                        "containsActiveProbeData",\n                        data.devices.any(SessionActiveProbeSummaryCalculator::hasActiveProbeData),\n                    )\n                    put(\n                        "handling",\n                        "Treat as sensitive local telemetry. Share only with trusted reviewers.",\n                    )\n                },\n            )\n            put(\n                "session",\n                mapSession(\n                    session = data.session,\n                    exportDate = data.exportDate,\n                ),\n            )\n        }\n\n'''
s = s[:obj_start] + new_mapper_head + s[map_session:]
p.write_text(s)

# 4) Wire the ViewModel to streamed file export for Share while keeping String export for Copy/tests.
p = Path("feature/settings/src/main/java/io/blueeye/feature/settings/SettingsViewModel.kt")
s = p.read_text()
if "import java.io.File\n" not in s:
    s = s.replace("import io.blueeye.core.model.IdentityCarryoverVerdict\n", "import io.blueeye.core.model.IdentityCarryoverVerdict\nimport java.io.File\n", 1)
marker = '''        fun exportDatabase(onResult: (String?) -> Unit) {\n            viewModelScope.launch {\n                val json = databaseExporter.export()\n                onResult(json)\n            }\n        }\n\n'''
addition = marker + '''        fun exportDatabaseToFile(\n            file: File,\n            onResult: (Boolean) -> Unit,\n        ) {\n            viewModelScope.launch {\n                val exported =\n                    databaseExporter.exportToFile(file) {\n                        fieldMvpDiagnosticsAppendFragment(uiState.value)\n                    }\n                onResult(exported)\n            }\n        }\n\n'''
if marker not in s:
    raise SystemExit("SettingsViewModel exportDatabase marker not found")
s = s.replace(marker, addition, 1)
p.write_text(s)

# 5) Make Share go directly to the temp file; finalize atomically and share that file.
p = Path("feature/settings/src/main/java/io/blueeye/feature/settings/DatabaseExportActions.kt")
s = p.read_text()
start = s.index("internal fun prepareDatabaseExportForShare(")
end = s.index("internal fun isClipboardPayloadSafe(", start)
new_prepare = '''internal fun prepareDatabaseExportForShare(\n    context: Context,\n    viewModel: SettingsViewModel,\n) {\n    Toast.makeText(context, "Preparing session export...", Toast.LENGTH_SHORT).show()\n    val directory = File(context.cacheDir, "session_exports")\n    if (!directory.exists() && !directory.mkdirs()) {\n        Toast.makeText(context, "Unable to prepare session export", Toast.LENGTH_SHORT).show()\n        return\n    }\n\n    val target = File(directory, "blueeye-session-export.json")\n    val temporary = File(directory, "blueeye-session-export.json.tmp")\n    viewModel.exportDatabaseToFile(temporary) { exported ->\n        if (!exported) {\n            temporary.delete()\n            Toast.makeText(context, "Export failed", Toast.LENGTH_SHORT).show()\n        } else {\n            runCatching {\n                replaceSessionExportFile(temporary, target)\n                shareExportFile(context, target)\n            }.onFailure {\n                temporary.delete()\n                Toast.makeText(context, "Unable to share session export", Toast.LENGTH_SHORT).show()\n            }\n        }\n    }\n}\n\n'''
s = s[:start] + new_prepare + s[end:]

share_start = s.index("internal fun shareExport(")
new_tail = '''private fun shareExportFile(\n    context: Context,\n    exportFile: File,\n) {\n    val uri =\n        FileProvider.getUriForFile(\n            context,\n            "${context.packageName}.fileprovider",\n            exportFile,\n        )\n    val sendIntent =\n        Intent(Intent.ACTION_SEND).apply {\n            type = "application/json"\n            putExtra(Intent.EXTRA_SUBJECT, "BlueEye session export")\n            putExtra(Intent.EXTRA_STREAM, uri)\n            clipData = ClipData.newRawUri("BlueEye Session Export JSON", uri)\n            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)\n        }\n    val chooser =\n        Intent.createChooser(sendIntent, "Share BlueEye session export").apply {\n            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)\n        }\n    context.startActivity(chooser)\n}\n\nprivate fun replaceSessionExportFile(\n    temporary: File,\n    target: File,\n) {\n    if (target.exists() && !target.delete()) {\n        temporary.delete()\n        throw IOException("Unable to replace previous session export")\n    }\n    if (!temporary.renameTo(target)) {\n        temporary.copyTo(target, overwrite = true)\n        temporary.delete()\n    }\n}\n'''
s = s[:share_start] + new_tail
p.write_text(s)

# 6) Regression: streamed mapper output parses to the exact same JSON tree as the in-memory mapper.
p = Path("feature/settings/src/test/java/io/blueeye/feature/settings/DatabaseExportJsonMapperTest.kt")
s = p.read_text()
if "import kotlinx.serialization.json.Json\n" not in s:
    s = s.replace("import kotlinx.serialization.json.JsonObject\n", "import kotlinx.serialization.json.Json\nimport kotlinx.serialization.json.JsonObject\n", 1)
if "import java.io.StringWriter\n" not in s:
    s = s.replace("import org.junit.Test\n", "import org.junit.Test\nimport java.io.StringWriter\n", 1)
class_marker = "class DatabaseExportJsonMapperTest {\n"
test = '''class DatabaseExportJsonMapperTest {\n    @Test\n    fun `streamed export is semantically identical to in-memory mapper`() {\n        val device =\n            device(\n                fingerprint = "stream-device",\n                lastSeenAt = SESSION_STARTED_AT + 10_000L,\n                calibrationLabel = DeviceCalibrationLabel.UNKNOWN,\n            )\n        val sample =\n            sample(\n                deviceFingerprint = device.fingerprint,\n                timestamp = SESSION_STARTED_AT + 12_000L,\n                latitude = 51.1,\n                longitude = 17.0,\n            )\n        val data =\n            DatabaseExportData(\n                devices = listOf(device),\n                samples = listOf(sample),\n                session =\n                    DatabaseExportSessionData(\n                        devices = listOf(device),\n                        samples = listOf(sample),\n                        label = DeviceCalibrationLabel.UNKNOWN,\n                        startedAt = SESSION_STARTED_AT,\n                        notes = "stream-test",\n                        activeCollectionEnabled = false,\n                        followMeObservations = emptyList(),\n                        alertEvidenceEvents = emptyList(),\n                    ),\n                exportDate = EXPORT_DATE,\n            )\n        val writer = StringWriter()\n        val json = Json { prettyPrint = true }\n\n        DatabaseExportJsonMapper.writeExport(data, writer, json)\n\n        assertEquals(\n            DatabaseExportJsonMapper.buildExport(data),\n            Json.parseToJsonElement(writer.toString()).jsonObject,\n        )\n    }\n\n'''
if class_marker not in s:
    raise SystemExit("DatabaseExportJsonMapperTest class marker not found")
s = s.replace(class_marker, test, 1)
p.write_text(s)

# 7) Regression for the streamed diagnostics root-tail fragment.
p = Path("feature/settings/src/test/java/io/blueeye/feature/settings/FieldMvpExportAppendTest.kt")
s = p.read_text()
marker = "class FieldMvpExportAppendTest {\n"
test = '''class FieldMvpExportAppendTest {\n    @Test\n    fun `diagnostics append fragment is valid at streamed root tail`() {\n        val uiState = SettingsUiState(scannerDiagnostics = ScannerRuntimeDiagnostics())\n        val json = "{\\\"schemaVersion\\\":19${fieldMvpDiagnosticsAppendFragment(uiState)}}"\n\n        val root = Json.parseToJsonElement(json).jsonObject\n\n        assertEquals(19L, root.getValue("schemaVersion").jsonPrimitive.long)\n        root.getValue("fieldMvpDiagnostics").jsonObject\n    }\n\n'''
if marker not in s:
    raise SystemExit("FieldMvpExportAppendTest class marker not found")
s = s.replace(marker, test, 1)
p.write_text(s)
PY

# Guard scope before invoking Gradle.
EXPECTED_FILES=$(cat <<'EOF'
core/data/src/main/java/io/blueeye/service/ScannerService.kt
feature/settings/src/main/java/io/blueeye/feature/settings/DatabaseExportActions.kt
feature/settings/src/main/java/io/blueeye/feature/settings/DatabaseExporter.kt
feature/settings/src/main/java/io/blueeye/feature/settings/FieldMvpExportAppend.kt
feature/settings/src/main/java/io/blueeye/feature/settings/SettingsViewModel.kt
feature/settings/src/test/java/io/blueeye/feature/settings/DatabaseExportJsonMapperTest.kt
feature/settings/src/test/java/io/blueeye/feature/settings/FieldMvpExportAppendTest.kt
EOF
)
ACTUAL_FILES="$(git diff --name-only | sort)"
if [[ "$ACTUAL_FILES" != "$(printf '%s\n' "$EXPECTED_FILES" | sort)" ]]; then
  echo "ERROR: unexpected changed-file set" >&2
  echo "actual:" >&2
  printf '%s\n' "$ACTUAL_FILES" >&2
  exit 22
fi

git diff --check

echo "--- diff stat ---"
git diff --stat

echo "--- focused settings tests ---"
./gradlew :feature:settings:testDebugUnitTest

echo "--- core data tests ---"
./gradlew :core:data:testDebugUnitTest

echo "--- quality gate ---"
./gradlew qualityCheck

echo "--- debug APK ---"
./gradlew :app:assembleDebug

git diff --check

git add \
  core/data/src/main/java/io/blueeye/service/ScannerService.kt \
  feature/settings/src/main/java/io/blueeye/feature/settings/DatabaseExportActions.kt \
  feature/settings/src/main/java/io/blueeye/feature/settings/DatabaseExporter.kt \
  feature/settings/src/main/java/io/blueeye/feature/settings/FieldMvpExportAppend.kt \
  feature/settings/src/main/java/io/blueeye/feature/settings/SettingsViewModel.kt \
  feature/settings/src/test/java/io/blueeye/feature/settings/DatabaseExportJsonMapperTest.kt \
  feature/settings/src/test/java/io/blueeye/feature/settings/FieldMvpExportAppendTest.kt

git commit -m "Harden long field scan export"
NEW_HEAD="$(git rev-parse HEAD)"
git push origin HEAD:main

git fetch origin main >/dev/null
REMOTE_HEAD="$(git rev-parse origin/main)"
if [[ "$REMOTE_HEAD" != "$NEW_HEAD" ]]; then
  echo "ERROR: pushed main does not match local commit" >&2
  exit 23
fi

if [[ -n "$(git status --porcelain)" ]]; then
  echo "ERROR: worktree dirty after commit" >&2
  git status --short >&2
  exit 24
fi

echo "PREWALK_HARDENING_PASS"
echo "new_head=$NEW_HEAD"
