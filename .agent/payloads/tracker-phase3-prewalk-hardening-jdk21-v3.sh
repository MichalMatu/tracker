#!/usr/bin/env bash
set -euo pipefail

export JAVA_HOME="$(/usr/libexec/java_home -v 21)"
export PATH="$JAVA_HOME/bin:$PATH"
printf '%s\n' '--- java ---'
java -version

git fetch origin agent-control >/dev/null
git show origin/agent-control:.agent/payloads/tracker-phase3-prewalk-hardening-v1.sh > /tmp/tracker-phase3-prewalk-hardening-v1.sh
chmod +x /tmp/tracker-phase3-prewalk-hardening-v1.sh
exec /tmp/tracker-phase3-prewalk-hardening-v1.sh
