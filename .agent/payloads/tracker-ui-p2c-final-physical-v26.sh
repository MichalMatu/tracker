#!/bin/bash
set -euo pipefail

serial=$(adb devices | awk 'NR>1 && $2=="device" {print $1; exit}')
test -n "$serial"
model=$(adb -s "$serial" shell getprop ro.product.model | tr -d '\r')
test "$model" = "SM-S906B"

private="$HOME/agent-private/tracker/ui-p2c-final-physical-20260912-v26"
mkdir -p "$private"

copy_db() {
  local dest=$1
  adb -s "$serial" shell am force-stop io.blueeye || true
  sleep 1
  adb -s "$serial" shell run-as io.blueeye ls databases > "$private/databases-list.txt" 2>"$private/databases-list.err" || true
  if adb -s "$serial" exec-out run-as io.blueeye cat databases/tracker_database > "$dest" 2>"$private/db-copy.err"; then
    test -s "$dest"
    return 0
  fi
  return 1
}

# Recover privacy-safe DB aggregates before relaunch if run-as is available.
pre_db="$private/tracker_database-pre"
if copy_db "$pre_db"; then
  pre_db_ok=1
else
  pre_db_ok=0
fi

# Launch exact installed app, navigate to Radar without clearing data, and ensure scanning is running.
adb -s "$serial" shell am start -W -n io.blueeye/.MainActivity > "$private/launch.txt"
sleep 2
ui="$private/ui.xml"
dump_ui() {
  adb -s "$serial" shell uiautomator dump /sdcard/blueeye-p2c.xml >/dev/null
  adb -s "$serial" exec-out cat /sdcard/blueeye-p2c.xml > "$ui"
}
tap_node() {
  local attr=$1 value=$2
  dump_ui
  local xy
  xy=$(python3 tools/ui-smoke/ui_node.py "$ui" "$attr" "$value" 2>/dev/null || true)
  test -n "$xy"
  local x y
  read -r x y <<<"$xy"
  adb -s "$serial" shell input tap "$x" "$y"
  sleep 1
}

dump_ui
if python3 tools/ui-smoke/ui_node.py "$ui" content-desc "Menu" >/dev/null 2>&1; then
  tap_node content-desc "Menu"
  dump_ui
  if python3 tools/ui-smoke/ui_node.py "$ui" text "Radar" >/dev/null 2>&1; then
    tap_node text "Radar"
  fi
fi

dump_ui
if python3 tools/ui-smoke/ui_node.py "$ui" content-desc "Scan" >/dev/null 2>&1; then
  tap_node content-desc "Scan"
fi
sleep 15

# Comparable P0/P1/P2A frame sample: reset gfxinfo, 8 up + 8 down swipes.
adb -s "$serial" logcat -c || true
adb -s "$serial" shell dumpsys gfxinfo io.blueeye reset >/dev/null || true
for _ in $(seq 1 8); do
  adb -s "$serial" shell input swipe 540 1700 540 550 350
  sleep 0.35
done
for _ in $(seq 1 8); do
  adb -s "$serial" shell input swipe 540 550 540 1700 350
  sleep 0.35
done
sleep 1
adb -s "$serial" shell dumpsys gfxinfo io.blueeye > "$private/gfxinfo-16swipe.txt" || true
adb -s "$serial" shell dumpsys meminfo io.blueeye > "$private/meminfo.txt" || true
adb -s "$serial" logcat -d -v epoch > "$private/logcat.txt" || true

# Final consistent DB snapshot after the benchmark, then relaunch app for the user.
post_db="$private/tracker_database-post"
if copy_db "$post_db"; then
  post_db_ok=1
else
  post_db_ok=0
fi
adb -s "$serial" shell am start -W -n io.blueeye/.MainActivity >/dev/null || true

python3 - "$private" "$pre_db_ok" "$post_db_ok" <<'PY'
from pathlib import Path
import re, sqlite3, sys

root=Path(sys.argv[1])
pre_ok=sys.argv[2]=='1'
post_ok=sys.argv[3]=='1'

gfx=(root/'gfxinfo-16swipe.txt').read_text(errors='replace') if (root/'gfxinfo-16swipe.txt').exists() else ''
patterns={
 'TOTAL_FRAMES':r'Total frames rendered:\s*([0-9]+)',
 'JANKY_FRAMES':r'Janky frames:\s*([0-9]+)\s*\(([^)]+)\)',
 'P50_MS':r'50th percentile:\s*([0-9]+)ms',
 'P90_MS':r'90th percentile:\s*([0-9]+)ms',
 'P95_MS':r'95th percentile:\s*([0-9]+)ms',
 'P99_MS':r'99th percentile:\s*([0-9]+)ms',
}
for key,pat in patterns.items():
    m=re.search(pat,gfx)
    print(f"{key}=" + (('/'.join(m.groups())) if m and len(m.groups())>1 else (m.group(1) if m else 'UNAVAILABLE')))

log=(root/'logcat.txt').read_text(errors='replace') if (root/'logcat.txt').exists() else ''
for kw in ['FATAL EXCEPTION','OutOfMemoryError','SQLiteException','ANR in io.blueeye']:
    k='LOG_'+re.sub('[^A-Z0-9]+','_',kw.upper()).strip('_')+'_COUNT'
    print(f"{k}={log.count(kw)}")

mem=(root/'meminfo.txt').read_text(errors='replace') if (root/'meminfo.txt').exists() else ''
m=re.search(r'TOTAL PSS:\s*([0-9]+)',mem)
print(f"TOTAL_PSS_KB={m.group(1) if m else 'UNAVAILABLE'}")
print(f"PRE_DB_SNAPSHOT={'PASS' if pre_ok else 'UNAVAILABLE'}")
print(f"POST_DB_SNAPSHOT={'PASS' if post_ok else 'UNAVAILABLE'}")

def summarize(label,path):
    if not path.exists() or path.stat().st_size < 16:
        return
    try:
        con=sqlite3.connect(f'file:{path}?mode=ro',uri=True)
        integrity=con.execute('PRAGMA integrity_check').fetchone()[0]
        print(f"{label}_DB_INTEGRITY={integrity}")
        tables={r[0] for r in con.execute("select name from sqlite_master where type='table'")}
        for t in ['devices','signal_samples','follow_me_observations']:
            if t in tables:
                count=con.execute(f'SELECT COUNT(*) FROM "{t}"').fetchone()[0]
                print(f"{label}_{t.upper()}_COUNT={count}")
        if 'devices' in tables:
            cols={r[1] for r in con.execute('PRAGMA table_info("devices")')}
            if 'lastSeenAt' in cols:
                mx=con.execute('SELECT MAX(lastSeenAt) FROM devices').fetchone()[0]
                if mx is not None:
                    recent=con.execute('SELECT COUNT(*) FROM devices WHERE lastSeenAt > ?', (mx-180000,)).fetchone()[0]
                    print(f"{label}_RECENT_180S_AT_DATASET_END={recent}")
        con.close()
    except Exception as e:
        print(f"{label}_DB_READ_ERROR={type(e).__name__}")

summarize('PRE',root/'tracker_database-pre')
summarize('POST',root/'tracker_database-post')
print(f"PRIVATE_CAPTURE_DIR={root}")
PY
