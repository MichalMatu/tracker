#!/usr/bin/env bash
set -euo pipefail

EXPECTED_MAIN="def523abd7951958174b58ad5bb4bd885b40881c"

export JAVA_HOME="$(/usr/libexec/java_home -v 21)"

git fetch origin main chore/preproduction-readiness-20260913 agent-control
git checkout main
git pull --ff-only origin main
test -z "$(git status --porcelain)"
test "$(git rev-parse HEAD)" = "$EXPECTED_MAIN"

# Bring over only the useful hardening commits. Dependabot is intentionally excluded.
git cherry-pick \
  2d0c3a79605d34816459b8f272a25e9cff9bb8c4 \
  464bc97b11dd06fed0befc1574014c82fa846ff9 \
  94992224ae63be8c5e2d78f9d1624ecff3596b63

test ! -e .github/dependabot.yml

python3 - <<'PY'
from pathlib import Path

path = Path("docs/QUALITY_GATE.md")
text = path.read_text()
anchor = "## Physical validation\n"
insert = """## Production and privacy release gate

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

"""
if insert not in text:
    if anchor not in text:
        raise SystemExit("quality gate anchor missing")
    text = text.replace(anchor, insert + anchor)
    path.write_text(text)
PY

git diff --check
./gradlew --no-daemon qualityCheck :app:assembleDebug

git add docs/QUALITY_GATE.md
if ! git diff --cached --quiet; then
  git commit -m "Document production privacy gate"
fi

test ! -e .github/dependabot.yml
test -z "$(git status --porcelain)"

git push origin main

echo "PUSHED_HEAD=$(git rev-parse HEAD)"
echo "DEPENDABOT_ENABLED=no"
echo "PREPRODUCTION_HARDENING=passed"
