#!/usr/bin/env bash
set -euo pipefail
PKG='io.blueeye'
SERIAL_EXPECTED='RFCT70L7E8J'
EXPECTED='4e3751598f9df679cd7e8e55482e17fb35fdc622'

git fetch --no-tags origin main >/dev/null
HEAD="$(git rev-parse origin/main)"
echo "source_head=$HEAD"
test "$HEAD" = "$EXPECTED"

adb start-server >/dev/null
SERIAL="$(adb devices | awk 'NR>1 && $2=="device" {print $1}' | head -n1)"
echo "serial=$SERIAL"
test "$SERIAL" = "$SERIAL_EXPECTED"

echo '--- package/process state ---'
adb -s "$SERIAL" shell pidof "$PKG" || true
adb -s "$SERIAL" shell dumpsys activity services "$PKG" | sed -n '1,220p' || true

echo '--- notification/channel state ---'
adb -s "$SERIAL" shell dumpsys notification --noredact 2>/dev/null \
  | grep -i -E -C 8 'io\.blueeye|field_mvp_alerts|BlueEye|urgent alerts|alert tray' \
  | tail -n 800 || true

echo '--- relevant logcat ---'
adb -s "$SERIAL" logcat -d -v threadtime 2>/dev/null \
  | grep -i -E 'io\.blueeye|BlueEye|ScannerService|AndroidAlertDispatcher|TrackerAlert|TacticalAlert|PublicSafety|Follow.?Me|ShadowMatch|Ringtone|AudioService|NotificationService|NotificationManager|Vibrator|FATAL EXCEPTION|AndroidRuntime' \
  | tail -n 3500 || true

echo 'PHASE3_FIELD_LOG_AUDIT_DONE'
