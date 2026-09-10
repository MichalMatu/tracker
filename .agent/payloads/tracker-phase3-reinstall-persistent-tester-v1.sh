#!/usr/bin/env bash
set -euo pipefail

EXPECTED='c1577ff92a0deb6bf14c29d0320d53f3aee87632'
QUALITY_RUN='34309681594'
ARTIFACT="tracker-debug-${EXPECTED}"
EXPECTED_CI_SHA='2b7fecf31f4a2031a63db5f70c4139976983f2107d9bd46b8b843ca9834ba45f'
TARGET_CERT='fff1ada9aa9e2709b4e4bda8333404feae854372df319b77f1f45d7869a61cb6'
EXPECTED_SIGNED_SHA='a84f2ed3b6260b9f1baec5c8955a7a316f070de7208260a5788d46d43edbe2a1'
PKG='io.blueeye'
OUT='/tmp/tracker-phase3-reinstall-persistent-tester-v1'
KEYDIR="$HOME/.local/share/blueeye-tracker/signing"
KS="$KEYDIR/tester.p12"
PW="$KEYDIR/tester.password"
ALIAS='blueeye-tester'

rm -rf "$OUT"
mkdir -p "$OUT/ci"

git fetch --no-tags origin main >/dev/null
HEAD="$(git rev-parse origin/main)"
echo "source_head=$HEAD"
test "$HEAD" = "$EXPECTED"

RUN_JSON="$(gh run view "$QUALITY_RUN" --repo MichalMatu/tracker --json status,conclusion,headSha)"
echo "quality_run=$RUN_JSON"
python3 -c 'import json,sys; d=json.loads(sys.argv[1]); assert d["status"]=="completed" and d["conclusion"]=="success" and d["headSha"]==sys.argv[2]' "$RUN_JSON" "$EXPECTED"

gh run download "$QUALITY_RUN" --repo MichalMatu/tracker --name "$ARTIFACT" --dir "$OUT/ci"
CIAPK="$(find "$OUT/ci" -type f -name '*.apk' | head -n1)"
test -s "$CIAPK"
CI_SHA="$(shasum -a 256 "$CIAPK" | awk '{print $1}')"
echo "ci_apk_sha256=$CI_SHA"
test "$CI_SHA" = "$EXPECTED_CI_SHA"

test -s "$KS"
test -s "$PW"
APKSIGNER="$HOME/Library/Android/sdk/build-tools/36.0.0/apksigner"
if [ ! -x "$APKSIGNER" ]; then
  APKSIGNER="$(command -v apksigner || true)"
fi
echo "apksigner=$APKSIGNER"
test -x "$APKSIGNER"

PASS="$(cat "$PW")"
SIGNED="$OUT/BlueEye-Tracker-${EXPECTED}-persistent-tester.apk"
"$APKSIGNER" sign \
  --ks "$KS" \
  --ks-key-alias "$ALIAS" \
  --ks-pass "pass:$PASS" \
  --key-pass "pass:$PASS" \
  --out "$SIGNED" \
  "$CIAPK"

"$APKSIGNER" verify --verbose --print-certs "$SIGNED" > "$OUT/signed.verify.txt"
SIGNED_CERT="$(awk -F': ' '/Signer #1 certificate SHA-256 digest:/{print $2; exit}' "$OUT/signed.verify.txt" | tr 'A-F' 'a-f')"
SIGNED_SHA="$(shasum -a 256 "$SIGNED" | awk '{print $1}')"
echo "signed_cert_sha256=$SIGNED_CERT"
echo "signed_apk_sha256=$SIGNED_SHA"
test "$SIGNED_CERT" = "$TARGET_CERT"
test "$SIGNED_SHA" = "$EXPECTED_SIGNED_SHA"

SERIAL="$(adb devices | awk 'NR>1 && $2=="device" {print $1}' | head -n1)"
test -n "$SERIAL"
echo "serial=$SERIAL"
adb devices -l
CURRENT_USER="$(adb -s "$SERIAL" shell am get-current-user | tr -d '\r')"
echo "current_android_user=$CURRENT_USER"
test "$CURRENT_USER" = '0'

if adb -s "$SERIAL" shell pm path --user 0 "$PKG" 2>/dev/null | grep -q '^package:'; then
  echo 'package_before_install=present'
  adb -s "$SERIAL" install -r "$SIGNED" | tee "$OUT/install.txt"
else
  echo 'package_before_install=absent'
  adb -s "$SERIAL" install "$SIGNED" | tee "$OUT/install.txt"
fi
grep -q 'Success' "$OUT/install.txt"

CURRENT_PATH="$(adb -s "$SERIAL" shell pm path --user 0 "$PKG" | tr -d '\r' | sed -n 's/^package://p' | head -n1)"
test -n "$CURRENT_PATH"
echo "installed_path=$CURRENT_PATH"
adb -s "$SERIAL" pull "$CURRENT_PATH" "$OUT/installed.apk" >/dev/null
INSTALLED_SHA="$(shasum -a 256 "$OUT/installed.apk" | awk '{print $1}')"
INSTALLED_CERT="$("$APKSIGNER" verify --print-certs "$OUT/installed.apk" | awk -F': ' '/Signer #1 certificate SHA-256 digest:/{print $2; exit}' | tr 'A-F' 'a-f')"
echo "installed_apk_sha256=$INSTALLED_SHA"
echo "installed_cert_sha256=$INSTALLED_CERT"
test "$INSTALLED_SHA" = "$SIGNED_SHA"
test "$INSTALLED_CERT" = "$TARGET_CERT"

for perm in \
  android.permission.BLUETOOTH_SCAN \
  android.permission.BLUETOOTH_CONNECT \
  android.permission.ACCESS_FINE_LOCATION \
  android.permission.ACCESS_COARSE_LOCATION \
  android.permission.POST_NOTIFICATIONS; do
  adb -s "$SERIAL" shell pm grant --user 0 "$PKG" "$perm" >/dev/null 2>&1 || true
done

adb -s "$SERIAL" shell am start --user 0 -W -n "$PKG/.MainActivity" >/dev/null
sleep 2
PID="$(adb -s "$SERIAL" shell pidof "$PKG" | tr -d '\r')"
echo "pid_after_launch=$PID"
test -n "$PID"

echo 'PHASE3_PERSISTENT_TESTER_REINSTALL_PASS'
