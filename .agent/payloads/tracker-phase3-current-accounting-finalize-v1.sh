#!/usr/bin/env bash
set -euo pipefail

EXPECTED='53cccf4fa39525febf54e0a57f65796efbd51812'
PKG='io.blueeye'
SERIAL_EXPECTED='RFCT70L7E8J'
TARGET_CERT='fff1ada9aa9e2709b4e4bda8333404feae854372df319b77f1f45d7869a61cb6'
TARGET_APK_SHA='3b88a37485cf3a500af8d2e33b38819370b0fb58a5fca8bd9e940e7aea10d8ca'
OUT='/tmp/tracker-phase3-current-accounting-finalize-v1'
rm -rf "$OUT" && mkdir -p "$OUT"

cleanup() {
  if [ -n "${SERIAL:-}" ]; then
    adb -s "$SERIAL" shell svc power stayon false >/dev/null 2>&1 || true
  fi
}
trap cleanup EXIT

git fetch --no-tags origin main >/dev/null
HEAD="$(git rev-parse origin/main)"
echo "source_head=$HEAD"
test "$HEAD" = "$EXPECTED"

SERIAL="$(adb devices | awk 'NR>1 && $2=="device" {print $1}' | head -n1)"
test "$SERIAL" = "$SERIAL_EXPECTED"
echo "serial=$SERIAL"
adb -s "$SERIAL" shell svc power stayon usb >/dev/null 2>&1 || true
adb -s "$SERIAL" shell input keyevent KEYCODE_WAKEUP >/dev/null 2>&1 || true
adb -s "$SERIAL" shell wm dismiss-keyguard >/dev/null 2>&1 || true

test "$(adb -s "$SERIAL" shell am get-current-user | tr -d '\r')" = '0'
APKSIGNER="$HOME/Library/Android/sdk/build-tools/36.0.0/apksigner"
if [ ! -x "$APKSIGNER" ]; then APKSIGNER="$(command -v apksigner || true)"; fi
test -x "$APKSIGNER"
CURRENT_PATH="$(adb -s "$SERIAL" shell pm path --user 0 "$PKG" | tr -d '\r' | sed -n 's/^package://p' | head -n1)"
test -n "$CURRENT_PATH"
adb -s "$SERIAL" pull "$CURRENT_PATH" "$OUT/installed.apk" >/dev/null
INSTALLED_SHA="$(shasum -a 256 "$OUT/installed.apk" | awk '{print $1}')"
INSTALLED_CERT="$("$APKSIGNER" verify --print-certs "$OUT/installed.apk" | awk -F': ' '/Signer #1 certificate SHA-256 digest:/{print $2; exit}' | tr 'A-F' 'a-f')"
echo "installed_apk_sha256=$INSTALLED_SHA"
echo "installed_cert_sha256=$INSTALLED_CERT"
test "$INSTALLED_SHA" = "$TARGET_APK_SHA"
test "$INSTALLED_CERT" = "$TARGET_CERT"

for permission in \
  android.permission.BLUETOOTH_SCAN \
  android.permission.BLUETOOTH_CONNECT \
  android.permission.ACCESS_FINE_LOCATION \
  android.permission.ACCESS_COARSE_LOCATION \
  android.permission.POST_NOTIFICATIONS
do
  adb -s "$SERIAL" shell pm grant --user 0 "$PKG" "$permission" >/dev/null 2>&1 || true
done

snapshot_db() {
  local label="$1"
  local db="$OUT/$label.db"
  adb -s "$SERIAL" shell run-as "$PKG" cp databases/tracker_database "cache/$label.db"
  adb -s "$SERIAL" exec-out run-as "$PKG" cat "cache/$label.db" > "$db"
  sqlite3 "$db" 'PRAGMA integrity_check; select count(*) from devices; select count(*) from signal_samples; select count(*) from follow_me_observations;' > "$OUT/$label.counts"
  echo "--- $label DB ---"
  cat "$OUT/$label.counts"
  test "$(head -n1 "$OUT/$label.counts")" = 'ok'
}

# Fresh process gives process-runtime ingest counters a clean origin while preserving Room data.
adb -s "$SERIAL" shell am force-stop "$PKG" >/dev/null
sleep 2
snapshot_db baseline
BASE_SIGNALS="$(sed -n '3p' "$OUT/baseline.counts")"

XML="$OUT/window.xml"
dump_ui() {
  local dest="$1"
  local ok=0
  for _ in 1 2 3 4 5; do
    if adb -s "$SERIAL" shell uiautomator dump /sdcard/blueeye-phase3-final.xml >/dev/null 2>&1 && \
       adb -s "$SERIAL" exec-out cat /sdcard/blueeye-phase3-final.xml > "$dest" 2>/dev/null && \
       grep -q '<hierarchy' "$dest"; then
      ok=1
      break
    fi
    sleep 1
  done
  test "$ok" = '1'
}
coords() {
  dump_ui "$XML"
  python3 tools/ui-smoke/ui_node.py "$XML" "$1" "$2"
}
tap_node() {
  local c x y
  c="$(coords "$1" "$2")"
  test -n "$c"
  read -r x y <<< "$c"
  adb -s "$SERIAL" shell input tap "$x" "$y"
  sleep 1
}
ensure_root() {
  adb -s "$SERIAL" shell am start -W --user 0 -n "$PKG/.MainActivity" >/dev/null
  sleep 2
  for _ in 1 2 3 4 5; do
    if coords content-desc Menu >/dev/null 2>&1; then return 0; fi
    adb -s "$SERIAL" shell input keyevent BACK >/dev/null 2>&1 || true
    sleep 1
  done
  coords content-desc Menu >/dev/null
}

ensure_root
tap_node content-desc Menu
if coords text Radar >/dev/null 2>&1; then tap_node text Radar; fi
sleep 1
if ! adb -s "$SERIAL" shell dumpsys activity services "$PKG" | grep -q 'ScannerService'; then
  tap_node content-desc Scan
fi
for _ in $(seq 1 15); do
  if adb -s "$SERIAL" shell dumpsys activity services "$PKG" | grep -q 'ScannerService'; then break; fi
  sleep 1
done
adb -s "$SERIAL" shell dumpsys activity services "$PKG" | grep -q 'ScannerService'
PID="$(adb -s "$SERIAL" shell pidof "$PKG" | tr -d '\r')"
test -n "$PID"
echo "scan_pid=$PID"
echo "ACCOUNTING_WINDOW_START=$(date -u +%FT%TZ)"
sleep 75
# Stop through the product UI. This preserves process-runtime diagnostics for export.
tap_node content-desc Scan
for _ in $(seq 1 15); do
  if ! adb -s "$SERIAL" shell dumpsys activity services "$PKG" | grep -q 'ScannerService'; then break; fi
  sleep 1
done
if adb -s "$SERIAL" shell dumpsys activity services "$PKG" | grep -q 'ScannerService'; then
  echo 'scanner_service_still_present_after_normal_stop'
  exit 2
fi
PID_AFTER_STOP="$(adb -s "$SERIAL" shell pidof "$PKG" | tr -d '\r')"
echo "pid_after_stop=$PID_AFTER_STOP"
test "$PID_AFTER_STOP" = "$PID"
echo "ACCOUNTING_WINDOW_STOP=$(date -u +%FT%TZ)"
sleep 3

# Navigate to Settings -> Database & Updates and generate the canonical file-backed Share export.
ensure_root
tap_node content-desc Menu
tap_node text Settings
for _ in 1 2 3 4; do
  if coords text 'Database & Updates' >/dev/null 2>&1; then break; fi
  adb -s "$SERIAL" shell input swipe 540 1700 540 600 300
  sleep 1
done
tap_node text 'Database & Updates'
sleep 2
adb -s "$SERIAL" shell run-as "$PKG" rm -f cache/session_exports/blueeye-session-export.json >/dev/null 2>&1 || true
for _ in $(seq 1 10); do
  if coords text Share >/dev/null 2>&1; then break; fi
  adb -s "$SERIAL" shell input swipe 540 1750 540 500 350
  sleep 1
done
coords text 'Session export' >/dev/null || true
tap_node text Share
sleep 6
adb -s "$SERIAL" shell run-as "$PKG" ls -l cache/session_exports/blueeye-session-export.json
adb -s "$SERIAL" exec-out run-as "$PKG" cat cache/session_exports/blueeye-session-export.json > "$OUT/session-export.json"
test -s "$OUT/session-export.json"
EXPORT_SHA="$(shasum -a 256 "$OUT/session-export.json" | awk '{print $1}')"
EXPORT_BYTES="$(wc -c < "$OUT/session-export.json" | tr -d ' ')"
echo "export_bytes=$EXPORT_BYTES"
echo "export_sha256=$EXPORT_SHA"
# Return from Android Sharesheet without killing the app process.
adb -s "$SERIAL" shell input keyevent BACK >/dev/null 2>&1 || true
sleep 2
PID_AFTER_EXPORT="$(adb -s "$SERIAL" shell pidof "$PKG" | tr -d '\r')"
test "$PID_AFTER_EXPORT" = "$PID"

python3 - "$OUT/session-export.json" <<'PY'
import json, sys
p=sys.argv[1]
d=json.load(open(p))
scanner=d['fieldMvpDiagnostics']['scanner']
ing=scanner['ingest']
keys=[
 'rawBleCallbacksTotal','enqueueAcceptedTotal','enqueueRejectedTotal','queueDroppedTotal','coalescedTotal',
 'processingStartedTotal','processingSucceededTotal','processingFailedTotal','provisionalDiscardedTotal',
 'persistedDeviceUpdatesTotal','deviceUpdateThrottledTotal','signalSamplesWrittenTotal','signalSamplesThrottledTotal',
 'signalSampleWriteFailuresTotal','queueDepth','queueHighWaterMark','maxQueueWaitMs','maxProcessingDurationMs'
]
print('scanner_state='+str(scanner.get('state')))
print('startedAt='+str(scanner.get('startedAt')))
print('lastBleSeenAt='+str(scanner.get('lastBleSeenAt')))
for k in keys: print(f'{k}={ing.get(k)}')
raw=ing['rawBleCallbacksTotal']
accepted=ing['enqueueAcceptedTotal']
rejected=ing['enqueueRejectedTotal']
coalesced=ing['coalescedTotal']
started=ing['processingStartedTotal']
succeeded=ing['processingSucceededTotal']
failed=ing['processingFailedTotal']
assert raw > 0, raw
assert raw == accepted + coalesced + rejected, (raw,accepted,coalesced,rejected)
assert rejected == 0, rejected
assert ing['queueDroppedTotal'] == 0, ing['queueDroppedTotal']
assert failed == 0, failed
assert ing['signalSampleWriteFailuresTotal'] == 0, ing['signalSampleWriteFailuresTotal']
assert ing['queueDepth'] == 0, ing['queueDepth']
assert started == succeeded + failed, (started,succeeded,failed)
assert accepted == started, (accepted,started)
assert ing['signalSamplesWrittenTotal'] > 0, ing['signalSamplesWrittenTotal']
assert scanner.get('lastBleSeenAt') is not None
assert scanner.get('startedAt') is not None
assert scanner['lastBleSeenAt'] > scanner['startedAt']
print('CURRENT_INGEST_ACCOUNTING_PASS')
PY

snapshot_db final
FINAL_SIGNALS="$(sed -n '3p' "$OUT/final.counts")"
echo "baseline_signal_samples=$BASE_SIGNALS"
echo "final_signal_samples=$FINAL_SIGNALS"
test "$FINAL_SIGNALS" -gt "$BASE_SIGNALS"

python3 - "$OUT/session-export.json" "$OUT/final.counts" <<'PY'
import json,sys
j=json.load(open(sys.argv[1]))
lines=[x.strip() for x in open(sys.argv[2]) if x.strip()]
devices=int(lines[1]); samples=int(lines[2])
print('export_deviceCount='+str(j.get('deviceCount')))
print('export_sampleCount='+str(j.get('sampleCount')))
assert j.get('deviceCount') == devices, (j.get('deviceCount'),devices)
assert j.get('sampleCount') == samples, (j.get('sampleCount'),samples)
print('EXPORT_DB_RECONCILIATION_PASS')
PY

if adb -s "$SERIAL" logcat -d -v brief | grep -E 'TransactionTooLargeException|FATAL EXCEPTION.*io.blueeye|ANR in io.blueeye|DeadObjectException'; then
  echo 'CRASH_SIGNATURE_FOUND'
  exit 3
fi

echo 'NO_CRASH_SIGNATURES'
echo 'PHASE3_CURRENT_ACCOUNTING_FINALIZE_PASS'
