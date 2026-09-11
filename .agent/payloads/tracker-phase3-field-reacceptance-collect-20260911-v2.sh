#!/usr/bin/env bash
set -euo pipefail
TMP='/tmp/tracker-phase3-field-reacceptance-collect-20260911-v1.sh'
git fetch --no-tags origin agent-control >/dev/null
git show origin/agent-control:.agent/payloads/tracker-phase3-field-reacceptance-collect-20260911-v1.sh > "$TMP"
python3 - "$TMP" <<'PY'
import sys
p=sys.argv[1]
lines=open(p).readlines()
for i,line in enumerate(lines):
    if line.startswith('mapfile -t DEVS '):
        lines[i] = 'DEVS=( $(adb devices | awk \'NR>1 && $2=="device" {print $1}\') )\n'
        break
else:
    raise SystemExit('device enumeration line not found')
open(p,'w').writelines(lines)
PY
chmod 700 "$TMP"
exec "$TMP"
