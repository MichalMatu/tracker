#!/usr/bin/env bash
set -euo pipefail

EXPECTED_HEAD="3a1ab7554bec9eca5fedff7d727665f5188af876"
export JAVA_HOME="$(/usr/libexec/java_home -v 21)"
export PATH="$JAVA_HOME/bin:$PATH"

git fetch origin main agent-control >/dev/null
[[ "$(git rev-parse origin/main)" == "$EXPECTED_HEAD" ]]
git checkout main >/dev/null 2>&1 || git checkout -b main origin/main
git reset --hard origin/main >/dev/null
[[ -z "$(git status --porcelain)" ]]

git show origin/agent-control:.agent/payloads/tracker-phase3-prewalk-hardening-final-v6.sh > /tmp/prewalk-v6.sh
python3 - <<'PY'
from pathlib import Path
p = Path('/tmp/prewalk-v6.sh')
s = p.read_text()
marker = "printf '%s\\n' '--- focused detekt ---'"
if marker not in s:
    raise SystemExit('v6 detekt marker not found')
Path('/tmp/prewalk-v6-apply-only.sh').write_text(s.split(marker, 1)[0])
PY
bash /tmp/prewalk-v6-apply-only.sh

set +e
./gradlew :feature:settings:detekt --rerun-tasks
RC=$?
set -e
printf 'detekt_exit=%s\n' "$RC"
REPORT="feature/settings/build/reports/detekt/detekt.txt"
printf '%s\n' '--- exact detekt report ---'
if [[ -f "$REPORT" ]]; then
  cat "$REPORT"
else
  echo 'detekt report missing'
fi
printf '%s\n' '--- imports DatabaseExporter ---'
sed -n '1,40p' feature/settings/src/main/java/io/blueeye/feature/settings/DatabaseExporter.kt
printf '%s\n' '--- imports SettingsViewModel ---'
sed -n '1,45p' feature/settings/src/main/java/io/blueeye/feature/settings/SettingsViewModel.kt
exit 0
