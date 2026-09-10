#!/usr/bin/env bash
set -euo pipefail

EXPECTED_HEAD="3a1ab7554bec9eca5fedff7d727665f5188af876"
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
java -version

git fetch origin main agent-control >/dev/null
[[ "$(git rev-parse origin/main)" == "$EXPECTED_HEAD" ]]
git checkout main >/dev/null 2>&1 || git checkout -b main origin/main
git reset --hard origin/main >/dev/null
[[ -z "$(git status --porcelain)" ]]

# Recreate the reviewed v8 diff without running its gates or commit.
git show origin/agent-control:.agent/payloads/tracker-phase3-prewalk-hardening-final-v8.sh > /tmp/prewalk-v8.sh
sed '/printf .*focused detekt/,$d' /tmp/prewalk-v8.sh > /tmp/prewalk-v8-apply-only.sh
bash /tmp/prewalk-v8-apply-only.sh

# Mechanical test update after moving writeExport to DatabaseExportStreamWriter.
python3 - <<'PY'
from pathlib import Path
p = Path("feature/settings/src/test/java/io/blueeye/feature/settings/DatabaseExportJsonMapperTest.kt")
s = p.read_text()
old = "DatabaseExportJsonMapper.writeExport(data, writer, json)"
new = "DatabaseExportStreamWriter.writeExport(data, writer, json)"
old_count = s.count(old)
new_count = s.count(new)
if old_count == 1 and new_count == 0:
    s = s.replace(old, new, 1)
elif old_count == 0 and new_count == 1:
    pass
else:
    raise SystemExit(f"unexpected writeExport call state old={old_count} new={new_count}")
p.write_text(s)
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
