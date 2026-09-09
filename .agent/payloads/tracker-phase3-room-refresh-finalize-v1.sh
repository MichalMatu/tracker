#!/usr/bin/env bash
set -euo pipefail
EXPECTED_SOURCE='c1577ff92a0deb6bf14c29d0320d53f3aee87632'
EXPECTED_APK='a84f2ed3b6260b9f1baec5c8955a7a316f070de7208260a5788d46d43edbe2a1'
EXPECTED_CERT='fff1ada9aa9e2709b4e4bda8333404feae854372df319b77f1f45d7869a61cb6'
EXPECTED_PID='31242'
PKG='io.blueeye'
BASE='/tmp/tracker-phase3-room-refresh-e2e-v2'
OUT='/tmp/tracker-phase3-room-refresh-finalize-v1'
rm -rf "$OUT" && mkdir -p "$OUT"
test -s "$BASE/baseline.json"
test -s "$BASE/baseline_db.counts"

git fetch --no-tags origin main >/dev/null
HEAD="$(git rev-parse origin/main)"; echo "source_head=$HEAD"; test "$HEAD" = "$EXPECTED_SOURCE"
SERIAL="$(adb devices | awk 'NR>1 && $2=="device" {print $1}' | head -n1)"; test -n "$SERIAL"; echo "serial=$SERIAL"
PID0="$(adb -s "$SERIAL" shell pidof "$PKG" | tr -d '\r')"; echo "pid_before=$PID0"; test "$PID0" = "$EXPECTED_PID"
adb -s "$SERIAL" shell dumpsys activity services "$PKG" | grep -q ScannerService; echo scanner_service_before=present
APKSIGNER="$(find "$HOME/Library/Android/sdk/build-tools" -type f -name apksigner 2>/dev/null | sort | tail -n1)"; test -x "$APKSIGNER"
APP_PATH="$(adb -s "$SERIAL" shell pm path "$PKG" | tr -d '\r' | sed -n 's/^package://p' | head -n1)"; test -n "$APP_PATH"
adb -s "$SERIAL" pull "$APP_PATH" "$OUT/installed.apk" >/dev/null
APK_SHA="$(shasum -a 256 "$OUT/installed.apk" | awk '{print $1}')"; CERT_SHA="$("$APKSIGNER" verify --print-certs "$OUT/installed.apk" | awk -F': ' '/Signer #1 certificate SHA-256 digest:/{print $2; exit}' | tr 'A-F' 'a-f')"
echo "installed_apk_sha256=$APK_SHA"; echo "installed_cert_sha256=$CERT_SHA"; test "$APK_SHA" = "$EXPECTED_APK"; test "$CERT_SHA" = "$EXPECTED_CERT"

OLD_STAY="$(adb -s "$SERIAL" shell settings get global stay_on_while_plugged_in | tr -d '\r')"
cleanup(){ if [ "$OLD_STAY" = 'null' ] || [ -z "$OLD_STAY" ]; then adb -s "$SERIAL" shell settings delete global stay_on_while_plugged_in >/dev/null 2>&1 || true; else adb -s "$SERIAL" shell settings put global stay_on_while_plugged_in "$OLD_STAY" >/dev/null 2>&1 || true; fi; }
trap cleanup EXIT
adb -s "$SERIAL" shell svc power stayon usb
adb -s "$SERIAL" shell input keyevent KEYCODE_WAKEUP || true
adb -s "$SERIAL" shell wm dismiss-keyguard >/dev/null 2>&1 || true
sleep 1
adb -s "$SERIAL" logcat -d -v threadtime > "$OUT/continuity-logcat.txt" || true

XML="$OUT/window.xml"
dump_ui(){ adb -s "$SERIAL" shell input keyevent KEYCODE_WAKEUP >/dev/null 2>&1 || true; adb -s "$SERIAL" shell wm dismiss-keyguard >/dev/null 2>&1 || true; adb -s "$SERIAL" shell uiautomator dump /sdcard/blueeye-finalize.xml >/dev/null; adb -s "$SERIAL" exec-out cat /sdcard/blueeye-finalize.xml > "$XML"; }
coords(){ dump_ui; python3 tools/ui-smoke/ui_node.py "$XML" "$1" "$2"; }
tap_node(){ local c; c="$(coords "$1" "$2")"; test -n "$c"; set -- $c; adb -s "$SERIAL" shell input tap "$1" "$2"; sleep 0.8; }
scroll_until(){ local label="$1"; for i in 1 2 3 4 5 6 7 8; do if coords text "$label" >/dev/null 2>&1; then return 0; fi; adb -s "$SERIAL" shell input swipe 540 1750 540 550 350; sleep 0.6; done; return 1; }
go_radar(){ adb -s "$SERIAL" shell am start -W -n "$PKG/.MainActivity" >/dev/null; sleep 0.8; if coords content-desc Menu >/dev/null 2>&1; then tap_node content-desc Menu; tap_node text Radar; fi; }
capture_export(){ local dest="$1"; adb -s "$SERIAL" shell am start -W -n "$PKG/.MainActivity" >/dev/null; sleep 0.8; if ! coords content-desc Menu >/dev/null 2>&1; then adb -s "$SERIAL" shell input keyevent BACK || true; sleep 0.5; fi; tap_node content-desc Menu; tap_node text Settings; tap_node text 'Database & Updates'; scroll_until Share; adb -s "$SERIAL" shell run-as "$PKG" rm -f cache/session_exports/blueeye-session-export.json || true; tap_node text Share; for i in 1 2 3 4 5 6 7 8 9 10; do if adb -s "$SERIAL" shell run-as "$PKG" test -s cache/session_exports/blueeye-session-export.json; then break; fi; sleep 1; done; adb -s "$SERIAL" exec-out run-as "$PKG" cat cache/session_exports/blueeye-session-export.json > "$dest"; python3 -m json.tool "$dest" >/dev/null; adb -s "$SERIAL" shell input keyevent BACK || true; sleep 0.8; }
db_snapshot(){ local name="$1"; adb -s "$SERIAL" exec-out run-as "$PKG" tar cf - databases > "$OUT/$name.tar"; mkdir -p "$OUT/$name"; tar xf "$OUT/$name.tar" -C "$OUT/$name"; local db="$OUT/$name/databases/tracker_database"; sqlite3 "$db" 'PRAGMA integrity_check; select count(*) from devices; select count(*) from signal_samples; select count(*) from follow_me_observations;' > "$OUT/$name.counts"; echo "--- $name counts ---"; cat "$OUT/$name.counts"; }

capture_export "$OUT/live.json"
db_snapshot live_db
python3 - "$BASE/baseline.json" "$OUT/live.json" <<'PY'
import json,sys
b=json.load(open(sys.argv[1]))['fieldMvpDiagnostics']['scanner']
l=json.load(open(sys.argv[2]))['fieldMvpDiagnostics']['scanner']
bi=b['ingest']; li=l['ingest']
for prefix,s,i in [('baseline',b,bi),('live',l,li)]:
    print(prefix+'_state='+str(s.get('state')))
    print(prefix+'_startedAt='+str(s.get('startedAt')))
    print(prefix+'_lastBleSeenAt='+str(s.get('lastBleSeenAt')))
    print(prefix+'_rawBleCallbacksTotal='+str(i.get('rawBleCallbacksTotal')))
    print(prefix+'_queueDroppedTotal='+str(i.get('queueDroppedTotal')))
assert 'Running' in str(l.get('state'))
assert l.get('startedAt') == b.get('startedAt'), (b.get('startedAt'), l.get('startedAt'))
assert l.get('lastBleSeenAt') and b.get('lastBleSeenAt') and l['lastBleSeenAt'] > b['lastBleSeenAt']
assert l['lastBleSeenAt'] - l['startedAt'] > 300_000, (l['startedAt'], l['lastBleSeenAt'])
assert li['rawBleCallbacksTotal'] > bi['rawBleCallbacksTotal']
assert li['queueDroppedTotal'] == 0
print('LIVE_BLE_SURVIVED_300S_PASS')
PY
REFRESH_START="$(grep -c 'Refreshing passive BLE scan registration' "$OUT/continuity-logcat.txt" || true)"; REFRESH_DONE="$(grep -c 'Passive BLE scan registration refreshed' "$OUT/continuity-logcat.txt" || true)"; echo "refresh_start_log_count=$REFRESH_START"; echo "refresh_done_log_count=$REFRESH_DONE"

# Stop normally, then export a drained final snapshot.
go_radar
if adb -s "$SERIAL" shell dumpsys activity services "$PKG" | grep -q ScannerService; then tap_node content-desc Scan; sleep 5; fi
if adb -s "$SERIAL" shell dumpsys activity services "$PKG" | grep -q ScannerService; then echo scanner_service_after_stop=present; exit 1; else echo scanner_service_after_stop=absent; fi
PID1="$(adb -s "$SERIAL" shell pidof "$PKG" | tr -d '\r')"; echo "pid_after_stop=$PID1"; test "$PID1" = "$PID0"
capture_export "$OUT/final.json"
db_snapshot final_db
adb -s "$SERIAL" logcat -d -v threadtime > "$OUT/final-logcat.txt" || true
python3 - "$BASE/baseline.json" "$OUT/final.json" "$BASE/baseline_db.counts" "$OUT/final_db.counts" <<'PY'
import json,sys
b=json.load(open(sys.argv[1])); f=json.load(open(sys.argv[2])); bi=b['fieldMvpDiagnostics']['scanner']['ingest']; fi=f['fieldMvpDiagnostics']['scanner']['ingest']
keys=['rawBleCallbacksTotal','enqueueAcceptedTotal','enqueueRejectedTotal','queueDroppedTotal','coalescedTotal','processingStartedTotal','processingSucceededTotal','processingFailedTotal','signalSamplesWrittenTotal','signalSampleWriteFailuresTotal']
d={k:fi[k]-bi[k] for k in keys}
for k in keys: print('delta_'+k+'='+str(d[k]))
print('final_queueDepth='+str(fi['queueDepth']))
assert d['rawBleCallbacksTotal'] > 0
assert d['rawBleCallbacksTotal'] == d['enqueueAcceptedTotal'] + d['coalescedTotal'] + d['enqueueRejectedTotal']
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
ERR_RE='FATAL EXCEPTION|ANR in io.blueeye|Process: io.blueeye|TransactionTooLargeException|DeadObjectException|OutOfMemory|SQLite.*(error|exception)|Room.*(error|exception)|SecurityException'
ERR_COUNT="$(grep -Eic "$ERR_RE" "$OUT/final-logcat.txt" || true)"; echo "log_error_count=$ERR_COUNT"; test "$ERR_COUNT" -eq 0
# Oversized Copy regression: process must stay alive even when export is too large for clipboard.
adb -s "$SERIAL" shell am start -W -n "$PKG/.MainActivity" >/dev/null; sleep 0.8; tap_node content-desc Menu; tap_node text Settings; tap_node text 'Database & Updates'; scroll_until Copy; tap_node text Copy; sleep 2
PID2="$(adb -s "$SERIAL" shell pidof "$PKG" | tr -d '\r')"; echo "pid_after_copy=$PID2"; test "$PID2" = "$PID0"
echo 'PHASE3_ROOM_REFRESH_E2E_PASS'
