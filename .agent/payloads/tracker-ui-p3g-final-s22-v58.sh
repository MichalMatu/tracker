#!/bin/bash
set -euo pipefail

expected_sha="e1e09df50caf7cf9939420d42bd48bfb21ea6823"
serial=$(adb devices | awk 'NR>1 && $2=="device" {print $1; exit}')
test -n "$serial"
test "$(adb -s "$serial" shell getprop ro.product.model | tr -d '\r')" = "SM-S906B"
test "$(git rev-parse HEAD)" = "$expected_sha"
test "$(git rev-parse origin/ui/radar-details-redesign)" = "$expected_sha"

private="$HOME/agent-private/tracker/ui-p3g-final-s22-20260912-v58"
mkdir -p "$private"
ui="$private/current.xml"

dump_ui() {
  adb -s "$serial" shell uiautomator dump /sdcard/blueeye-p3g-v58.xml >/dev/null
  adb -s "$serial" exec-out cat /sdcard/blueeye-p3g-v58.xml > "$ui"
}

click_by_desc() {
  local value=$1 xy x y
  dump_ui
  xy=$(python3 tools/ui-smoke/ui_node.py "$ui" content-desc "$value" 2>/dev/null || true)
  test -n "$xy"
  read -r x y <<<"$xy"
  adb -s "$serial" shell input tap "$x" "$y"
  sleep 1
}

click_by_text() {
  local value=$1 xy x y
  dump_ui
  xy=$(python3 tools/ui-smoke/ui_node.py "$ui" text "$value" 2>/dev/null || true)
  test -n "$xy"
  read -r x y <<<"$xy"
  adb -s "$serial" shell input tap "$x" "$y"
  sleep 1
}

scroll_until_text() {
  local text=$1 i
  for i in $(seq 0 18); do
    dump_ui
    if grep -Fq "text=\"$text\"" "$ui"; then
      echo "$i"
      return 0
    fi
    adb -s "$serial" shell input swipe 540 1700 540 520 300
    sleep 0.35
  done
  echo -1
}

adb -s "$serial" logcat -c || true
adb -s "$serial" shell am force-stop io.blueeye || true
adb -s "$serial" shell am start -W -n io.blueeye/.MainActivity > "$private/launch.txt"
sleep 2
dump_ui

# Get back to Radar root if Android restored a nested destination.
for _ in $(seq 1 4); do
  if grep -Fq 'content-desc="Menu"' "$ui"; then break; fi
  adb -s "$serial" shell input keyevent BACK || true
  sleep 0.6
  dump_ui
done

echo RADAR_ROOT_PRESENT=$(grep -Fq 'content-desc="Menu"' "$ui" && echo PASS || echo FAIL)
test "$(grep -c 'content-desc="Menu"' "$ui" || true)" -gt 0

# If there is no visible device card, start scanning and wait only until one appears.
if ! grep -Eq 'text="-?[0-9]+ dBm"' "$ui" || ! grep -Fq 'text="Seen ' "$ui"; then
  echo RADAR_CARD_INITIAL=NONE
  scan_xy=$(python3 tools/ui-smoke/ui_node.py "$ui" content-desc "Scan" 2>/dev/null || true)
  if test -n "$scan_xy"; then
    read -r sx sy <<<"$scan_xy"
    adb -s "$serial" shell input tap "$sx" "$sy"
    echo SCAN_STARTED=PASS
  else
    echo SCAN_BUTTON_FOUND=NO
  fi
  found=0
  for i in $(seq 1 12); do
    sleep 5
    dump_ui
    echo SCAN_WAIT_HEARTBEAT_${i}
    if grep -Eq 'text="-?[0-9]+ dBm"' "$ui" && grep -Fq 'text="Seen ' "$ui"; then
      found=1
      echo RADAR_CARD_AVAILABLE_AFTER_${i}x5S=PASS
      break
    fi
  done
  test "$found" = "1"
else
  echo RADAR_CARD_INITIAL=PASS
fi

# Click first structural Radar card without printing identifying values.
python3 - "$ui" > "$private/card.coords" <<'PY'
import re, sys, xml.etree.ElementTree as ET
root=ET.parse(sys.argv[1]).getroot()
bounds_re=re.compile(r"\[(\d+),(\d+)\]\[(\d+),(\d+)\]")
rssi_re=re.compile(r"^-?\d+ dBm$")

def texts(node):
    return [x.attrib.get("text", "") for x in node.iter("node") if x.attrib.get("text")]

candidates=[]
for node in root.iter("node"):
    if node.attrib.get("clickable") != "true" or node.attrib.get("enabled", "true") != "true":
        continue
    ts=texts(node)
    if any(rssi_re.match(t) for t in ts) and any(t.startswith("Seen ") for t in ts):
        m=bounds_re.fullmatch(node.attrib.get("bounds", ""))
        if m:
            x1,y1,x2,y2=map(int,m.groups())
            candidates.append((y1,x1,(x1+x2)//2,(y1+y2)//2))
if not candidates:
    sys.exit(2)
candidates.sort()
print(candidates[0][2], candidates[0][3])
PY
read -r card_x card_y < "$private/card.coords"
adb -s "$serial" shell input tap "$card_x" "$card_y"
sleep 2
dump_ui
cp "$ui" "$private/details-top.xml"

grep -Fq 'text="Details"' "$ui"
echo DETAILS_TITLE_PRESENT=PASS
if grep -Fq 'content-desc="Raw Data"' "$ui" || grep -Fq 'text="Raw Data"' "$ui"; then
  echo RAW_DATA_TOPBAR_ABSENT=FAIL
  exit 1
fi
echo RAW_DATA_TOPBAR_ABSENT=PASS
grep -Fq 'content-desc="Refresh focused scan"' "$ui"
echo REFRESH_TOPBAR_PRESENT=PASS

tech_step=$(scroll_until_text "Technical details")
echo TECHNICAL_DETAILS_SCROLL_STEP=$tech_step
test "$tech_step" != "-1"
raw_step=$(scroll_until_text "Raw Data")
echo RAW_DATA_TECHNICAL_SCROLL_STEP=$raw_step
test "$raw_step" != "-1"
dump_ui
grep -Fq 'text="Raw Data"' "$ui"
echo RAW_DATA_UNDER_TECHNICAL_PRESENT=PASS

click_by_text "Raw Data"
dump_ui
grep -Fq 'text="Copy Raw Data"' "$ui"
echo RAW_DATA_DIALOG_OPEN=PASS
grep -Fq 'text="Export JSON to Clipboard"' "$ui"
echo RAW_DATA_EXPORT_PRESENT=PASS
click_by_text "Close"

adb -s "$serial" logcat -d -v epoch > "$private/logcat.txt" || true
fatal=$(grep -c 'FATAL EXCEPTION' "$private/logcat.txt" || true)
oom=$(grep -c 'OutOfMemoryError' "$private/logcat.txt" || true)
anr=$(grep -c 'ANR in io.blueeye' "$private/logcat.txt" || true)
echo LOG_FATAL_EXCEPTION_COUNT=$fatal
echo LOG_OOM_COUNT=$oom
echo LOG_ANR_COUNT=$anr
test "$fatal" = "0"
test "$oom" = "0"
test "$anr" = "0"
test -n "$(adb -s "$serial" shell pidof io.blueeye | tr -d '\r')"

test "$(git rev-parse HEAD)" = "$expected_sha"
test "$(git rev-parse origin/ui/radar-details-redesign)" = "$expected_sha"
test -z "$(git status --porcelain)"
echo PRIVATE_CAPTURE_DIR="$private"
echo P3G_V58_S22_PASS
