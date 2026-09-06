#!/usr/bin/env bash
set -euo pipefail

if [[ -z "${TRACKER_SANDBOX_ROOT:-}" ]]; then
  echo "TRACKER_SANDBOX_ROOT is not set. Run bootstrap-sandbox.sh and source its env.sh first." >&2
  exit 2
fi

if [[ ! -x ./gradlew ]]; then
  echo "Run this script from the Tracker repository root." >&2
  exit 2
fi

# The sandbox has no reliable direct network. The restored Gradle/Android pack is
# authoritative, so fail fast instead of attempting downloads.
workers=3
for arg in "$@"; do
  case "$arg" in
    qualityCheck|:app:assembleDebug) workers=2 ;;
  esac
done

# Focused module tests get 3 workers for speed. Broad lint/detekt/test/APK gates use
# 2 workers to stay below the ~5.8 GiB sandbox memory ceiling.
exec ./gradlew --offline --parallel --max-workers="$workers" "$@"
