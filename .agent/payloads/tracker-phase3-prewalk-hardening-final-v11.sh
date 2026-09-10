#!/usr/bin/env bash
set -euo pipefail

# Reuse the reviewed v8 payload exactly, but patch its test call in-script before executing it.
git fetch origin agent-control >/dev/null
git show origin/agent-control:.agent/payloads/tracker-phase3-prewalk-hardening-final-v8.sh > /tmp/tracker-phase3-prewalk-hardening-final-v11-inner.sh

python3 - <<'PY'
from pathlib import Path
p = Path('/tmp/tracker-phase3-prewalk-hardening-final-v11-inner.sh')
s = p.read_text()
needle = """git diff --check
ACTUAL_FILES=$(git status --porcelain | sed -E 's/^.. //' | sort)
"""
if s.count(needle) != 1:
    raise SystemExit(f'unexpected v8 insertion marker count={s.count(needle)}')
insert = """# Mechanical test update after moving writeExport to DatabaseExportStreamWriter.
python3 - <<'PYFIX'
from pathlib import Path
p = Path("feature/settings/src/test/java/io/blueeye/feature/settings/DatabaseExportJsonMapperTest.kt")
s = p.read_text()
old = "DatabaseExportJsonMapper.writeExport(data, writer, json)"
new = "DatabaseExportStreamWriter.writeExport(data, writer, json)"
if s.count(old) != 1:
    raise SystemExit(f"expected exactly one old writeExport call in test, found {s.count(old)}")
s = s.replace(old, new, 1)
p.write_text(s)
PYFIX

git diff --check
ACTUAL_FILES=$(git status --porcelain | sed -E 's/^.. //' | sort)
"""
s = s.replace(needle, insert, 1)
p.write_text(s)
PY

chmod +x /tmp/tracker-phase3-prewalk-hardening-final-v11-inner.sh
exec /tmp/tracker-phase3-prewalk-hardening-final-v11-inner.sh
