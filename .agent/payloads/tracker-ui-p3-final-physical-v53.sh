#!/bin/bash
set -euo pipefail

expected_sha="f398bf978393a19aa1f7eb76f0ca4356e364851f"
private="$HOME/agent-private/tracker/ui-p3-final-physical-20260912-v53"
source_private="$HOME/agent-private/tracker/ui-p3-final-physical-20260912-v51"
mkdir -p "$private"

serial=$(adb devices | awk 'NR>1 && $2=="device" {print $1; exit}')
test -n "$serial"
test "$(adb -s "$serial" shell getprop ro.product.model | tr -d '\r')" = "SM-S906B"
test "$(git rev-parse HEAD)" = "$expected_sha"
test "$(git rev-parse origin/ui/radar-details-redesign)" = "$expected_sha"

echo SOURCE_V51_DIR_EXISTS=$(test -d "$source_private" && echo PASS || echo NO)
for name in radar-before-scan.xml radar-after-5min-scan.xml details-top.xml details-identity.xml details-actions.xml details-history.xml details-technical.xml details-all-evidence.xml radar-final.xml current.xml logcat.txt gfxinfo-details.txt meminfo-details.txt tracker_database-pre tracker_database-post; do
  if test -s "$source_private/$name"; then
    echo "V51_ARTIFACT_${name//[^A-Za-z0-9]/_}=PASS"
  else
    echo "V51_ARTIFACT_${name//[^A-Za-z0-9]/_}=NO"
  fi
done

ui="$private/current.xml"
raw_dump_ui() {
  adb -s "$serial" shell uiautomator dump /sdcard/blueeye-p3-v53.xml >/dev/null
  adb -s "$serial" exec-out cat /sdcard/blueeye-p3-v53.xml > "$ui"
}
shot() {
  local name=$1
  raw_dump_ui
  cp "$ui" "$private/$name.xml"
  adb -s "$serial" exec-out screencap -p > "$private/$name.png"
}

adb -s "$serial" shell am start -W -n io.blueeye/.MainActivity > "$private/launch.txt"
sleep 2
raw_dump_ui

# If a drawer is open or we're not on Radar, navigate semantically.
if python3 tools/ui-smoke/ui_node.py "$ui" content-desc "Menu" >/dev/null 2>&1; then
  :
else
  adb -s "$serial" shell input keyevent BACK || true
  sleep 1
  raw_dump_ui
fi

if python3 tools/ui-smoke/ui_node.py "$ui" content-desc "Menu" >/dev/null 2>&1; then
  xy=$(python3 tools/ui-smoke/ui_node.py "$ui" content-desc "Menu" 2>/dev/null || true)
  if test -n "$xy"; then
    read -r x y <<<"$xy"
    adb -s "$serial" shell input tap "$x" "$y"
    sleep 1
    raw_dump_ui
    if python3 tools/ui-smoke/ui_node.py "$ui" text "Radar" >/dev/null 2>&1; then
      xy=$(python3 tools/ui-smoke/ui_node.py "$ui" text "Radar")
      read -r x y <<<"$xy"
      adb -s "$serial" shell input tap "$x" "$y"
      sleep 1
    else
      adb -s "$serial" shell input keyevent BACK || true
    fi
  fi
fi

shot radar-entry

# Find first clickable Radar card structurally: clickable node whose subtree contains RSSI text and a Seen line.
python3 - "$ui" > "$private/card.coords" <<'PY'
import re, sys, xml.etree.ElementTree as ET
p=sys.argv[1]
root=ET.parse(p).getroot()
bounds_re=re.compile(r"\[(\d+),(\d+)\]\[(\d+),(\d+)\]")
rssi_re=re.compile(r"^-?\d+ dBm$")

def texts(n):
    vals=[]
    for x in n.iter("node"):
        t=x.attrib.get("text","")
        if t: vals.append(t)
    return vals

candidates=[]
for n in root.iter("node"):
    if n.attrib.get("clickable")!="true" or n.attrib.get("enabled","true")!="true":
        continue
    ts=texts(n)
    if any(rssi_re.match(t) for t in ts) and any(t.startswith("Seen ") for t in ts):
        m=bounds_re.fullmatch(n.attrib.get("bounds", ""))
        if m:
            x1,y1,x2,y2=map(int,m.groups())
            candidates.append((y1, x1, (x1+x2)//2, (y1+y2)//2))
if not candidates:
    sys.exit(2)
candidates.sort()
print(candidates[0][2], candidates[0][3])
PY

read -r card_x card_y < "$private/card.coords"
echo RADAR_CARD_STRUCTURAL_MATCH=PASS
adb -s "$serial" shell input tap "$card_x" "$card_y"
sleep 2
shot details-top

python3 - "$private/details-top.xml" <<'PY'
from pathlib import Path
import re, sys
s=Path(sys.argv[1]).read_text(errors='replace')
summary_heads=[
 'Watchlist device','Marked false positive','Marked known safe','Marked suspicious','Marked true positive',
 'Identity carryover needs review','Identity carryover confirmed','Identity carryover marked false','Identity carryover inconclusive',
 'Tracker-like evidence','Review movement evidence','Public-safety-like signal','Evidence needs review','No attention evidence'
]
checks={
 'DETAILS_TITLE_PRESENT':'text="Details"' in s,
 'DETAILS_RSSI_PRESENT':bool(re.search(r'text="-?\d+ dBm"',s)),
 'DETAILS_LAST_SEEN_PRESENT':'Last seen ' in s,
 'DETAILS_DECISION_SUMMARY_PRESENT':any(h in s for h in summary_heads),
 'RAW_DATA_ACTION_PRESENT':'content-desc="Raw Data"' in s,
 'REFRESH_ACTION_PRESENT':'content-desc="Refresh focused scan"' in s,
}
for k,v in checks.items(): print(f'{k}={"PASS" if v else "FAIL"}')
PY

scroll_until_text() {
  local text=$1
  local i
  for i in $(seq 0 14); do
    raw_dump_ui
    if grep -Fq "text=\"$text\"" "$ui"; then
      echo "$i"
      return 0
    fi
    adb -s "$serial" shell input swipe 540 1700 540 520 300
    sleep 0.4
  done
  echo -1
}

identity_step=$(scroll_until_text "Identity"); echo IDENTITY_SCROLL_STEP=$identity_step; test "$identity_step" != "-1"; shot details-identity
actions_step=$(scroll_until_text "Actions / Review"); echo ACTIONS_SCROLL_STEP=$actions_step; test "$actions_step" != "-1"; shot details-actions
history_step=$(scroll_until_text "History"); echo HISTORY_SCROLL_STEP=$history_step; test "$history_step" != "-1"; shot details-history
technical_step=$(scroll_until_text "Technical details"); echo TECHNICAL_SCROLL_STEP=$technical_step; test "$technical_step" != "-1"; shot details-technical
all_evidence_step=$(scroll_until_text "All evidence"); echo ALL_EVIDENCE_SCROLL_STEP=$all_evidence_step
if test "$all_evidence_step" != "-1"; then shot details-all-evidence; fi

# Validate section-local semantics without emitting identifying device values.
python3 - "$private/details-identity.xml" "$private/details-actions.xml" "$private/details-history.xml" "$private/details-technical.xml" <<'PY'
from pathlib import Path
import sys
files=[Path(x).read_text(errors='replace') for x in sys.argv[1:]]
identity,actions,history,technical=files
checks={
 'IDENTITY_SECTION_VISIBLE':'Identity' in identity,
 'ACTIONS_SECTION_VISIBLE':'Actions / Review' in actions and 'calibration' in actions.lower(),
 'HISTORY_SECTION_VISIBLE':'History' in history and 'First Seen' in history and 'Last Seen' in history and 'Encounters' in history,
 'TECHNICAL_SECTION_VISIBLE':'Technical details' in technical and 'Connection Status' in technical and 'Radio' in technical,
}
for k,v in checks.items(): print(f'{k}={"PASS" if v else "FAIL"}')
PY

adb -s "$serial" shell dumpsys gfxinfo io.blueeye reset >/dev/null || true
for _ in $(seq 1 8); do adb -s "$serial" shell input swipe 540 1700 540 520 300; sleep 0.2; done
for _ in $(seq 1 8); do adb -s "$serial" shell input swipe 540 520 540 1700 300; sleep 0.2; done
sleep 1
adb -s "$serial" shell dumpsys gfxinfo io.blueeye > "$private/gfxinfo-details.txt" || true
adb -s "$serial" shell dumpsys meminfo io.blueeye > "$private/meminfo-details.txt" || true
adb -s "$serial" logcat -d -v epoch > "$private/logcat.txt" || true

adb -s "$serial" shell input keyevent BACK || true
sleep 1
raw_dump_ui
# Stop scanning only if the Scan button is present; v51 may have left it active.
if python3 tools/ui-smoke/ui_node.py "$ui" content-desc "Scan" >/dev/null 2>&1; then
  xy=$(python3 tools/ui-smoke/ui_node.py "$ui" content-desc "Scan")
  read -r x y <<<"$xy"
  adb -s "$serial" shell input tap "$x" "$y"
  sleep 1
fi
shot radar-final

adb -s "$serial" shell am force-stop io.blueeye || true
sleep 1
if adb -s "$serial" exec-out run-as io.blueeye cat databases/tracker_database > "$private/tracker_database-post" 2>"$private/db-copy.err"; then db_ok=1; else db_ok=0; fi
adb -s "$serial" shell am start -W -n io.blueeye/.MainActivity >/dev/null || true

python3 - "$private" "$db_ok" <<'PY'
from pathlib import Path
import re, sqlite3, sys
root=Path(sys.argv[1]); db_ok=sys.argv[2]=='1'
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
print(f'POST_DB_SNAPSHOT={"PASS" if db_ok else "UNAVAILABLE"}')
if db_ok:
 p=root/'tracker_database-post'
 try:
  con=sqlite3.connect(f'file:{p}?mode=ro',uri=True)
  print('DB_INTEGRITY='+str(con.execute('PRAGMA integrity_check').fetchone()[0]))
  tables={r[0] for r in con.execute("select name from sqlite_master where type='table'")}
  for t in ['devices','signal_samples','follow_me_observations']:
   if t in tables: print(f'{t.upper()}_COUNT='+str(con.execute(f'SELECT COUNT(*) FROM "{t}"').fetchone()[0]))
  if 'devices' in tables:
   cols={r[1] for r in con.execute('pragma table_info(devices)')}
   for candidate in ['macAddressType','addressType','deviceType','technology']:
    if candidate in cols:
     rows=con.execute(f'SELECT "{candidate}", COUNT(*) FROM devices GROUP BY "{candidate}" ORDER BY COUNT(*) DESC').fetchall()
     safe=';'.join(f'{str(v)}:{n}' for v,n in rows)
     print(f'{candidate.upper()}_COUNTS={safe}')
  con.close()
 except Exception as e:
  print('DB_READ_ERROR='+type(e).__name__)
print('PRIVATE_CAPTURE_DIR='+str(root))
PY

test "$(git rev-parse HEAD)" = "$expected_sha"
test "$(git rev-parse origin/ui/radar-details-redesign)" = "$expected_sha"
test -z "$(git status --porcelain)"
echo P3_V53_PHYSICAL_AUDIT_DONE
