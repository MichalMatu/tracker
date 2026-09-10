#!/usr/bin/env bash
set -euo pipefail
EXPECTED='53cccf4fa39525febf54e0a57f65796efbd51812'
PKG='io.blueeye'
SERIAL_EXPECTED='RFCT70L7E8J'
OUT='/tmp/tracker-phase3-export-share-ui-diagnose-v1'
rm -rf "$OUT" && mkdir -p "$OUT"

git fetch --no-tags origin main >/dev/null
HEAD="$(git rev-parse origin/main)"
echo "source_head=$HEAD"
test "$HEAD" = "$EXPECTED"
SERIAL="$(adb devices | awk 'NR>1 && $2=="device" {print $1}' | head -n1)"
test "$SERIAL" = "$SERIAL_EXPECTED"
echo "serial=$SERIAL"
test "$(adb -s "$SERIAL" shell am get-current-user | tr -d '\r')" = '0'
adb -s "$SERIAL" shell input keyevent KEYCODE_WAKEUP >/dev/null 2>&1 || true
adb -s "$SERIAL" shell wm dismiss-keyguard >/dev/null 2>&1 || true
adb -s "$SERIAL" shell am start -W --user 0 -n "$PKG/.MainActivity" >/dev/null
sleep 2

XML="$OUT/window.xml"
dump_ui() {
  for _ in 1 2 3 4 5; do
    if adb -s "$SERIAL" shell uiautomator dump /sdcard/blueeye-export-diagnose.xml >/dev/null 2>&1 && \
       adb -s "$SERIAL" exec-out cat /sdcard/blueeye-export-diagnose.xml > "$XML" 2>/dev/null && grep -q '<hierarchy' "$XML"; then return 0; fi
    sleep 1
  done
  return 1
}
coords() {
  dump_ui
  python3 tools/ui-smoke/ui_node.py "$XML" "$1" "$2"
}
tap_node() {
  local c x y
  c="$(coords "$1" "$2")"
  test -n "$c"
  read -r x y <<< "$c"
  echo "tap $1=$2 at $x,$y"
  adb -s "$SERIAL" shell input tap "$x" "$y"
  sleep 1
}

for _ in 1 2 3 4 5; do
  if coords content-desc Menu >/dev/null 2>&1; then break; fi
  adb -s "$SERIAL" shell input keyevent BACK >/dev/null 2>&1 || true
  sleep 1
done
tap_node content-desc Menu
tap_node text Settings
for _ in 1 2 3 4 5 6; do
  if coords text 'Database & Updates' >/dev/null 2>&1; then break; fi
  adb -s "$SERIAL" shell input swipe 540 1750 540 550 350
  sleep 1
done
tap_node text 'Database & Updates'
sleep 2
for _ in $(seq 1 12); do
  if coords text 'Session export' >/dev/null 2>&1 && coords text Share >/dev/null 2>&1; then break; fi
  adb -s "$SERIAL" shell input swipe 540 1750 540 500 350
  sleep 1
done
dump_ui
python3 - "$XML" <<'PY'
import re,sys,xml.etree.ElementTree as ET
p=sys.argv[1]
r=ET.parse(p).getroot()
for n in r.iter('node'):
    t=n.attrib.get('text',''); d=n.attrib.get('content-desc',''); b=n.attrib.get('bounds','')
    if t in {'Session export','Copy','Share'} or d in {'Session export','Copy','Share'}:
        print(f'node text={t!r} desc={d!r} bounds={b!r} clickable={n.attrib.get("clickable")!r} enabled={n.attrib.get("enabled")!r}')
PY
coords text 'Session export' >/dev/null
SHARE_COORDS="$(coords text Share)"
test -n "$SHARE_COORDS"
read -r SX SY <<< "$SHARE_COORDS"
echo "share_coords=$SX,$SY"
FILE='cache/session_exports/blueeye-session-export.json'
adb -s "$SERIAL" shell run-as "$PKG" rm -f "$FILE" >/dev/null 2>&1 || true
adb -s "$SERIAL" shell input tap "$SX" "$SY"
for i in $(seq 1 30); do
  if adb -s "$SERIAL" shell run-as "$PKG" test -s "$FILE" 2>/dev/null; then
    echo "export_present_after_seconds=$i"
    adb -s "$SERIAL" shell run-as "$PKG" ls -l "$FILE"
    adb -s "$SERIAL" exec-out run-as "$PKG" cat "$FILE" > "$OUT/session-export.json"
    python3 - "$OUT/session-export.json" <<'PY'
import hashlib,json,sys
b=open(sys.argv[1],'rb').read(); d=json.loads(b)
print('export_bytes='+str(len(b)))
print('export_sha256='+hashlib.sha256(b).hexdigest())
print('deviceCount='+str(d.get('deviceCount')))
print('sampleCount='+str(d.get('sampleCount')))
print('has_fieldMvpDiagnostics='+str('fieldMvpDiagnostics' in d))
PY
    echo 'EXPORT_SHARE_UI_DIAGNOSE_PASS'
    exit 0
  fi
  sleep 1
done

echo 'export_present=0_after_30s'
dump_ui || true
python3 - "$XML" <<'PY'
import xml.etree.ElementTree as ET,sys
r=ET.parse(sys.argv[1]).getroot()
vals=[]
for n in r.iter('node'):
    t=n.attrib.get('text',''); d=n.attrib.get('content-desc','')
    if t or d: vals.append(f'text={t!r} desc={d!r}')
print('foreground_nodes='+' | '.join(vals[-80:]))
PY
exit 4
