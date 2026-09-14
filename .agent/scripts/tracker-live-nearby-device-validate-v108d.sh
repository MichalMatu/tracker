#!/bin/bash
set -euo pipefail

PACKAGE="io.blueeye"
ACTIVITY="io.blueeye/.MainActivity"
UI_XML="/tmp/tracker-live-nearby-ui-v108d.xml"
OLD_TIMEOUT=""

restore_timeout() {
  if [ -n "$OLD_TIMEOUT" ]; then
    adb shell settings put system screen_off_timeout "$OLD_TIMEOUT" >/dev/null 2>&1 || true
  fi
}
trap restore_timeout EXIT

dump_ui() {
  adb shell uiautomator dump /sdcard/tracker-live-nearby-ui-v108d.xml >/dev/null
  adb pull /sdcard/tracker-live-nearby-ui-v108d.xml "$UI_XML" >/dev/null 2>&1
}

tap_attr() {
  local attr="$1"
  local value="$2"
  python3 - "$UI_XML" "$attr" "$value" <<'PY'
import re, sys, xml.etree.ElementTree as ET
path, attr, value = sys.argv[1:]
root = ET.parse(path).getroot()
for node in root.iter('node'):
    if node.attrib.get(attr) == value:
        m = re.match(r'\[(\d+),(\d+)\]\[(\d+),(\d+)\]', node.attrib.get('bounds',''))
        if m:
            x1,y1,x2,y2 = map(int,m.groups())
            print(f"{(x1+x2)//2} {(y1+y2)//2}")
            raise SystemExit(0)
raise SystemExit(1)
PY
}

tap_exact() {
  local attr="$1" value="$2" coords
  coords=$(tap_attr "$attr" "$value")
  adb shell input tap $coords >/dev/null
}

report_ui() {
  local label="$1"
  python3 - "$UI_XML" "$label" <<'PY'
import re, sys, xml.etree.ElementTree as ET
path, label = sys.argv[1:]
root = ET.parse(path).getroot()
texts=[]
for n in root.iter('node'):
    for key in ('text','content-desc'):
        v=n.attrib.get(key,'').strip()
        if v and v not in texts:
            texts.append(v)
summary = next((v for v in texts if re.match(r'^(Scanning|Scanner paused|Starting scanner|Scanner error) · \d+ active now · \d+ recent$', v)), None)
active = next((v for v in texts if re.match(r'^Active now · \d+$', v)), None)
recent = next((v for v in texts if re.match(r'^Recently seen · \d+$', v)), None)
print(f"{label}_SUMMARY={summary or 'not-visible'}")
print(f"{label}_ACTIVE_HEADER={active or 'not-visible'}")
print(f"{label}_RECENT_HEADER={recent or 'not-visible'}")
PY
}

find_fhn_group_on_screen() {
  python3 - "$UI_XML" <<'PY'
import re, sys, xml.etree.ElementTree as ET
root=ET.parse(sys.argv[1]).getroot()
texts=[n.attrib.get('text','').strip() for n in root.iter('node') if n.attrib.get('text','').strip()]
if 'Google Find Hub' not in texts:
    raise SystemExit(1)
summary=next((t for t in texts if re.match(r'^\d+ active now · \d+ recent · \d+ identities seen in last 3 min$',t)), None)
strong=next((t for t in texts if re.match(r'^Strongest -?\d+ dBm$',t)), None)
print('FHN_GROUP_VISIBLE=yes')
print('FHN_GROUP_SUMMARY='+(summary or 'summary-not-visible'))
print('FHN_GROUP_SIGNAL='+(strong or 'signal-not-visible'))
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
adb shell am start -W -n "$ACTIVITY" >/tmp/tracker-v108d-launch.log
sleep 3

dump_ui
if ! tap_attr content-desc "Open menu" >/dev/null 2>&1; then
  echo "OPEN_MENU_VISIBLE=no"
  python3 - "$UI_XML" <<'PY'
import xml.etree.ElementTree as ET, sys
root=ET.parse(sys.argv[1]).getroot()
vals=[]
for n in root.iter('node'):
    for k in ('text','content-desc'):
        v=n.attrib.get(k,'').strip()
        if v and v not in vals: vals.append(v)
print('VISIBLE_UI_LABELS='+' | '.join(vals[:25]))
PY
  exit 1
fi
echo "OPEN_MENU_VISIBLE=yes"
tap_exact content-desc "Open menu"
sleep 1
dump_ui
if ! tap_attr text "Live Nearby" >/dev/null 2>&1; then
  echo "LIVE_NEARBY_MENU_VISIBLE=no"
  exit 1
fi
echo "LIVE_NEARBY_MENU_VISIBLE=yes"
tap_exact text "Live Nearby"
sleep 3
dump_ui
python3 - "$UI_XML" <<'PY'
import sys, xml.etree.ElementTree as ET
root=ET.parse(sys.argv[1]).getroot()
texts=[n.attrib.get('text','') for n in root.iter('node')]
assert 'Live Nearby' in texts
print('LIVE_NEARBY_ROUTE_OK=yes')
PY

if tap_attr text "Scan" >/dev/null 2>&1; then
  tap_exact text "Scan"
  sleep 3
  echo "SCANNER_STARTED=yes"
elif tap_attr text "Pause" >/dev/null 2>&1; then
  echo "SCANNER_ALREADY_RUNNING=yes"
else
  echo "SCANNER_CONTROL_NOT_VISIBLE=yes"
  exit 1
fi

dump_ui
report_ui "INITIAL"
echo "STATIC_CAPTURE_SECONDS=300"
sleep 300
adb shell input keyevent KEYCODE_WAKEUP >/dev/null 2>&1 || true
adb shell am start -W -n "$ACTIVITY" >/dev/null
sleep 2
dump_ui
report_ui "FINAL"

FOUND=0
for i in $(seq 1 14); do
  if find_fhn_group_on_screen; then FOUND=1; break; fi
  adb shell input swipe 540 1850 540 650 300 >/dev/null
  sleep 1
  dump_ui
done
if [ "$FOUND" -eq 0 ]; then echo "FHN_GROUP_VISIBLE=no"; fi

echo "RAW_IDENTIFIERS_EXPOSED=no"
echo "DEVICE_VALIDATION_COMPLETE=yes"
