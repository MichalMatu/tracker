#!/usr/bin/env bash
set -euo pipefail

ROOT=$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)
OUT="$ROOT/build/ui-smoke"
XML="$OUT/current.xml"
mkdir -p "$OUT"
rm -f "$OUT"/*.png "$OUT"/*.xml "$OUT"/*.log 2>/dev/null || true

log() { printf '[ui-smoke] %s\n' "$*"; }

dump_ui() {
  adb shell uiautomator dump /sdcard/blueeye-ui.xml >/dev/null
  adb exec-out cat /sdcard/blueeye-ui.xml > "$XML"
}

coords() {
  local attribute=$1
  local value=$2
  local mode=${3:-clickable}
  dump_ui
  if [[ "$mode" == "raw" ]]; then
    python3 "$ROOT/tools/ui-smoke/ui_node.py" "$XML" "$attribute" "$value" --raw
  else
    python3 "$ROOT/tools/ui-smoke/ui_node.py" "$XML" "$attribute" "$value"
  fi
}

wait_target() {
  local attribute=$1
  local value=$2
  for _ in $(seq 1 25); do
    if coords "$attribute" "$value" >/dev/null 2>&1; then
      return 0
    fi
    sleep 1
  done
  log "missing target: $attribute=$value"
  dump_ui || true
  cp "$XML" "$OUT/failure.xml" || true
  adb exec-out screencap -p > "$OUT/failure.png" 2>/dev/null || true
  adb logcat -d > "$OUT/failure-logcat.log" 2>/dev/null || true
  return 1
}

tap_target() {
  local attribute=$1
  local value=$2
  wait_target "$attribute" "$value"
  read -r x y < <(coords "$attribute" "$value")
  log "tap $attribute=$value at $x,$y"
  adb shell input tap "$x" "$y"
  sleep 1
  assert_alive
}

tap_text() { tap_target text "$1"; }
tap_desc() { tap_target content-desc "$1"; }

screen_width() {
  adb shell wm size | sed -n 's/.*Physical size: \([0-9]*\)x.*/\1/p' | head -1
}

tap_switch_for_text() {
  local label=$1
  wait_target text "$label"
  read -r _ y < <(coords text "$label" raw)
  local width
  width=$(screen_width)
  local x=$((width - 72))
  log "tap switch for '$label' at $x,$y"
  adb shell input tap "$x" "$y"
  sleep 1
  assert_alive
}

scroll_until() {
  local text=$1
  for _ in $(seq 1 8); do
    if coords text "$text" >/dev/null 2>&1; then
      return 0
    fi
    adb shell input swipe 540 1700 540 550 350
    sleep 1
  done
  log "could not scroll to text=$text"
  return 1
}

shot() {
  local name=$1
  dump_ui
  adb exec-out screencap -p > "$OUT/$name.png"
  cp "$XML" "$OUT/$name.xml"
  log "captured $name"
}

assert_alive() {
  if ! adb shell pidof io.blueeye >/dev/null 2>&1; then
    adb logcat -d > "$OUT/process-death.log" || true
    log "application process is not alive"
    return 1
  fi
}

back() {
  adb shell input keyevent BACK
  sleep 1
  assert_alive
}

log "build app and instrumentation seed"
"$ROOT/gradlew" --parallel --max-workers=3 :app:assembleDebug :app:assembleDebugAndroidTest
APP_APK=$(find "$ROOT/app/build/outputs/apk/debug" -name '*.apk' | head -1)
TEST_APK=$(find "$ROOT/app/build/outputs/apk/androidTest/debug" -name '*.apk' | head -1)

test -n "$APP_APK"
test -n "$TEST_APK"
adb install -r "$APP_APK"
adb install -r "$TEST_APK"
adb shell pm clear io.blueeye >/dev/null

log "seed deterministic Room fixtures"
adb shell am instrument -w \
  -e class io.blueeye.UiSmokeSeedInstrumentedTest#seedUiSmokeDevices \
  io.blueeye.test/androidx.test.runner.AndroidJUnitRunner | tee "$OUT/seed.log"
grep -q 'OK (1 test)' "$OUT/seed.log"

for permission in \
  android.permission.BLUETOOTH_SCAN \
  android.permission.BLUETOOTH_CONNECT \
  android.permission.ACCESS_FINE_LOCATION \
  android.permission.ACCESS_COARSE_LOCATION \
  android.permission.POST_NOTIFICATIONS; do
  adb shell pm grant io.blueeye "$permission" 2>/dev/null || true
done

adb logcat -c
adb shell am start -W -n io.blueeye/.MainActivity | tee "$OUT/launch.log"
wait_target text "Walk bag tag"
assert_alive
shot 01-radar-seeded

# Radar top-bar controls and dialogs.
tap_desc "Baseline"
shot 02-radar-baseline

tap_desc "Filter"
wait_target text "Filter Devices"
shot 03-radar-filter-dialog
tap_text "Apply"

tap_desc "Start active GATT collection"
wait_target text "Start active GATT collection?"
shot 04-radar-active-gatt-dialog
tap_text "Cancel"

tap_desc "Calibrate"
wait_target text "Suspicious"
shot 05-radar-calibration-menu
tap_text "Suspicious"

# Open first visible Details action.
tap_desc "Details"
wait_target content-desc "Edit"
shot 06-details

tap_desc "Edit"
wait_target text "Edit Device"
shot 07-details-edit
tap_switch_for_text "Alert Sound"
tap_switch_for_text "Alert Vibration"
tap_text "Save"

tap_desc "Raw Data"
wait_target text "Copy Raw Data"
shot 08-details-raw-data
tap_text "Copy Raw Data"
tap_text "Export JSON to Clipboard"
tap_text "Close"

tap_desc "Refresh focused scan"
sleep 1
back
wait_target content-desc "Menu"

# Add one non-watchlist card, then exercise Watchlist.
if coords content-desc "Watch device" >/dev/null 2>&1; then
  tap_desc "Watch device"
fi
tap_desc "Menu"
tap_text "Watchlist"
wait_target text "Watchlist"
shot 09-watchlist
if coords text "Signal Hints" >/dev/null 2>&1; then
  tap_switch_for_text "Signal Hints"
fi
if coords content-desc "Remove from Watchlist" >/dev/null 2>&1; then
  tap_desc "Remove from Watchlist"
fi
shot 10-watchlist-after-controls

# Settings main and subsections.
tap_desc "Menu"
tap_text "Settings"
wait_target text "Settings"
shot 11-settings-main

tap_text "Alerts & Collection"
wait_target text "Tracker Detection"
shot 12-settings-alerts
for label in "Vibration" "Sound" "Heads-Up Notification" "Tracker Detection"; do
  if coords text "$label" >/dev/null 2>&1; then
    tap_switch_for_text "$label"
  fi
done
if scroll_until "Test alert"; then
  tap_text "Test alert"
fi
back
wait_target text "Appearance"

tap_text "Appearance"
wait_target text "Theme Mode"
shot 13-settings-appearance
for label in Light Dark System Tactical Midnight Forest Classic; do
  if coords text "$label" >/dev/null 2>&1; then
    tap_text "$label"
  fi
done
if coords text "Dynamic Colors (Material You)" >/dev/null 2>&1; then
  tap_switch_for_text "Dynamic Colors (Material You)"
fi
shot 14-settings-appearance-after-controls
back
wait_target text "Database & Updates"

tap_text "Database & Updates"
wait_target text "Last updated: Never"
shot 15-settings-database
if scroll_until "Start new session"; then
  tap_text "Start new session"
fi
if scroll_until "Update All Databases"; then
  shot 16-settings-database-actions
fi
if scroll_until "Copy"; then
  tap_text "Copy"
fi
if coords text "Share" >/dev/null 2>&1; then
  tap_text "Share"
  sleep 1
  adb exec-out screencap -p > "$OUT/17-share-sheet.png"
  back
fi

# Return to Radar and exercise destructive clear paths last.
back
wait_target content-desc "Menu"
tap_desc "Menu"
tap_text "Radar"
wait_target content-desc "Clear"

tap_desc "Clear"
wait_target text "Delete all data?"
shot 18-clear-dialog
tap_text "Keep Watchlist"
wait_target content-desc "Clear"
shot 19-radar-keep-watchlist

tap_desc "Clear"
wait_target text "Delete All"
tap_text "Delete All"
sleep 1
shot 20-radar-empty

# Hardware-dependent scan action is intentionally last; verify it cannot crash the app on emulator.
tap_desc "Scan"
sleep 2
shot 21-radar-after-scan-action
assert_alive

adb logcat -d > "$OUT/logcat.txt"
if grep -E 'FATAL EXCEPTION|Process: io\.blueeye.*FATAL' "$OUT/logcat.txt"; then
  log "fatal exception detected"
  exit 1
fi

log "Android UI smoke completed successfully"
