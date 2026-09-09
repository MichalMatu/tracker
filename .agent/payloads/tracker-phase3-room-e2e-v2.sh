#!/usr/bin/env bash
set -euo pipefail
EXPECTED='db8c3ce7d95897b0c7e487750f4b7cb8e30641b3'
EXPECTED_APK='4d4a58789fb94d5e984d2135c2942018f4d8ae0cfb06d6f49692a0a454941ace'
PKG='io.blueeye'
OUT='/tmp/tracker-phase3-room-e2e-v2'
rm -rf "$OUT" && mkdir -p "$OUT"
git fetch --no-tags origin main >/dev/null
HEAD="$(git rev-parse origin/main)"; echo "source_head=$HEAD"; test "$HEAD" = "$EXPECTED"
SERIAL="$(adb devices | awk 'NR>1 && $2=="device" {print $1}' | head -n1)"; test -n "$SERIAL"; echo "serial=$SERIAL"; adb devices -l
adb -s "$SERIAL" shell getprop ro.product.model
adb -s "$SERIAL" shell getprop ro.build.version.release
adb -s "$SERIAL" shell getprop ro.build.version.sdk
adb -s "$SERIAL" shell dumpsys package "$PKG" | grep -E -m 30 'versionName=|versionCode=|firstInstallTime|lastUpdateTime|BLUETOOTH_SCAN|BLUETOOTH_CONNECT|ACCESS_FINE_LOCATION|ACCESS_COARSE_LOCATION|POST_NOTIFICATIONS' || true
P="$(adb -s "$SERIAL" shell pm path "$PKG" | tr -d '\r' | sed -n 's/^package://p' | head -n1)"; test -n "$P"
TMPAPK="$(mktemp)"; adb -s "$SERIAL" pull "$P" "$TMPAPK" >/dev/null
HASH="$(shasum -a 256 "$TMPAPK" | awk '{print $1}')"; rm -f "$TMPAPK"; echo "installed_apk_sha256=$HASH"; test "$HASH" = "$EXPECTED_APK"
BT="$(adb -s "$SERIAL" shell settings get global bluetooth_on | tr -d '\r')"; echo "bluetooth_on=$BT"; test "$BT" = '1'
adb -s "$SERIAL" shell am start -W -n "$PKG/.MainActivity" >/dev/null; sleep 2
XML="$OUT/window.xml"
dump_ui(){ adb -s "$SERIAL" shell uiautomator dump /sdcard/blueeye-room.xml >/dev/null; adb -s "$SERIAL" exec-out cat /sdcard/blueeye-room.xml > "$XML"; }
coords(){ dump_ui; python3 tools/ui-smoke/ui_node.py "$XML" "$1" "$2"; }
tap_node(){ local c; c="$(coords "$1" "$2")"; test -n "$c"; set -- $c; adb -s "$SERIAL" shell input tap "$1" "$2"; sleep 0.8; }
scroll_until(){ local label="$1"; for i in 1 2 3 4 5 6 7 8; do if coords text "$label" >/dev/null 2>&1; then return 0; fi; adb -s "$SERIAL" shell input swipe 540 1750 540 550 350; sleep 0.6; done; return 1; }
scanner_running(){ adb -s "$SERIAL" shell dumpsys activity services "$PKG" | grep -q 'ScannerService'; }
go_radar(){ adb -s "$SERIAL" shell am start -W -n "$PKG/.MainActivity" >/dev/null; sleep 0.7; if coords content-desc Menu >/dev/null 2>&1; then tap_node content-desc Menu; tap_node text Radar; fi; }
capture_export(){ local dest="$1"; adb -s "$SERIAL" shell am start -W -n "$PKG/.MainActivity" >/dev/null; sleep 0.7; tap_node content-desc Menu; tap_node text Settings; tap_node text 'Database & Updates'; scroll_until Share; adb -s "$SERIAL" shell run-as "$PKG" rm -f cache/session_exports/blueeye-session-export.json || true; tap_node text Share; for i in 1 2 3 4 5 6 7 8 9 10; do if adb -s "$SERIAL" shell run-as "$PKG" test -s cache/session_exports/blueeye-session-export.json; then break; fi; sleep 1; done; adb -s "$SERIAL" exec-out run-as "$PKG" cat cache/session_exports/blueeye-session-export.json > "$dest"; python3 -m json.tool "$dest" >/dev/null; adb -s "$SERIAL" shell input keyevent BACK || true; sleep 0.8; }
capture_db(){ local name="$1"; adb -s "$SERIAL" exec-out run-as "$PKG" tar cf - databases > "$OUT/$name.tar"; mkdir -p "$OUT/$name"; tar xf "$OUT/$name.tar" -C "$OUT/$name"; local db="$OUT/$name/databases/tracker_database"; test -f "$db"; sqlite3 "$db" 'PRAGMA integrity_check; select count(*) from devices; select count(*) from signal_samples; select count(*) from follow_me_observations;' > "$OUT/$name.counts"; echo "--- $name counts ---"; cat "$OUT/$name.counts"; }
if ! scanner_running; then go_radar; tap_node content-desc Scan; sleep 4; fi
scanner_running; echo 'scanner_running=1'; echo "pid=$(adb -s "$SERIAL" shell pidof "$PKG" | tr -d '\r')"
adb -s "$SERIAL" logcat -c
echo '--- BASELINE ---'; capture_export "$OUT/baseline.json"; capture_db baseline_db
python3 - "$OUT/baseline.json" <<'PY'
import json,sys
i=json.load(open(sys.argv[1]))['fieldMvpDiagnostics']['scanner']['ingest']
for k in ('rawBleCallbacksTotal','enqueueAcceptedTotal','enqueueRejectedTotal','queueDroppedTotal','coalescedTotal','processingStartedTotal','processingSucceededTotal','processingFailedTotal','persistedDeviceUpdatesTotal','signalSamplesWrittenTotal','queueDepth','queueHighWaterMark'):
    print(f'baseline_{k}={i[k]}')
PY
go_radar; echo "SOAK_START=$(date -u +%FT%TZ)"
for n in 1 2 3 4; do sleep 45; PID="$(adb -s "$SERIAL" shell pidof "$PKG" | tr -d '\r')"; test -n "$PID"; scanner_running; echo "soak_checkpoint_${n}_pid=$PID"; done
echo "SOAK_END=$(date -u +%FT%TZ)"
go_radar; if scanner_running; then tap_node content-desc Scan; sleep 3; fi
if scanner_running; then echo 'scanner_service_still_present_after_stop=1'; else echo 'scanner_service_still_present_after_stop=0'; fi
echo '--- FINAL ---'; capture_export "$OUT/final.json"; capture_db final_db
adb -s "$SERIAL" logcat -d -v threadtime > "$OUT/logcat.txt" || true
python3 - "$OUT/baseline.json" "$OUT/final.json" "$OUT/baseline_db.counts" "$OUT/final_db.counts" <<'PY'
import json,sys
b=json.load(open(sys.argv[1])); f=json.load(open(sys.argv[2]))
bi=b['fieldMvpDiagnostics']['scanner']['ingest']; fi=f['fieldMvpDiagnostics']['scanner']['ingest']
keys=['rawBleCallbacksTotal','enqueueAcceptedTotal','enqueueRejectedTotal','queueDroppedTotal','coalescedTotal','processingStartedTotal','processingSucceededTotal','processingFailedTotal','provisionalDiscardedTotal','persistedDeviceUpdatesTotal','deviceUpdateThrottledTotal','signalSamplesWrittenTotal','signalSamplesThrottledTotal','signalSampleWriteFailuresTotal','totalQueueWaitMs','totalProcessingDurationMs']
d={k:fi[k]-bi[k] for k in keys}
print('--- DELTAS ---')
for k in keys: print(f'{k}={d[k]}')
for k in ('queueDepth','queueHighWaterMark','maxQueueWaitMs','maxProcessingDurationMs'): print(f'final_{k}={fi[k]}')
raw=d['rawBleCallbacksTotal']; accepted=d['enqueueAcceptedTotal']; coal=d['coalescedTotal']; rej=d['enqueueRejectedTotal']
print('raw_accounting_rhs='+str(accepted+coal+rej))
assert raw > 0
assert raw == accepted + coal + rej
assert fi['queueDroppedTotal'] == 0
assert d['queueDroppedTotal'] == 0
assert d['processingFailedTotal'] == 0
assert d['signalSampleWriteFailuresTotal'] == 0
assert fi['queueDepth'] == 0
assert d['processingStartedTotal'] == d['processingSucceededTotal'] + d['processingFailedTotal']
bc=open(sys.argv[3]).read().strip().splitlines(); fc=open(sys.argv[4]).read().strip().splitlines()
print('baseline_db_counts='+repr(bc)); print('final_db_counts='+repr(fc))
assert bc and bc[0] == 'ok'; assert fc and fc[0] == 'ok'
print('ROOM_E2E_ACCOUNTING_PASS')
PY
ERR_RE='FATAL EXCEPTION|ANR in io.blueeye|Process: io.blueeye|TransactionTooLargeException|DeadObjectException|OutOfMemory|SQLite.*(error|exception)|Room.*(error|exception)|SecurityException'
echo '--- APP ERRORS ---'; grep -Ei "$ERR_RE" "$OUT/logcat.txt" | tail -n 100 || true
ERR_COUNT="$(grep -Eic "$ERR_RE" "$OUT/logcat.txt" || true)"; echo "LOG_ERROR_COUNT=$ERR_COUNT"; test "$ERR_COUNT" -eq 0
echo 'PHASE3_ROOM_E2E_PASS'
