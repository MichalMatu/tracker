#!/usr/bin/env bash
set -euo pipefail

EXPECTED_HEAD="3a1ab7554bec9eca5fedff7d727665f5188af876"
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

export JAVA_HOME="$(/usr/libexec/java_home -v 21)"
export PATH="$JAVA_HOME/bin:$PATH"
printf '%s\n' '--- java ---'
java -version

git fetch origin main agent-control >/dev/null
[[ "$(git rev-parse origin/main)" == "$EXPECTED_HEAD" ]]
git checkout main >/dev/null 2>&1 || git checkout -b main origin/main
git reset --hard origin/main >/dev/null
[[ -z "$(git status --porcelain)" ]]

# Reuse the already-reviewed deterministic v1 patch application, but stop before its tests/commit.
git show origin/agent-control:.agent/payloads/tracker-phase3-prewalk-hardening-v1.sh > /tmp/prewalk-v1.sh
sed '/echo "--- focused settings tests ---"/,$d' /tmp/prewalk-v1.sh > /tmp/prewalk-apply-only.sh
bash /tmp/prewalk-apply-only.sh

ACTUAL_FILES=$(git status --porcelain | sed -E 's/^.. //' | sort)
[[ "$ACTUAL_FILES" == "$(printf '%s\n' "$EXPECTED_FILES" | sort)" ]]

# Fix only the three Detekt findings from v5: two import-order findings and one TooManyFunctions.
python3 - <<'PY'
from pathlib import Path

p = Path("feature/settings/src/main/java/io/blueeye/feature/settings/DatabaseExporter.kt")
s = p.read_text()
old = """import io.blueeye.core.model.SignalSample
import java.io.File
import java.io.Writer
import kotlinx.coroutines.Dispatchers
"""
new = """import io.blueeye.core.model.SignalSample
import kotlinx.coroutines.Dispatchers
"""
if old not in s:
    raise SystemExit("DatabaseExporter import-order marker not found")
s = s.replace(old, new, 1)
old = """import kotlinx.serialization.json.put
import javax.inject.Inject
"""
new = """import kotlinx.serialization.json.put
import java.io.File
import java.io.Writer
import javax.inject.Inject
"""
if old not in s:
    raise SystemExit("DatabaseExporter java import insertion marker not found")
s = s.replace(old, new, 1)

helper = '''    private fun <T> writeJsonArray(
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

'''
if helper not in s:
    raise SystemExit("writeJsonArray member helper not found")
s = s.replace(helper, "", 1)
anchor = """    private const val SCHEMA_VERSION = 19
}

private val SessionReviewDeviceQueueDecision.kind: String
"""
top_level_helper = '''    private const val SCHEMA_VERSION = 19
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

private val SessionReviewDeviceQueueDecision.kind: String
'''
if anchor not in s:
    raise SystemExit("DatabaseExporter top-level helper anchor not found")
s = s.replace(anchor, top_level_helper, 1)
p.write_text(s)

p = Path("feature/settings/src/main/java/io/blueeye/feature/settings/SettingsViewModel.kt")
s = p.read_text()
old = """import io.blueeye.core.model.IdentityCarryoverVerdict
import java.io.File
import kotlinx.coroutines.flow.MutableStateFlow
"""
new = """import io.blueeye.core.model.IdentityCarryoverVerdict
import kotlinx.coroutines.flow.MutableStateFlow
"""
if old not in s:
    raise SystemExit("SettingsViewModel import-order marker not found")
s = s.replace(old, new, 1)
old = """import kotlinx.coroutines.launch
import javax.inject.Inject
"""
new = """import kotlinx.coroutines.launch
import java.io.File
import javax.inject.Inject
"""
if old not in s:
    raise SystemExit("SettingsViewModel java import insertion marker not found")
s = s.replace(old, new, 1)
p.write_text(s)
PY

git diff --check
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
