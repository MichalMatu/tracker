#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR=$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)
PROFILE=${1:-quality}
if [[ $# -gt 0 ]]; then
  shift
fi

case "$PROFILE" in
  doctor)
    exec "$SCRIPT_DIR/sandbox-doctor.sh" "$@"
    ;;
  quality|check)
    exec "$SCRIPT_DIR/run-sandbox-gradle.sh" qualityCheck "$@"
    ;;
  build)
    exec "$SCRIPT_DIR/run-sandbox-gradle.sh" :app:assembleDebug "$@"
    ;;
  full)
    "$SCRIPT_DIR/run-sandbox-gradle.sh" qualityCheck "$@"
    exec "$SCRIPT_DIR/run-sandbox-gradle.sh" :app:assembleDebug "$@"
    ;;
  gradle)
    if [[ $# -eq 0 ]]; then
      echo "Usage: $0 gradle <gradle-task> [args...]" >&2
      exit 2
    fi
    exec "$SCRIPT_DIR/run-sandbox-gradle.sh" "$@"
    ;;
  *)
    echo "Unknown sandbox profile: $PROFILE" >&2
    echo "Profiles: doctor, quality, check, build, full, gradle <task...>" >&2
    exit 2
    ;;
esac
