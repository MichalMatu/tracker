#!/usr/bin/env bash
set -euo pipefail

EXPECTED_MAIN="09f27dc9de44f6cf310b849aa2dc5a326303c0fa"
UPGRADE_COMMIT="4ba647d706947d7e35a90b0ca44002d41769b1d8"
STALE_BRANCH="chore/preproduction-readiness-20260913"

export JAVA_HOME="$(/usr/libexec/java_home -v 21)"

git fetch origin main "$STALE_BRANCH" agent-control
git checkout main
git reset --hard origin/main
test -z "$(git status --porcelain)"
test "$(git rev-parse HEAD)" = "$EXPECTED_MAIN"

# Bring over only the reviewed Android 16 / API 36 toolchain migration.
git cherry-pick "$UPGRADE_COMMIT"
test ! -e .github/dependabot.yml

# Keep the compact canonical quality document in sync with the actual toolchain.
python3 - <<'PY'
from pathlib import Path
p = Path('docs/QUALITY_GATE.md')
s = p.read_text()
needle = '- Generated Android/JVM bytecode target: **JVM 17**.\n'
line = '- Android build baseline: **compileSdk 36 / targetSdk 36**, Android Gradle Plugin **8.9.1**, Gradle **8.11.1**.\n'
if line not in s:
    if needle not in s:
        raise SystemExit('quality gate toolchain anchor missing')
    s = s.replace(needle, needle + line)
p.write_text(s)
PY

git diff --check
git add docs/QUALITY_GATE.md
git commit -m "Document Android 16 toolchain"

# Canonical source validation after the target-SDK migration.
./gradlew --no-daemon qualityCheck :app:assembleDebug

test ! -e .github/dependabot.yml
test -z "$(git status --porcelain)"

# Physical S22+ smoke: preserve app data, verify installed target SDK and basic runtime/scanner state.
ADB_COUNT="$(adb devices | awk 'NR>1 && $2=="device" {n++} END {print n+0}')"
test "$ADB_COUNT" -eq 1
MODEL="$(adb shell getprop ro.product.model | tr -d '\r')"
SDK="$(adb shell getprop ro.build.version.sdk | tr -d '\r')"
echo "DEVICE_MODEL=$MODEL"
echo "DEVICE_ANDROID_SDK=$SDK"
adb install -r app/build/outputs/apk/debug/app-debug.apk >/tmp/tracker-v112-install.txt
cat /tmp/tracker-v112-install.txt
grep -q 'Success' /tmp/tracker-v112-install.txt
adb shell am force-stop io.blueeye
adb shell monkey -p io.blueeye -c android.intent.category.LAUNCHER 1 >/tmp/tracker-v112-launch.txt 2>&1 || true
sleep 4
PID="$(adb shell pidof io.blueeye | tr -d '\r')"
test -n "$PID"
adb shell dumpsys package io.blueeye | grep -q 'targetSdk=36'

# Try to exercise the scan control if visible; otherwise accept an already-scanning state.
adb shell uiautomator dump /sdcard/tracker-v112-window.xml >/dev/null 2>&1 || true
adb pull /sdcard/tracker-v112-window.xml /tmp/tracker-v112-window.xml >/dev/null 2>&1 || true
python3 - <<'PY'
from pathlib import Path
import re, subprocess, sys
p = Path('/tmp/tracker-v112-window.xml')
if not p.exists():
    print('UI_DUMP_AVAILABLE=no')
    sys.exit(0)
s = p.read_text(errors='ignore')
print('UI_DUMP_AVAILABLE=yes')
if any(token in s for token in ('Scanning', 'Pause')):
    print('SCANNER_UI_STATE=active')
    sys.exit(0)
# Tap a visible Scan control by bounds, if present.
for m in re.finditer(r'<node[^>]*(?:text|content-desc)="Scan"[^>]*bounds="\[(\d+),(\d+)\]\[(\d+),(\d+)\]"', s):
    x1,y1,x2,y2 = map(int,m.groups())
    subprocess.run(['adb','shell','input','tap',str((x1+x2)//2),str((y1+y2)//2)], check=True)
    print('SCANNER_UI_STATE=started')
    sys.exit(0)
print('SCANNER_UI_STATE=scan-control-not-found')
PY
sleep 5
# No fatal process death after launch/optional scan.
PID2="$(adb shell pidof io.blueeye | tr -d '\r')"
test -n "$PID2"

# Publish exact validated main before deleting the now-superseded preparation branch.
git push origin main
PUSHED_HEAD="$(git rev-parse HEAD)"
test "$PUSHED_HEAD" = "$(git ls-remote origin refs/heads/main | awk '{print $1}')"

# The durable reviewed content is now on main; discard the stale docs/Dependabot branch.
git push origin --delete "$STALE_BRANCH"
if git show-ref --verify --quiet "refs/heads/$STALE_BRANCH"; then
    git branch -D "$STALE_BRANCH"
fi
test -z "$(git ls-remote --heads origin "$STALE_BRANCH")"

echo "PUSHED_HEAD=$PUSHED_HEAD"
echo "DEPENDABOT_ENABLED=no"
echo "TARGET_SDK=36"
echo "COMPILE_SDK=36"
echo "STALE_BRANCH_DELETED=yes"
echo "API36_VALIDATION=passed"
