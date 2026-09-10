#!/usr/bin/env bash
set -euo pipefail

git fetch origin agent-control >/dev/null
git show origin/agent-control:.agent/payloads/tracker-phase3-room-refresh-e2e-v6.sh > /tmp/tracker-phase3-room-refresh-e2e-v7-inner.sh
python3 - <<'PY'
from pathlib import Path
p = Path('/tmp/tracker-phase3-room-refresh-e2e-v7-inner.sh')
s = p.read_text()
s = s.replace("OUT='/tmp/tracker-phase3-room-refresh-e2e-v6'", "OUT='/tmp/tracker-phase3-room-refresh-e2e-v7'", 1)
old = 'db_snapshot(){\n  local name="$1" dir="$OUT/$name"'
new = 'db_snapshot(){\n  local name="$1"\n  local dir="$OUT/$name"'
if old not in s:
    raise SystemExit('expected db_snapshot local declaration not found')
s = s.replace(old, new, 1)
p.write_text(s)
PY
chmod +x /tmp/tracker-phase3-room-refresh-e2e-v7-inner.sh
exec /tmp/tracker-phase3-room-refresh-e2e-v7-inner.sh
