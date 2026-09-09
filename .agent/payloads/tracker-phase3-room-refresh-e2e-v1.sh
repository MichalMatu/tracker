#!/usr/bin/env bash
set -euo pipefail

EXPECTED='c1577ff92a0deb6bf14c29d0320d53f3aee87632'
RUN_ID='34309681594'
ARTIFACT="tracker-debug-${EXPECTED}"
PKG='io.blueeye'
OUT='/tmp/tracker-phase3-room-refresh-e2e-v1'
rm -rf "$OUT"
mkdir -p "$OUT/artifact"

git fetch --no-tags origin main >/dev/null
HEAD="$(git rev-parse origin/main)"
echo "source_head=$HEAD"
test "$HEAD" = "$EXPECTED"

RUN_JSON="$(gh run view "$RUN_ID" --repo MichalMatu/tracker --json headSha,status,conclusion)"
echo "quality_run=$RUN_JSON"
python3 - "$EXPECTED" "$RUN_JSON" <<'PY'
import json,sys
expected=sys.argv[1]
d=json.loads(sys.argv[2])
assert d['headSha']==expected, d
assert d['status']=='completed', d
assert d['conclusion']=='success', d
PY

gh run download "$RUN_ID" --repo MichalMatu/tracker -n "$ARTIFACT" -D "$OUT/artifact"
APK="$(find "$OUT/artifact" -type f -name '*.apk' | head -n1)"
test -n "$APK"
APK_HASH="$(shasum -a 256 "$APK" | awk '{print $1}')"
echo "artifact_apk=$APK"
echo "artifact_apk_sha256=$APK_HASH"

SERIAL="$(adb devices | awk 'NR>1 && $2=="device" {print $1}' | head -n1)"
test -n "$SERIAL"
echo "serial=$SERIAL"
adb devices -l
MODEL="$(adb -s "$SERIAL" shell getprop ro.product.model | tr -d '\r')"
ANDROID="$(adb -s "$SERIAL" shell getprop ro.build.version.release | tr -d '\r')"
SDK="$(adb -s "$SERIAL" shell getprop ro.build.version.sdk | tr -d '\r')"
echo "device=$MODEL android=$ANDROID sdk=$SDK"

echo '--- PREUPDATE BACKUP ---'
BACKUP="$OUT/preupdate-data.tar"
adb -s "$SERIAL" shell am force-stop "$PKG" || true
adb -s "$SERIAL" exec-out run-as "$PKG" tar cf - databases files shared_prefs > "$BACKUP"
echo "backup_sha256=$(shasum -a 256 "$BACKUP" | awk '{print $1}')"
mkdir -p "$OUT/preupdate"
tar xf "$BACKUP" -C "$OUT/preupdate"
PRE_DB="$OUT/preupdate/databases/tracker_database"
test -f "$PRE_DB"
sqlite3 "$PRE_DB" 'PRAGMA integrity_check; select count(*) from devices; select count(*) from signal_samples; select count(*) from follow_me_observations;' | tee "$OUT/preupdate.counts"

echo '--- INSTALL EXACT CI APK ---'
adb -s "$SERIAL" install -r "$APK"
P="$(adb -s "$SERIAL" shell pm path "$PKG" | tr -d '\r' | sed -n 's/^package://p' | head -n1)"
test -n "$P"
INSTALLED="$(mktemp)"
adb -s "$SERIAL" pull "$P" "$INSTALLED" >/dev/null
INSTALLED_HASH="$(shasum -a 256 "$INSTALLED" | awk '{print $1}')"
rm -f "$INSTALLED"
echo "installed_apk_sha256=$INSTALLED_HASH"
test "$INSTALLED_HASH" = "$APK_HASH"

for PERM in android.permission.BLUETOOTH_SCAN android.permission.BLUETOOTH_CONNECT android.permission.ACCESS_FINE_LOCATION android.permission.ACCESS_COARSE_LOCATION android.permission.POST_NOTIFICATIONS; do
  adb -s "$SERIAL" shell pm grant "$PKG" "$PERM" 2>/dev/null || true
done
BT="$(adb -s "$SERIAL" shell settings get global bluetooth_on | tr -d '\r')"
echo "bluetooth_on=$BT"
test "$BT" = '1'

mkdir -p "$OUT/postinstall"
adb -s "$SERIAL" exec-out run-as "$PKG" tar cf - databases > "$OUT/postinstall.tar"
tar xf "$OUT/postinstall.tar" -C "$OUT/postinstall"
POST_DB="$OUT/postinstall/databases/tracker_database"
test -f "$POST_DB"
sqlite3 "$POST_DB" 'PRAGMA integrity_check; select count(*) from devices; select count(*) from signal_samples; select count(*) from follow_me_observations;' | tee "$OUT/postinstall.counts"
diff -u "$OUT/preupdate.counts" "$OUT/postinstall.counts"

echo '--- UI HELPERS ---'
adb -s "$SERIAL" shell am start -W -n "$PKG/.MainActivity" >/dev/null
sleep 2
XML="$OUT/window.xml"
dump_ui(){
  adb -s "$SERIAL" shell uiautomator dump /sdcard/blueeye-room-refresh.xml >/dev/null
  adb -s "$SERIAL" exec-out cat /sdcard/blueeye-room-refresh.xml > "$XML"
}
coords(){ dump_ui; python3 tools/ui-smoke/ui_node.py "$XML" "$1" "$2"; }
tap_node(){
  local c
  c="$(coords "$1" "$2")"
  test -n "$c"
  set -- $c
  adb -s "$SERIAL" shell input tap "$1" "$2"
  sleep 0.8
}
scroll_until(){
  local label="$1"
  for i in 1 2 3 4 5 6 7 8; do
    if coords text "$label" >/dev/null 2>&1; then return 0; fi
    adb -s "$SERIAL" shell input swipe 540 1750 540 550 350
    sleep 0.6
  done
  return 1
}
scanner_running(){ adb -s "$SERIAL" shell dumpsys activity services "$PKG" | grep -q 'ScannerService'; }
go_radar(){
  adb -s "$SERIAL" shell am start -W -n "$PKG/.MainActivity" >/dev/null
  sleep 0.7
  if coords content-desc Menu >/dev/null 2>&1; then
    tap_node content-desc Menu
    tap_node text Radar
  fi
}
capture_export(){
  local dest="$1"
  adb -s "$SERIAL" shell am start -W -n "$PKG/.MainActivity" >/dev/null
  sleep 0.7
  tap_node content-desc Menu
  tap_node text Settings
  tap_node text 'Database & Updates'
  scroll_until Share
  adb -s "$SERIAL" shell run-as "$PKG" rm -f cache/session_exports/blueeye-session-export.json || true
  tap_node text Share
  for i in 1 2 3 4 5 6 7 8 9 10; do
    if adb -s "$SERIAL" shell run-as "$PKG" test -s cache/session_exports/blueeye-session-export.json; then break; fi
    sleep 1
  done
  adb -s "$SERIAL" exec-out run-as "$PKG" cat cache/session_exports/blueeye-session-export.json > "$dest"
  python3 -m json.tool "$dest" >/dev/null
  adb -s "$SERIAL" shell input keyevent BACK || true
  sleep 0.8
}
print_export(){
  python3 - "$1" "$2" <<'PY'
import json,sys
label=sys.argv[1]
d=json.load(open(sys.argv[2]))
s=d['fieldMvpDiagnostics']['scanner']; i=s['ingest']
print(label+'_state='+str(s['state']))
print(label+'_startedAt='+str(s['startedAt']))
print(label+'_lastBleSeenAt='+str(s['lastBleSeenAt']))
for k in ('rawBleCallbacksTotal','enqueueAcceptedTotal','enqueueRejectedTotal','queueDroppedTotal','coalescedTotal','processingStartedTotal','processingSucceededTotal','processingFailedTotal','persistedDeviceUpdatesTotal','signalSamplesWrittenTotal','signalSampleWriteFailuresTotal','queueDepth','queueHighWaterMark'):
    print(label+'_'+k+'='+str(i[k]))
PY
}

# Start from a known Idle state, then start through the normal Radar control.
go_radar
if scanner_running; then
  tap_node content-desc Scan
  sleep 3
fi
if scanner_running; then echo 'prestart_stop_failed'; exit 1; fi
adb -s "$SERIAL" logcat -c
tap_node content-desc Scan
sleep 5
scanner_running
PID0="$(adb -s "$SERIAL" shell pidof "$PKG" | tr -d '\r')"
test -n "$PID0"
echo "scan_pid=$PID0"

echo '--- BASELINE RUNNING EXPORT ---'
capture_export "$OUT/baseline.json"
print_export baseline "$OUT/baseline.json"
go_radar
scanner_running

echo "SOAK_START=$(date -u +%FT%TZ)"
sleep 180
PID="$(adb -s "$SERIAL" shell pidof "$PKG" | tr -d '\r')"; test "$PID" = "$PID0"; scanner_running
echo '--- CHECKPOINT 180S ---'
capture_export "$OUT/t180.json"; print_export t180 "$OUT/t180.json"; go_radar; scanner_running

sleep 150
PID="$(adb -s "$SERIAL" shell pidof "$PKG" | tr -d '\r')"; test "$PID" = "$PID0"; scanner_running
echo '--- CHECKPOINT >300S ---'
capture_export "$OUT/t330.json"; print_export t330 "$OUT/t330.json"; go_radar; scanner_running

sleep 90
PID="$(adb -s "$SERIAL" shell pidof "$PKG" | tr -d '\r')"; test "$PID" = "$PID0"; scanner_running
echo '--- CHECKPOINT >400S ---'
capture_export "$OUT/t420.json"; print_export t420 "$OUT/t420.json"; go_radar; scanner_running
echo "SOAK_END=$(date -u +%FT%TZ)"

# Stop normally and let accepted work drain before the frozen export.
tap_node content-desc Scan
sleep 4
if scanner_running; then echo 'scanner_service_still_present_after_stop=1'; exit 1; else echo 'scanner_service_still_present_after_stop=0'; fi
capture_export "$OUT/final.json"
print_export final "$OUT/final.json"

echo '--- FINAL DATABASE ---'
adb -s "$SERIAL" exec-out run-as "$PKG" tar cf - databases > "$OUT/final-db.tar"
mkdir -p "$OUT/final-db"
tar xf "$OUT/final-db.tar" -C "$OUT/final-db"
FINAL_DB="$OUT/final-db/databases/tracker_database"
test -f "$FINAL_DB"
ls -al "$OUT/final-db/databases"
sqlite3 "$FINAL_DB" 'PRAGMA integrity_check; select count(*) from devices; select count(*) from signal_samples; select count(*) from follow_me_observations;' | tee "$OUT/final.counts"

echo '--- RECONCILIATION ---'
python3 - "$OUT/baseline.json" "$OUT/t180.json" "$OUT/t330.json" "$OUT/t420.json" "$OUT/final.json" "$OUT/preupdate.counts" "$OUT/final.counts" <<'PY'
import json,sys
paths=sys.argv[1:6]
base,t180,t330,t420,final=[json.load(open(p)) for p in paths]
def scanner(d): return d['fieldMvpDiagnostics']['scanner']
def ingest(d): return scanner(d)['ingest']
bi=ingest(base); fi=ingest(final)
keys=['rawBleCallbacksTotal','enqueueAcceptedTotal','enqueueRejectedTotal','queueDroppedTotal','coalescedTotal','processingStartedTotal','processingSucceededTotal','processingFailedTotal','provisionalDiscardedTotal','persistedDeviceUpdatesTotal','deviceUpdateThrottledTotal','signalSamplesWrittenTotal','signalSamplesThrottledTotal','signalSampleWriteFailuresTotal','totalQueueWaitMs','totalProcessingDurationMs']
delta={k:fi[k]-bi[k] for k in keys}
for k in keys: print(f'delta_{k}={delta[k]}')
raw=delta['rawBleCallbacksTotal']; accepted=delta['enqueueAcceptedTotal']; coalesced=delta['coalescedTotal']; rejected=delta['enqueueRejectedTotal']
print('raw_accounting_rhs='+str(accepted+coalesced+rejected))
assert raw > 0, 'no callbacks during patched soak'
assert raw == accepted + coalesced + rejected, (raw,accepted,coalesced,rejected)
assert fi['queueDroppedTotal'] == 0, fi['queueDroppedTotal']
assert delta['queueDroppedTotal'] == 0, delta['queueDroppedTotal']
assert delta['processingFailedTotal'] == 0, delta['processingFailedTotal']
assert delta['signalSampleWriteFailuresTotal'] == 0, delta['signalSampleWriteFailuresTotal']
assert fi['queueDepth'] == 0, fi['queueDepth']
assert delta['processingStartedTotal'] == delta['processingSucceededTotal'] + delta['processingFailedTotal'], delta
s0=scanner(base); s330=scanner(t330); s420=scanner(t420)
start=s0['startedAt']; last330=s330['lastBleSeenAt']; last420=s420['lastBleSeenAt']
print('scan_started_at='+str(start)); print('last_ble_t330='+str(last330)); print('last_ble_t420='+str(last420))
assert start is not None and last330 is not None and last420 is not None
assert last330 > start + 300_000, (start,last330)
assert last420 > last330, (last330,last420)
r0=bi['rawBleCallbacksTotal']; r180=ingest(t180)['rawBleCallbacksTotal']; r330=ingest(t330)['rawBleCallbacksTotal']; r420=ingest(t420)['rawBleCallbacksTotal']
print(f'raw_progression={r0},{r180},{r330},{r420}')
assert r180 > r0 and r330 > r180 and r420 > r330, (r0,r180,r330,r420)
pre=open(sys.argv[6]).read().strip().splitlines(); fin=open(sys.argv[7]).read().strip().splitlines()
print('pre_counts='+repr(pre)); print('final_counts='+repr(fin))
assert pre and pre[0]=='ok'; assert fin and fin[0]=='ok'
assert int(fin[2]) > int(pre[2]), (pre,fin)
print('PATCHED_ROOM_ACCOUNTING_PASS')
PY

APP_LOG="$OUT/app.log"
adb -s "$SERIAL" logcat --pid="$PID0" -d -v threadtime > "$APP_LOG" || true
echo '--- REFRESH MARKERS ---'
grep -F 'Refreshing passive BLE scan registration' "$APP_LOG" || true
grep -F 'Passive BLE scan registration refreshed' "$APP_LOG" || true
REFRESH_STARTS="$(grep -Fc 'Refreshing passive BLE scan registration' "$APP_LOG" || true)"
REFRESH_DONE="$(grep -Fc 'Passive BLE scan registration refreshed' "$APP_LOG" || true)"
echo "refresh_starts=$REFRESH_STARTS refresh_done=$REFRESH_DONE"
test "$REFRESH_STARTS" -ge 1
test "$REFRESH_DONE" -ge 1
ERR_RE='FATAL EXCEPTION|ANR in io.blueeye|TransactionTooLargeException|DeadObjectException|OutOfMemory|SQLite.*(error|exception)|Room.*(error|exception)|SecurityException'
echo '--- APP ERRORS ---'
grep -Ei "$ERR_RE" "$APP_LOG" | tail -n 100 || true
ERR_COUNT="$(grep -Eic "$ERR_RE" "$APP_LOG" || true)"
echo "app_error_count=$ERR_COUNT"
test "$ERR_COUNT" -eq 0

echo 'PHASE3_ROOM_REFRESH_E2E_PASS'
