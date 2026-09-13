#!/usr/bin/env bash
set -euo pipefail

EXPECTED_HEAD="a468a0b2cac0df4f8dbad7b42956a0a22879a512"
BRANCH="chore/preproduction-readiness-20260913"

[ "$(git rev-parse HEAD)" = "$EXPECTED_HEAD" ]
[ "$(git rev-parse origin/$BRANCH)" = "$EXPECTED_HEAD" ]
[ -z "$(git status --porcelain)" ]

python3 - <<'PY'
from pathlib import Path

def replace_exact(path, old, new, expected=1):
    p = Path(path)
    text = p.read_text()
    count = text.count(old)
    if count != expected:
        raise SystemExit(f"{path}: expected {expected} occurrences of {old!r}, found {count}")
    p.write_text(text.replace(old, new))

replace_exact("gradle/libs.versions.toml", 'agp = "8.7.3"', 'agp = "8.9.1"')
replace_exact("gradle/wrapper/gradle-wrapper.properties", 'gradle-8.9-bin.zip', 'gradle-8.11.1-bin.zip')

android_modules = [
    "app/build.gradle.kts",
    "core/ui/build.gradle.kts",
    "core/data/build.gradle.kts",
    "core/decoders/build.gradle.kts",
    "feature/radar/build.gradle.kts",
    "feature/details/build.gradle.kts",
    "feature/settings/build.gradle.kts",
    "feature/watchlist/build.gradle.kts",
]
for path in android_modules:
    replace_exact(path, "compileSdk = 34", "compileSdk = 36")

replace_exact("app/build.gradle.kts", "targetSdk = 33", "targetSdk = 36")
replace_exact("app/src/main/AndroidManifest.xml", 'tools:targetApi="34"', 'tools:targetApi="36"')
PY

git diff --check

grep -R --line-number --fixed-strings 'compileSdk = 34' app core feature --include='build.gradle.kts' && {
  echo "OLD_COMPILE_SDK_REMAINS" >&2
  exit 1
} || true
! grep -R --line-number --fixed-strings 'targetSdk = 33' app --include='build.gradle.kts'

echo PREPROD_V61_PATCH_APPLIED
