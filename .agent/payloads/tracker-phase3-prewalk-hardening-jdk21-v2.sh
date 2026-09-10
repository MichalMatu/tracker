#!/usr/bin/env bash
set -euo pipefail

EXPECTED_MAIN="3a1ab7554bec9eca5fedff7d727665f5188af876"
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

git fetch origin main agent-control >/dev/null
ACTUAL_MAIN=$(git rev-parse origin/main)
HEAD_SHA=$(git rev-parse HEAD)
printf 'origin_main=%s\nhead=%s\n' "$ACTUAL_MAIN" "$HEAD_SHA"
[[ "$ACTUAL_MAIN" == "$EXPECTED_MAIN" ]]
[[ "$HEAD_SHA" == "$EXPECTED_MAIN" ]]

ACTUAL_FILES=$(git status --porcelain | sed -E 's/^.. //' | sort)
printf '%s\n' "--- dirty files ---" "$ACTUAL_FILES"
[[ "$ACTUAL_FILES" == "$(printf '%s\n' "$EXPECTED_FILES" | sort)" ]]

git diff --check

export JAVA_HOME="$(/usr/libexec/java_home -v 21)"
export PATH="$JAVA_HOME/bin:$PATH"
printf '%s\n' '--- java ---'
java -version

printf '%s\n' '--- quality gate JDK21 ---'
./gradlew qualityCheck
printf '%s\n' '--- assemble debug JDK21 ---'
./gradlew :app:assembleDebug

git diff --check

git add $EXPECTED_FILES
git diff --cached --check

git commit -m "Harden long field scan export"
COMMIT_SHA=$(git rev-parse HEAD)
printf 'commit_sha=%s\n' "$COMMIT_SHA"
git push origin HEAD:main

git fetch origin main >/dev/null
[[ "$(git rev-parse origin/main)" == "$COMMIT_SHA" ]]
[[ -z "$(git status --porcelain)" ]]
printf 'PHASE3_PREWALK_HARDENING_JDK21_PASS=%s\n' "$COMMIT_SHA"
