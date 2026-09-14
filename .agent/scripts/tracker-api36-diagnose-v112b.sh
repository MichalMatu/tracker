#!/usr/bin/env bash
set -euo pipefail

git fetch origin main chore/preproduction-readiness-20260913 agent-control >/dev/null 2>&1 || true

echo "LOCAL_BRANCH=$(git branch --show-current)"
echo "LOCAL_HEAD=$(git rev-parse HEAD)"
echo "ORIGIN_MAIN=$(git ls-remote origin refs/heads/main | awk '{print $1}')"
echo "WORKTREE_DIRTY=$(test -n "$(git status --porcelain)" && echo yes || echo no)"

echo "ADB_DEVICES_BEGIN"
adb devices
echo "ADB_DEVICES_END"
ADB_COUNT="$(adb devices | awk 'NR>1 && $2=="device" {n++} END {print n+0}')"
echo "ADB_COUNT=$ADB_COUNT"
if [ "$ADB_COUNT" -eq 1 ]; then
  echo "DEVICE_MODEL=$(adb shell getprop ro.product.model | tr -d '\r')"
  echo "DEVICE_ANDROID_SDK=$(adb shell getprop ro.build.version.sdk | tr -d '\r')"
fi

if git ls-remote --exit-code --heads origin chore/preproduction-readiness-20260913 >/dev/null 2>&1; then
  echo "STALE_BRANCH_PRESENT=yes"
else
  echo "STALE_BRANCH_PRESENT=no"
fi

echo "DEPENDABOT_ON_MAIN=$(git cat-file -e origin/main:.github/dependabot.yml 2>/dev/null && echo yes || echo no)"
