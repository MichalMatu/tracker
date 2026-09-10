#!/usr/bin/env bash
set -euo pipefail
EXPECTED='f7a2808a108ab817d03f83cd5e550d5651e6ed3f'
PKG='io.blueeye'
SERIAL='RFCT70L7E8J'
ROOT="$(pwd)"
UI="$ROOT/tools/ui-smoke/ui_node.py"
OUT='/tmp/tracker-phase3-alert-s22-accept-v2'
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
coords() {
  local attr="$1" value="$2" extra="${3:-}"
  dump_ui
  if [[ -n "$extra" ]]; then python3 "$UI" "$XML" "$attr" "$value" "$extra"; else python3 "$UI" "$XML" "$attr" "$value"; fi
}
wait_target() {
  local attr="$1" value="$2"
  for _ in $(seq 1 20); do
    if coords "$attr" "$value" >/dev/null 2>&1; then return 0; fi
    sleep 0.25
  done
  echo "missing_target=$attr:$value" >&2
  return 1
}
tap_target() {
  local attr="$1" value="$2"
  wait_target "$attr" "$value"
  read -r x y < <(coords "$attr" "$value")
  adb -s "$SERIAL" shell input tap "$x" "$y"
  sleep 0.35
}
switch_info() {
  local label="$1"
  wait_target text "$label"
  dump_ui
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
    m=re.fullmatch(r'\[(\d+),(\d+)\]\[(\d+),(\d+)\]',n.attrib.get('bounds',''))
    if not m:
        continue
    x1,y1,x2,y2=map(int,m.groups())
    if x1<=x<=x2 and y1<=y<=y2:
        area=(x2-x1)*(y2-y1)
        if best is None or area<best[0]:
            best=(area,n.attrib.get('checked','false'))
if best is None:
    raise SystemExit(2)
print(best[1])
PY
)
  printf '%s %s %s\n' "$state" "$x" "$y"
}
ensure_switch() {
  local label="$1" desired="$2"
  local state x y
  read -r state x y < <(switch_info "$label")
  if [[ "$state" != "$desired" ]]; then
    adb -s "$SERIAL" shell input tap "$x" "$y"
    sleep 0.45
    read -r state x y < <(switch_info "$label")
  fi
  test "$state" = "$desired"
}
notif_dump() { adb -s "$SERIAL" shell dumpsys notification --noredact; }
alert_present() { grep -Fq 'BlueEye test alert' "$1"; }
clear_alert_via_detection_cycle() {
  ensure_switch 'Tracker Detection' false
  sleep 0.25
  notif_dump > "$OUT/clear-check.txt"
  if alert_present "$OUT/clear-check.txt"; then echo alert_not_cleared >&2; return 1; fi
  ensure_switch 'Tracker Detection' true
}
scroll_to_test() {
  local n=0
  while ! coords text 'Test alert' >/dev/null 2>&1; do
    test "$n" -lt 8
    adb -s "$SERIAL" shell input swipe 540 1700 540 550 220
    n=$((n+1))
    sleep 0.12
  done
  read -r tx ty < <(coords text 'Test alert')
  printf '%s %s %s\n' "$n" "$tx" "$ty"
}
return_to_top_fast() {
  local n="$1"
  for _ in $(seq 1 "$n"); do adb -s "$SERIAL" shell input swipe 540 550 540 1700 180 >/dev/null; done
  sleep 0.12
}
run_case() {
  local case_name="$1" label="$2" expect_after="$3"
  echo "CASE=$case_name"
  ensure_switch 'Tracker Detection' true
  ensure_switch 'Sound' true
  ensure_switch 'Vibration' true
  ensure_switch 'Heads-Up Notification' true
  clear_alert_via_detection_cycle
  local state toggle_x toggle_y
  read -r state toggle_x toggle_y < <(switch_info "$label")
  test "$state" = true
  local scrolls tx ty
  read -r scrolls tx ty < <(scroll_to_test)
  notif_dump > "$OUT/${case_name}-before-notification.txt"
  adb -s "$SERIAL" shell input tap "$tx" "$ty"
  sleep 0.12
  local seen=0
  for _ in $(seq 1 8); do
    notif_dump > "$OUT/${case_name}-during-notification.txt"
    if alert_present "$OUT/${case_name}-during-notification.txt"; then seen=1; break; fi
    sleep 0.05
  done
  test "$seen" -eq 1
  adb -s "$SERIAL" shell dumpsys vibrator_manager > "$OUT/${case_name}-during-vibrator.txt" || true
  adb -s "$SERIAL" shell dumpsys audio > "$OUT/${case_name}-during-audio.txt" || true
  return_to_top_fast "$scrolls"
  adb -s "$SERIAL" shell input tap "$toggle_x" "$toggle_y"
  sleep 0.28
  read -r state _ _ < <(switch_info "$label")
  test "$state" = false
  notif_dump > "$OUT/${case_name}-after-notification.txt"
  adb -s "$SERIAL" shell dumpsys vibrator_manager > "$OUT/${case_name}-after-vibrator.txt" || true
  adb -s "$SERIAL" shell dumpsys audio > "$OUT/${case_name}-after-audio.txt" || true
  if [[ "$expect_after" == absent ]]; then
    if alert_present "$OUT/${case_name}-after-notification.txt"; then echo "$case_name alert_still_present" >&2; return 1; fi
  else
    alert_present "$OUT/${case_name}-after-notification.txt"
  fi
  test -n "$(adb -s "$SERIAL" shell pidof "$PKG" | tr -d '\r')"
  echo "${case_name}_notification_policy=PASS"
  if [[ "$label" != 'Tracker Detection' ]]; then clear_alert_via_detection_cycle; fi
  ensure_switch "$label" true
}

adb -s "$SERIAL" shell input keyevent KEYCODE_WAKEUP >/dev/null 2>&1 || true
adb -s "$SERIAL" shell wm dismiss-keyguard >/dev/null 2>&1 || true
adb -s "$SERIAL" shell am force-stop "$PKG" >/dev/null 2>&1 || true
adb -s "$SERIAL" shell am start -W -n "$PKG/.MainActivity" >/dev/null
wait_target content-desc 'Menu'
tap_target content-desc 'Menu'
tap_target text 'Settings'
tap_target text 'Alerts & Collection'
wait_target text 'Tracker Detection'

read -r ORIG_DET _ _ < <(switch_info 'Tracker Detection')
read -r ORIG_VIB _ _ < <(switch_info 'Vibration')
read -r ORIG_SOUND _ _ < <(switch_info 'Sound')
read -r ORIG_HEADS _ _ < <(switch_info 'Heads-Up Notification')
echo "original_settings detection=$ORIG_DET vibration=$ORIG_VIB sound=$ORIG_SOUND headsUp=$ORIG_HEADS"
restore() {
  set +e
  for _ in $(seq 1 8); do
    if coords text 'Tracker Detection' >/dev/null 2>&1; then break; fi
    adb -s "$SERIAL" shell input swipe 540 550 540 1700 220 >/dev/null 2>&1 || true
  done
  ensure_switch 'Tracker Detection' "$ORIG_DET" >/dev/null 2>&1 || true
  ensure_switch 'Vibration' "$ORIG_VIB" >/dev/null 2>&1 || true
  ensure_switch 'Sound' "$ORIG_SOUND" >/dev/null 2>&1 || true
  ensure_switch 'Heads-Up Notification' "$ORIG_HEADS" >/dev/null 2>&1 || true
}
trap restore EXIT

run_case detection-off 'Tracker Detection' absent
run_case sound-off 'Sound' present
run_case vibration-off 'Vibration' present

notif_dump > "$OUT/final-notification.txt"
if alert_present "$OUT/final-notification.txt"; then echo final_alert_leak >&2; exit 1; fi
printf 'vibrator_evidence_files=%s\n' "$OUT/*-vibrator.txt"
printf 'audio_evidence_files=%s\n' "$OUT/*-audio.txt"
echo 'PHASE3_ALERT_S22_NOTIFICATION_POLICY_ACCEPTANCE_PASS'
