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

git fetch origin main >/dev/null
[[ "$(git rev-parse origin/main)" == "$EXPECTED_MAIN" ]]
[[ "$(git rev-parse HEAD)" == "$EXPECTED_MAIN" ]]
ACTUAL_FILES=$(git status --porcelain | sed -E 's/^.. //' | sort)
[[ "$ACTUAL_FILES" == "$(printf '%s\n' "$EXPECTED_FILES" | sort)" ]]

export JAVA_HOME="$(/usr/libexec/java_home -v 21)"
export PATH="$JAVA_HOME/bin:$PATH"
java -version

set +e
./gradlew :feature:settings:detekt --rerun-tasks --console=plain > /tmp/tracker-detekt-v4.log 2>&1
RC=$?
set -e
cat /tmp/tracker-detekt-v4.log
printf 'detekt_exit=%s\n' "$RC"

for report in feature/settings/build/reports/detekt/detekt.txt feature/settings/build/reports/detekt/detekt.xml feature/settings/build/reports/detekt/detekt.html; do
  if [[ -f "$report" ]]; then
    echo "--- $report ---"
    if [[ "$report" == *.html ]]; then
      grep -E "(Debt|Issue|DatabaseExporter|DatabaseExportActions|SettingsViewModel|FieldMvp)" "$report" | head -n 120 || true
    else
      cat "$report"
    fi
  fi
done

exit 0
