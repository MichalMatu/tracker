# Releases and Artifacts

BlueEye Tracker uses two complementary distribution channels.

## GitHub Releases — persistent tester builds

### Rolling tester release

Use this for the normal phone-testing flow:

- <https://github.com/MichalMatu/tracker/releases/tag/latest-tester>

After every successful **Quality** run on `main`, `.github/workflows/release.yml` automatically moves the `latest-tester` tag to the verified commit and refreshes:

- `BlueEye-Tracker-latest-tester-debug.apk`
- `BlueEye-Tracker-latest-tester-debug.apk.sha256`

The URL stays stable while the APK contents advance with verified `main`.

### Versioned tester releases

For milestone builds, push a version tag such as `v1.0.0-stable-core.1` or manually dispatch the Release workflow with a `v...` tag. The workflow runs `qualityCheck`, builds the APK, and publishes a separate prerelease with a matching checksum.

All current tester builds are **debug-signed**, not production/Play Store artifacts.

## GitHub Actions — exact per-commit artifacts

Every successful push to `main` runs the `Quality` workflow and publishes:

- `tracker-debug-<SHA>` — debug APK
- `tracker-source-<SHA>` — exact source snapshot

Workflow page:

- <https://github.com/MichalMatu/tracker/actions/workflows/quality.yml>

Actions artifacts are ideal for engineering/debugging because the artifact name contains the exact commit SHA, but they expire after the configured retention period.

## Which one should I use?

- **Tester / phone install:** rolling `latest-tester` Release
- **Milestone / shareable version:** versioned `v...` Release
- **Developer / exact SHA reproduction:** GitHub Actions artifacts
- **Production distribution:** not implemented yet; requires stable signing and release policy
