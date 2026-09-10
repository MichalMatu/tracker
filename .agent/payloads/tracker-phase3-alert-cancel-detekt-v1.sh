#!/usr/bin/env bash
set -euo pipefail

EXPECTED_SHA="3f49b3456d81c51a540c2d472c1fa456c9a213d6"
BRANCH="phase3-alert-cancellation-v1"

git fetch origin "$BRANCH" >/dev/null
[[ "$(git rev-parse origin/$BRANCH)" == "$EXPECTED_SHA" ]]
[[ "$(git rev-parse HEAD)" == "$EXPECTED_SHA" ]]
[[ -z "$(git status --porcelain)" ]]

export JAVA_HOME="$(/usr/libexec/java_home -v 21)"
export PATH="$JAVA_HOME/bin:$PATH"
java -version

set +e
./gradlew :core:data:detekt --rerun-tasks --console=plain > /tmp/tracker-alert-cancel-detekt-v1.log 2>&1
RC=$?
set -e
cat /tmp/tracker-alert-cancel-detekt-v1.log
printf 'detekt_exit=%s\n' "$RC"

for report in core/data/build/reports/detekt/detekt.txt core/data/build/reports/detekt/detekt.xml; do
  if [[ -f "$report" ]]; then
    echo "--- $report ---"
    cat "$report"
  fi
done

exit 0
