#!/usr/bin/env bash
set -euo pipefail

# Read changed paths from stdin and print the narrowest useful first Gradle gate.
# Example:
#   git diff --name-only BASE...HEAD | tools/sandbox/select-gate.sh

files=$(cat)
if [[ -z "$files" ]]; then
  echo "./gradlew help"
  exit 0
fi

need_app=0
need_core_data=0
need_domain=0
need_model=0
need_decoders=0
need_radar=0
need_settings=0
need_details=0
need_watchlist=0

while IFS= read -r f; do
  case "$f" in
    app/*) need_app=1 ;;
    core/data/*) need_core_data=1 ;;
    core/domain/*) need_domain=1 ;;
    core/model/*) need_model=1 ;;
    core/decoders/*) need_decoders=1 ;;
    feature/radar/*) need_radar=1 ;;
    feature/settings/*) need_settings=1 ;;
    feature/details/*) need_details=1 ;;
    feature/watchlist/*) need_watchlist=1 ;;
    gradle/*|build.gradle.kts|settings.gradle.kts|gradle.properties) need_app=1 ;;
  esac
done <<< "$files"

args=()
((need_model)) && args+=(":core:model:test")
((need_domain)) && args+=(":core:domain:test")
((need_decoders)) && args+=(":core:decoders:testDebugUnitTest")
((need_core_data)) && args+=(":core:data:testDebugUnitTest")
((need_radar)) && args+=(":feature:radar:testDebugUnitTest")
((need_settings)) && args+=(":feature:settings:testDebugUnitTest")
((need_details)) && args+=(":feature:details:testDebugUnitTest")
((need_watchlist)) && args+=(":feature:watchlist:testDebugUnitTest")
((need_app)) && args+=(":app:assembleDebug")

if ((${#args[@]} == 0)); then
  echo "git diff --check"
else
  printf './gradlew'
  printf ' %q' "${args[@]}"
  printf '\n'
fi
