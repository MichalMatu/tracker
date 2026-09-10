#!/usr/bin/env bash
set -euo pipefail
export JAVA_HOME="$(/usr/libexec/java_home -v 21)"
export PATH="$JAVA_HOME/bin:$PATH"

git fetch origin agent-control >/dev/null
# Reuse the exact deterministic v1 payload so the diagnosed diff is byte-for-byte the intended patch.
git show origin/agent-control:.agent/payloads/tracker-phase3-prewalk-hardening-v1.sh > /tmp/tracker-phase3-prewalk-hardening-v1.sh
chmod +x /tmp/tracker-phase3-prewalk-hardening-v1.sh
set +e
/tmp/tracker-phase3-prewalk-hardening-v1.sh
payload_exit=$?
set -e
printf 'original_payload_exit=%s\n' "$payload_exit"
printf '%s\n' '--- exact detekt report ---'
if [[ -f feature/settings/build/reports/detekt/detekt.txt ]]; then
  cat feature/settings/build/reports/detekt/detekt.txt
else
  echo 'ERROR: detekt report missing' >&2
  exit 30
fi
printf '%s\n' '--- dirty files after diagnostic ---'
git status --short
# Expected: original payload fails at quality gate and leaves the intended patch dirty.
[[ "$payload_exit" -ne 0 ]]
