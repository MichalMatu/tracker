#!/usr/bin/env bash
set -euo pipefail

EXPECTED_HEAD="3a1ab7554bec9eca5fedff7d727665f5188af876"
NEW_STREAM_FILE="feature/settings/src/main/java/io/blueeye/feature/settings/DatabaseExportStreamWriter.kt"
EXPECTED_FILES=$(cat <<'EOF'
core/data/src/main/java/io/blueeye/service/ScannerService.kt
feature/settings/src/main/java/io/blueeye/feature/settings/DatabaseExportActions.kt
feature/settings/src/main/java/io/blueeye/feature/settings/DatabaseExporter.kt
feature/settings/src/main/java/io/blueeye/feature/settings/DatabaseExportStreamWriter.kt
feature/settings/src/main/java/io/blueeye/feature/settings/FieldMvpExportAppend.kt
feature/settings/src/main/java/io/blueeye/feature/settings/SettingsViewModel.kt
feature/settings/src/test/java/io/blueeye/feature/settings/DatabaseExportJsonMapperTest.kt
feature/settings/src/test/java/io/blueeye/feature/settings/FieldMvpExportAppendTest.kt
EOF
)

export JAVA_HOME="$(/usr/libexec/java_home -v 21)"
export PATH="$JAVA_HOME/bin:$PATH"
printf '%s\n' '--- java ---'
java -version

git fetch origin main agent-control >/dev/null
[[ "$(git rev-parse origin/main)" == "$EXPECTED_HEAD" ]]
git checkout main >/dev/null 2>&1 || git checkout -b main origin/main
git reset --hard origin/main >/dev/null
[[ -z "$(git status --porcelain)" ]]

# Recreate the reviewed v6 diff without running its gates/commit.
git show origin/agent-control:.agent/payloads/tracker-phase3-prewalk-hardening-final-v6.sh > /tmp/prewalk-v6.sh
sed '/printf .*focused detekt/,$d' /tmp/prewalk-v6.sh > /tmp/prewalk-v6-apply-only.sh
bash /tmp/prewalk-v6-apply-only.sh

python3 - <<'PY'
from pathlib import Path

p = Path("feature/settings/src/main/java/io/blueeye/feature/settings/DatabaseExporter.kt")
s = p.read_text()

old_call = "DatabaseExportJsonMapper.writeExport("
new_call = "DatabaseExportStreamWriter.writeExport("
if old_call not in s:
    raise SystemExit("stream writer call marker not found")
s = s.replace(old_call, new_call, 1)

start = s.index("    fun writeExport(\n")
end = s.index("    private fun buildExportMetadata(", start)
s = s[:start] + s[end:]

helper_start = s.index("private fun <T> writeJsonArray(\n")
helper_end = s.index("private val SessionReviewDeviceQueueDecision.kind", helper_start)
s = s[:helper_start] + s[helper_end:]

for old, new in [
    ("    private fun buildExportMetadata(", "    internal fun buildExportMetadata("),
    ("    private fun mapDevice(", "    internal fun mapDevice("),
    ("    private fun mapSample(", "    internal fun mapSample("),
]:
    if old not in s:
        raise SystemExit(f"visibility marker not found: {old.strip()}")
    s = s.replace(old, new, 1)

s = s.replace("import java.io.File\nimport java.io.Writer\n", "import java.io.File\n", 1)
p.write_text(s)

stream = Path("feature/settings/src/main/java/io/blueeye/feature/settings/DatabaseExportStreamWriter.kt")
stream.write_text('''package io.blueeye.feature.settings

import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import java.io.Writer

internal object DatabaseExportStreamWriter {
    fun writeExport(
        data: DatabaseExportData,
        writer: Writer,
        json: Json,
        trailingRootFragmentProvider: () -> String = { "" },
    ) {
        val metadata = json.encodeToString(DatabaseExportJsonMapper.buildExportMetadata(data))
        val closingBrace = metadata.lastIndexOf('}')
        check(closingBrace >= 0) { "Export metadata is not a JSON object" }

        var prefixEnd = closingBrace
        while (prefixEnd > 0 && metadata[prefixEnd - 1].isWhitespace()) {
            prefixEnd -= 1
        }

        writer.write(metadata, 0, prefixEnd)
        writer.write(",\\n  \\\"devices\\\": [")
        writeJsonArray(writer, data.devices, json, DatabaseExportJsonMapper::mapDevice)
        writer.write("\\n  ],\\n  \\\"signalSamples\\\": [")
        writeJsonArray(writer, data.samples, json, DatabaseExportJsonMapper::mapSample)
        writer.write("\\n  ]")

        val trailingRootFragment = trailingRootFragmentProvider()
        if (trailingRootFragment.isNotBlank()) {
            writer.write(trailingRootFragment)
        }
        writer.write("\\n}")
    }

    private fun <T> writeJsonArray(
        writer: Writer,
        values: List<T>,
        json: Json,
        mapper: (T) -> JsonObject,
    ) {
        values.forEachIndexed { index, value ->
            if (index > 0) writer.write(",")
            writer.write("\\n    ")
            writer.write(json.encodeToString(mapper(value)))
        }
    }
}
''')
PY

git diff --check
ACTUAL_FILES=$(git status --porcelain | sed -E 's/^.. //' | sort)
[[ "$ACTUAL_FILES" == "$(printf '%s\n' "$EXPECTED_FILES" | sort)" ]]
printf '%s\n' '--- final diff stat ---'
git diff --stat

printf '%s\n' '--- focused detekt ---'
./gradlew :feature:settings:detekt
printf '%s\n' '--- focused settings tests ---'
./gradlew :feature:settings:testDebugUnitTest
printf '%s\n' '--- core data tests ---'
./gradlew :core:data:testDebugUnitTest
printf '%s\n' '--- full quality gate ---'
./gradlew qualityCheck
printf '%s\n' '--- assemble debug ---'
./gradlew :app:assembleDebug

git diff --check
git add $EXPECTED_FILES
git diff --cached --check

git commit -m "Harden long field scan export"
COMMIT_SHA="$(git rev-parse HEAD)"
printf 'commit_sha=%s\n' "$COMMIT_SHA"
git push origin HEAD:main

git fetch origin main >/dev/null
[[ "$(git rev-parse origin/main)" == "$COMMIT_SHA" ]]
[[ -z "$(git status --porcelain)" ]]
printf 'PHASE3_PREWALK_HARDENING_PASS=%s\n' "$COMMIT_SHA"
