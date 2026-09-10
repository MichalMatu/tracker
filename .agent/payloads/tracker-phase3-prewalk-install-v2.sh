#!/usr/bin/env bash
set -euo pipefail
EXPECTED='4e3751598f9df679cd7e8e55482e17fb35fdc622'
REPO='MichalMatu/tracker'
PKG='io.blueeye'
SERIAL_EXPECTED='RFCT70L7E8J'
TARGET_CERT='fff1ada9aa9e2709b4e4bda8333404feae854372df319b77f1f45d7869a61cb6'
KEYSTORE="$HOME/.local/share/blueeye-tracker/signing/tester.p12"
PASSFILE="$HOME/.local/share/blueeye-tracker/signing/tester.password"
ALIAS='blueeye-tester'
OUT='/tmp/tracker-phase3-prewalk-install-v2'
RUNS=(34455536091 34455536134 34455536171 34455536146)

export JAVA_HOME="$(/usr/libexec/java_home -v 21)"
export PATH="$JAVA_HOME/bin:$PATH"
rm -rf "$OUT" && mkdir -p "$OUT"

git fetch --no-tags origin main >/dev/null
HEAD="$(git rev-parse origin/main)"
echo "source_head=$HEAD"
test "$HEAD" = "$EXPECTED"

for run in "${RUNS[@]}"; do
  RUN_JSON="$(gh run view "$run" --repo "$REPO" --json name,status,conclusion,headSha)"
  echo "ci_run_$run=$RUN_JSON"
  python3 -c 'import json,sys; d=json.loads(sys.argv[1]); assert d["status"]=="completed" and d["conclusion"]=="success" and d["headSha"]==sys.argv[2]' "$RUN_JSON" "$EXPECTED"
done

git checkout --detach "$EXPECTED" >/dev/null 2>&1
test "$(git rev-parse HEAD)" = "$EXPECTED"
test -z "$(git status --porcelain)"

SERIAL="$(adb devices | awk 'NR>1 && $2=="device" {print $1}' | head -n1)"
echo "serial=$SERIAL"
test "$SERIAL" = "$SERIAL_EXPECTED"

APKSIGNER="$HOME/Library/Android/sdk/build-tools/36.0.0/apksigner"
if [ ! -x "$APKSIGNER" ]; then APKSIGNER="$(command -v apksigner || true)"; fi
test -x "$APKSIGNER"; test -s "$KEYSTORE"; test -s "$PASSFILE"

CURRENT_PATH="$(adb -s "$SERIAL" shell pm path --user 0 "$PKG" | tr -d '\r' | sed -n 's/^package://p' | head -n1)"
test -n "$CURRENT_PATH"
adb -s "$SERIAL" pull "$CURRENT_PATH" "$OUT/installed-before.apk" >/dev/null
CURRENT_CERT="$("$APKSIGNER" verify --print-certs "$OUT/installed-before.apk" | awk -F': ' '/Signer #1 certificate SHA-256 digest:/{print $2; exit}' | tr 'A-F' 'a-f')"
echo "installed_before_cert_sha256=$CURRENT_CERT"
test "$CURRENT_CERT" = "$TARGET_CERT"

snapshot_counts() {
  local label="$1"
  adb -s "$SERIAL" shell run-as "$PKG" cp databases/tracker_database cache/tracker-prewalk-install-check.db
  adb -s "$SERIAL" exec-out run-as "$PKG" cat cache/tracker-prewalk-install-check.db > "$OUT/$label.db"
  sqlite3 "$OUT/$label.db" 'PRAGMA integrity_check; select count(*) from devices; select count(*) from signal_samples; select count(*) from follow_me_observations;' > "$OUT/$label.counts"
  echo "--- $label db ---"; cat "$OUT/$label.counts"
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
INSTALLED_PATH="$(adb -s "$SERIAL" shell pm path --user 0 "$PKG" | tr -d '\r' | sed -n 's/^package://p' | head -n1)"
adb -s "$SERIAL" pull "$INSTALLED_PATH" "$OUT/installed-after.apk" >/dev/null
INSTALLED_SHA="$(shasum -a 256 "$OUT/installed-after.apk" | awk '{print $1}')"
INSTALLED_CERT="$("$APKSIGNER" verify --print-certs "$OUT/installed-after.apk" | awk -F': ' '/Signer #1 certificate SHA-256 digest:/{print $2; exit}' | tr 'A-F' 'a-f')"
echo "installed_apk_sha256=$INSTALLED_SHA"
echo "installed_cert_sha256=$INSTALLED_CERT"
test "$INSTALLED_SHA" = "$SIGNED_SHA"
test "$INSTALLED_CERT" = "$TARGET_CERT"
snapshot_counts after_install
cmp -s "$OUT/before_install.counts" "$OUT/after_install.counts"

for perm in android.permission.BLUETOOTH_SCAN android.permission.BLUETOOTH_CONNECT android.permission.ACCESS_FINE_LOCATION android.permission.ACCESS_COARSE_LOCATION android.permission.POST_NOTIFICATIONS; do adb -s "$SERIAL" shell pm grant --user 0 "$PKG" "$perm" >/dev/null 2>&1 || true; done
adb -s "$SERIAL" shell am start --user 0 -W -n "$PKG/.MainActivity" >/dev/null
sleep 2
PID="$(adb -s "$SERIAL" shell pidof "$PKG" | tr -d '\r')"
echo "pid_after_launch=$PID"
test -n "$PID"
echo "PHASE3_PREWALK_INSTALL_PASS=$EXPECTED"
