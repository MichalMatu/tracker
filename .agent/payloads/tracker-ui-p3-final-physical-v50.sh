#!/bin/bash
set -euo pipefail

serial=$(adb devices | awk 'NR>1 && $2=="device" {print $1; exit}')
test -n "$serial"
model=$(adb -s "$serial" shell getprop ro.product.model | tr -d '\r')
test "$model" = "SM-S906B"

private="$HOME/agent-private/tracker/ui-p3-final-physical-20260912-v50"
mkdir -p "$private"
ui="$private/current.xml"

raw_dump_ui() {
  adb -s "$serial" shell uiautomator dump /sdcard/blueeye-p3.xml >/dev/null
  adb -s "$serial" exec-out cat /sdcard/blueeye-p3.xml > "$ui"
}

coords() {
  local attr=$1 value=$2
  raw_dump_ui
  python3 tools/ui-smoke/ui_node.py "$ui" "$attr" "$value" 2>/dev/null
}

tap_target() {
  local attr=$1 value=$2
  local xy x y
  xy=$(coords "$attr" "$value" || true)
  test -n "$xy"
  read -r x y <<<"$xy"
  adb -s "$serial" shell input tap "$x" "$y"
  sleep 1
}

shot() {
  local name=$1
  raw_dump_ui
  cp "$ui" "$private/$name.xml"
  adb -s "$serial" exec-out screencap -p > "$private/$name.png"
}

scroll_until_text() {
  local text=$1
  local i
  for i in $(seq 0 12); do
    raw_dump_ui
    if grep -Fq "text=\"$text\"" "$ui"; then
      echo "$i"
      return 0
    fi
    adb -s "$serial" shell input swipe 540 1700 540 520 350
    sleep 0.5
  done
  echo -1
}

copy_db() {
  local dest=$1
  if adb -s "$serial" exec-out run-as io.blueeye cat databases/tracker_database > "$dest" 2>"$private/db-copy.err"; then
    test -s "$dest"
    return 0
  fi
  return 1
}

adb -s "$serial" shell am force-stop io.blueeye || true
sleep 1
copy_db "$private/tracker_database-pre" && pre_db=1 || pre_db=0
adb -s "$serial" logcat -c || true
adb -s "$serial" shell dumpsys gfxinfo io.blueeye reset >/dev/null || true
adb -s "$serial" shell am start -W -n io.blueeye/.MainActivity > "$private/launch.txt"
sleep 2

raw_dump_ui
if python3 tools/ui-smoke/ui_node.py "$ui" content-desc "Menu" >/dev/null 2>&1; then
  tap_target content-desc "Menu"
  raw_dump_ui
  if python3 tools/ui-smoke/ui_node.py "$ui" text "Radar" >/dev/null 2>&1; then
    tap_target text "Radar"
  fi
fi
shot radar-before-scan

# A force-stop above guarantees a fresh runtime scanner state; start a real BLE scan.
tap_target content-desc "Scan"
sleep 300
shot radar-after-5min-scan

# Open the first currently visible real device details using semantics, not coordinates.
raw_dump_ui
if ! python3 tools/ui-smoke/ui_node.py "$ui" content-desc "Details" >/dev/null 2>&1; then
  for _ in $(seq 1 6); do
    adb -s "$serial" shell input swipe 540 1700 540 650 300
    sleep 0.5
    raw_dump_ui
    if python3 tools/ui-smoke/ui_node.py "$ui" content-desc "Details" >/dev/null 2>&1; then
      break
    fi
  done
fi
tap_target content-desc "Details"
sleep 2
shot details-top

# Record decision-first semantics without printing device-identifying text.
python3 - "$private/details-top.xml" <<'PY'
from pathlib import Path
import re, sys
s=Path(sys.argv[1]).read_text(errors='replace')
checks={
 'DETAILS_TITLE_PRESENT':'text="Details"' in s,
 'DETAILS_RSSI_PRESENT':bool(re.search(r'text="-?\d+ dBm"',s)),
 'DETAILS_LAST_SEEN_PRESENT':'Last seen ' in s,
 'RAW_DATA_ACTION_PRESENT':'content-desc="Raw Data"' in s,
 'REFRESH_ACTION_PRESENT':'content-desc="Refresh focused scan"' in s,
}
for k,v in checks.items(): print(f'{k}={"PASS" if v else "FAIL"}')
PY

identity_step=$(scroll_until_text "Identity")
echo IDENTITY_SCROLL_STEP=$identity_step
shot details-identity

actions_step=$(scroll_until_text "Actions / Review")
echo ACTIONS_SCROLL_STEP=$actions_step
shot details-actions

history_step=$(scroll_until_text "History")
echo HISTORY_SCROLL_STEP=$history_step
shot details-history

technical_step=$(scroll_until_text "Technical details")
echo TECHNICAL_SCROLL_STEP=$technical_step
shot details-technical

all_evidence_step=$(scroll_until_text "All evidence")
echo ALL_EVIDENCE_SCROLL_STEP=$all_evidence_step
if test "$all_evidence_step" != "-1"; then shot details-all-evidence; fi

# Stress scrolling while live data can still update; capture rendering/memory/log failures.
adb -s "$serial" shell dumpsys gfxinfo io.blueeye reset >/dev/null || true
for _ in $(seq 1 8); do adb -s "$serial" shell input swipe 540 1700 540 520 300; sleep 0.25; done
for _ in $(seq 1 8); do adb -s "$serial" shell input swipe 540 520 540 1700 300; sleep 0.25; done
sleep 1
adb -s "$serial" shell dumpsys gfxinfo io.blueeye > "$private/gfxinfo-details.txt" || true
adb -s "$serial" shell dumpsys meminfo io.blueeye > "$private/meminfo-details.txt" || true
adb -s "$serial" logcat -d -v epoch > "$private/logcat.txt" || true

# Return to Radar and stop the scan that this payload started.
adb -s "$serial" shell input keyevent BACK
sleep 1
raw_dump_ui
if python3 tools/ui-smoke/ui_node.py "$ui" content-desc "Scan" >/dev/null 2>&1; then
  tap_target content-desc "Scan"
fi
sleep 2
shot radar-final

copy_db "$private/tracker_database-post" && post_db=1 || post_db=0

python3 - "$private" "$pre_db" "$post_db" <<'PY'
from pathlib import Path
import re, sqlite3, sys
root=Path(sys.argv[1]); pre_ok=sys.argv[2]=='1'; post_ok=sys.argv[3]=='1'
log=(root/'logcat.txt').read_text(errors='replace') if (root/'logcat.txt').exists() else ''
for label,needle in [('FATAL_EXCEPTION','FATAL EXCEPTION'),('OUT_OF_MEMORY','OutOfMemoryError'),('ANR','ANR in io.blueeye')]:
    print(f'LOG_{label}_COUNT={log.count(needle)}')

gfx=(root/'gfxinfo-details.txt').read_text(errors='replace') if (root/'gfxinfo-details.txt').exists() else ''
for key,pat in {
 'TOTAL_FRAMES':r'Total frames rendered:\s*([0-9]+)',
 'JANKY_FRAMES':r'Janky frames:\s*([0-9]+)\s*\(([^)]+)\)',
 'P50_MS':r'50th percentile:\s*([0-9]+)ms',
 'P90_MS':r'90th percentile:\s*([0-9]+)ms',
 'P95_MS':r'95th percentile:\s*([0-9]+)ms',
 'P99_MS':r'99th percentile:\s*([0-9]+)ms',
}.items():
    m=re.search(pat,gfx)
    print(f'{key}=' + (('/'.join(m.groups())) if m and len(m.groups())>1 else (m.group(1) if m else 'UNAVAILABLE')))

mem=(root/'meminfo-details.txt').read_text(errors='replace') if (root/'meminfo-details.txt').exists() else ''
m=re.search(r'TOTAL PSS:\s*([0-9]+)',mem)
print('TOTAL_PSS_KB='+(m.group(1) if m else 'UNAVAILABLE'))
print(f'PRE_DB_SNAPSHOT={"PASS" if pre_ok else "UNAVAILABLE"}')
print(f'POST_DB_SNAPSHOT={"PASS" if post_ok else "UNAVAILABLE"}')

def summarize(label,path):
    if not path.exists() or path.stat().st_size < 16: return
    try:
        con=sqlite3.connect(f'file:{path}?mode=ro',uri=True)
        print(f'{label}_DB_INTEGRITY='+str(con.execute('PRAGMA integrity_check').fetchone()[0]))
        tables={r[0] for r in con.execute("select name from sqlite_master where type='table'")}
        if 'devices' in tables:
            print(f'{label}_DEVICES_COUNT='+str(con.execute('select count(*) from devices').fetchone()[0]))
            cols={r[1] for r in con.execute('pragma table_info(devices)')}
            for candidate in ['macAddressType','addressType','deviceType','technology']:
                if candidate in cols:
                    rows=con.execute(f'SELECT "{candidate}", COUNT(*) FROM devices GROUP BY "{candidate}" ORDER BY COUNT(*) DESC').fetchall()
                    safe=';'.join(f'{str(v)}:{n}' for v,n in rows)
                    print(f'{label}_{candidate.upper()}_COUNTS={safe}')
        con.close()
    except Exception as e:
        print(f'{label}_DB_READ_ERROR={type(e).__name__}')

summarize('PRE',root/'tracker_database-pre')
summarize('POST',root/'tracker_database-post')
print(f'PRIVATE_CAPTURE_DIR={root}')
PY
