#!/usr/bin/env bash
set -euo pipefail

EXPECTED_MAIN="def523abd7951958174b58ad5bb4bd885b40881c"
export JAVA_HOME="$(/usr/libexec/java_home -v 21)"

git fetch origin main chore/preproduction-readiness-20260913 agent-control
git checkout main
git reset --hard origin/main
test -z "$(git status --porcelain)"
test "$(git rev-parse HEAD)" = "$EXPECTED_MAIN"

# Keep only the approved hardening commits. Dependabot is intentionally excluded.
git cherry-pick \
  2d0c3a79605d34816459b8f272a25e9cff9bb8c4 \
  464bc97b11dd06fed0befc1574014c82fa846ff9 \
  94992224ae63be8c5e2d78f9d1624ecff3596b63

test ! -e .github/dependabot.yml

python3 - <<'PY'
from pathlib import Path
p = Path('docs/QUALITY_GATE.md')
s = p.read_text()
anchor = '## Physical validation\n'
insert = '''## Production and privacy release gate

Before calling any artifact production-ready, verify the exact candidate SHA with the normal quality/build gates plus all of the following:

- build a release artifact deliberately; tester/debug APKs are not production artifacts;
- keep production signing keys and signing properties outside Git;
- use a monotonically increasing `versionCode` and an intentional `versionName`;
- verify current Play/Android target-SDK and build-tool requirements before release;
- review Bluetooth/location/notification permissions and Play Data safety against actual runtime behavior;
- keep local BLE/location observation data out of Android backup and repository history;
- review global cleartext-network allowance before production distribution;
- run release-variant checks plus physical-device BLE/background acceptance on the exact release candidate.

Repository hardening rules: `.gitignore` must cover signing material, local environment secrets and private capture/database artifacts; `CODEOWNERS` may define default review ownership. Dependency-update bots are intentionally not enabled.

'''
if insert not in s:
    if anchor not in s:
        raise SystemExit('quality gate anchor missing')
    p.write_text(s.replace(anchor, insert + anchor))
PY

git diff --check
git add docs/QUALITY_GATE.md
git commit -m "Document production privacy gate"

# Canonical ktlint currently reports formatting debt in the Radar module. Apply
# its formatter and ensure it touches only the exact known presentation/tests.
./gradlew --no-daemon :feature:radar:ktlintFormat

mapfile_cmd=''
changed="$(git diff --name-only | sort)"
allowed="$(printf '%s\n' \
  feature/radar/src/main/java/io/blueeye/feature/radar/presentation/LiveNearbyScreen.kt \
  feature/radar/src/main/java/io/blueeye/feature/radar/presentation/LiveNearbySignalHistory.kt \
  feature/radar/src/main/java/io/blueeye/feature/radar/presentation/ProtocolNoiseGrouping.kt \
  feature/radar/src/main/java/io/blueeye/feature/radar/presentation/RadarProtocolGroupCard.kt \
  feature/radar/src/main/java/io/blueeye/feature/radar/presentation/RadarScreen.kt \
  feature/radar/src/main/java/io/blueeye/feature/radar/presentation/RadarUiCardOrder.kt \
  feature/radar/src/test/java/io/blueeye/feature/radar/presentation/LiveNearbyAssemblerTest.kt \
  feature/radar/src/test/java/io/blueeye/feature/radar/presentation/RadarProtocolGroupMapperTest.kt | sort)"
if [ "$changed" != "$allowed" ]; then
  echo "Unexpected ktlintFormat file set" >&2
  echo "CHANGED:" >&2
  printf '%s\n' "$changed" >&2
  echo "ALLOWED:" >&2
  printf '%s\n' "$allowed" >&2
  exit 1
fi

git add feature/radar/src/main/java/io/blueeye/feature/radar/presentation \
        feature/radar/src/test/java/io/blueeye/feature/radar/presentation
git commit -m "Format radar presentation"

git diff --check
./gradlew --no-daemon qualityCheck :app:assembleDebug

test ! -e .github/dependabot.yml
test -z "$(git status --porcelain)"
git push origin main

echo "PUSHED_HEAD=$(git rev-parse HEAD)"
echo "DEPENDABOT_ENABLED=no"
echo "HARDENING_VALIDATION=passed"
