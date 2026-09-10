#!/usr/bin/env bash
set -euo pipefail
EXPECTED='a15877bdb338a54d1bc30885b876c9e8f1459494'
PKG='io.blueeye'
SERIAL='RFCT70L7E8J'
ROOT="$(pwd)"
UI="$ROOT/tools/ui-smoke/ui_node.py"
OUT='/tmp/tracker-phase3-alert-s22-detection-off-accept-v2'
XML="$OUT/ui.xml"
rm -rf "$OUT" && mkdir -p "$OUT"

test "$(git rev-parse HEAD)" = "$EXPECTED"
test "$(adb devices | awk 'NR>1 && $2=="device" {n++} END {print n+0}')" -eq 1
test "$(adb devices | awk 'NR>1 && $2=="device" {print $1}' | head -n1)" = "$SERIAL"
model="$(adb -s "$SERIAL" shell getprop ro.product.model | tr -d '\r')"
echo "device_model=$model"
case "$model" in SM-S906*) ;; *) echo unexpected_device_model; exit 3;; esac

dump_ui() {
  adb -s "$SERIAL" shell uiautomator dump /sdcard/blueeye-accept-ui.xml >/dev/null
  adb -s "$SERIAL" exec-out cat /sdcard/blueeye-accept-ui.xml > "$XML"
}
coords_from_dump() {
  python3 "$UI" "$XML" "$1" "$2" ${3:-}
}
wait_and_dump_for() {
  local attr="$1" value="$2"
  for _ in $(seq 1 12); do
    dump_ui
    if python3 "$UI" "$XML" "$attr" "$value" >/dev/null 2>&1; then return 0; fi
    sleep 0.25
  done
  echo "missing_target=$attr:$value" >&2
  return 1
}
switch_from_dump() {
  local label="$1"
  local x y
  read -r x y < <(python3 "$UI" "$XML" text "$label" --nearest-checkable)
  local state
  state=$(python3 - "$XML" "$x" "$y" <<'PY'
import re,sys,xml.etree.ElementTree as ET
path,x,y=sys.argv[1],int(sys.argv[2]),int(sys.argv[3])
best=None
for n in ET.parse(path).iter('node'):
    if n.attrib.get('checkable')!='true':
        continue
    m=re.fullmatch(r'\[(\d+),(\d+)\]\[(\d+),(\d+)\]', n.attrib.get('bounds',''))
    if not m:
        continue
    x1,y1,x2,y2=map(int,m.groups())
    if x1<=x<=x2 and y1<=y<=y2:
        area=(x2-x1)*(y2-y1)
        if best is None or area<best[0]:
            best=(area,n.attrib.get('checked','false'),n.attrib.get('enabled','false'))
if best is None:
    raise SystemExit(2)
print(best[1], best[2], x, y)
PY
)
  printf '%s\n' "$state"
}
set_switch() {
  local label="$1" desired="$2"
  wait_and_dump_for text "$label"
  local state enabled x y
  read -r state enabled x y < <(switch_from_dump "$label")
  echo "switch_before label=$label checked=$state enabled=$enabled"
  test "$enabled" = true
  if [[ "$state" != "$desired" ]]; then
    adb -s "$SERIAL" shell input tap "$x" "$y"
    sleep 0.55
    wait_and_dump_for text "$label"
    read -r state enabled x y < <(switch_from_dump "$label")
  fi
  echo "switch_after label=$label checked=$state enabled=$enabled"
  test "$state" = "$desired"
}
notif_dump() { adb -s "$SERIAL" shell dumpsys notification --noredact; }
alert_present() { grep -Fq 'BlueEye test alert' "$1"; }

adb -s "$SERIAL" shell input keyevent KEYCODE_WAKEUP >/dev/null 2>&1 || true
adb -s "$SERIAL" shell wm dismiss-keyguard >/dev/null 2>&1 || true
adb -s "$SERIAL" shell am force-stop "$PKG" >/dev/null 2>&1 || true
adb -s "$SERIAL" shell am start -W -n "$PKG/.MainActivity" >/dev/null
wait_and_dump_for content-desc 'Menu'
read -r x y < <(coords_from_dump content-desc 'Menu')
adb -s "$SERIAL" shell input tap "$x" "$y"; sleep 0.4
wait_and_dump_for text 'Settings'
read -r x y < <(coords_from_dump text 'Settings')
adb -s "$SERIAL" shell input tap "$x" "$y"; sleep 0.4
wait_and_dump_for text 'Alerts & Collection'
read -r x y < <(coords_from_dump text 'Alerts & Collection')
adb -s "$SERIAL" shell input tap "$x" "$y"; sleep 0.5
wait_and_dump_for text 'Tracker Detection'

read -r ORIG_DET _ _ _ < <(switch_from_dump 'Tracker Detection')
read -r ORIG_VIB _ _ _ < <(switch_from_dump 'Vibration') || ORIG_VIB=false
read -r ORIG_SOUND _ _ _ < <(switch_from_dump 'Sound') || ORIG_SOUND=false
read -r ORIG_HEADS _ _ _ < <(switch_from_dump 'Heads-Up Notification') || ORIG_HEADS=false
echo "original_settings detection=$ORIG_DET vibration=$ORIG_VIB sound=$ORIG_SOUND headsUp=$ORIG_HEADS"

restore() {
  set +e
  for _ in $(seq 1 6); do adb -s "$SERIAL" shell input swipe 540 520 540 1800 120 >/dev/null 2>&1 || true; done
  sleep 0.3
  set_switch 'Tracker Detection' true >/dev/null 2>&1 || true
  set_switch 'Vibration' "$ORIG_VIB" >/dev/null 2>&1 || true
  set_switch 'Sound' "$ORIG_SOUND" >/dev/null 2>&1 || true
  set_switch 'Heads-Up Notification' "$ORIG_HEADS" >/dev/null 2>&1 || true
  set_switch 'Tracker Detection' "$ORIG_DET" >/dev/null 2>&1 || true
}
trap restore EXIT

# Master first: child switches are disabled while detection is OFF.
set_switch 'Tracker Detection' true
set_switch 'Vibration' true
set_switch 'Sound' true
set_switch 'Heads-Up Notification' true

# Capture master-switch coordinates while at the top. They remain stable after returning to top.
wait_and_dump_for text 'Tracker Detection'
read -r det_state det_enabled DET_X DET_Y < <(switch_from_dump 'Tracker Detection')
test "$det_state" = true
test "$det_enabled" = true

# Scroll to the test-alert action and remember how many gestures are needed to return.
scrolls=0
while true; do
  dump_ui
  if python3 "$UI" "$XML" text 'Test alert' >/dev/null 2>&1; then break; fi
  test "$scrolls" -lt 8
  adb -s "$SERIAL" shell input swipe 540 1700 540 500 140 >/dev/null
  scrolls=$((scrolls+1))
  sleep 0.12
done
read -r TEST_X TEST_Y < <(coords_from_dump text 'Test alert')
echo "test_alert_scrolls=$scrolls"

notif_dump > "$OUT/before-notification.txt"
if alert_present "$OUT/before-notification.txt"; then echo stale_test_alert_before >&2; exit 1; fi
adb -s "$SERIAL" shell dumpsys vibrator_manager > "$OUT/before-vibrator.txt" || true
adb -s "$SERIAL" shell dumpsys audio > "$OUT/before-audio.txt" || true

start_ms=$(python3 - <<'PY'
import time
print(int(time.time()*1000))
PY
)
adb -s "$SERIAL" shell input tap "$TEST_X" "$TEST_Y"
seen=0
for _ in $(seq 1 6); do
  notif_dump > "$OUT/during-notification.txt"
  if alert_present "$OUT/during-notification.txt"; then seen=1; break; fi
  sleep 0.04
done
test "$seen" -eq 1
seen_ms=$(python3 - <<'PY'
import time
print(int(time.time()*1000))
PY
)

# Return to top using coordinates only; no UI dumps on the cancellation path.
for _ in $(seq 1 "$scrolls"); do
  adb -s "$SERIAL" shell input swipe 540 520 540 1800 90 >/dev/null
done
adb -s "$SERIAL" shell input tap "$DET_X" "$DET_Y"
off_tap_ms=$(python3 - <<'PY'
import time
print(int(time.time()*1000))
PY
)
sleep 0.35
notif_dump > "$OUT/after-notification.txt"
if alert_present "$OUT/after-notification.txt"; then echo alert_still_present_after_detection_off >&2; exit 1; fi
pid="$(adb -s "$SERIAL" shell pidof "$PKG" | tr -d '\r')"
test -n "$pid"
adb -s "$SERIAL" shell dumpsys vibrator_manager > "$OUT/after-vibrator.txt" || true
adb -s "$SERIAL" shell dumpsys audio > "$OUT/after-audio.txt" || true

# Verify the UI state after cancellation, outside the latency-sensitive path.
wait_and_dump_for text 'Tracker Detection'
read -r final_state final_enabled _ _ < <(switch_from_dump 'Tracker Detection')
test "$final_state" = false
echo "alert_seen_latency_ms=$((seen_ms-start_ms))"
echo "detection_off_tap_latency_ms=$((off_tap_ms-start_ms))"
echo "process_alive_after_cancel=true"
echo "notification_cancelled_after_detection_off=true"
echo "evidence_dir=$OUT"
echo 'PHASE3_ALERT_S22_DETECTION_OFF_ACCEPTANCE_PASS'
