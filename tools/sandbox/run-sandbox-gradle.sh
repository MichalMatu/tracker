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

# Root gradle.properties is deliberately conservative for the 8 GB Mac.
# The isolated GRADLE_USER_HOME supplies the sandbox resource profile.
exec ./gradlew --parallel --max-workers=3 "$@"
