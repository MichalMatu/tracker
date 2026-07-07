# Field MVP implementation status

Branch: `field-mvp-work`

## Completed in this branch

- Production decoder multibindings are trimmed to tracker, beacon, audio, Samsung/Fast Pair/Chipolo-like, generic signal, and public-safety-like evidence scope.
- Smart-home and industrial/environmental decoder modules are detached from the production Hilt `Set<BleBeaconDecoder>`.
- Scanner runtime diagnostics are exposed through the domain `ScannerRuntimeController` and updated from BLE and Classic repository entry points.
- Settings shows a compact Field MVP diagnostics panel with scanner status, last BLE/Classic timestamps, results/minute, notification permission/channel status, and last alert delivery result.
- Settings includes a real system-notification **Test alert** action routed through the same dispatcher as production alerts.
- Follow-Me, watchlist return, public-safety-like, and test alerts now go through one `AlertDispatcher` policy for notification, sound, vibration, heads-up channel, permission, channel, and cooldown handling.
- Session export is enriched with Field MVP scanner and alert diagnostics before copy/share.
- A root `FIELD_SESSION_CHECKLIST.md` documents the first phone test sessions and MVP boundaries.

## Verified locally

- `JAVA_HOME=/Users/michal/.local/jdks/jdk-17.0.19+10/Contents/Home ./gradlew qualityCheck`
- `JAVA_HOME=/Users/michal/.local/jdks/jdk-17.0.19+10/Contents/Home ./gradlew :app:assembleDebug`
- `git diff --check`

## Not verified in this environment

- Real phone screen-off BLE behavior
- Real notification heads-up behavior on a locked phone

Gradle requires a Java 17 toolchain. This machine has Java 17 under `~/.local/jdks`, while the default `java` is newer.

## Known remaining work after local validation

- Wire dropped queue diagnostics directly from the BLE scanner event queue if the local compiler accepts the scanner patch.
- Add/adjust unit tests for the unified dispatcher and Field MVP decoder set.
- Add a dedicated background watchlist scan strategy if the first real phone tests show broad screen-off scanning is unreliable for the target devices.
