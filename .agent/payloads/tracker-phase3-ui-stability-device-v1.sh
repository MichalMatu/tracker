#!/usr/bin/env bash
set -euo pipefail

EXPECTED='53cccf4fa39525febf54e0a57f65796efbd51812'
QUALITY_RUN='34430002217'
PKG='io.blueeye'
TARGET_CERT='fff1ada9aa9e2709b4e4bda8333404feae854372df319b77f1f45d7869a61cb6'
KEYSTORE="$HOME/.local/share/blueeye-tracker/signing/tester.p12"
PASSFILE="$HOME/.local/share/blueeye-tracker/signing/tester.password"
ALIAS='blueeye-tester'
OUT='/tmp/tracker-phase3-ui-stability-device-v1'
rm -rf "$OUT" && mkdir -p "$OUT/ci" "$OUT/dumps"

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
test -n "$SERIAL"
echo "serial=$SERIAL"
adb -s "$SERIAL" shell getprop ro.product.model | tr -d '\r' | sed 's/^/model=/'
test "$(adb -s "$SERIAL" shell am get-current-user | tr -d '\r')" = '0'
adb -s "$SERIAL" shell svc power stayon usb >/dev/null 2>&1 || true
adb -s "$SERIAL" shell input keyevent KEYCODE_WAKEUP >/dev/null 2>&1 || true
adb -s "$SERIAL" shell wm dismiss-keyguard >/dev/null 2>&1 || true

APKSIGNER="$HOME/Library/Android/sdk/build-tools/36.0.0/apksigner"
if [ ! -x "$APKSIGNER" ]; then APKSIGNER="$(command -v apksigner || true)"; fi
test -x "$APKSIGNER"
test -s "$KEYSTORE"
test -s "$PASSFILE"

ARTIFACT="tracker-debug-$EXPECTED"
if gh run download "$QUALITY_RUN" -n "$ARTIFACT" -D "$OUT/ci" >/dev/null 2>&1; then
  echo 'apk_source=quality_artifact'
else
  echo 'quality_artifact_download_failed; building exact source locally'
  git checkout --detach "$EXPECTED" >/dev/null 2>&1
  ./gradlew :app:assembleDebug --no-daemon
  cp app/build/outputs/apk/debug/app-debug.apk "$OUT/ci/app-debug.apk"
  echo 'apk_source=local_exact_sha_build'
fi
CI_APK="$(find "$OUT/ci" -type f -name '*.apk' | head -n1)"
test -s "$CI_APK"
CI_SHA="$(shasum -a 256 "$CI_APK" | awk '{print $1}')"
echo "ci_apk_sha256=$CI_SHA"

"$APKSIGNER" sign \
  --ks "$KEYSTORE" \
  --ks-type PKCS12 \
  --ks-key-alias "$ALIAS" \
  --ks-pass "file:$PASSFILE" \
  --out "$OUT/tracker-tester.apk" \
  "$CI_APK" >/dev/null
SIGNED_SHA="$(shasum -a 256 "$OUT/tracker-tester.apk" | awk '{print $1}')"
SIGNED_CERT="$("$APKSIGNER" verify --print-certs "$OUT/tracker-tester.apk" | awk -F': ' '/Signer #1 certificate SHA-256 digest:/{print $2; exit}' | tr 'A-F' 'a-f')"
echo "signed_apk_sha256=$SIGNED_SHA"
echo "signed_cert_sha256=$SIGNED_CERT"
test "$SIGNED_CERT" = "$TARGET_CERT"

# Stop the old process so Room flushes cleanly; this is an update install, not a data reset.
adb -s "$SERIAL" shell am force-stop "$PKG" >/dev/null 2>&1 || true
sleep 1
snapshot_counts() {
  local label="$1"
  local db="$OUT/$label.db"
  adb -s "$SERIAL" shell run-as "$PKG" cp databases/tracker_database cache/tracker-ui-check.db
  adb -s "$SERIAL" exec-out run-as "$PKG" cat cache/tracker-ui-check.db > "$db"
  sqlite3 "$db" 'PRAGMA integrity_check; select count(*) from devices; select count(*) from signal_samples; select count(*) from follow_me_observations;' > "$OUT/$label.counts"
  echo "--- $label db ---"
  cat "$OUT/$label.counts"
  test "$(head -n1 "$OUT/$label.counts")" = 'ok'
}
snapshot_counts before_install

adb -s "$SERIAL" install -r "$OUT/tracker-tester.apk" | tee "$OUT/install.txt"
grep -q 'Success' "$OUT/install.txt"
CURRENT_PATH="$(adb -s "$SERIAL" shell pm path --user 0 "$PKG" | tr -d '\r' | sed -n 's/^package://p' | head -n1)"
test -n "$CURRENT_PATH"
adb -s "$SERIAL" pull "$CURRENT_PATH" "$OUT/installed.apk" >/dev/null
INSTALLED_SHA="$(shasum -a 256 "$OUT/installed.apk" | awk '{print $1}')"
INSTALLED_CERT="$("$APKSIGNER" verify --print-certs "$OUT/installed.apk" | awk -F': ' '/Signer #1 certificate SHA-256 digest:/{print $2; exit}' | tr 'A-F' 'a-f')"
echo "installed_apk_sha256=$INSTALLED_SHA"
echo "installed_cert_sha256=$INSTALLED_CERT"
test "$INSTALLED_SHA" = "$SIGNED_SHA"
test "$INSTALLED_CERT" = "$TARGET_CERT"
snapshot_counts after_install
cmp -s "$OUT/before_install.counts" "$OUT/after_install.counts"
echo 'UPDATE_INSTALL_DATA_PRESERVED_PASS'

for permission in \
  android.permission.BLUETOOTH_SCAN \
  android.permission.BLUETOOTH_CONNECT \
  android.permission.ACCESS_FINE_LOCATION \
  android.permission.ACCESS_COARSE_LOCATION \
  android.permission.POST_NOTIFICATIONS
do
  adb -s "$SERIAL" shell pm grant --user 0 "$PKG" "$permission" >/dev/null 2>&1 || true
done

XML="$OUT/window.xml"
dump_ui() {
  local dest="$1"
  adb -s "$SERIAL" shell uiautomator dump /sdcard/blueeye-ui-stability.xml >/dev/null
  adb -s "$SERIAL" exec-out cat /sdcard/blueeye-ui-stability.xml > "$dest"
}
coords() {
  dump_ui "$XML"
  python3 tools/ui-smoke/ui_node.py "$XML" "$1" "$2"
}
tap_node() {
  local c
  c="$(coords "$1" "$2")"
  test -n "$c"
  set -- $c
  adb -s "$SERIAL" shell input tap "$1" "$2"
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
# Navigate to Radar if the drawer is available.
tap_node content-desc Menu
if coords text Radar >/dev/null 2>&1; then tap_node text Radar; fi
sleep 1
# Start scanner if not already active.
if ! adb -s "$SERIAL" shell dumpsys activity services "$PKG" | grep -q 'ScannerService'; then
  tap_node content-desc Scan
  sleep 8
fi
adb -s "$SERIAL" shell dumpsys activity services "$PKG" | grep -q 'ScannerService'
echo 'scanner_service=present'

# Sample Radar geometry while live values keep changing.
for i in $(seq 1 10); do
  dump_ui "$OUT/dumps/radar-$i.xml"
  sleep 1
 done
python3 - "$OUT/dumps" <<'PY'
import glob,re,sys,xml.etree.ElementTree as ET
root=sys.argv[1]
def ys(path, attr, value):
    tree=ET.parse(path)
    out=[]
    for n in tree.iter('node'):
        if n.attrib.get(attr)==value:
            m=re.match(r'\[(\d+),(\d+)\]\[(\d+),(\d+)\]', n.attrib.get('bounds',''))
            if m: out.append((int(m.group(2))+int(m.group(4)))//2)
    return out
series=[ys(p,'content-desc','Details') for p in sorted(glob.glob(root+'/radar-*.xml'))]
print('radar_details_y_series=', series)
nonempty=[s for s in series if s]
assert nonempty, 'No Details controls found on Radar'
print('RADAR_GEOMETRY_CAPTURE_PASS')
PY

# Prove a control remains clickable during live updates by opening Details.
tap_node content-desc Details
coords content-desc Back >/dev/null
echo 'RADAR_LIVE_TAP_PASS'

# Details: stable labels should not bounce vertically after settling.
sleep 3
for i in $(seq 1 8); do
  dump_ui "$OUT/dumps/details-$i.xml"
  sleep 1
 done
python3 - "$OUT/dumps" <<'PY'
import glob,re,sys,xml.etree.ElementTree as ET
root=sys.argv[1]
labels=('Connection Status','Identity','Activity','Radio')
series={k:[] for k in labels}
for path in sorted(glob.glob(root+'/details-*.xml')):
    tree=ET.parse(path)
    found={}
    for n in tree.iter('node'):
        text=n.attrib.get('text','')
        if text in labels:
            m=re.match(r'\[(\d+),(\d+)\]\[(\d+),(\d+)\]', n.attrib.get('bounds',''))
            if m: found[text]=(int(m.group(2))+int(m.group(4)))//2
    for k in labels:
        if k in found: series[k].append(found[k])
print('details_label_y_series=', series)
checked=0
for k,vals in series.items():
    if len(vals)>=4:
        spread=max(vals)-min(vals)
        print(f'details_{k}_y_spread={spread}')
        assert spread <= 3, f'{k} moved by {spread}px'
        checked+=1
assert checked>=2, 'Not enough stable Details labels visible for geometry check'
print('DETAILS_GEOMETRY_STABLE_PASS')
PY

# Settings diagnostics: rows are fixed one-line geometry even while counters update.
adb -s "$SERIAL" shell input keyevent BACK >/dev/null 2>&1 || true
sleep 1
ensure_root
tap_node content-desc Menu
tap_node text Settings
# Enter Alerts & Collection; diagnostics card is there.
tap_node text 'Alerts & Collection'
sleep 2
# Scroll to diagnostics once, then sample without further scrolling.
for _ in 1 2 3 4 5 6; do
  if coords text 'Runtime profile' >/dev/null 2>&1; then break; fi
  adb -s "$SERIAL" shell input swipe 540 1700 540 550 350
  sleep 1
 done
coords text 'Runtime profile' >/dev/null
for i in $(seq 1 8); do
  dump_ui "$OUT/dumps/settings-$i.xml"
  sleep 1
 done
python3 - "$OUT/dumps" <<'PY'
import glob,re,sys,xml.etree.ElementTree as ET
root=sys.argv[1]
labels=('Runtime profile','Scanner','Raw BLE/min','Queue depth')
series={k:[] for k in labels}
for path in sorted(glob.glob(root+'/settings-*.xml')):
    tree=ET.parse(path)
    for n in tree.iter('node'):
        text=n.attrib.get('text','')
        if text in labels:
            m=re.match(r'\[(\d+),(\d+)\]\[(\d+),(\d+)\]', n.attrib.get('bounds',''))
            if m: series[text].append((int(m.group(2))+int(m.group(4)))//2)
print('settings_label_y_series=', series)
checked=0
for k,vals in series.items():
    if len(vals)>=4:
        spread=max(vals)-min(vals)
        print(f'settings_{k}_y_spread={spread}')
        assert spread <= 3, f'{k} moved by {spread}px'
        checked+=1
assert checked>=2, 'Not enough diagnostics labels visible for geometry check'
print('SETTINGS_GEOMETRY_STABLE_PASS')
PY

PID="$(adb -s "$SERIAL" shell pidof "$PKG" | tr -d '\r')"
test -n "$PID"
echo "final_pid=$PID"
echo 'PHASE3_UI_STABILITY_DEVICE_PASS'
