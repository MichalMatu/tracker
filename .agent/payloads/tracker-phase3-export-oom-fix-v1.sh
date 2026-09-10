#!/usr/bin/env bash
set -euo pipefail
EXPECTED='53cccf4fa39525febf54e0a57f65796efbd51812'
git fetch --no-tags origin main >/dev/null
git checkout main >/dev/null 2>&1
git reset --hard origin/main >/dev/null
test "$(git rev-parse HEAD)" = "$EXPECTED"
test -z "$(git status --porcelain)"

python3 - <<'PY'
from pathlib import Path

p = Path('feature/settings/src/main/java/io/blueeye/feature/settings/FieldMvpExportAppend.kt')
s = p.read_text()
s = s.replace('package io.blueeye.feature.settings\n\n', 'package io.blueeye.feature.settings\n\nimport java.io.Writer\n\n', 1)
marker = '\nprivate fun Long?.jsonValue(): String = this?.toString() ?: "null"\n'
insert = r'''

internal fun Writer.writeWithFieldMvpDiagnostics(
    json: String,
    uiState: SettingsUiState,
) {
    val insertionPoint = json.lastIndexOf('}')
    if (insertionPoint <= 0) {
        write(json)
        return
    }

    // Build only the small diagnostics fragment. Never create a second copy of the full export.
    val decoratedEmptyObject = "{}".withFieldMvpDiagnostics(uiState)
    val diagnosticsFragment = decoratedEmptyObject.substring(1, decoratedEmptyObject.length - 1)
    var prefixEnd = insertionPoint
    while (prefixEnd > 0 && json[prefixEnd - 1].isWhitespace()) {
        prefixEnd -= 1
    }

    write(json, 0, prefixEnd)
    write(diagnosticsFragment)
    write(json, insertionPoint, json.length - insertionPoint)
}
'''
assert marker in s
s = s.replace(marker, insert + marker, 1)
p.write_text(s)

p = Path('feature/settings/src/main/java/io/blueeye/feature/settings/DatabaseExportActions.kt')
s = p.read_text()
old = '''    viewModel.exportDatabase { json ->\n        if (json == null) {\n            Toast.makeText(context, "Export failed", Toast.LENGTH_SHORT).show()\n        } else {\n            onExportReady(json.withFieldMvpDiagnostics(viewModel.uiState.value))\n        }\n    }\n}\n'''
new = '''    viewModel.exportDatabase { json ->\n        if (json == null) {\n            Toast.makeText(context, "Export failed", Toast.LENGTH_SHORT).show()\n        } else if (isClipboardPayloadSafe(json)) {\n            onExportReady(json.withFieldMvpDiagnostics(viewModel.uiState.value))\n        } else {\n            // Let the clipboard guard reject large exports without first duplicating the whole JSON.\n            onExportReady(json)\n        }\n    }\n}\n\ninternal fun prepareDatabaseExportForShare(\n    context: Context,\n    viewModel: SettingsViewModel,\n) {\n    Toast.makeText(context, "Preparing session export...", Toast.LENGTH_SHORT).show()\n    viewModel.exportDatabase { json ->\n        if (json == null) {\n            Toast.makeText(context, "Export failed", Toast.LENGTH_SHORT).show()\n        } else {\n            shareExport(\n                context = context,\n                json = json,\n                uiState = viewModel.uiState.value,\n            )\n        }\n    }\n}\n'''
assert old in s
s = s.replace(old, new, 1)
s = s.replace('''internal fun shareExport(\n    context: Context,\n    json: String,\n) {\n    runCatching {\n        val exportFile = writeSessionExportFile(context, json)\n''', '''internal fun shareExport(\n    context: Context,\n    json: String,\n    uiState: SettingsUiState,\n) {\n    runCatching {\n        val exportFile = writeSessionExportFile(context, json, uiState)\n''', 1)
s = s.replace('''private fun writeSessionExportFile(\n    context: Context,\n    json: String,\n): File {\n''', '''private fun writeSessionExportFile(\n    context: Context,\n    json: String,\n    uiState: SettingsUiState,\n): File {\n''', 1)
s = s.replace('''    temporary.bufferedWriter(Charsets.UTF_8).use { writer ->\n        writer.write(json)\n    }\n''', '''    temporary.bufferedWriter(Charsets.UTF_8).use { writer ->\n        writer.writeWithFieldMvpDiagnostics(json, uiState)\n    }\n''', 1)
p.write_text(s)

p = Path('feature/settings/src/main/java/io/blueeye/feature/settings/SettingsScreen.kt')
s = p.read_text()
old = '''            onShareExport = {\n                prepareDatabaseExport(context, viewModel) { json ->\n                    shareExport(context, json)\n                }\n            },\n'''
new = '''            onShareExport = {\n                prepareDatabaseExportForShare(context, viewModel)\n            },\n'''
assert old in s
s = s.replace(old, new, 1)
p.write_text(s)

p = Path('feature/settings/src/test/java/io/blueeye/feature/settings/FieldMvpExportAppendTest.kt')
s = p.read_text()
s = s.replace('import org.junit.Assert.assertEquals\n', 'import org.junit.Assert.assertEquals\nimport java.io.StringWriter\n', 1)
insert_before = '\n}\n'
test = r'''

    @Test
    fun `streamed diagnostics keep base export valid`() {
        val uiState = SettingsUiState(scannerDiagnostics = ScannerRuntimeDiagnostics())
        val base = """
            {
              "schemaVersion": 19,
              "sampleCount": 2,
              "signalSamples": [{"rssi":-60},{"rssi":-61}]
            }
        """.trimIndent()
        val writer = StringWriter()

        writer.writeWithFieldMvpDiagnostics(base, uiState)

        val root = Json.parseToJsonElement(writer.toString()).jsonObject
        assertEquals(19L, root.getValue("schemaVersion").jsonPrimitive.long)
        assertEquals(2L, root.getValue("sampleCount").jsonPrimitive.long)
        root.getValue("fieldMvpDiagnostics").jsonObject
    }
'''
idx = s.rfind(insert_before)
assert idx != -1
s = s[:idx] + test + s[idx:]
p.write_text(s)
PY

git diff --check
git diff -- feature/settings/src/main/java/io/blueeye/feature/settings/FieldMvpExportAppend.kt feature/settings/src/main/java/io/blueeye/feature/settings/DatabaseExportActions.kt feature/settings/src/main/java/io/blueeye/feature/settings/SettingsScreen.kt feature/settings/src/test/java/io/blueeye/feature/settings/FieldMvpExportAppendTest.kt
./gradlew :feature:settings:testDebugUnitTest --no-daemon

git add feature/settings/src/main/java/io/blueeye/feature/settings/FieldMvpExportAppend.kt \
        feature/settings/src/main/java/io/blueeye/feature/settings/DatabaseExportActions.kt \
        feature/settings/src/main/java/io/blueeye/feature/settings/SettingsScreen.kt \
        feature/settings/src/test/java/io/blueeye/feature/settings/FieldMvpExportAppendTest.kt
git commit -m 'Avoid duplicate large export allocation'
git push origin HEAD:main
printf 'committed_sha=%s\n' "$(git rev-parse HEAD)"
