# Phase 3 final code-quality audit — 2026-09-11

Status: **READ-ONLY SECOND PASS COMPLETE; NO APPLICATION REFACTOR RECOMMENDED BEFORE FINAL FIELD ACCEPTANCE**

Audit source: `40eac7a504d363a05cd6c235c25146e01bb36ff2`

## Why this audit exists

Phase 3 is already behaviorally stabilized. This second pass asks whether obvious God objects, layering violations, tooling blind spots or cleanup opportunities justify one more source change before the final field walk.

Conclusion: **no**. There is real structural debt, but the remaining hotspots are either already intentionally bounded owners or identity/export/settings areas where a late refactor would increase risk without improving the evidence needed for the final walk.

## Verification performed

Local Agent task `tracker-final-structural-audit-v3` ran read-only against exact `40eac7a...` and finished PASS:

- exact SHA verified;
- clean worktree verified before/after;
- all-module Detekt on Temurin JDK 21: **BUILD SUCCESSFUL**;
- `git diff --check`: PASS.

Independent architecture grep also verified:

- Android SDK imports in `core:domain`: `0`;
- feature imports of `io.blueeye.core.data`: `0`;
- feature raw `Color(0xFF...)` / `R.color.*`: `0`;
- XML layouts: `0`;
- scanned legacy Fragment/AsyncTask/LiveData/RxJava references: `0`.

Canonical exact-SHA Quality and Android UI Smoke are also green; this read-only pass did not replace them.

## Repository size snapshot

- Kotlin files scanned: `590`
- Kotlin LOC scanned: `64,028`
- production Kotlin files: `487`
- production Kotlin LOC: `48,831`

Largest production Kotlin files by LOC:

| File | LOC | Approx. functions | Assessment |
| --- | ---: | ---: | --- |
| `DeviceEvidenceFactory.kt` | 733 | 27 | File-level hotspot; much is already separated into helper factories/parsers. Do not label the singleton object alone as a 733-line God object. |
| `DeviceCorrelationStrategy.kt` | 669 | 26 | **Real post-Phase-3 structural hotspot**; several identity heuristics and parsers share one class. High regression risk; do not refactor before field evidence. |
| `DatabaseExporter.kt` | 620 | 27 | Large file, but injectable exporter is only one part; mapping/streaming helper objects account for much of size. Candidate file split later. |
| `SettingsScreen.kt` | 609 | 9 | Compose readability/file-organization debt, not runtime ownership debt. |
| `SettingsViewModel.kt` | 557 | 30 | **Real broad coordinator**; candidate for post-Phase-3 use-case/controller split. |
| `DetailsScreen.kt` | 513 | 9 | Compose organization debt. |
| `RadarScreen.kt` | 506 | 10 | Compose organization debt; current runtime behavior already accepted. |
| `WatchlistScreen.kt` | 501 | 10 | Compose organization debt. |
| `AndroidAlertDispatcher.kt` | 474 | 22 | Large but currently intentional authoritative alert-side-effect owner after the P0 cancellation fix. Keep stable through acceptance. |
| `AddressCarryoverTracker.kt` | 467 | 16 | Identity-critical; refactor only with fixture-backed identity work. |
| `TacticalProcessor.kt` | 456 | 18 | Classifier hotspot; existing regression coverage more valuable than late reshaping. |
| `ScannerService.kt` | 424 | 25 | Large but intentional authoritative scanner lifecycle owner from Phase 2. Keep stable. |
| `SessionCalibrationCard.kt` | 422 | 9 | UI file-size debt. |
| `RadarDeviceItem.kt` | 404 | 10 | UI file-size debt. |
| `BleScanHandler.kt` | 391 | 15 | Large orchestration coordinator; previously and again intentionally deferred because splitting it crosses alert/identity boundaries. |

## God-object judgement

### Do not refactor now

`ScannerService`, `AndroidAlertDispatcher` and `BleScanHandler` are large, but their present ownership boundaries are part of already-tested stability contracts. Moving responsibilities immediately before a field run has negative expected value.

`DeviceEvidenceFactory.kt` looks worst by raw LOC but the file already contains multiple focused helper objects/functions. The correct future improvement is file/package organization, not a blind class split.

Large Compose files are mostly presentation decomposition/readability debt. They are not evidence of hidden BLE/runtime ownership.

### Real candidates after Phase 3

1. **`DeviceCorrelationStrategy`**
   - destructive 30 s carryover;
   - non-destructive long-gap candidate matching;
   - weighted payload/UUID/name/interval matching;
   - same-name corroboration;
   - Apple shadow matching;
   - Microsoft shadow matching;
   - RSSI penalties, sequence heuristics and payload parsing.

   Proposed later boundary: pure matcher/policy components (`DestructiveCarryoverPolicy`, vendor shadow matchers, `FeatureSimilarity`, candidate policy) behind the existing strategy facade, each replayable from field fixtures. Do not change thresholds while extracting.

2. **`SettingsViewModel`**
   - reference-database updates;
   - alert preferences/test alerts;
   - appearance preferences;
   - session calibration/notes;
   - export orchestration;
   - review/watchlist actions;
   - scanner/alert diagnostics aggregation.

   Proposed later boundary: focused interactors/controllers and smaller state producers while preserving one UI state surface.

3. **`DatabaseExporter.kt`**
   - split file organization into export data loading, stream writing, metadata/session mapping and device/sample mapping;
   - preserve schema/version/output byte-for-byte with golden tests during any refactor.

These are maintainability improvements, not Phase 3 blockers.

## Suppression and Detekt debt

Source-level suppression counts observed in this pass include:

- `TooGenericExceptionCaught`: 32
- `SwallowedException`: 19
- `TooManyFunctions`: 11
- `LongParameterList`: 10
- `CyclomaticComplexMethod`: 8

There are six Detekt baseline files with `2,410` accepted entries. The dominant category is `MagicNumber` (`1,587`, about two thirds of the baseline), followed by:

- `ReturnCount`: 195
- `TooGenericExceptionCaught`: 102
- `SwallowedException`: 90
- `ArgumentListWrapping`: 70
- `LoopWithTooManyJumpStatements`: 67
- `NestedBlockDepth`: 62
- `CyclomaticComplexMethod`: 38

Do **not** regenerate or delete the baseline wholesale. After Phase 3, pay down high-signal complexity/exception debt first; `MagicNumber` cleanup has much lower leverage.

## Tooling audit

Existing project-native checks remain the right stack:

- Detekt;
- ktlint;
- Android lint;
- unit tests;
- emulator Android UI Smoke;
- gitleaks;
- physical ADB evidence.

An ephemeral `lizard 1.24.0` probe was installed in `/tmp` and removed after the audit. Java-mode analysis mostly followed generated KAPT Java and was too noisy for Kotlin architecture decisions. It should **not** be added to Gradle or the repository.

### Tooling blind spots / future work

- `core:data/src` and `core:decoders/src` are still excluded from ktlint source checks because of historical formatting debt. Pay this down one module at a time in dedicated formatting-only changes.
- Detekt baselines hide acknowledged legacy debt. Reduce them deliberately alongside focused refactors; never baseline new problems casually.
- Gradle 8.9 reports deprecated features that will become incompatible with Gradle 9.0. Treat this as a dedicated post-stability tooling modernization task.
- Local Agent's shell currently defaults to Oracle JDK 22 even though Temurin 21.0.2 is installed. Detekt 1.23.x rejects JVM target 22. All local Gradle/Detekt tasks must explicitly set `JAVA_HOME="$(/usr/libexec/java_home -v 21)"`. This is an environment trap, not an application defect.

## Optimization judgement

No speculative performance refactor is justified before the field run. The previous full walk reconciled ingest with zero dropped/rejected/failed processing, and the known location latency issue already received a bounded active-fix retry backoff.

The next optimization decision must be evidence-driven from the final walk:

- queue high-water / wait tail;
- processing latency;
- location-fix behavior;
- sample write outcomes;
- DB growth;
- GPS coverage;
- HCI-to-Tracker observation differences.

If these remain healthy, optimize maintainability rather than the ingest algorithm.

## Recommended cleanup order after Phase 3

1. close Phase 3 from field evidence first;
2. split `DeviceCorrelationStrategy` behind stable behavior/fixture tests;
3. split `SettingsViewModel` responsibilities;
4. split `DatabaseExporter.kt` by mapper/writer/data-loader roles with byte/schema golden tests;
5. reduce high-signal Detekt baseline entries (`CyclomaticComplexMethod`, nested control flow, generic/swallowed exceptions);
6. restore ktlint coverage to `core:data`, then `core:decoders`, as formatting-only work;
7. audit Gradle 9 deprecations;
8. only then consider broader Compose file organization.

No application-code change is recommended as part of this audit checkpoint.
