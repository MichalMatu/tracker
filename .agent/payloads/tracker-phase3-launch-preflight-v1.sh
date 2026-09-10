#!/usr/bin/env bash
set -u
EXPECTED='53cccf4fa39525febf54e0a57f65796efbd51812'
PKG='io.blueeye'
SERIAL_EXPECTED='RFCT70L7E8J'

git fetch --no-tags origin main >/dev/null || exit 10
HEAD="$(git rev-parse origin/main)"
echo "source_head=$HEAD"
test "$HEAD" = "$EXPECTED" || exit 11
SERIAL="$(adb devices | awk 'NR>1 && $2=="device" {print $1}' | head -n1)"
echo "serial=${SERIAL:-none}"
test "$SERIAL" = "$SERIAL_EXPECTED" || exit 12

echo '--- package path ---'
adb -s "$SERIAL" shell pm path --user 0 "$PKG" 2>&1 || true
echo '--- current user ---'
adb -s "$SERIAL" shell am get-current-user 2>&1 || true
echo '--- launch ---'
set +e
LAUNCH_OUT="$(adb -s "$SERIAL" shell am start -W --user 0 -n "$PKG/.MainActivity" 2>&1)"
RC=$?
set -e
echo "launch_rc=$RC"
printf '%s\n' "$LAUNCH_OUT"
sleep 2
echo "pid=$(adb -s "$SERIAL" shell pidof "$PKG" 2>/dev/null | tr -d '\r' || true)"
echo '--- foreground ---'
adb -s "$SERIAL" shell dumpsys window windows 2>/dev/null | grep -E 'mCurrentFocus|mFocusedApp' | head -n 5 || true
echo '--- package summary ---'
adb -s "$SERIAL" shell dumpsys package "$PKG" 2>/dev/null | grep -E 'versionName=|versionCode=|User 0:|enabled=' | head -n 20 || true
if [ "$RC" -ne 0 ]; then exit 20; fi
if ! adb -s "$SERIAL" shell pidof "$PKG" >/dev/null 2>&1; then exit 21; fi
echo 'PHASE3_LAUNCH_PREFLIGHT_PASS'
