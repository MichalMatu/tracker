#!/usr/bin/env bash
set -euo pipefail

EXPECTED_MAIN='d15f6985371ef3ffe0496d7a14454589d56978ee'
ACCEPTED='40eac7a504d363a05cd6c235c25146e01bb36ff2'
PKG='io.blueeye'
EXPECTED_MODEL='SM-S906B'
EXPECTED_APK='17ebd807c526eda077e9e7f9e96e6306924890e4c201a3c5d3e50f9d76237a65'
EXPECTED_CERT='fb07493cf97b0a84ec410f7f72f12938b7f9d47151546e15c049c49c27807b11'
ROOT="$HOME/agent-workspace/repos/tracker/checkpoints/phase3-field-reacceptance-20260911"
STAMP="$(date -u +%Y%m%dT%H%M%SZ)"
OUT="$ROOT/$STAMP"
mkdir -p "$OUT" "$ROOT"
chmod 700 "$ROOT" "$OUT"
printf '%s\n' "$STAMP" > "$ROOT/LATEST"
chmod 600 "$ROOT/LATEST"
sha(){ shasum -a 256 "$1" | awk '{print $1}'; }

# Source/binding-compatible preflight; no source or app mutation.
git fetch --no-tags origin main >/dev/null
HEAD="$(git rev-parse origin/main)"
test "$HEAD" = "$EXPECTED_MAIN"
test "$(git merge-base "$ACCEPTED" "$HEAD")" = "$ACCEPTED"
test -z "$(git diff --name-only "$ACCEPTED".."$HEAD" | grep -v '^docs/' || true)"
printf '%s\n' "$HEAD" > "$OUT/source-main.txt"
DEVS=( $(adb devices | awk 'NR>1 && $2=="device" {print $1}') )
test "${#DEVS[@]}" -eq 1
SERIAL="${DEVS[0]}"
MODEL="$(adb -s "$SERIAL" shell getprop ro.product.model | tr -d '\r')"
test "$MODEL" = "$EXPECTED_MODEL"
test "$(adb -s "$SERIAL" shell am get-current-user | tr -d '\r')" = '0'
echo "device_model=$MODEL"

# Preserve ADB/logcat before bringing the app to foreground.
date -u +%Y-%m-%dT%H:%M:%SZ > "$OUT/collection-start-utc.txt"
adb -s "$SERIAL" shell dumpsys package "$PKG" > "$OUT/adb-package.txt"
adb -s "$SERIAL" shell dumpsys activity services "$PKG" > "$OUT/adb-services-before.txt" || true
adb -s "$SERIAL" shell dumpsys activity processes "$PKG" > "$OUT/adb-processes-before.txt" || true
adb -s "$SERIAL" shell dumpsys bluetooth_manager > "$OUT/adb-bluetooth-before.txt" || true
adb -s "$SERIAL" logcat -d -v threadtime > "$OUT/logcat-before.txt" || true
PID_BEFORE="$(adb -s "$SERIAL" shell pidof "$PKG" 2>/dev/null | tr -d '\r' | awk '{print $1}' || true)"
if [ -n "$PID_BEFORE" ]; then
  echo 'process_alive_before=1'
  adb -s "$SERIAL" logcat -d -v threadtime --pid="$PID_BEFORE" > "$OUT/logcat-app-before.txt" || true
else
  echo 'process_alive_before=0'
  : > "$OUT/logcat-app-before.txt"
fi
if grep -qi 'ScannerService' "$OUT/adb-services-before.txt"; then echo 'scanner_service_before=1'; else echo 'scanner_service_before=0'; fi

# Installed package identity.
APK_PATH="$(adb -s "$SERIAL" shell pm path --user 0 "$PKG" | tr -d '\r' | sed -n 's/^package://p' | head -n1)"
test -n "$APK_PATH"
adb -s "$SERIAL" pull "$APK_PATH" "$OUT/installed.apk" >/dev/null
APK_SHA="$(sha "$OUT/installed.apk")"
APKSIGNER="$HOME/Library/Android/sdk/build-tools/36.0.0/apksigner"
if [ ! -x "$APKSIGNER" ]; then APKSIGNER="$(command -v apksigner || true)"; fi
test -x "$APKSIGNER"
CERT_SHA="$("$APKSIGNER" verify --print-certs "$OUT/installed.apk" | awk -F': ' '/Signer #1 certificate SHA-256 digest:/{print tolower($2); exit}')"
test "$APK_SHA" = "$EXPECTED_APK"
test "$CERT_SHA" = "$EXPECTED_CERT"
rm -f "$OUT/installed.apk"
echo "installed_apk_sha256=$APK_SHA"
echo "installed_cert_sha256=$CERT_SHA"
VERSION_CODE="$(grep -m1 -o 'versionCode=[0-9]*' "$OUT/adb-package.txt" | cut -d= -f2 || true)"
VERSION_NAME="$(grep -m1 -o 'versionName=[^[:space:]]*' "$OUT/adb-package.txt" | cut -d= -f2 || true)"
echo "versionCode=${VERSION_CODE:-unknown} versionName=${VERSION_NAME:-unknown}"

# Open existing app state, never force-stop/reinstall/clear data.
adb -s "$SERIAL" shell input keyevent KEYCODE_WAKEUP >/dev/null 2>&1 || true
adb -s "$SERIAL" shell wm dismiss-keyguard >/dev/null 2>&1 || true
adb -s "$SERIAL" shell am start -W --user 0 -n "$PKG/.MainActivity" > "$OUT/am-start.txt" 2>&1 || true
sleep 2
PID_AFTER_START="$(adb -s "$SERIAL" shell pidof "$PKG" 2>/dev/null | tr -d '\r' | awk '{print $1}' || true)"
test -n "$PID_AFTER_START"
if [ -n "$PID_BEFORE" ] && [ "$PID_BEFORE" = "$PID_AFTER_START" ]; then echo 'same_process_preserved=1'; else echo 'same_process_preserved=0'; fi

# Supported Session Export Share flow.
UI="$OUT/ui-window.xml"
dump_ui(){
  adb -s "$SERIAL" shell uiautomator dump /sdcard/phase3-field-ui.xml >/dev/null 2>&1 || return 1
  adb -s "$SERIAL" exec-out cat /sdcard/phase3-field-ui.xml > "$UI" 2>/dev/null || return 1
  grep -q '<hierarchy' "$UI"
}
coords(){ dump_ui && python3 tools/ui-smoke/ui_node.py "$UI" "$1" "$2"; }
tap(){ local c x y; c="$(coords "$1" "$2" || true)"; [ -n "$c" ] || return 1; read -r x y <<< "$c"; adb -s "$SERIAL" shell input tap "$x" "$y" >/dev/null; sleep 1; }
root_menu(){
  adb -s "$SERIAL" shell am start -W --user 0 -n "$PKG/.MainActivity" >/dev/null 2>&1 || true
  sleep 1
  local i
  for i in 1 2 3 4 5 6 7 8; do
    if coords content-desc Menu >/dev/null 2>&1; then return 0; fi
    adb -s "$SERIAL" shell input keyevent BACK >/dev/null 2>&1 || true
    sleep 1
  done
  return 1
}
EXPORT_OK=0
if root_menu && tap content-desc Menu && tap text Settings; then
  for _ in 1 2 3 4 5 6; do
    coords text 'Database & Updates' >/dev/null 2>&1 && break
    adb -s "$SERIAL" shell input swipe 540 1750 540 550 350 >/dev/null 2>&1 || true
    sleep 1
  done
  if tap text 'Database & Updates'; then
    for _ in 1 2 3 4 5 6 7 8 9 10 11 12; do
      if coords text 'Session export' >/dev/null 2>&1 && coords text Share >/dev/null 2>&1; then break; fi
      adb -s "$SERIAL" shell input swipe 540 1750 540 500 350 >/dev/null 2>&1 || true
      sleep 1
    done
    EF='cache/session_exports/blueeye-session-export.json'
    adb -s "$SERIAL" shell run-as "$PKG" rm -f "$EF" >/dev/null 2>&1 || true
    if tap text Share; then
      for _ in $(seq 1 180); do
        if adb -s "$SERIAL" shell run-as "$PKG" test -s "$EF" 2>/dev/null; then EXPORT_OK=1; break; fi
        sleep 1
      done
    fi
    if [ "$EXPORT_OK" -eq 1 ]; then
      adb -s "$SERIAL" exec-out run-as "$PKG" cat "$EF" > "$OUT/blueeye-session-export.json"
      python3 -m json.tool "$OUT/blueeye-session-export.json" >/dev/null
      echo "session_export_bytes=$(wc -c < "$OUT/blueeye-session-export.json" | tr -d ' ')"
      echo "session_export_sha256=$(sha "$OUT/blueeye-session-export.json")"
    fi
  fi
fi
if [ "$EXPORT_OK" -ne 1 ]; then echo 'session_export_missing=1'; fi
adb -s "$SERIAL" shell input keyevent BACK >/dev/null 2>&1 || true

# ADB/logcat/UI after export.
dump_ui || true
adb -s "$SERIAL" exec-out screencap -p > "$OUT/final-screen.png" 2>/dev/null || true
adb -s "$SERIAL" shell dumpsys activity services "$PKG" > "$OUT/adb-services-after-export.txt" || true
adb -s "$SERIAL" shell dumpsys activity processes "$PKG" > "$OUT/adb-processes-after-export.txt" || true
adb -s "$SERIAL" shell dumpsys meminfo "$PKG" > "$OUT/adb-meminfo-after-export.txt" || true
adb -s "$SERIAL" logcat -d -v threadtime > "$OUT/logcat-after-export.txt" || true
PID_AFTER_EXPORT="$(adb -s "$SERIAL" shell pidof "$PKG" 2>/dev/null | tr -d '\r' | awk '{print $1}' || true)"
if [ -n "$PID_AFTER_EXPORT" ]; then adb -s "$SERIAL" logcat -d -v threadtime --pid="$PID_AFTER_EXPORT" > "$OUT/logcat-app-after-export.txt" || true; fi

# Room/WAL/SHM; take up to three quick matching tuples and validate on a host-side copy.
ROOM_OK=0
for ATTEMPT in 1 2 3; do
  D='cache/phase3_field_room'
  H="$OUT/room-attempt-$ATTEMPT"
  A="$OUT/room-analysis-$ATTEMPT"
  rm -rf "$H" "$A" && mkdir -p "$H" "$A"
  if adb -s "$SERIAL" shell run-as "$PKG" rm -rf "$D" >/dev/null 2>&1 && \
     adb -s "$SERIAL" shell run-as "$PKG" mkdir -p "$D" >/dev/null 2>&1 && \
     adb -s "$SERIAL" shell run-as "$PKG" cp databases/tracker_database "$D/tracker_database" >/dev/null 2>&1; then
    for S in -wal -shm; do
      if adb -s "$SERIAL" shell run-as "$PKG" test -f "databases/tracker_database$S" 2>/dev/null; then
        adb -s "$SERIAL" shell run-as "$PKG" cp "databases/tracker_database$S" "$D/tracker_database$S" >/dev/null 2>&1 || true
      fi
    done
    adb -s "$SERIAL" exec-out run-as "$PKG" cat "$D/tracker_database" > "$H/tracker_database" 2>/dev/null || true
    for S in -wal -shm; do
      if adb -s "$SERIAL" shell run-as "$PKG" test -f "$D/tracker_database$S" 2>/dev/null; then
        adb -s "$SERIAL" exec-out run-as "$PKG" cat "$D/tracker_database$S" > "$H/tracker_database$S" 2>/dev/null || true
      fi
    done
  fi
  if [ -s "$H/tracker_database" ]; then
    cp "$H"/tracker_database* "$A/"
    if command -v sqlite3 >/dev/null 2>&1 && [ "$(sqlite3 "$A/tracker_database" 'PRAGMA integrity_check;' 2>/dev/null | head -n1 || true)" = 'ok' ]; then
      rm -rf "$OUT/room" && mkdir -p "$OUT/room" && cp "$H"/tracker_database* "$OUT/room/"
      ROOM_OK=1
      break
    fi
  fi
  sleep 1
done
if [ "$ROOM_OK" -eq 1 ]; then
  echo 'room_integrity=ok'
  [ -f "$OUT/room/tracker_database-wal" ] && echo 'room_wal_present=1' || echo 'room_wal_present=0'
  [ -f "$OUT/room/tracker_database-shm" ] && echo 'room_shm_present=1' || echo 'room_shm_present=0'
  for F in "$OUT/room"/tracker_database*; do echo "room_$(basename "$F")_sha256=$(sha "$F")"; done
else
  echo 'room_integrity=failed'
fi

# Bugreport while HCI snoop remains on, then extract btsnoop_hci privately.
adb -s "$SERIAL" shell dumpsys bluetooth_manager > "$OUT/adb-bluetooth-before-bugreport.txt" || true
BUG="$OUT/bugreport.zip"
BUG_OK=0
if adb -s "$SERIAL" bugreport "$BUG" > "$OUT/bugreport-command.txt" 2>&1 && [ -s "$BUG" ]; then BUG_OK=1; fi
echo "bugreport_captured=$BUG_OK"
HCI_OK=0
if [ "$BUG_OK" -eq 1 ]; then
  python3 - "$BUG" "$OUT" <<'PY'
import hashlib,json,os,re,sys,zipfile
bug,out=sys.argv[1:]
with zipfile.ZipFile(bug) as z:
    xs=[i for i in z.infolist() if not i.is_dir() and re.search(r'(btsnoop|snoop.*bluetooth|bluetooth.*snoop)',i.filename,re.I)]
    exact=[i for i in xs if re.search(r'btsnoop_hci(?:\.log)?$',i.filename,re.I)]
    pick=max(exact or xs,key=lambda i:i.file_size,default=None)
    json.dump({'candidate_count':len(xs),'entry':pick.filename if pick else None,'size':pick.file_size if pick else 0},open(os.path.join(out,'hci-extract-meta-private.json'),'w'),indent=2)
    if pick:
        data=z.read(pick)
        open(os.path.join(out,'btsnoop_hci.log'),'wb').write(data)
        open(os.path.join(out,'btsnoop_hci.sha256'),'w').write(hashlib.sha256(data).hexdigest()+'\n')
PY
  [ -s "$OUT/btsnoop_hci.log" ] && HCI_OK=1 || true
fi
echo "hci_snoop_extracted=$HCI_OK"
if [ "$HCI_OK" -eq 1 ]; then
  echo "hci_snoop_bytes=$(wc -c < "$OUT/btsnoop_hci.log" | tr -d ' ')"
  echo "hci_snoop_sha256=$(cat "$OUT/btsnoop_hci.sha256")"
  if command -v capinfos >/dev/null 2>&1; then capinfos -a -e -u -c -s "$OUT/btsnoop_hci.log" > "$OUT/hci-capinfos-private.txt" 2>&1 || true; fi
  if command -v tshark >/dev/null 2>&1; then
    tshark -r "$OUT/btsnoop_hci.log" -T fields -e frame.time_epoch > "$OUT/hci-frame-times-private.txt" 2>/dev/null || true
    echo 'tshark_available=1'
    echo "hci_frame_count=$(grep -c . "$OUT/hci-frame-times-private.txt" || true)"
  else
    echo 'tshark_available=0'
  fi
fi
adb -s "$SERIAL" logcat -d -v threadtime > "$OUT/logcat-final.txt" || true
date -u +%Y-%m-%dT%H:%M:%SZ > "$OUT/collection-end-utc.txt"

python3 - "$OUT" "$EXPORT_OK" "$ROOM_OK" "$BUG_OK" "$HCI_OK" <<'PY'
import hashlib,json,os,sys
out=sys.argv[1]; flags=[int(x) for x in sys.argv[2:]]
def m(rel):
 p=os.path.join(out,rel)
 if not os.path.isfile(p): return None
 h=hashlib.sha256(); n=0
 with open(p,'rb') as f:
  for b in iter(lambda:f.read(1024*1024),b''): h.update(b); n+=len(b)
 return {'bytes':n,'sha256':h.hexdigest()}
json.dump({'session_export_ok':bool(flags[0]),'room_ok':bool(flags[1]),'bugreport_ok':bool(flags[2]),'hci_ok':bool(flags[3]),'artifacts':{'session_export':m('blueeye-session-export.json'),'room_db':m('room/tracker_database'),'room_wal':m('room/tracker_database-wal'),'room_shm':m('room/tracker_database-shm'),'hci_snoop':m('btsnoop_hci.log')}},open(os.path.join(out,'manifest-safe.json'),'w'),indent=2)
PY

echo "checkpoint=$OUT"
echo "collection_flags export=$EXPORT_OK room=$ROOM_OK bugreport=$BUG_OK hci=$HCI_OK"
if [ "$EXPORT_OK" -eq 1 ] && [ "$ROOM_OK" -eq 1 ] && [ "$BUG_OK" -eq 1 ] && [ "$HCI_OK" -eq 1 ]; then
  echo 'PHASE3_FIELD_REACCEPTANCE_COLLECTION_PASS'
  exit 0
fi
echo 'PHASE3_FIELD_REACCEPTANCE_COLLECTION_PARTIAL'
exit 9
