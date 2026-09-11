#!/usr/bin/env bash
set -euo pipefail
TMP='/tmp/tracker-phase3-field-reacceptance-collect-20260911-v1-patched.sh'
git fetch --no-tags origin agent-control >/dev/null
git show origin/agent-control:.agent/payloads/tracker-phase3-field-reacceptance-collect-20260911-v1.sh > "$TMP"
python3 - "$TMP" <<'PY'
import sys
p=sys.argv[1]
lines=open(p).readlines()
patched=[]
for line in lines:
    if line.startswith('mapfile -t DEVS '):
        patched.append('DEVS=( $(adb devices | awk \'NR>1 && $2=="device" {print $1}\') )\n')
    elif line.startswith('PID_BEFORE="$(adb -s '):
        patched.append(line.rstrip('\n')[:-1] + ' || true)"\n')
    elif line.startswith('PID_AFTER_START="$(adb -s '):
        patched.append(line.rstrip('\n')[:-1] + ' || true)"\n')
    elif line.startswith('PID_AFTER_EXPORT="$(adb -s '):
        patched.append(line.rstrip('\n')[:-1] + ' || true)"\n')
    else:
        patched.append(line)
open(p,'w').writelines(patched)
PY
chmod 700 "$TMP"
exec "$TMP"
