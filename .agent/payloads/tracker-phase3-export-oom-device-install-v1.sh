#!/usr/bin/env bash
set -euo pipefail
EXPECTED='ee10fd0a080fff61f6b20322b51df7c733259511'
PKG='io.blueeye'
TARGET_CERT='fff1ada9aa9e2709b4e4bda8333404feae854372df319b77f1f45d7869a61cb6'
KEYSTORE="$HOME/.local/share/blueeye-tracker/signing/tester.p12"
PASSFILE="$HOME/.local/share/blueeye-tracker/signing/tester.password"
ALIAS='blueeye-tester'
OUT='/tmp/tracker-phase3-export-oom-device-install-v1'
rm -rf "$OUT" && mkdir -p "$OUT"

git fetch --no-tags origin main >/dev/null
HEAD="$(git rev-parse origin/main)"
echo "source_head=$HEAD"
test "$HEAD" = "$EXPECTED"
git checkout --detach "$EXPECTED" >/dev/null 2>&1

SERIAL="$(adb devices | awk 'NR>1 && $2=="device" {print $1}' | head -n1)"
test "$SERIAL" = 'RFCT70L7E8J'
echo "serial=$SERIAL"
APKSIGNER="$HOME/Library/Android/sdk/build-tools/36.0.0/apksigner"
if [ ! -x "$APKSIGNER" ]; then APKSIGNER="$(command -v apksigner || true)"; fi
test -x "$APKSIGNER"
test -s "$KEYSTORE"
test -s "$PASSFILE"

snapshot_counts() {
  local label="$1"
  adb -s "$SERIAL" shell run-as "$PKG" cp databases/tracker_database cache/tracker-export-fix-check.db
  adb -s "$SERIAL" exec-out run-as "$PKG" cat cache/tracker-export-fix-check.db > "$OUT/$label.db"
  sqlite3 "$OUT/$label.db" 'PRAGMA integrity_check; select count(*) from devices; select count(*) from signal_samples; select count(*) from follow_me_observations;' > "$OUT/$label.counts"
  echo "--- $label db ---"
  cat "$OUT/$label.counts"
  test "$(head -n1 "$OUT/$label.counts")" = 'ok'
}

adb -s "$SERIAL" shell am force-stop "$PKG" >/dev/null 2>&1 || true
sleep 1
snapshot_counts before_install

./gradlew :app:assembleDebug --no-daemon
cp app/build/outputs/apk/debug/app-debug.apk "$OUT/app-debug.apk"
"$APKSIGNER" sign --ks "$KEYSTORE" --ks-type PKCS12 --ks-key-alias "$ALIAS" --ks-pass "file:$PASSFILE" --out "$OUT/tracker-tester.apk" "$OUT/app-debug.apk" >/dev/null
SIGNED_SHA="$(shasum -a 256 "$OUT/tracker-tester.apk" | awk '{print $1}')"
SIGNED_CERT="$("$APKSIGNER" verify --print-certs "$OUT/tracker-tester.apk" | awk -F': ' '/Signer #1 certificate SHA-256 digest:/{print $2; exit}' | tr 'A-F' 'a-f')"
echo "signed_apk_sha256=$SIGNED_SHA"
echo "signed_cert_sha256=$SIGNED_CERT"
test "$SIGNED_CERT" = "$TARGET_CERT"

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
adb -s "$SERIAL" shell am start -W --user 0 -n "$PKG/.MainActivity" >/dev/null
sleep 2
echo 'PHASE3_EXPORT_OOM_DEVICE_INSTALL_PASS'
