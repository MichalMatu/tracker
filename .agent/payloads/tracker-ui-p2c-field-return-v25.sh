#!/bin/bash
set -euo pipefail

serial=$(adb devices | awk 'NR>1 && $2=="device" {print $1; exit}')
test -n "$serial"
model=$(adb -s "$serial" shell getprop ro.product.model | tr -d '\r')
test "$model" = "SM-S906B"

private="$HOME/agent-private/tracker/ui-p2c-field-20260912-v25"
mkdir -p "$private"

# Build exact checked-out commit so the installed APK can be byte-verified.
export JAVA_HOME="$(/usr/libexec/java_home -v 21)"
./gradlew :app:assembleDebug --console=plain >/dev/null
local_apk="app/build/outputs/apk/debug/app-debug.apk"
test -f "$local_apk"
local_sha=$(shasum -a 256 "$local_apk" | awk '{print $1}')
package_path=$(adb -s "$serial" shell pm path io.blueeye | tr -d '\r' | sed -n 's/^package://p' | head -n1)
test -n "$package_path"
installed_sha=$(adb -s "$serial" shell sha256sum "$package_path" | tr -d '\r' | awk '{print $1}')

adb -s "$serial" shell dumpsys gfxinfo io.blueeye > "$private/gfxinfo.txt" || true
adb -s "$serial" shell dumpsys gfxinfo io.blueeye framestats > "$private/framestats.txt" || true
adb -s "$serial" shell dumpsys meminfo io.blueeye > "$private/meminfo.txt" || true
adb -s "$serial" shell dumpsys activity exit-info io.blueeye > "$private/exit-info.txt" || true
adb -s "$serial" logcat -b crash -d -v epoch > "$private/logcat-crash.txt" || true
adb -s "$serial" logcat -d -v epoch > "$private/logcat-main.txt" || true

# Close the app only after rendering/log captures so Room is cleanly checkpointed for the private DB snapshot.
adb -s "$serial" shell am force-stop io.blueeye || true
sleep 1
if adb -s "$serial" shell run-as io.blueeye sh -c 'cd /data/data/io.blueeye && tar cf - databases' > "$private/databases.tar" 2>"$private/db-copy.err"; then
  mkdir -p "$private/db"
  tar xf "$private/databases.tar" -C "$private/db" || true
  db_snapshot=1
else
  db_snapshot=0
fi

python3 - "$private" "$local_sha" "$installed_sha" "$model" "$db_snapshot" <<'PY'
from pathlib import Path
import re, sqlite3, sys, json, datetime

root = Path(sys.argv[1])
local_sha, installed_sha, model = sys.argv[2:5]
db_snapshot = sys.argv[5] == '1'

print(f"DEVICE_MODEL={model}")
print(f"APK_SHA_MATCH={'PASS' if local_sha == installed_sha else 'FAIL'}")
print(f"LOCAL_APK_SHA256={local_sha}")
print(f"INSTALLED_APK_SHA256={installed_sha}")
print(f"PRIVATE_CAPTURE_DIR={root}")

# Parse standard gfxinfo summary without dumping private UI content.
gfx = (root / 'gfxinfo.txt').read_text(errors='replace') if (root / 'gfxinfo.txt').exists() else ''
patterns = {
    'TOTAL_FRAMES': r'Total frames rendered:\s*([0-9]+)',
    'JANKY_FRAMES': r'Janky frames:\s*([0-9]+)\s*\(([^)]+)\)',
    'P50_MS': r'50th percentile:\s*([0-9]+)ms',
    'P90_MS': r'90th percentile:\s*([0-9]+)ms',
    'P95_MS': r'95th percentile:\s*([0-9]+)ms',
    'P99_MS': r'99th percentile:\s*([0-9]+)ms',
}
for key, pat in patterns.items():
    m = re.search(pat, gfx)
    if m:
        print(f"{key}=" + ('/'.join(m.groups()) if len(m.groups()) > 1 else m.group(1)))
    else:
        print(f"{key}=UNAVAILABLE")

# Crash/ANR summary only; raw logs remain private.
crash = (root / 'logcat-crash.txt').read_text(errors='replace') if (root / 'logcat-crash.txt').exists() else ''
main = (root / 'logcat-main.txt').read_text(errors='replace') if (root / 'logcat-main.txt').exists() else ''
exitinfo = (root / 'exit-info.txt').read_text(errors='replace') if (root / 'exit-info.txt').exists() else ''
keywords = ['FATAL EXCEPTION', 'OutOfMemoryError', 'SQLiteException', 'ANR in io.blueeye']
for kw in keywords:
    count = crash.count(kw) + main.count(kw)
    print(f"LOG_{re.sub('[^A-Z0-9]+','_',kw.upper()).strip('_')}_COUNT={count}")
# Exit-info reason lines, but don't print potentially sensitive descriptions.
interesting_exit = 0
for line in exitinfo.splitlines():
    low = line.lower()
    if any(x in low for x in ['reason=crash', 'reason=anr', 'reason=low_memory', 'reason=excessive_resource_usage']):
        interesting_exit += 1
print(f"EXIT_INFO_CRASH_ANR_RESOURCE_COUNT={interesting_exit}")

# Memory summary.
mem = (root / 'meminfo.txt').read_text(errors='replace') if (root / 'meminfo.txt').exists() else ''
m = re.search(r'TOTAL PSS:\s*([0-9]+)', mem)
print(f"TOTAL_PSS_KB={m.group(1) if m else 'UNAVAILABLE'}")

print(f"DB_SNAPSHOT={'PASS' if db_snapshot else 'UNAVAILABLE'}")
if not db_snapshot:
    sys.exit(0)

# Find SQLite DBs and print only counts and time ranges, never rows/identifiers/locations.
dbfiles=[]
for p in (root/'db').rglob('*'):
    if not p.is_file() or p.name.endswith(('-wal','-shm','-journal')):
        continue
    try:
        if p.read_bytes()[:16] == b'SQLite format 3\x00':
            dbfiles.append(p)
    except Exception:
        pass
print(f"SQLITE_DB_COUNT={len(dbfiles)}")

def fmt_ts(v):
    if v is None: return 'NULL'
    try:
        x=float(v)
    except Exception:
        return 'NONNUMERIC'
    if x > 1e12: sec=x/1000.0
    elif x > 1e9: sec=x
    else: return str(v)
    try:
        return datetime.datetime.fromtimestamp(sec, tz=datetime.timezone.utc).isoformat()
    except Exception:
        return str(v)

for dbi, db in enumerate(dbfiles, 1):
    print(f"DB{dbi}_NAME={db.name}")
    try:
        con=sqlite3.connect(f'file:{db}?mode=ro', uri=True)
        tables=[r[0] for r in con.execute("select name from sqlite_master where type='table' and name not like 'sqlite_%'")]
        for t in tables:
            if t not in {'devices','signal_samples','follow_me_observations'} and not any(k in t.lower() for k in ['scan','session']):
                continue
            q='"'+t.replace('"','""')+'"'
            try:
                count=con.execute(f'SELECT COUNT(*) FROM {q}').fetchone()[0]
                print(f"TABLE_{t}_COUNT={count}")
            except Exception:
                continue
            try:
                cols=[r[1] for r in con.execute(f'PRAGMA table_info({q})')]
            except Exception:
                cols=[]
            for c in cols:
                lc=c.lower()
                if not any(k in lc for k in ['timestamp','seenat','observedat','createdat','updatedat','recordedat','time']):
                    continue
                qc='"'+c.replace('"','""')+'"'
                try:
                    mn,mx=con.execute(f'SELECT MIN({qc}), MAX({qc}) FROM {q}').fetchone()
                except Exception:
                    continue
                if mn is not None or mx is not None:
                    print(f"TABLE_{t}_{c}_MIN={fmt_ts(mn)}")
                    print(f"TABLE_{t}_{c}_MAX={fmt_ts(mx)}")
        con.close()
    except Exception as e:
        print(f"DB{dbi}_READ_ERROR={type(e).__name__}")
PY
