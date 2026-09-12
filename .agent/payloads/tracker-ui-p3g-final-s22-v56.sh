#!/bin/bash
set -euo pipefail

expected_sha="e1e09df50caf7cf9939420d42bd48bfb21ea6823"
serial=$(adb devices | awk 'NR>1 && $2=="device" {print $1; exit}')
test -n "$serial"
test "$(adb -s "$serial" shell getprop ro.product.model | tr -d '\r')" = "SM-S906B"
test "$(git rev-parse HEAD)" = "$expected_sha"
test "$(git rev-parse origin/ui/radar-details-redesign)" = "$expected_sha"

private="$HOME/agent-private/tracker/ui-p3g-final-s22-20260912-v56"
mkdir -p "$private"
ui="$private/current.xml"

raw_dump_ui() {
  adb -s "$serial" shell uiautomator dump /sdcard/blueeye-p3g-v56.xml >/dev/null
  adb -s "$serial" exec-out cat /sdcard/blueeye-p3g-v56.xml > "$ui"
}

coords() {
  local attr=$1 value=$2
  raw_dump_ui
  python3 tools/ui-smoke/ui_node.py "$ui" "$attr" "$value" 2>/dev/null
}

tap_target() {
  local attr=$1 value=$2 xy x y
  xy=$(coords "$attr" "$value" || true)
  test -n "$xy"
  read -r x y <<<"$xy"
  adb -s "$serial" shell input tap "$x" "$y"
  sleep 1
}

scroll_until_text() {
  local text=$1 i
  for i in $(seq 0 16); do
    raw_dump_ui
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
raw_dump_ui

# Return to Radar if a prior screen was restored.
for _ in $(seq 1 4); do
  if python3 tools/ui-smoke/ui_node.py "$ui" content-desc "Menu" >/dev/null 2>&1; then
    break
  fi
  adb -s "$serial" shell input keyevent BACK || true
  sleep 0.7
  raw_dump_ui
done

if python3 tools/ui-smoke/ui_node.py "$ui" content-desc "Menu" >/dev/null 2>&1; then
  tap_target content-desc "Menu"
  raw_dump_ui
  if python3 tools/ui-smoke/ui_node.py "$ui" text "Radar" >/dev/null 2>&1; then
    tap_target text "Radar"
  else
    adb -s "$serial" shell input keyevent BACK || true
    sleep 0.5
  fi
fi
raw_dump_ui

# Open first clickable Radar card structurally without emitting identifying text.
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
raw_dump_ui
cp "$ui" "$private/details-top.xml"

if grep -Fq 'text="Details"' "$ui"; then echo DETAILS_TITLE_PRESENT=PASS; else echo DETAILS_TITLE_PRESENT=FAIL; exit 1; fi
if grep -Fq 'content-desc="Raw Data"' "$ui" || grep -Fq 'text="Raw Data"' "$ui"; then
  echo RAW_DATA_TOPBAR_ABSENT=FAIL
  exit 1
else
  echo RAW_DATA_TOPBAR_ABSENT=PASS
fi
if grep -Fq 'content-desc="Refresh focused scan"' "$ui"; then echo REFRESH_TOPBAR_PRESENT=PASS; else echo REFRESH_TOPBAR_PRESENT=FAIL; exit 1; fi

tech_step=$(scroll_until_text "Technical details")
echo TECHNICAL_DETAILS_SCROLL_STEP=$tech_step
test "$tech_step" != "-1"
raw_step=$(scroll_until_text "Raw Data")
echo RAW_DATA_TECHNICAL_SCROLL_STEP=$raw_step
test "$raw_step" != "-1"
raw_dump_ui
if grep -Fq 'text="Raw Data"' "$ui"; then echo RAW_DATA_UNDER_TECHNICAL_PRESENT=PASS; else echo RAW_DATA_UNDER_TECHNICAL_PRESENT=FAIL; exit 1; fi

tap_target text "Raw Data"
raw_dump_ui
if grep -Fq 'text="Copy Raw Data"' "$ui"; then echo RAW_DATA_DIALOG_OPEN=PASS; else echo RAW_DATA_DIALOG_OPEN=FAIL; exit 1; fi
if grep -Fq 'text="Export JSON to Clipboard"' "$ui"; then echo RAW_DATA_EXPORT_PRESENT=PASS; else echo RAW_DATA_EXPORT_PRESENT=FAIL; exit 1; fi
tap_target text "Close"

adb -s "$serial" logcat -d -v epoch > "$private/logcat.txt" || true
echo LOG_FATAL_EXCEPTION_COUNT=$(grep -c 'FATAL EXCEPTION' "$private/logcat.txt" || true)
echo LOG_OOM_COUNT=$(grep -c 'OutOfMemoryError' "$private/logcat.txt" || true)
echo LOG_ANR_COUNT=$(grep -c 'ANR in io.blueeye' "$private/logcat.txt" || true)
test "$(grep -c 'FATAL EXCEPTION' "$private/logcat.txt" || true)" = "0"
test "$(grep -c 'OutOfMemoryError' "$private/logcat.txt" || true)" = "0"
test "$(grep -c 'ANR in io.blueeye' "$private/logcat.txt" || true)" = "0"
test -n "$(adb -s "$serial" shell pidof io.blueeye | tr -d '\r')"

test "$(git rev-parse HEAD)" = "$expected_sha"
test "$(git rev-parse origin/ui/radar-details-redesign)" = "$expected_sha"
test -z "$(git status --porcelain)"
echo PRIVATE_CAPTURE_DIR="$private"
echo P3G_V56_S22_PASS
