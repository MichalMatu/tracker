#!/usr/bin/env bash
set -euo pipefail
EXPECTED='c1577ff92a0deb6bf14c29d0320d53f3aee87632'
QUALITY_RUN='34309681594'
ARTIFACT="tracker-debug-${EXPECTED}"
PKG='io.blueeye'
OUT='/tmp/tracker-phase3-room-refresh-e2e-v2'
KEYDIR="$HOME/.local/share/blueeye-tracker/signing"
KS="$KEYDIR/tester.p12"
PW="$KEYDIR/tester.password"
ALIAS='blueeye-tester'
TARGET_CERT='fff1ada9aa9e2709b4e4bda8333404feae854372df319b77f1f45d7869a61cb6'
rm -rf "$OUT" && mkdir -p "$OUT/ci"

git fetch --no-tags origin main >/dev/null
HEAD="$(git rev-parse origin/main)"; echo "source_head=$HEAD"; test "$HEAD" = "$EXPECTED"
RUN_JSON="$(gh run view "$QUALITY_RUN" --repo MichalMatu/tracker --json status,conclusion,headSha)"; echo "quality_run=$RUN_JSON"
python3 -c 'import json,sys; d=json.loads(sys.argv[1]); assert d["status"]=="completed" and d["conclusion"]=="success" and d["headSha"]==sys.argv[2]' "$RUN_JSON" "$EXPECTED"
gh run download "$QUALITY_RUN" --repo MichalMatu/tracker --name "$ARTIFACT" --dir "$OUT/ci"
CIAPK="$(find "$OUT/ci" -type f -name '*.apk' | head -n1)"; test -s "$CIAPK"
echo "ci_apk_sha256=$(shasum -a 256 "$CIAPK" | awk '{print $1}')"
test -s "$KS"; test -s "$PW"
APKSIGNER="$(find "$HOME/Library/Android/sdk/build-tools" -type f -name apksigner 2>/dev/null | sort | tail -n1)"; test -x "$APKSIGNER"; echo "apksigner=$APKSIGNER"
PASS="$(cat "$PW")"
SIGNED="$OUT/BlueEye-Tracker-${EXPECTED}-persistent-tester.apk"
"$APKSIGNER" sign --ks "$KS" --ks-key-alias "$ALIAS" --ks-pass "pass:$PASS" --key-pass "pass:$PASS" --out "$SIGNED" "$CIAPK"
"$APKSIGNER" verify --verbose --print-certs "$SIGNED" > "$OUT/signed.verify.txt"
SIGNED_CERT="$(awk -F': ' '/Signer #1 certificate SHA-256 digest:/{print $2; exit}' "$OUT/signed.verify.txt" | tr 'A-F' 'a-f')"; echo "signed_cert_sha256=$SIGNED_CERT"; test "$SIGNED_CERT" = "$TARGET_CERT"
SIGNED_SHA="$(shasum -a 256 "$SIGNED" | awk '{print $1}')"; echo "signed_apk_sha256=$SIGNED_SHA"

SERIAL="$(adb devices | awk 'NR>1 && $2=="device" {print $1}' | head -n1)"; test -n "$SERIAL"; echo "serial=$SERIAL"; adb devices -l
MODEL="$(adb -s "$SERIAL" shell getprop ro.product.model | tr -d '\r')"; SDK="$(adb -s "$SERIAL" shell getprop ro.build.version.sdk | tr -d '\r')"; echo "device=$MODEL sdk=$SDK"
CURRENT_PATH="$(adb -s "$SERIAL" shell pm path "$PKG" | tr -d '\r' | sed -n 's/^package://p' | head -n1)"; test -n "$CURRENT_PATH"
adb -s "$SERIAL" pull "$CURRENT_PATH" "$OUT/pre-migration-installed.apk" >/dev/null
CURRENT_CERT="$("$APKSIGNER" verify --print-certs "$OUT/pre-migration-installed.apk" | awk -F': ' '/Signer #1 certificate SHA-256 digest:/{print $2; exit}' | tr 'A-F' 'a-f')"; echo "installed_cert_before=$CURRENT_CERT"

db_snapshot(){ local name="$1"; adb -s "$SERIAL" exec-out run-as "$PKG" tar cf - databases > "$OUT/$name.tar"; mkdir -p "$OUT/$name"; tar xf "$OUT/$name.tar" -C "$OUT/$name"; local db="$OUT/$name/databases/tracker_database"; test -f "$db"; sqlite3 "$db" 'PRAGMA integrity_check; select count(*) from devices; select count(*) from signal_samples; select count(*) from follow_me_observations;' > "$OUT/$name.counts"; echo "--- $name counts ---"; cat "$OUT/$name.counts"; find "$OUT/$name/databases" -maxdepth 1 -type f -exec stat -f '%N %z' {} \; | sort; }

if [ "$CURRENT_CERT" != "$TARGET_CERT" ]; then
  echo 'signature_migration=required'
  adb -s "$SERIAL" shell am force-stop "$PKG"
  adb -s "$SERIAL" exec-out run-as "$PKG" tar cf - databases files shared_prefs > "$OUT/pre-migration-data.tar"
  tar tf "$OUT/pre-migration-data.tar" | sort > "$OUT/pre-migration-data.list"
  mkdir -p "$OUT/pre-migration-data"; tar xf "$OUT/pre-migration-data.tar" -C "$OUT/pre-migration-data"
  sqlite3 "$OUT/pre-migration-data/databases/tracker_database" 'PRAGMA integrity_check; select count(*) from devices; select count(*) from signal_samples; select count(*) from follow_me_observations;' > "$OUT/pre-migration.counts"
  cat "$OUT/pre-migration.counts"; test "$(head -n1 "$OUT/pre-migration.counts")" = 'ok'
  echo "backup_sha256=$(shasum -a 256 "$OUT/pre-migration-data.tar" | awk '{print $1}')"
  adb -s "$SERIAL" uninstall "$PKG" | grep -q Success
  adb -s "$SERIAL" install "$SIGNED" | grep -q Success
  adb -s "$SERIAL" exec-in run-as "$PKG" tar xf - < "$OUT/pre-migration-data.tar"
else
  echo 'signature_migration=not_required'
  adb -s "$SERIAL" install -r "$SIGNED" | grep -q Success
fi
for perm in android.permission.BLUETOOTH_SCAN android.permission.BLUETOOTH_CONNECT android.permission.ACCESS_FINE_LOCATION android.permission.ACCESS_COARSE_LOCATION android.permission.POST_NOTIFICATIONS; do adb -s "$SERIAL" shell pm grant "$PKG" "$perm" >/dev/null 2>&1 || true; done
adb -s "$SERIAL" shell am start -W -n "$PKG/.MainActivity" >/dev/null; sleep 3
CURRENT_PATH="$(adb -s "$SERIAL" shell pm path "$PKG" | tr -d '\r' | sed -n 's/^package://p' | head -n1)"; adb -s "$SERIAL" pull "$CURRENT_PATH" "$OUT/installed-after.apk" >/dev/null
INSTALLED_SHA="$(shasum -a 256 "$OUT/installed-after.apk" | awk '{print $1}')"; INSTALLED_CERT="$("$APKSIGNER" verify --print-certs "$OUT/installed-after.apk" | awk -F': ' '/Signer #1 certificate SHA-256 digest:/{print $2; exit}' | tr 'A-F' 'a-f')"; echo "installed_apk_sha256=$INSTALLED_SHA"; echo "installed_cert_after=$INSTALLED_CERT"; test "$INSTALLED_SHA" = "$SIGNED_SHA"; test "$INSTALLED_CERT" = "$TARGET_CERT"
if [ -s "$OUT/pre-migration.counts" ]; then db_snapshot post_migration_db; diff -u "$OUT/pre-migration.counts" "$OUT/post_migration_db.counts"; fi

XML="$OUT/window.xml"
dump_ui(){ adb -s "$SERIAL" shell uiautomator dump /sdcard/blueeye-room.xml >/dev/null; adb -s "$SERIAL" exec-out cat /sdcard/blueeye-room.xml > "$XML"; }
coords(){ dump_ui; python3 tools/ui-smoke/ui_node.py "$XML" "$1" "$2"; }
tap_node(){ local c; c="$(coords "$1" "$2")"; test -n "$c"; set -- $c; adb -s "$SERIAL" shell input tap "$1" "$2"; sleep 0.8; }
scroll_until(){ local label="$1"; for i in 1 2 3 4 5 6 7 8; do if coords text "$label" >/dev/null 2>&1; then return 0; fi; adb -s "$SERIAL" shell input swipe 540 1750 540 550 350; sleep 0.6; done; return 1; }
scanner_running(){ adb -s "$SERIAL" shell dumpsys activity services "$PKG" | grep -q 'ScannerService'; }
go_radar(){ adb -s "$SERIAL" shell am start -W -n "$PKG/.MainActivity" >/dev/null; sleep 0.7; if coords content-desc Menu >/dev/null 2>&1; then tap_node content-desc Menu; tap_node text Radar; fi; }
capture_export(){ local dest="$1"; adb -s "$SERIAL" shell am start -W -n "$PKG/.MainActivity" >/dev/null; sleep 0.7; tap_node content-desc Menu; tap_node text Settings; tap_node text 'Database & Updates'; scroll_until Share; adb -s "$SERIAL" shell run-as "$PKG" rm -f cache/session_exports/blueeye-session-export.json || true; tap_node text Share; for i in 1 2 3 4 5 6 7 8 9 10; do if adb -s "$SERIAL" shell run-as "$PKG" test -s cache/session_exports/blueeye-session-export.json; then break; fi; sleep 1; done; adb -s "$SERIAL" exec-out run-as "$PKG" cat cache/session_exports/blueeye-session-export.json > "$dest"; python3 -m json.tool "$dest" >/dev/null; adb -s "$SERIAL" shell input keyevent BACK || true; sleep 0.8; }
print_ingest(){ local label="$1" file="$2"; python3 - "$label" "$file" <<'PY'
import json,sys
label,path=sys.argv[1:]
d=json.load(open(path)); s=d['fieldMvpDiagnostics']['scanner']; i=s['ingest']
print(label+'_state='+str(s.get('state')))
for k in ('lastBleSeenAt','bleResultsPerMinute'): print(f'{label}_{k}={s.get(k)}')
for k in ('rawBleCallbacksTotal','enqueueAcceptedTotal','enqueueRejectedTotal','queueDroppedTotal','coalescedTotal','processingStartedTotal','processingSucceededTotal','processingFailedTotal','signalSamplesWrittenTotal','signalSampleWriteFailuresTotal','queueDepth','queueHighWaterMark','maxQueueWaitMs','maxProcessingDurationMs'): print(f'{label}_{k}={i.get(k)}')
PY
}

adb -s "$SERIAL" logcat -c
go_radar
if ! scanner_running; then tap_node content-desc Scan; sleep 8; fi
scanner_running
PID0="$(adb -s "$SERIAL" shell pidof "$PKG" | tr -d '\r')"; test -n "$PID0"; echo "scan_pid_start=$PID0"
capture_export "$OUT/baseline.json"; print_ingest baseline "$OUT/baseline.json"; db_snapshot baseline_db
python3 - "$OUT/baseline.json" <<'PY'
import json,sys
d=json.load(open(sys.argv[1])); s=d['fieldMvpDiagnostics']['scanner']; i=s['ingest']
assert i['rawBleCallbacksPerMinute'] > 0
assert i['queueDroppedTotal'] == 0
assert s['lastBleSeenAt'] is not None
print('BASELINE_LIVE_BLE_PASS')
PY
go_radar; echo "SOAK_START=$(date -u +%FT%TZ)"
sleep 180
scanner_running; test "$(adb -s "$SERIAL" shell pidof "$PKG" | tr -d '\r')" = "$PID0"; capture_export "$OUT/checkpoint180.json"; print_ingest cp180 "$OUT/checkpoint180.json"; go_radar
sleep 150
scanner_running; test "$(adb -s "$SERIAL" shell pidof "$PKG" | tr -d '\r')" = "$PID0"; capture_export "$OUT/checkpoint330.json"; print_ingest cp330 "$OUT/checkpoint330.json"; go_radar
sleep 90
scanner_running; test "$(adb -s "$SERIAL" shell pidof "$PKG" | tr -d '\r')" = "$PID0"; capture_export "$OUT/checkpoint420.json"; print_ingest cp420 "$OUT/checkpoint420.json"
python3 - "$OUT/checkpoint180.json" "$OUT/checkpoint330.json" "$OUT/checkpoint420.json" <<'PY'
import json,sys
D=[json.load(open(p))['fieldMvpDiagnostics']['scanner'] for p in sys.argv[1:]]
I=[d['ingest'] for d in D]
assert I[1]['rawBleCallbacksTotal'] > I[0]['rawBleCallbacksTotal'], (I[0]['rawBleCallbacksTotal'],I[1]['rawBleCallbacksTotal'])
assert I[2]['rawBleCallbacksTotal'] > I[1]['rawBleCallbacksTotal'], (I[1]['rawBleCallbacksTotal'],I[2]['rawBleCallbacksTotal'])
assert D[1]['lastBleSeenAt'] > D[0]['lastBleSeenAt']
assert D[2]['lastBleSeenAt'] > D[1]['lastBleSeenAt']
assert I[1]['queueDroppedTotal'] == 0 and I[2]['queueDroppedTotal'] == 0
print('BLE_SURVIVED_300S_PASS')
PY
echo "SOAK_END=$(date -u +%FT%TZ)"
go_radar; if scanner_running; then tap_node content-desc Scan; sleep 5; fi
if scanner_running; then echo scanner_service_still_present_after_stop=1; exit 1; else echo scanner_service_still_present_after_stop=0; fi
capture_export "$OUT/final.json"; print_ingest final "$OUT/final.json"; db_snapshot final_db
# Re-test oversized Copy transport on the same final process.
adb -s "$SERIAL" shell am start -W -n "$PKG/.MainActivity" >/dev/null; sleep 0.7; tap_node content-desc Menu; tap_node text Settings; tap_node text 'Database & Updates'; scroll_until Copy; tap_node text Copy; sleep 2; PID_AFTER_COPY="$(adb -s "$SERIAL" shell pidof "$PKG" | tr -d '\r')"; echo "pid_after_copy=$PID_AFTER_COPY"; test "$PID_AFTER_COPY" = "$PID0"
adb -s "$SERIAL" logcat -d -v threadtime > "$OUT/logcat.txt" || true
python3 - "$OUT/baseline.json" "$OUT/final.json" "$OUT/baseline_db.counts" "$OUT/final_db.counts" <<'PY'
import json,sys
b=json.load(open(sys.argv[1])); f=json.load(open(sys.argv[2])); bi=b['fieldMvpDiagnostics']['scanner']['ingest']; fi=f['fieldMvpDiagnostics']['scanner']['ingest']
keys=['rawBleCallbacksTotal','enqueueAcceptedTotal','enqueueRejectedTotal','queueDroppedTotal','coalescedTotal','processingStartedTotal','processingSucceededTotal','processingFailedTotal','provisionalDiscardedTotal','persistedDeviceUpdatesTotal','deviceUpdateThrottledTotal','signalSamplesWrittenTotal','signalSamplesThrottledTotal','signalSampleWriteFailuresTotal','totalQueueWaitMs','totalProcessingDurationMs']
d={k:fi[k]-bi[k] for k in keys}
print('--- DELTAS ---')
for k in keys: print(f'{k}={d[k]}')
raw=d['rawBleCallbacksTotal']; accepted=d['enqueueAcceptedTotal']; coal=d['coalescedTotal']; rej=d['enqueueRejectedTotal']
print('raw_accounting_rhs='+str(accepted+coal+rej))
assert raw > 0
assert raw == accepted + coal + rej
assert fi['queueDroppedTotal'] == 0 and d['queueDroppedTotal'] == 0
assert d['processingFailedTotal'] == 0
assert d['signalSampleWriteFailuresTotal'] == 0
assert fi['queueDepth'] == 0
assert d['processingStartedTotal'] == d['processingSucceededTotal'] + d['processingFailedTotal']
assert d['enqueueAcceptedTotal'] == d['processingStartedTotal']
bc=open(sys.argv[3]).read().strip().splitlines(); fc=open(sys.argv[4]).read().strip().splitlines()
print('baseline_db_counts='+repr(bc)); print('final_db_counts='+repr(fc))
assert bc and bc[0]=='ok' and fc and fc[0]=='ok'
assert int(fc[2]) > int(bc[2])
print('ROOM_E2E_ACCOUNTING_PASS')
PY
REFRESH_COUNT="$(grep -c 'Refreshing passive BLE scan before platform timeout' "$OUT/logcat.txt" || true)"; echo "ble_refresh_log_count=$REFRESH_COUNT"; test "$REFRESH_COUNT" -ge 1
ERR_RE='FATAL EXCEPTION|ANR in io.blueeye|Process: io.blueeye|TransactionTooLargeException|DeadObjectException|OutOfMemory|SQLite.*(error|exception)|Room.*(error|exception)|SecurityException|BLE scan failed: scanning too frequently'
echo '--- APP ERRORS ---'; grep -Ei "$ERR_RE" "$OUT/logcat.txt" | tail -n 100 || true
ERR_COUNT="$(grep -Eic "$ERR_RE" "$OUT/logcat.txt" || true)"; echo "LOG_ERROR_COUNT=$ERR_COUNT"; test "$ERR_COUNT" -eq 0
echo "final_export_size=$(stat -f %z "$OUT/final.json")"; echo "final_export_sha256=$(shasum -a 256 "$OUT/final.json" | awk '{print $1}')"
echo 'PHASE3_ROOM_REFRESH_E2E_PASS'
