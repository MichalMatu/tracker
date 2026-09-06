# Releases and Artifacts

BlueEye Tracker uses two complementary distribution channels.

## GitHub Releases — persistent tester builds

Use Releases when a tester needs a stable download URL:

- <https://github.com/MichalMatu/tracker/releases>

The Release workflow builds from a version tag and publishes:

- `BlueEye-Tracker-<tag>-debug.apk`
- `BlueEye-Tracker-<tag>-debug.apk.sha256`

These builds are intentionally marked as **prerelease/tester** builds while stabilization is active. They are debug-signed and are not production/Play Store artifacts.

### Creating a tester release

Preferred path:

1. choose a version tag such as `v1.0.0-stable-core.1`,
2. push the tag,
3. `.github/workflows/release.yml` runs the full quality gate, builds the APK and creates/updates the GitHub Release.

The workflow also supports manual dispatch with an explicit `v...` tag.

## GitHub Actions — exact per-commit artifacts

Every successful push to `main` runs the `Quality` workflow and publishes:

- `tracker-debug-<SHA>` — debug APK
- `tracker-source-<SHA>` — exact source snapshot

Workflow page:

- <https://github.com/MichalMatu/tracker/actions/workflows/quality.yml>

Actions artifacts are ideal for engineering/debugging because the artifact name contains the exact commit SHA, but they expire after the configured retention period.

## Which one should I use?

- **Tester / phone install:** GitHub Releases
- **Developer / exact SHA reproduction:** GitHub Actions artifacts
- **Production distribution:** not implemented yet; requires stable signing and release policy
