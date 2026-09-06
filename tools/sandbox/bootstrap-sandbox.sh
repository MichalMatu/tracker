#!/usr/bin/env bash
set -euo pipefail

ROOT=${1:-$PWD/.sandbox}
SCRIPT_DIR=$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)
REPO_ROOT=$(cd "$SCRIPT_DIR/../.." && pwd)

mkdir -p "$ROOT"/{gradle-home,android-sdk,work,logs,tmp}
cp "$REPO_ROOT/tools/sandbox/gradle-sandbox.properties" "$ROOT/gradle-home/gradle.properties"

JAVA_DEFAULT=/usr/lib/jvm/java-21-openjdk-amd64
cat > "$ROOT/env.sh" <<ENV
export TRACKER_SANDBOX_ROOT="$ROOT"
export GRADLE_USER_HOME="$ROOT/gradle-home"
export ANDROID_HOME="$ROOT/android-sdk"
export ANDROID_SDK_ROOT="$ROOT/android-sdk"
export TMPDIR="$ROOT/tmp"
export JAVA_HOME="${JAVA_HOME:-$JAVA_DEFAULT}"
export PATH="\$JAVA_HOME/bin:\$PATH"
ENV

printf 'Tracker sandbox prepared at %s\n' "$ROOT"
printf 'Next: source %s/env.sh\n' "$ROOT"
