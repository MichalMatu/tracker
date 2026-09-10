#!/usr/bin/env bash
set -euo pipefail

EXPECTED_SHA="1908b48ef35c3ea564e8276ee04c10a00f0d8dd8"
ROOT=$(git rev-parse --show-toplevel)
OUT="$ROOT/build/phase3-alert-s22-accept"
XML="$OUT/current.xml"
mkdir -p "$OUT"
rm -f "$OUT"/* 2>/dev/null || true

log() { printf '[alert-accept] %s\n' "$*"; }

log "verify exact source"
git fetch origin main >/dev/null
ACTUAL_ORIGIN=$(git rev-parse origin/main)
ACTUAL_HEAD=$(git rev-parse HEAD)
printf 'origin_main=%s\nhead=%s\n' "$ACTUAL_ORIGIN" "$ACTUAL_HEAD"
test "$ACTUAL_ORIGIN" = "$EXPECTED_SHA"
test "$ACTUAL_HEAD" = "$EXPECTED_SHA"
test -z "$(git status --porcelain)"

log "verify single Samsung device"
adb start-server >/dev/null
adb devices -l | tee "$OUT/adb-devices.txt"
DEVICE_COUNT=$(adb devices | awk 'NR>1 && $2=="device" {n++} END {print n+0}')
test "$DEVICE_COUNT" -eq 1
MANUFACTURER=$(adb shell getprop ro.product.manufacturer | tr -d '\r')
MODEL=$(adb shell getprop ro.product.model | tr -d '\r')
printf 'manufacturer=%s\nmodel=%s\n' "$MANUFACTURER" "$MODEL" | tee "$OUT/device.txt"
printf '%s' "$MANUFACTURER" | grep -Eiq '^samsung$'

export JAVA_HOME="$(/usr/libexec/java_home -v 21)"
export PATH="$JAVA_HOME/bin:$PATH"
log "build exact debug APK"
./gradlew --parallel --max-workers=3 :app:assembleDebug --console=plain | tee "$OUT/build.log"
APP_APK=$(find "$ROOT/app/build/outputs/apk/debug" -name '*.apk' | head -1)
test -n "$APP_APK"
shasum -a 256 "$APP_APK" | tee "$OUT/apk.sha256"

log "install without clearing app data"
adb install -r "$APP_APK" | tee "$OUT/install.log"
for permission in \
  android.permission.BLUETOOTH_SCAN \
  android.permission.BLUETOOTH_CONNECT \
  android.permission.ACCESS_FINE_LOCATION \
  android.permission.ACCESS_COARSE_LOCATION \
  android.permission.POST_NOTIFICATIONS; do
  adb shell pm grant io.blueeye "$permission" 2>/dev/null || true
done

adb shell input keyevent KEYCODE_WAKEUP >/dev/null 2>&1 || true
adb shell wm dismiss-keyguard >/dev/null 2>&1 || true
adb shell am start -W -n io.blueeye/.MainActivity | tee "$OUT/launch.log"
sleep 2

cat > /tmp/blueeye-switch-state.py <<'PY'
import re, sys, xml.etree.ElementTree as ET
xml_path, label = sys.argv[1], sys.argv[2]
root = ET.parse(xml_path).getroot()
bounds_re = re.compile(r"\[(\d+),(\d+)\]\[(\d+),(\d+)\]")
def center(node):
    m = bounds_re.fullmatch(node.attrib.get("bounds", ""))
    if not m: return None
    x1,y1,x2,y2 = map(int,m.groups())
    return ((x1+x2)//2,(y1+y2)//2)
labels=[n for n in root.iter('node') if n.attrib.get('text')==label]
if not labels: raise SystemExit(2)
lc=center(labels[0])
if not lc: raise SystemExit(3)
cands=[]
for n in root.iter('node'):
    if n.attrib.get('checkable')!='true': continue
    c=center(n)
    if not c: continue
    cands.append((abs(c[1]-lc[1]), abs(c[0]-lc[0]), n, c))
if not cands: raise SystemExit(4)
cands.sort(key=lambda x:(x[0],x[1]))
node,c=cands[0][2],cands[0][3]
print(node.attrib.get('checked','false'), c[0], c[1], node.attrib.get('enabled','true'))
PY

raw_dump_ui() {
  adb shell uiautomator dump /sdcard/blueeye-alert-accept.xml >/dev/null
  adb exec-out cat /sdcard/blueeye-alert-accept.xml > "$XML"
}

wait_attr() {
  local attr=$1 value=$2
  for _ in $(seq 1 20); do
    raw_dump_ui || true
    if python3 "$ROOT/tools/ui-smoke/ui_node.py" "$XML" "$attr" "$value" >/dev/null 2>&1; then return 0; fi
    sleep 1
  done
  return 1
}

tap_attr() {
  local attr=$1 value=$2
  wait_attr "$attr" "$value"
  local x y
  read -r x y < <(python3 "$ROOT/tools/ui-smoke/ui_node.py" "$XML" "$attr" "$value")
  log "tap $attr=$value at $x,$y"
  adb shell input tap "$x" "$y"
  sleep 1
}

tap_text() { tap_attr text "$1"; }
tap_desc() { tap_attr content-desc "$1"; }

scroll_down_until() {
  local text=$1
  for _ in $(seq 1 8); do
    raw_dump_ui
    if python3 "$ROOT/tools/ui-smoke/ui_node.py" "$XML" text "$text" >/dev/null 2>&1; then return 0; fi
    adb shell input swipe 540 1750 540 550 300
    sleep 0.5
  done
  return 1
}

scroll_up_until() {
  local text=$1
  for _ in $(seq 1 8); do
    raw_dump_ui
    if python3 "$ROOT/tools/ui-smoke/ui_node.py" "$XML" text "$text" >/dev/null 2>&1; then return 0; fi
    adb shell input swipe 540 550 540 1750 300
    sleep 0.5
  done
  return 1
}

switch_info() {
  local label=$1
  raw_dump_ui
  python3 /tmp/blueeye-switch-state.py "$XML" "$label"
}

set_switch() {
  local label=$1 desired=$2
  scroll_up_until "$label" || scroll_down_until "$label"
  local state x y enabled
  read -r state x y enabled < <(switch_info "$label")
  log "switch '$label' state=$state enabled=$enabled desired=$desired"
  if [[ "$state" != "$desired" ]]; then
    test "$enabled" = "true"
    adb shell input tap "$x" "$y"
    sleep 1
    scroll_up_until "$label" || scroll_down_until "$label"
    read -r state x y enabled < <(switch_info "$label")
    test "$state" = "$desired"
  fi
}

log "navigate to Alerts & Collection"
for _ in $(seq 1 5); do
  raw_dump_ui || true
  if python3 "$ROOT/tools/ui-smoke/ui_node.py" "$XML" content-desc "Menu" >/dev/null 2>&1; then break; fi
  adb shell input keyevent BACK
  sleep 0.5
done
wait_attr content-desc "Menu"
tap_desc "Menu"
tap_text "Settings"
wait_attr text "Settings"
tap_text "Alerts & Collection"
wait_attr text "Tracker Detection"

scroll_up_until "Tracker Detection"
read -r ORIG_DETECTION _ < <(switch_info "Tracker Detection")
read -r ORIG_VIBRATION _ < <(switch_info "Vibration")
read -r ORIG_SOUND _ < <(switch_info "Sound")
printf 'original_detection=%s\noriginal_vibration=%s\noriginal_sound=%s\n' "$ORIG_DETECTION" "$ORIG_VIBRATION" "$ORIG_SOUND" | tee "$OUT/original-settings.txt"

restore_settings() {
  set +e
  scroll_up_until "Tracker Detection" >/dev/null 2>&1
  set_switch "Tracker Detection" true >/dev/null 2>&1
  set_switch "Vibration" "$ORIG_VIBRATION" >/dev/null 2>&1
  set_switch "Sound" "$ORIG_SOUND" >/dev/null 2>&1
  set_switch "Tracker Detection" "$ORIG_DETECTION" >/dev/null 2>&1
  set -e
}
trap restore_settings EXIT

set_switch "Tracker Detection" true
set_switch "Vibration" true
set_switch "Sound" true

adb shell dumpsys activity services io.blueeye > "$OUT/services-before.txt" || true
adb shell dumpsys notification --noredact > "$OUT/notifications-before.txt" || true

log "fire Test alert"
scroll_down_until "Test alert"
tap_text "Test alert"
sleep 0.35
adb exec-out screencap -p > "$OUT/after-test-alert.png"
adb shell dumpsys notification --noredact > "$OUT/notifications-alert-active.txt" || true
adb shell dumpsys audio > "$OUT/audio-alert-active.txt" || true
adb shell dumpsys vibrator_manager > "$OUT/vibrator-alert-active.txt" 2>/dev/null || adb shell dumpsys vibrator > "$OUT/vibrator-alert-active.txt" 2>/dev/null || true
adb logcat -d -v brief > "$OUT/logcat-alert-active.txt" || true

# Active notification evidence must include the dispatcher channel while alert is active.
if ! grep -Eq 'field_mvp_alerts_(heads_up|tray)_v1' "$OUT/notifications-alert-active.txt"; then
  echo 'FAIL: dispatcher alert notification channel not observed after Test alert' >&2
  exit 21
fi

log "disable master Tracker Detection while alert side effects are active"
scroll_up_until "Tracker Detection"
set_switch "Tracker Detection" false
sleep 1
adb exec-out screencap -p > "$OUT/after-detection-off.png"
adb shell dumpsys notification --noredact > "$OUT/notifications-after-off.txt" || true
adb shell dumpsys audio > "$OUT/audio-after-off.txt" || true
adb shell dumpsys vibrator_manager > "$OUT/vibrator-after-off.txt" 2>/dev/null || adb shell dumpsys vibrator > "$OUT/vibrator-after-off.txt" 2>/dev/null || true
adb shell dumpsys activity services io.blueeye > "$OUT/services-after.txt" || true
adb logcat -d -v brief > "$OUT/logcat-after-off.txt" || true

read -r OFF_STATE _ < <(switch_info "Tracker Detection")
test "$OFF_STATE" = "false"

# Restrict notification check to active NotificationRecord entries, not channel configuration.
python3 - "$OUT/notifications-after-off.txt" <<'PY'
import re, sys
text=open(sys.argv[1],errors='replace').read()
records=re.findall(r'NotificationRecord\(.*?(?=\n\s*NotificationRecord\(|\n\s*Ranking Config|\Z)', text, flags=re.S)
bad=[r for r in records if 'io.blueeye' in r and ('field_mvp_alerts_heads_up_v1' in r or 'field_mvp_alerts_tray_v1' in r)]
if bad:
    print('FAIL: active dispatcher notification remains after detection OFF', file=sys.stderr)
    print(bad[0][:1500], file=sys.stderr)
    raise SystemExit(22)
print('active_dispatcher_notifications_after_off=0')
PY

# Surface concise audio/vibrator evidence for review. Different Samsung builds format these dumps differently.
printf '%s\n' '--- active audio alarm evidence ---'
grep -Ei -C 3 'USAGE_ALARM|usage.?alarm|Ringtone|io\.blueeye' "$OUT/audio-alert-active.txt" | head -n 120 || true
printf '%s\n' '--- audio after OFF evidence ---'
grep -Ei -C 3 'USAGE_ALARM|usage.?alarm|Ringtone|io\.blueeye' "$OUT/audio-after-off.txt" | head -n 120 || true
printf '%s\n' '--- active vibration evidence ---'
grep -Ei -C 3 'io\.blueeye|vibrat|active|running' "$OUT/vibrator-alert-active.txt" | head -n 120 || true
printf '%s\n' '--- vibration after OFF evidence ---'
grep -Ei -C 3 'io\.blueeye|vibrat|active|running' "$OUT/vibrator-after-off.txt" | head -n 120 || true

if grep -Eq 'FATAL EXCEPTION|Process: io\.blueeye.*FATAL' "$OUT/logcat-after-off.txt"; then
  echo 'FAIL: fatal exception observed' >&2
  exit 23
fi

echo 'ALERT_CANCELLATION_DEVICE_ACCEPTANCE=PASS'
echo "accepted_sha=$EXPECTED_SHA"
echo "device=$MANUFACTURER $MODEL"
