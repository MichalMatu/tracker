#!/usr/bin/env bash
set -euo pipefail

fail() {
  echo "FAIL: $*" >&2
  exit 1
}

if [[ -z "${TRACKER_SANDBOX_ROOT:-}" ]]; then
  DEFAULT_ENV=/mnt/data/tracker-sandbox/env.sh
  if [[ -f "$DEFAULT_ENV" ]]; then
    # shellcheck disable=SC1091
    source "$DEFAULT_ENV"
  else
    fail "TRACKER_SANDBOX_ROOT is not set. Run bootstrap-sandbox.sh and source env.sh first."
  fi
fi

REPO_ROOT=$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)
cd "$REPO_ROOT"

[[ "$(uname -s)" == "Linux" ]] || fail "sandbox flow supports Linux only"
[[ "$(uname -m)" == "x86_64" ]] || fail "sandbox flow supports x86_64 only"
[[ -x ./gradlew ]] || fail "gradlew not found in repository root"
[[ -n "${JAVA_HOME:-}" ]] || fail "JAVA_HOME is not set"
[[ -n "${GRADLE_USER_HOME:-}" ]] || fail "GRADLE_USER_HOME is not set"
[[ -n "${ANDROID_HOME:-}" ]] || fail "ANDROID_HOME is not set"
command -v java >/dev/null 2>&1 || fail "java missing"

JAVA_MAJOR=$(java -XshowSettings:properties -version 2>&1 | awk -F'= ' '/^[[:space:]]*java.version = / {split($2,v,"."); print v[1]; exit}')
[[ "$JAVA_MAJOR" == "21" ]] || fail "JDK 21 required; found $(java -version 2>&1 | head -1)"

printf 'repo=%s\n' "$REPO_ROOT"
printf 'java=%s\n' "$(java -version 2>&1 | head -1)"
printf 'gradle_user_home=%s\n' "$GRADLE_USER_HOME"
printf 'android_home=%s\n' "$ANDROID_HOME"

if [[ -f .sandbox-snapshot/git-sha.txt ]]; then
  printf 'source_snapshot_sha=%s\n' "$(cat .sandbox-snapshot/git-sha.txt)"
else
  printf 'source_snapshot_sha=not-embedded (normal git checkout)\n'
fi

printf 'sandbox_doctor=ok\n'
