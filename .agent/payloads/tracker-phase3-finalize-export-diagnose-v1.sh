#!/usr/bin/env bash
set -euo pipefail
EXPECTED='c1577ff92a0deb6bf14c29d0320d53f3aee87632'
PKG='io.blueeye'
EXPECTED_PID='9585'

git fetch --no-tags origin main >/dev/null
HEAD="$(git rev-parse origin/main)"
echo "source_head=$HEAD"
test "$HEAD" = "$EXPECTED"
SERIAL="$(adb devices | awk 'NR>1 && $2=="device" {print $1}' | head -n1)"
test -n "$SERIAL"
echo "serial=$SERIAL"
PID_NOW="$(adb -s "$SERIAL" shell pidof "$PKG" | tr -d '\r')"
echo "pid_now=$PID_NOW expected_pid=$EXPECTED_PID"
if [ "$PID_NOW" = "$EXPECTED_PID" ]; then echo 'same_process=1'; else echo 'same_process=0'; fi
if adb -s "$SERIAL" shell dumpsys activity services "$PKG" | grep -q 'ScannerService'; then echo 'scanner_service=present'; else echo 'scanner_service=absent'; fi

echo '--- session export cache ---'
adb -s "$SERIAL" shell run-as "$PKG" ls -la cache/session_exports 2>&1 || true
if adb -s "$SERIAL" shell run-as "$PKG" test -s cache/session_exports/blueeye-session-export.json; then
  echo 'cached_export_present=1'
  adb -s "$SERIAL" exec-out run-as "$PKG" cat cache/session_exports/blueeye-session-export.json > /tmp/tracker-phase3-finalize-export-diagnose.json
  echo "cached_export_size=$(wc -c < /tmp/tracker-phase3-finalize-export-diagnose.json | tr -d ' ')"
  echo "cached_export_sha256=$(shasum -a 256 /tmp/tracker-phase3-finalize-export-diagnose.json | awk '{print $1}')"
  python3 -m json.tool /tmp/tracker-phase3-finalize-export-diagnose.json >/dev/null && echo 'cached_export_json_valid=1'
  python3 - /tmp/tracker-phase3-finalize-export-diagnose.json <<'PY'
import json,sys
p=sys.argv[1]
d=json.load(open(p)); s=d.get('fieldMvpDiagnostics',{}).get('scanner',{}); i=s.get('ingest',{})
for k in ('startedAt','lastBleSeenAt','bleResultsPerMinute','state'):
 print(f'cached_{k}={s.get(k)}')
for k in ('rawBleCallbacksTotal','enqueueAcceptedTotal','enqueueRejectedTotal','queueDroppedTotal','coalescedTotal','processingStartedTotal','processingSucceededTotal','processingFailedTotal','signalSamplesWrittenTotal','signalSampleWriteFailuresTotal','queueDepth'):
 print(f'cached_{k}={i.get(k)}')
PY
else
  echo 'cached_export_present=0'
fi

echo '--- foreground ---'
adb -s "$SERIAL" shell dumpsys window windows | grep -E 'mCurrentFocus|mFocusedApp' | head -n 6 || true
adb -s "$SERIAL" shell input keyevent KEYCODE_WAKEUP >/dev/null 2>&1 || true
adb -s "$SERIAL" shell wm dismiss-keyguard >/dev/null 2>&1 || true
adb -s "$SERIAL" shell uiautomator dump /sdcard/blueeye-diagnose.xml >/dev/null 2>&1 || true
adb -s "$SERIAL" exec-out cat /sdcard/blueeye-diagnose.xml > /tmp/blueeye-diagnose.xml 2>/dev/null || true
python3 - /tmp/blueeye-diagnose.xml <<'PY'
import sys,xml.etree.ElementTree as ET
p=sys.argv[1]
try:
 root=ET.parse(p).getroot()
 vals=[]
 for n in root.iter('node'):
  t=(n.attrib.get('text') or '').strip(); d=(n.attrib.get('content-desc') or '').strip()
  if t: vals.append('text='+t)
  if d: vals.append('desc='+d)
 print('ui_nodes='+' | '.join(vals[:80]))
except Exception as e:
 print('ui_dump_parse_error='+type(e).__name__)
PY

echo 'PHASE3_FINALIZE_EXPORT_DIAGNOSE_DONE'
