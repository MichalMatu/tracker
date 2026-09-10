#!/usr/bin/env bash
set -euo pipefail
EXPECTED='f7a2808a108ab817d03f83cd5e550d5651e6ed3f'
PKG='io.blueeye'
SERIAL='RFCT70L7E8J'
ROOT="$(pwd)"
UI="$ROOT/tools/ui-smoke/ui_node.py"
OUT='/tmp/tracker-phase3-alert-s22-detection-off-accept-v1'
XML="$OUT/ui.xml"
rm -rf "$OUT" && mkdir -p "$OUT"

test "$(git rev-parse HEAD)" = "$EXPECTED"
test "$(adb devices | awk 'NR>1 && $2=="device" {n++} END {print n+0}')" -eq 1
test "$(adb devices | awk 'NR>1 && $2=="device" {print $1}' | head -n1)" = "$SERIAL"
model="$(adb -s "$SERIAL" shell getprop ro.product.model | tr -d '\r')"
echo "device_model=$model"
case "$model" in SM-S906*) ;; *) exit 3;; esac

dump_ui() {
  adb -s "$SERIAL" shell uiautomator dump /sdcard/blueeye-accept-ui.xml >/dev/null
  adb -s "$SERIAL" exec-out cat /sdcard/blueeye-accept-ui.xml > "$XML"
}
node_coords() { python3 "$UI" "$XML" "$1" "$2" ${3:-}; }
refresh_top_coords() {
  dump_ui
  read -r DET_X DET_Y < <(node_coords text 'Tracker Detection' --nearest-checkable)
  read -r VIB_X VIB_Y < <(node_coords text 'Vibration' --nearest-checkable)
  read -r SOUND_X SOUND_Y < <(node_coords text 'Sound' --nearest-checkable)
  read -r HEAD_X HEAD_Y < <(node_coords text 'Heads-Up Notification' --nearest-checkable)
  read -r ORIG_DET ORIG_VIB ORIG_SOUND ORIG_HEADS < <(python3 - "$XML" "$DET_X" "$DET_Y" "$VIB_X" "$VIB_Y" "$SOUND_X" "$SOUND_Y" "$HEAD_X" "$HEAD_Y" <<'PY'
import re,sys,xml.etree.ElementTree as ET
path=sys.argv[1]; pts=[tuple(map(int,sys.argv[i:i+2])) for i in range(2,10,2)]
nodes=list(ET.parse(path).iter('node'))
def state(x,y):
    best=None
    for n in nodes:
        if n.attrib.get('checkable')!='true': continue
        m=re.fullmatch(r'\[(\d+),(\d+)\]\[(\d+),(\d+)\]',n.attrib.get('bounds',''))
        if not m: continue
        x1,y1,x2,y2=map(int,m.groups())
        if x1<=x<=x2 and y1<=y<=y2:
            area=(x2-x1)*(y2-y1)
            if best is None or area<best[0]: best=(area,n.attrib.get('checked','false'))
    if best is None: raise SystemExit(2)
    return best[1]
print(*(state(x,y) for x,y in pts))
PY
)
}
set_top_state() {
  local current="$1" desired="$2" x="$3" y="$4"
  if [[ "$current" != "$desired" ]]; then adb -s "$SERIAL" shell input tap "$x" "$y" >/dev/null; sleep 0.12; fi
}
restore() {
  set +e
  for _ in $(seq 1 6); do adb -s "$SERIAL" shell input swipe 540 500 540 1900 120 >/dev/null 2>&1 || true; done
  sleep 0.3
  refresh_top_coords >/dev/null 2>&1 || return 0
  set_top_state "$ORIG_DET" "$SAVED_DET" "$DET_X" "$DET_Y"
  set_top_state "$ORIG_VIB" "$SAVED_VIB" "$VIB_X" "$VIB_Y"
  set_top_state "$ORIG_SOUND" "$SAVED_SOUND" "$SOUND_X" "$SOUND_Y"
  set_top_state "$ORIG_HEADS" "$SAVED_HEADS" "$HEAD_X" "$HEAD_Y"
}

adb -s "$SERIAL" shell input keyevent KEYCODE_WAKEUP >/dev/null 2>&1 || true
adb -s "$SERIAL" shell wm dismiss-keyguard >/dev/null 2>&1 || true
adb -s "$SERIAL" shell am force-stop "$PKG" >/dev/null 2>&1 || true
adb -s "$SERIAL" shell am start -W -n "$PKG/.MainActivity" >/dev/null
sleep 0.4

dump_ui
read -r x y < <(node_coords content-desc 'Menu')
adb -s "$SERIAL" shell input tap "$x" "$y"; sleep 0.25
dump_ui
read -r x y < <(node_coords text 'Settings')
adb -s "$SERIAL" shell input tap "$x" "$y"; sleep 0.25
dump_ui
read -r x y < <(node_coords text 'Alerts & Collection')
adb -s "$SERIAL" shell input tap "$x" "$y"; sleep 0.35
refresh_top_coords
SAVED_DET="$ORIG_DET"; SAVED_VIB="$ORIG_VIB"; SAVED_SOUND="$ORIG_SOUND"; SAVED_HEADS="$ORIG_HEADS"
echo "original_settings detection=$SAVED_DET vibration=$SAVED_VIB sound=$SAVED_SOUND headsUp=$SAVED_HEADS"
trap restore EXIT
set_top_state "$ORIG_DET" true "$DET_X" "$DET_Y"
set_top_state "$ORIG_VIB" true "$VIB_X" "$VIB_Y"
set_top_state "$ORIG_SOUND" true "$SOUND_X" "$SOUND_Y"
set_top_state "$ORIG_HEADS" true "$HEAD_X" "$HEAD_Y"
sleep 0.35
refresh_top_coords
test "$ORIG_DET" = true; test "$ORIG_VIB" = true; test "$ORIG_SOUND" = true; test "$ORIG_HEADS" = true

adb -s "$SERIAL" shell dumpsys notification --noredact > "$OUT/pre.txt"
SCANNER_BEFORE=0; grep -Fq 'BlueEye Active' "$OUT/pre.txt" && SCANNER_BEFORE=1 || true
scrolls=0
while true; do
  dump_ui
  if read -r TEST_X TEST_Y < <(node_coords text 'Test alert' 2>/dev/null); then break; fi
  test "$scrolls" -lt 6
  adb -s "$SERIAL" shell input swipe 540 1750 540 650 140 >/dev/null
  scrolls=$((scrolls+1))
  sleep 0.08
done
echo "scrolls_to_test=$scrolls"
adb -s "$SERIAL" shell input tap "$TEST_X" "$TEST_Y"
seen=0
for _ in $(seq 1 10); do
  adb -s "$SERIAL" shell dumpsys notification --noredact > "$OUT/during-notification.txt"
  if grep -Fq 'BlueEye test alert' "$OUT/during-notification.txt"; then seen=1; break; fi
  sleep 0.04
done
test "$seen" -eq 1
adb -s "$SERIAL" shell dumpsys vibrator_manager > "$OUT/during-vibrator.txt" || true
adb -s "$SERIAL" shell dumpsys audio > "$OUT/during-audio.txt" || true
start_ms=$(python3 - <<'PY'
import time
print(int(time.time()*1000))
PY
)
for _ in $(seq 1 "$scrolls"); do adb -s "$SERIAL" shell input swipe 540 650 540 1750 100 >/dev/null; done
adb -s "$SERIAL" shell input tap "$DET_X" "$DET_Y" >/dev/null
sleep 0.12
end_ms=$(python3 - <<'PY'
import time
print(int(time.time()*1000))
PY
)
echo "off_action_window_ms=$((end_ms-start_ms))"
adb -s "$SERIAL" shell dumpsys notification --noredact > "$OUT/after-notification.txt"
adb -s "$SERIAL" shell dumpsys vibrator_manager > "$OUT/after-vibrator.txt" || true
adb -s "$SERIAL" shell dumpsys audio > "$OUT/after-audio.txt" || true
if grep -Fq 'BlueEye test alert' "$OUT/after-notification.txt"; then echo alert_still_present; exit 1; fi
test -n "$(adb -s "$SERIAL" shell pidof "$PKG" | tr -d '\r')"
if [[ "$SCANNER_BEFORE" -eq 1 ]]; then grep -Fq 'BlueEye Active' "$OUT/after-notification.txt"; echo scanner_foreground_preserved=PASS; else echo scanner_foreground_precondition=absent; fi

dump_ui
read -r NOW_DET _ _ _ < <(python3 - "$XML" "$DET_X" "$DET_Y" "$VIB_X" "$VIB_Y" "$SOUND_X" "$SOUND_Y" "$HEAD_X" "$HEAD_Y" <<'PY'
import re,sys,xml.etree.ElementTree as ET
path=sys.argv[1]; pts=[tuple(map(int,sys.argv[i:i+2])) for i in range(2,10,2)]; nodes=list(ET.parse(path).iter('node'))
def state(x,y):
    best=None
    for n in nodes:
        if n.attrib.get('checkable')!='true': continue
        m=re.fullmatch(r'\[(\d+),(\d+)\]\[(\d+),(\d+)\]',n.attrib.get('bounds',''))
        if not m: continue
        x1,y1,x2,y2=map(int,m.groups())
        if x1<=x<=x2 and y1<=y<=y2:
            area=(x2-x1)*(y2-y1)
            if best is None or area<best[0]: best=(area,n.attrib.get('checked','false'))
    if best is None: raise SystemExit(2)
    return best[1]
print(*(state(x,y) for x,y in pts))
PY
)
test "$NOW_DET" = false
echo 'PHASE3_ALERT_S22_DETECTION_OFF_ACCEPTANCE_PASS'
