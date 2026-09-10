#!/usr/bin/env bash
set -euo pipefail

EXPECTED='c1577ff92a0deb6bf14c29d0320d53f3aee87632'
PKG='io.blueeye'
TARGET_CERT='fff1ada9aa9e2709b4e4bda8333404feae854372df319b77f1f45d7869a61cb6'
TARGET_APK='a84f2ed3b6260b9f1baec5c8955a7a316f070de7208260a5788d46d43edbe2a1'
OUT='/tmp/tracker-phase3-room-refresh-e2e-v6'
rm -rf "$OUT" && mkdir -p "$OUT"

git fetch --no-tags origin main >/dev/null
HEAD="$(git rev-parse origin/main)"
echo "source_head=$HEAD"
test "$HEAD" = "$EXPECTED"

SERIAL="$(adb devices | awk 'NR>1 && $2=="device" {print $1}' | head -n1)"
test -n "$SERIAL"
echo "serial=$SERIAL"
test "$(adb -s "$SERIAL" shell am get-current-user | tr -d '\r')" = '0'
adb -s "$SERIAL" shell svc power stayon usb >/dev/null 2>&1 || true
adb -s "$SERIAL" shell input keyevent KEYCODE_WAKEUP >/dev/null 2>&1 || true
adb -s "$SERIAL" shell wm dismiss-keyguard >/dev/null 2>&1 || true

for permission in \
  android.permission.BLUETOOTH_SCAN \
  android.permission.BLUETOOTH_CONNECT \
  android.permission.ACCESS_FINE_LOCATION \
  android.permission.ACCESS_COARSE_LOCATION \
  android.permission.POST_NOTIFICATIONS
do
  adb -s "$SERIAL" shell pm grant --user 0 "$PKG" "$permission" >/dev/null 2>&1 || true
done

echo 'runtime_permissions_granted'

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
test "$INSTALLED_SHA" = "$TARGET_APK"
test "$INSTALLED_CERT" = "$TARGET_CERT"

XML="$OUT/window.xml"
dump_ui(){
  adb -s "$SERIAL" shell input keyevent KEYCODE_WAKEUP >/dev/null 2>&1 || true
  adb -s "$SERIAL" shell wm dismiss-keyguard >/dev/null 2>&1 || true
  adb -s "$SERIAL" shell uiautomator dump /sdcard/blueeye-room.xml >/dev/null
  adb -s "$SERIAL" exec-out cat /sdcard/blueeye-room.xml > "$XML"
}
coords(){ dump_ui; python3 tools/ui-smoke/ui_node.py "$XML" "$1" "$2"; }
tap_node(){ local c; c="$(coords "$1" "$2")"; test -n "$c"; set -- $c; adb -s "$SERIAL" shell input tap "$1" "$2"; sleep 1; }
scroll_until(){ local label="$1"; for _ in 1 2 3 4 5 6 7 8; do if coords text "$label" >/dev/null 2>&1; then return 0; fi; adb -s "$SERIAL" shell input swipe 540 1750 540 550 350; sleep 1; done; return 1; }
scanner_running(){ adb -s "$SERIAL" shell dumpsys activity services "$PKG" | grep -q 'ScannerService'; }
go_radar(){
  adb -s "$SERIAL" shell input keyevent KEYCODE_WAKEUP >/dev/null 2>&1 || true
  adb -s "$SERIAL" shell wm dismiss-keyguard >/dev/null 2>&1 || true
  adb -s "$SERIAL" shell am start -W --user 0 -n "$PKG/.MainActivity" >/dev/null
  sleep 1
  if coords content-desc Menu >/dev/null 2>&1; then
    tap_node content-desc Menu
    tap_node text Radar
  fi
}
capture_export(){
  local dest="$1"
  go_radar
  tap_node content-desc Menu
  tap_node text Settings
  tap_node text 'Database & Updates'
  scroll_until Share
  adb -s "$SERIAL" shell run-as "$PKG" rm -f cache/session_exports/blueeye-session-export.json >/dev/null 2>&1 || true
  tap_node text Share
  for _ in $(seq 1 20); do
    if adb -s "$SERIAL" shell run-as "$PKG" test -s cache/session_exports/blueeye-session-export.json; then break; fi
    sleep 1
  done
  adb -s "$SERIAL" exec-out run-as "$PKG" cat cache/session_exports/blueeye-session-export.json > "$dest"
  python3 -m json.tool "$dest" >/dev/null
  adb -s "$SERIAL" shell input keyevent BACK >/dev/null 2>&1 || true
  sleep 1
}
print_ingest(){
  local label="$1" file="$2"
  python3 - "$label" "$file" <<'PY'
import json,sys
label,path=sys.argv[1:]
d=json.load(open(path)); s=d['fieldMvpDiagnostics']['scanner']; i=s['ingest']
for k in ('startedAt','lastBleSeenAt','bleResultsPerMinute','state'):
    print(f'{label}_{k}={s.get(k)}')
for k in ('rawBleCallbacksTotal','enqueueAcceptedTotal','enqueueRejectedTotal','queueDroppedTotal','coalescedTotal','processingStartedTotal','processingSucceededTotal','processingFailedTotal','persistedDeviceUpdatesTotal','signalSamplesWrittenTotal','signalSampleWriteFailuresTotal','queueDepth','queueHighWaterMark','maxQueueWaitMs','maxProcessingDurationMs'):
    print(f'{label}_{k}={i.get(k)}')
PY
}
db_snapshot(){
  local name="$1" dir="$OUT/$name"
  mkdir -p "$dir"
  adb -s "$SERIAL" shell run-as "$PKG" rm -f cache/tracker_database.snapshot cache/tracker_database.snapshot-wal cache/tracker_database.snapshot-shm >/dev/null 2>&1 || true
  adb -s "$SERIAL" shell run-as "$PKG" cp databases/tracker_database cache/tracker_database.snapshot
  if adb -s "$SERIAL" shell run-as "$PKG" test -f databases/tracker_database-wal; then adb -s "$SERIAL" shell run-as "$PKG" cp databases/tracker_database-wal cache/tracker_database.snapshot-wal; fi
  if adb -s "$SERIAL" shell run-as "$PKG" test -f databases/tracker_database-shm; then adb -s "$SERIAL" shell run-as "$PKG" cp databases/tracker_database-shm cache/tracker_database.snapshot-shm; fi
  adb -s "$SERIAL" exec-out run-as "$PKG" cat cache/tracker_database.snapshot > "$dir/tracker_database"
  if adb -s "$SERIAL" shell run-as "$PKG" test -f cache/tracker_database.snapshot-wal; then adb -s "$SERIAL" exec-out run-as "$PKG" cat cache/tracker_database.snapshot-wal > "$dir/tracker_database-wal"; fi
  if adb -s "$SERIAL" shell run-as "$PKG" test -f cache/tracker_database.snapshot-shm; then adb -s "$SERIAL" exec-out run-as "$PKG" cat cache/tracker_database.snapshot-shm > "$dir/tracker_database-shm"; fi
  sqlite3 "$dir/tracker_database" 'PRAGMA integrity_check; select count(*) from devices; select count(*) from signal_samples; select count(*) from follow_me_observations;' > "$OUT/$name.counts"
  echo "--- $name counts ---"
  cat "$OUT/$name.counts"
  test "$(head -n1 "$OUT/$name.counts")" = 'ok'
}

adb -s "$SERIAL" shell am start -W --user 0 -n "$PKG/.MainActivity" >/dev/null
sleep 2
go_radar
if scanner_running; then
  echo 'preexisting_scanner=running; stopping normally'
  tap_node content-desc Scan
  sleep 6
fi
if scanner_running; then echo 'unable_to_stop_preexisting_scanner=1'; exit 1; fi
BASE_PID="$(adb -s "$SERIAL" shell pidof "$PKG" | tr -d '\r')"
test -n "$BASE_PID"
echo "base_pid=$BASE_PID"
db_snapshot baseline_db

adb -s "$SERIAL" logcat -c
go_radar
tap_node content-desc Scan
sleep 12
scanner_running
PID0="$(adb -s "$SERIAL" shell pidof "$PKG" | tr -d '\r')"
test -n "$PID0"
echo "scan_pid_start=$PID0"
test "$PID0" = "$BASE_PID"
capture_export "$OUT/baseline.json"
print_ingest baseline "$OUT/baseline.json"
python3 - "$OUT/baseline.json" <<'PY'
import json,sys
d=json.load(open(sys.argv[1])); s=d['fieldMvpDiagnostics']['scanner']; i=s['ingest']
assert s['lastBleSeenAt'] is not None
assert i['rawBleCallbacksTotal'] > 0
assert i['queueDroppedTotal'] == 0
print('BASELINE_LIVE_BLE_PASS')
PY

go_radar
SOAK_EPOCH_MS="$(python3 - <<'PY'
import time
print(int(time.time()*1000))
PY
)"
echo "SOAK_START=$(date -u +%FT%TZ) epoch_ms=$SOAK_EPOCH_MS"
for target in 60 120 180 240 300 330 390 430; do
  while :; do
    NOW="$(date +%s)"
    START_SEC=$((SOAK_EPOCH_MS/1000))
    ELAPSED=$((NOW-START_SEC))
    [ "$ELAPSED" -ge "$target" ] && break
    sleep 5
  done
  PID_NOW="$(adb -s "$SERIAL" shell pidof "$PKG" | tr -d '\r')"
  test "$PID_NOW" = "$PID0"
  scanner_running
  REFRESH_NOW="$(adb -s "$SERIAL" logcat -d -v brief | grep -c 'Passive BLE scan registration refreshed' || true)"
  echo "checkpoint_${target}s pid=$PID_NOW scanner_service=present refresh_count=$REFRESH_NOW"
done

echo "SOAK_END=$(date -u +%FT%TZ)"
REFRESH_PRESTOP="$(adb -s "$SERIAL" logcat -d -v threadtime | grep -c 'Passive BLE scan registration refreshed' || true)"
echo "refresh_count_before_stop=$REFRESH_PRESTOP"
test "$REFRESH_PRESTOP" -ge 1

go_radar
tap_node content-desc Scan
sleep 8
if scanner_running; then echo 'scanner_service_still_present_after_stop=1'; exit 1; else echo 'scanner_service_still_present_after_stop=0'; fi
PID_STOPPED="$(adb -s "$SERIAL" shell pidof "$PKG" | tr -d '\r')"
test "$PID_STOPPED" = "$PID0"

capture_export "$OUT/final.json"
print_ingest final "$OUT/final.json"
db_snapshot final_db
adb -s "$SERIAL" logcat -d -v threadtime > "$OUT/logcat.txt" || true

python3 - "$OUT/baseline.json" "$OUT/final.json" "$OUT/baseline_db.counts" "$OUT/final_db.counts" "$SOAK_EPOCH_MS" <<'PY'
import json,sys
b=json.load(open(sys.argv[1])); f=json.load(open(sys.argv[2]))
bs=b['fieldMvpDiagnostics']['scanner']; fs=f['fieldMvpDiagnostics']['scanner']
bi=bs['ingest']; fi=fs['ingest']
keys=['rawBleCallbacksTotal','enqueueAcceptedTotal','enqueueRejectedTotal','queueDroppedTotal','coalescedTotal','processingStartedTotal','processingSucceededTotal','processingFailedTotal','persistedDeviceUpdatesTotal','signalSamplesWrittenTotal','signalSampleWriteFailuresTotal']
d={k:fi[k]-bi[k] for k in keys}
print('--- DELTAS ---')
for k in keys: print(f'{k}={d[k]}')
print('final_queueDepth=',fi['queueDepth'])
print('lastBleSeenAt_minus_startedAt=',fs['lastBleSeenAt']-fs['startedAt'])
print('lastBleSeenAt_minus_soakStart=',fs['lastBleSeenAt']-int(sys.argv[5]))
raw=d['rawBleCallbacksTotal']; rhs=d['enqueueAcceptedTotal']+d['coalescedTotal']+d['enqueueRejectedTotal']
print('raw_accounting_rhs=',rhs)
assert raw > 0
assert raw == rhs
assert fi['queueDroppedTotal'] == 0 and d['queueDroppedTotal'] == 0
assert d['processingFailedTotal'] == 0
assert d['signalSampleWriteFailuresTotal'] == 0
assert fi['queueDepth'] == 0
assert d['processingStartedTotal'] == d['processingSucceededTotal'] + d['processingFailedTotal']
assert d['enqueueAcceptedTotal'] == d['processingStartedTotal']
assert fs['lastBleSeenAt'] - fs['startedAt'] > 300_000
assert fs['lastBleSeenAt'] - int(sys.argv[5]) > 300_000
bc=open(sys.argv[3]).read().strip().splitlines(); fc=open(sys.argv[4]).read().strip().splitlines()
print('baseline_db_counts=',bc)
print('final_db_counts=',fc)
assert bc[0]=='ok' and fc[0]=='ok'
assert int(fc[2]) > int(bc[2])
print('ROOM_E2E_ACCOUNTING_PASS')
print('BLE_SURVIVED_300S_PASS')
PY

ERR_RE='FATAL EXCEPTION|ANR in io.blueeye|Process: io.blueeye|TransactionTooLargeException|DeadObjectException|OutOfMemory|SQLite.*(error|exception)|Room.*(error|exception)|SecurityException'
ERR_COUNT="$(grep -Eic "$ERR_RE" "$OUT/logcat.txt" || true)"
REFRESH_START="$(grep -c 'Refreshing passive BLE scan registration' "$OUT/logcat.txt" || true)"
REFRESH_DONE="$(grep -c 'Passive BLE scan registration refreshed' "$OUT/logcat.txt" || true)"
echo "LOG_ERROR_COUNT=$ERR_COUNT"
echo "REFRESH_START_COUNT=$REFRESH_START"
echo "REFRESH_DONE_COUNT=$REFRESH_DONE"
test "$ERR_COUNT" -eq 0
test "$REFRESH_START" -ge 1
test "$REFRESH_DONE" -ge 1

echo 'PHASE3_ROOM_REFRESH_E2E_PASS'
