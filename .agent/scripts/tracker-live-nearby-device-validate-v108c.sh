#!/bin/bash
set -euo pipefail
PACKAGE="io.blueeye"
UI_XML="/tmp/tracker-live-nearby-v108c.xml"
OLD_TIMEOUT=""

restore_timeout() {
  if [ -n "$OLD_TIMEOUT" ]; then
    adb shell settings put system screen_off_timeout "$OLD_TIMEOUT" >/dev/null 2>&1 || true
  fi
}
trap restore_timeout EXIT

dump_ui() {
  adb shell uiautomator dump /sdcard/tracker-live-nearby-v108c.xml >/dev/null
  adb pull /sdcard/tracker-live-nearby-v108c.xml "$UI_XML" >/dev/null 2>&1
}

coords_for() {
  local attr="$1" value="$2"
  python3 - "$UI_XML" "$attr" "$value" <<'PY'
import re, sys, xml.etree.ElementTree as ET
root=ET.parse(sys.argv[1]).getroot(); attr=sys.argv[2]; value=sys.argv[3]
for n in root.iter('node'):
    if n.attrib.get(attr)==value:
        m=re.match(r'\[(\d+),(\d+)\]\[(\d+),(\d+)\]', n.attrib.get('bounds',''))
        if m:
            x1,y1,x2,y2=map(int,m.groups()); print((x1+x2)//2,(y1+y2)//2); raise SystemExit(0)
raise SystemExit(1)
PY
}

tap_exact() {
  local attr="$1" value="$2" coords
  coords=$(coords_for "$attr" "$value")
  adb shell input tap $coords >/dev/null
}

ui_flag() {
  local value="$1"
  python3 - "$UI_XML" "$value" <<'PY'
import sys, xml.etree.ElementTree as ET
root=ET.parse(sys.argv[1]).getroot(); value=sys.argv[2]
for n in root.iter('node'):
    if n.attrib.get('text')==value or n.attrib.get('content-desc')==value:
        raise SystemExit(0)
raise SystemExit(1)
PY
}

report_live_summary() {
  local label="$1"
  python3 - "$UI_XML" "$label" <<'PY'
import re, sys, xml.etree.ElementTree as ET
root=ET.parse(sys.argv[1]).getroot(); label=sys.argv[2]
vals=[]
for n in root.iter('node'):
    for k in ('text','content-desc'):
        v=n.attrib.get(k,'').strip()
        if v: vals.append(v)
summary=next((v for v in vals if re.match(r'^(Scanning|Scanner paused|Starting scanner|Scanner error) · \d+ active now · \d+ recent$',v)),None)
fhn='Google Find Hub' in vals
fhn_summary=next((v for v in vals if re.match(r'^\d+ active now · \d+ recent · \d+ identities seen in last 3 min$',v)),None) if fhn else None
print(f'{label}_LIVE_SUMMARY={summary or "not-visible"}')
print(f'{label}_FHN_GROUP={"yes" if fhn else "no"}')
print(f'{label}_FHN_SUMMARY={fhn_summary or "not-visible"}')
PY
}

DEVICE_COUNT=$(adb devices | awk 'NR>1 && $2=="device" {c++} END {print c+0}')
test "$DEVICE_COUNT" -eq 1
SERIAL=$(adb devices | awk 'NR>1 && $2=="device" {print $1; exit}')
MODEL=$(adb -s "$SERIAL" shell getprop ro.product.model | tr -d '\r')
test "$MODEL" = "SM-S906B"
echo "DEVICE_OK=SM-S906B"
adb shell pm path "$PACKAGE" >/dev/null
echo "APP_INSTALLED=yes"

OLD_TIMEOUT=$(adb shell settings get system screen_off_timeout | tr -d '\r')
adb shell settings put system screen_off_timeout 600000 >/dev/null
adb shell monkey -p "$PACKAGE" -c android.intent.category.LAUNCHER 1 >/dev/null 2>&1
sleep 3
dump_ui
if ui_flag "Open menu"; then echo "OPEN_MENU_VISIBLE=yes"; else echo "OPEN_MENU_VISIBLE=no"; exit 1; fi

tap_exact content-desc "Open menu"
sleep 1
dump_ui
if ui_flag "Live Nearby"; then echo "DRAWER_LIVE_NEARBY_VISIBLE=yes"; else echo "DRAWER_LIVE_NEARBY_VISIBLE=no"; exit 1; fi

tap_exact text "Live Nearby"
sleep 3
dump_ui
if ui_flag "Live Nearby"; then echo "LIVE_NEARBY_ROUTE_OK=yes"; else echo "LIVE_NEARBY_ROUTE_OK=no"; exit 1; fi

if ui_flag "Scan"; then
  tap_exact text "Scan"; sleep 3; echo "SCANNER_STARTED=yes"
elif ui_flag "Pause"; then
  echo "SCANNER_ALREADY_RUNNING=yes"
else
  echo "SCANNER_CONTROL_VISIBLE=no"; exit 1
fi

dump_ui
report_live_summary "INITIAL"
echo "STATIC_CAPTURE_SECONDS=300"
sleep 300
adb shell input keyevent KEYCODE_WAKEUP >/dev/null 2>&1 || true
adb shell monkey -p "$PACKAGE" -c android.intent.category.LAUNCHER 1 >/dev/null 2>&1
sleep 2
dump_ui
# Re-enter Live Nearby if launcher returned to Radar.
if ! ui_flag "Live Nearby"; then
  if ui_flag "Open menu"; then tap_exact content-desc "Open menu"; sleep 1; dump_ui; fi
  if ui_flag "Live Nearby"; then tap_exact text "Live Nearby"; sleep 2; dump_ui; fi
fi
report_live_summary "FINAL"
# Search downward for the compact FHN group without exposing device identifiers.
FOUND=0
for i in $(seq 1 12); do
  if ui_flag "Google Find Hub"; then FOUND=1; report_live_summary "FHN_VISIBLE"; break; fi
  adb shell input swipe 540 1800 540 700 250 >/dev/null
  sleep 1
  dump_ui
done
echo "FHN_GROUP_FOUND=$FOUND"
echo "RAW_IDENTIFIERS_EXPOSED=no"
echo "DEVICE_VALIDATION_COMPLETE=yes"
