# Pipeline Audit

Date: 2026-06-21

Scope: current code path from Bluetooth observation to UI. This audit does not add product features. It identifies where the app creates noise, loses data, overstates confidence, or hides context from the user.

## Pipeline Map

| Stage | Current implementation | What moves forward | Main gap |
| --- | --- | --- | --- |
| Scanner service | `core/data/.../service/ScannerService.kt` | Starts foreground scan service, BLE scan, and opportunistic Classic discovery | Passive scanning is the default; automatic active GATT collection is explicit opt-in, and periodic RFCOMM remains disabled |
| BLE scan source | `core/data/.../scanner/source/BleScanSource.kt` | Android `ScanResult` from low-latency unfiltered BLE scan | High-volume input; no mode for quiet passive MVP |
| BLE extraction | `core/data/.../scanner/extractor/ScanResultExtractor.kt` | MAC, RSSI, name, all manufacturer records, service UUIDs, service data, appearance, raw bytes | Evidence sources are preserved structurally, but general non-alert classifier evidence is still not a first-class domain contract |
| BLE/Classic orchestration | `core/data/.../scanner/manager/BleScanner.kt`, `core/data/.../repository/handler/ble/BleScanHandler.kt`, `core/data/.../repository/handler/classic/ClassicScanHandler.kt` | Mutable BLE `ScanDataContext` through MAC resolver, classifier, enrichers, scoring, alerts, persistence; Classic results and SDP UUIDs into repository/persistence | Alerts still happen inside normal scan handling; active probing was removed from passive scan handling |
| MAC correlation | `MacAddressResolver.kt`, `AddressCarryoverTracker.kt` | Fingerprint may replace MAC for random addresses; carryover reason, confidence, feature summary, and user carryover verdict are persisted | Dedicated carryover verdicts are visible in review/UI; remaining gap is richer inconclusive/merge-correction workflow |
| Classification/enrichment | `ScanResultClassifier.kt`, `DeviceEnricher.kt`, vendor/tactical strategies | `deviceType`, `vendorModel`, `beaconType`, tactical flags, sensor data | Classification result is mostly a label; source and confidence are lost |
| Active probing | `BleConnectionManager.kt`, `RfcommProbeService.kt`, `ActiveCollectionRepository.kt` | User-triggered/opt-in GATT collection data and infrastructure for future RFCOMM probing | Active GATT has an explicit UI/policy boundary; periodic RFCOMM still needs separate opt-in before use |
| Persistence | `DevicePersister.kt`, `ClassicDevicePersister.kt`, `DeviceEntity.kt`, `SignalSampleEntity.kt`, `FollowMeObservationEntity.kt`, `AlertEvidenceEventEntity.kt` | Last device row, throttled signal samples, append-only Follow-Me score snapshots, append-only watchlist/public-safety/Follow-Me alert evidence events, service-level cleanup | General non-alert classifier evidence is still derived from latest device state |
| Scoring | `FollowMeScoreCalculator.kt`, `FollowMeSessionManager.kt`, `AlertDecisionEngine.kt` | `followingScore`, `trackingStatus`, persisted score components, bounded Follow-Me observation history | Score evolution is reviewable in Details/export, session export uses observed duration, and queued Follow-Me devices can be calibrated without reading JSON |
| Alerts | `TacticalAlertService.kt`, `TrackerAlertService.kt`, `AlertEvidenceEventRecorder.kt` | Vibration/notification/in-memory tactical detections plus durable watchlist/public-safety/Follow-Me alert evidence events | Alert history is reviewable in Details and export; Radar still needs evidence-based grouping |
| UI | `RadarViewModel.kt`, `RadarUiMapper.kt`, `RadarUiSectionMapper.kt`, `RadarScreen.kt` | Evidence/status-based Radar sections with tabs: Watchlist, Suspicious, Public Safety Signals, Nearby, Unknown / Noise | UI grouping exists; remaining gaps are long-session review workflows and better review affordances for identity/carryover decisions |

## P0 Findings

### P0-1: Evidence is first-class, but some evidence is latest-state derived

Files:

- `core/model/src/main/kotlin/io/blueeye/core/model/DetectionEvidence.kt`
- `core/data/src/main/java/io/blueeye/core/data/db/entity/DeviceEntity.kt`
- `core/model/src/main/kotlin/io/blueeye/core/model/Device.kt`
- `core/data/src/main/java/io/blueeye/core/data/evidence/DeviceEvidenceFactory.kt`
- `feature/radar/src/main/java/io/blueeye/feature/radar/presentation/RadarUiModels.kt`

`DetectionEvidence`, `EvidenceSource`, `DetectionConfidence`, and `EvidenceProvenance` are now domain contracts, and `Device` carries a list of evidence items into Radar, Details, watchlist, export, and alert history surfaces. UI labels can now show `source`, `confidence`, `reasonText`, raw value, parsed value, and passive/active provenance.

Impact: user-facing labels no longer have to rely on final device type alone. Axon/public-safety-like, tracker, watchlist, identity continuity, Follow-Me, generic name, inferred model, and service UUID context can be explained from evidence.

Remaining gap: general non-alert classifier evidence is still mostly derived deterministically from the latest `DeviceEntity` state by `DeviceEvidenceFactory`. Alert-worthy events are persisted in append-only `alert_evidence_events`, but ordinary classification evidence does not yet have the same historical event stream. Inferred model evidence is now surfaced as low-confidence identity context so `predictedModel` is no longer an unexplained UI field.

Required next work: keep mapping classifier outputs into deterministic evidence, then add session review/export flows that make latest-state evidence and historical alert evidence easy to compare.

### P0-2: BLE extraction drops evidence before classification

Files:

- `core/data/src/main/java/io/blueeye/core/scanner/extractor/ScanResultExtractor.kt`
- `core/data/src/main/java/io/blueeye/core/data/evidence/AdvertisementEvidenceParser.kt`
- `core/data/src/main/java/io/blueeye/core/data/evidence/DeviceEvidenceFactory.kt`

The extractor keeps Android's legacy `manufacturerId`/`manufacturerData` convenience fields for persistence, but also preserves all manufacturer records as `manufacturerDataById` and all service-data records as `serviceDataByUuid`. `AdvertisementEvidenceParser` recovers all Manufacturer Specific Data company IDs plus advertised/service-data UUIDs from `lastRawData`, so Radar/Details evidence no longer depends only on the first Android convenience manufacturer field.

Impact: UI evidence is less likely to miss useful passive signals, and BLE scan classification now receives the structured manufacturer/service-data maps. Apple Continuity no longer depends on Apple being the first manufacturer record, known Fast Pair/service-data fingerprints can influence scan type before fallback name/vendor heuristics, and vendor strategy classification now receives `VendorScanInput` with all manufacturer records plus service UUIDs. The vendor enricher no longer falls back to the legacy single `manufacturerId`/`manufacturerData` pair, and service-only Samsung SmartThings/Quick Share evidence can be classified without requiring a Samsung manufacturer payload.

Status update: `BeaconDecoderManager` and `BleBeaconDecoder` now use `BleBeaconScanInput`. The manager tries every manufacturer record as a separate candidate before falling back to raw/service-only decoding, so beacon/sensor decoding no longer depends on Android's selected manufacturer payload.

Required next work: continue converting general classifier outputs into durable first-class evidence rather than final labels.

Status update: non-tactical name and service UUID classifier matches now produce
neutral device-type evidence. They explain generic labels such as headphones or
wearables as classification context, preserve passive/active provenance, and do
not become risk claims.

Status update: BLE appearance AD structures are decoded from persisted raw
advertising bytes and surfaced as low-confidence `APPEARANCE` evidence. This
explains scan-time classifications such as watch, phone, laptop, tag, wearable,
or headphones without turning them into alert-worthy risk evidence.

Status update: Classic Bluetooth Class of Device metadata is surfaced as
low-confidence `CLASS_OF_DEVICE` evidence with `CLASSIC_DISCOVERY` provenance.
This explains Classic-derived labels such as headphones, phone, laptop, TV, or
wearable without treating the CoD field as proof of risk.

### P0-3: Classic Bluetooth SDP UUIDs are now threaded opportunistically

Files:

- `core/data/src/main/java/io/blueeye/core/scanner/source/ClassicScanSource.kt`
- `core/data/src/main/java/io/blueeye/core/scanner/manager/BleScanner.kt`
- `core/data/src/main/java/io/blueeye/core/data/repository/handler/classic/ClassicScanHandler.kt`
- `core/data/src/main/java/io/blueeye/core/data/repository/handler/classic/ClassicScanDataContext.kt`
- `core/data/src/main/java/io/blueeye/core/data/repository/handler/classic/ClassicDevicePersister.kt`

`ClassicScanSource` callback includes `List<ParcelUuid>?`, and `ACTION_UUID` sends those UUIDs. `BleScanner.ScanEvent.ClassicResult` now carries Classic observations and maps SDP UUIDs into repository `handleClassicDiscovery`. `ClassicScanDataContext` preserves the UUID list, and `ClassicDevicePersister` stores/merges it into `DeviceEntity.gattServices`.

Impact: Classic/BLE dedup and classification can use one of the strongest available mixed-protocol signals. This helps headphones and other dual-stack devices avoid duplicate BLE/Classic rows when SDP UUIDs overlap with recent BLE services. Details/export now resolve persisted Classic UUID lists through the same domain-facing service resolver used for live GATT services. `DeviceEvidenceFactory` also emits neutral low-confidence `SERVICE_UUID` evidence for observed service UUIDs that do not match tactical/public-safety registries, so Classic SDP UUIDs remain visible as identity context without becoming risk signals.

Status update: `DetectionEvidence.provenance` now distinguishes `BLE_ADVERTISEMENT`, `CLASSIC_SDP`, and `ACTIVE_GATT` for service UUID evidence. Details and generated evidence text describe Classic SDP as opportunistic because Android can reject discovery while the screen is off or Bluetooth resources are busy. Details and session export surface that provenance, and alert evidence history stores it durably. Identity/carryover evidence uses `FOLLOW_ME_ANALYSIS` provenance and includes persisted matcher context when available.

### P0-4: Passive scan is default; active collection is explicit

Files:

- `core/data/src/main/java/io/blueeye/service/ScannerService.kt`
- `core/data/src/main/java/io/blueeye/core/data/repository/handler/ble/BleScanHandler.kt`
- `core/domain/src/main/java/io/blueeye/core/domain/repository/ActiveCollectionRepository.kt`
- `core/data/src/main/java/io/blueeye/core/scanner/rfcomm/RfcommProbeService.kt`

Starting the scanner service now starts passive BLE scanning and Classic discovery only. `BleScanHandler` queues automatic GATT collection only when `ActiveCollectionRepository.autoActiveProbeEnabled` is enabled by the user through Radar/Settings confirmation, and `ScannerService` no longer starts periodic RFCOMM probing by default.

Impact: an MVP that claims local Bluetooth monitoring should default to passive observation. Active GATT/RFCOMM touches other devices, changes battery/privacy characteristics, and can make UI labels look stronger than passive evidence supports.

Status update: active probe evidence now carries explicit `ACTIVE_GATT` or
`ACTIVE_RFCOMM` provenance in addition to `isPassive=false`, so Details/export
can distinguish active collection from passive observations. Session export
schema `18` also adds `activeProbeDataDeviceCount` and
`activeProbeStatusCounts`, so review exports show how much active collection
actually produced session data. Session review now warns when active collection
was enabled but the session has no active probe data, without blocking passive
review/export.

Required next work: keep default passive, keep active evidence marked as active in UI/export, and do not re-enable periodic RFCOMM without a separate explicit opt-in boundary.

### P0-5: Tactical/Axon classification has labels without durable confidence evidence

Files:

- `core/data/src/main/java/io/blueeye/core/data/classifier/tactical/TacticalProcessor.kt`
- `core/data/src/main/java/io/blueeye/core/data/classifier/vendor/strategy/TacticalStrategy.kt`
- `core/data/src/main/java/io/blueeye/core/data/classifier/vendor/tactical/TacticalNameMatcher.kt`
- `core/data/src/main/java/io/blueeye/core/data/classifier/vendor/TacticalOuiRegistry.kt`
- `core/data/src/main/java/io/blueeye/core/data/repository/handler/ble/enricher/TacticalEnricher.kt`
- `core/data/src/main/java/io/blueeye/core/alert/TacticalAlertService.kt`

The OUI/MSD/fallback tactical path has confidence internally, and the pipeline carries the emitted `DetectionEvidence` into bounded append-only `alert_evidence_events` for public-safety-like signals. Name-based tactical classification now uses shared `TacticalNameMatcher` logic, emits explicit `EvidenceSource.NAME`, and caps name-only confidence at `MEDIUM` in both live `TacticalProcessor` output and durable `DeviceEvidenceFactory` evidence.

Impact: Details can review recent public-safety-like alert evidence after the live in-memory detection expires, and name-only matches no longer become unproven tactical labels. OUI/manufacturer/service/payload evidence remains higher-confidence only when the exact source is shown. Session Review and export now queue public-safety-like signals with inline `Known safe` / `False positive` calibration actions, while the copy still describes them as classification evidence rather than confirmed presence.

Status update: tactical OUI evidence now carries `DEVICE_REGISTRY`
provenance, while decoded tactical payload and manufacturer-ID fallback
evidence carry `BLE_ADVERTISEMENT` provenance at the processor output before
they reach live alerts or durable alert history.

Required next work: continue converting non-alert classifier outputs into durable first-class evidence and validate public-safety-like calibration behavior in real sessions before changing confidence thresholds.

### P0-6: Follow-Me evidence is reviewable but not yet calibrated

Files:

- `core/data/src/main/java/io/blueeye/core/data/tracker/FollowMeScoreCalculator.kt`
- `core/data/src/main/java/io/blueeye/core/data/repository/handler/ble/BleScanHandler.kt`
- `core/data/src/main/java/io/blueeye/core/data/db/dao/DeviceUpdateDao.kt`
- `core/model/src/main/kotlin/io/blueeye/core/model/Device.kt`
- `feature/radar/src/main/java/io/blueeye/feature/radar/presentation/RadarUiFormatter.kt`

`FollowMeScoreCalculator.ScoreResult` has a human-readable `explanation`, and `BleScanHandler` persists it as `followMeExplanation` alongside `trackingStatus`, `followingScore`, movement/baseline state, and individual score components for duration, RSSI stability, device type, MAC behavior, and encounter count. `DevicePersister` also records bounded append-only `follow_me_observations` snapshots when the Follow-Me status, score, explanation, or components materially change. When `AlertDecisionEngine` decides that a Follow-Me alert should fire, `AlertEvidenceEventRecorder` stores a debounced append-only `FOLLOW_ME_ALERT` event with the score, status, decision reason, and component scores. `DeviceEvidenceFactory` exposes the current score as `FOLLOW_ME_SCORE` evidence and creates component evidence from the persisted structured fields, not by parsing the explanation text.

Impact: Radar/Details/export can distinguish a Follow-Me scoring reason from noisy RSSI, duration, encounter, MAC-rotation, and movement/baseline components for the latest score. Details can show both score evolution and durable alert moments instead of inferring alerts from score snapshots. Session export now includes Follow-Me observation snapshots and alert evidence events for devices seen during the session, including event type counts, latest per-device history, RSSI quality, observed session duration based on the latest device/sample/history event rather than export time, and per-device review actions for calibration decisions. Session Calibration also shows the highest-priority next review step and a short device-level review queue from the current session mix, RSSI trends, identity carryover, and recent alert history; each queued device can open Details and selected queued reasons expose inline calibration actions. Devices the user already marked Known Safe or False Positive are filtered out of actionable long-session Follow-Me/RSSI/carryover review signals, while their historical evidence remains available in export.

Status update: latest-state and alert-history Follow-Me evidence now carries
`FOLLOW_ME_ANALYSIS` provenance for the overall score, structured score
components, movement suppression, baseline suppression, RSSI-pattern evidence,
and Follow-Me alert decisions. That keeps behavioral analysis visually separate
from BLE advertisement, Classic discovery, registry, and active-probe evidence.

Required next work: use the dedicated identity/carryover verdicts to tune merge confidence and add an explicit inconclusive workflow before raising confidence thresholds.

### P0-7: Short retention can delete evidence before the user can review it

Files:

- `core/data/src/main/java/io/blueeye/core/data/repository/handler/ble/BleScanHandler.kt`
- `core/data/src/main/java/io/blueeye/core/data/db/entity/SignalSampleEntity.kt`
- `core/data/src/main/java/io/blueeye/core/data/db/dao/DeviceSearchDao.kt`
- `core/domain/src/main/java/io/blueeye/core/domain/usecase/GetScannedDevicesUseCase.kt`

`BleScanHandler` no longer deletes non-watchlist devices while processing scan events. Radar still queries a 180 second visibility window, and `ScannerService` owns coarse cleanup via `deleteOldDevices` after the configured 24 hour retention period. Device rows still have cascading signal samples and Follow-Me observations when they are eventually deleted.

Impact: useful evidence for "was this moving with me?" is no longer deleted just because the device left the current Radar window. Follow-Me score changes retain bounded review history, and watchlist/public-safety/Follow-Me alert reasons retain bounded event history.

Required next work: keep Radar visibility as a query/window concern, then add export/session workflows before increasing confidence thresholds or claiming long-term behavior.

## P1 Findings

### P1-1: Watchlist return alert evidence keeps only the latest event

Files:

- `core/data/src/main/java/io/blueeye/core/data/repository/handler/ble/BleScanHandler.kt`
- `core/data/src/main/java/io/blueeye/core/alert/TacticalAlertService.kt`
- `core/data/src/main/java/io/blueeye/core/data/repository/WatchlistRepositoryImpl.kt`

Watchlist reappearance checks use `existing.lastSeenAt` before the current scan update and can vibrate if the device was offline for more than 60 seconds. The latest return event is stored on the device row as `lastWatchlistReturnAlertAt` plus `lastWatchlistReturnOfflineDurationMs`, and `DeviceEvidenceFactory` turns it into `WATCHLIST` evidence for Radar/Details. Each emitted return alert is also written to bounded append-only `alert_evidence_events`.

Impact: the user can review why the most recent watchlist return alert happened and can inspect recent historical return evidence in Details. Session export now includes `WATCHLIST_RETURN` alert evidence events for watchlisted devices seen during the session. Session Review also exposes inline `Pause alerts` and `Known safe` actions for watchlist returns, so unwanted return alerts can be tuned without editing JSON. Radar shows an `ALERTS PAUSED` badge for watchlist devices whose return alerts are disabled.

Required next work: validate the watchlist return cooldown and alert pause behavior in real sessions before changing thresholds.

### P1-2: Dedup/carryover explainability is now persisted

Files:

- `core/data/src/main/java/io/blueeye/core/data/tracker/AddressCarryoverTracker.kt`
- `core/data/src/main/java/io/blueeye/core/data/repository/handler/ble/DevicePersister.kt`
- `core/data/src/main/java/io/blueeye/core/data/repository/handler/classic/ClassicDevicePersister.kt`

The app merges devices using raw payload, UUID overlap, name matching, RSSI proximity, and carryover heuristics. The matcher now returns a structured carryover result containing the target ID plus reason code, confidence, and compact feature summary.

Impact: false merges are easier to spot because devices whose current random MAC differs from the stable fingerprint now expose low-confidence `IDENTITY_CARRYOVER` evidence with `FOLLOW_ME_ANALYSIS` provenance. The UI/export can show that a rotating Bluetooth address was correlated with an existing device record without treating it as a risk signal, and can include the exact matcher context such as weighted feature score, payload score, RSSI delta, time delta, and heuristic reason. Session Calibration now counts unreviewed identity carryover devices in the current session and routes the next-review guidance to Details before trusting merged history. It also has dedicated identity verdict actions (`Same device`, `False match`, `Inconclusive`) persisted as `IdentityCarryoverVerdict`, so carryover review no longer overloads the device-wide Known Safe / False Positive calibration label. Details decision summaries and Radar badges now surface the saved carryover verdict outside Settings/export, and reviewed verdicts are copied into carryover evidence context so `FALSE_MATCH` / `INCONCLUSIVE` are visible in Details/export without raising confidence.

Status update: session export schema `17` adds `identityCarryoverVerdictCounts`, so review exports can be grouped by `UNREVIEWED`, `CONFIRMED_SAME_DEVICE`, `FALSE_MATCH`, and `INCONCLUSIVE` without manually scanning each device summary.

Required next work: collect real `INCONCLUSIVE` and `FALSE_MATCH` outcomes from exports to tune carryover confidence, and add a merge-correction path only if false carryovers remain common.

### P1-3: Radar is grouped and surfaces evidence provenance

Files:

- `feature/radar/src/main/java/io/blueeye/feature/radar/presentation/RadarViewModel.kt`
- `feature/radar/src/main/java/io/blueeye/feature/radar/presentation/RadarUiMapper.kt`
- `feature/radar/src/main/java/io/blueeye/feature/radar/presentation/RadarUiSectionMapper.kt`
- `feature/radar/src/main/java/io/blueeye/feature/radar/presentation/RadarEvidenceUiFormatter.kt`
- `feature/radar/src/main/java/io/blueeye/feature/radar/presentation/RadarScreen.kt`

Radar now maps devices into `Nearby`, `Watchlist`, `Suspicious`, `Public Safety Signals`, and `Unknown/Noise`, and `RadarScreen` renders section tabs plus headers. Section routing is based on watchlist status, user suppression, Follow-Me/tracker evidence, public-safety-like evidence, identity, and attention confidence.

Impact: 20+ devices are no longer presented as one undifferentiated list. The user can separate ordinary nearby devices from watchlist, suspicious movement, public-safety-like, and noise buckets.

Status update: `RadarEvidenceUiFormatter` shows source/provenance in the primary evidence row and evidence chips, including BLE advertisement, Classic SDP, active GATT/RFCOMM, device registry, user action, and Follow-Me analysis. Details uses the same provenance concepts in the full evidence list.

Status update: Radar, Details, and Session Review no longer route devices into `Suspicious`, tracker-like, or `Public Safety Signals` decisions from `deviceType` alone. A `TRACKER` or `BODY_CAMERA` label without supporting evidence remains an ordinary nearby device unless Follow-Me score/status, calibration, watchlist state, or evidence justifies a higher-attention section.

Required next work: keep section routing evidence-driven and add review affordances for questionable identity/carryover decisions and long-session calibration.

### P1-4: Feature modules now use domain-facing contracts

Files:

- `feature/radar/src/main/java/io/blueeye/feature/radar/presentation/RadarViewModel.kt`
- `feature/settings/src/main/java/io/blueeye/feature/settings/SettingsViewModel.kt`

Presentation code now uses domain-facing contracts for scanner runtime, public-safety signal state, focused scan, connection state, sensor decoding, persisted service resolution, reference database updates, and signal sample reads. `feature:settings` no longer depends on Room entities, DAOs, DataStore preferences, or data-layer reference repositories.

Impact: feature modules are easier to test without hardware/data implementations, and UI code no longer decides how Room/DataStore/reference-file repositories are wired.

Status update: tracker-like, public-safety-like, and attention evidence classification now lives in `core:domain` as one shared classifier used by Radar, Details, and Session Review/export. This removes duplicated confidence/source keyword rules from feature modules and keeps high-attention routing consistent.

Required next work: keep new UI/export behavior behind domain contracts. Larger UX work can now focus on evidence-first grouping instead of dependency cleanup.

### P1-5: RSSI is useful but too visually authoritative

Files:

- `feature/radar/src/main/java/io/blueeye/feature/radar/presentation/RadarUiFormatter.kt`
- `core/data/src/main/java/io/blueeye/core/data/repository/handler/classic/ClassicScanDataContext.kt`
- `core/data/src/main/java/io/blueeye/core/data/repository/handler/classic/ClassicDevicePersister.kt`

Radar previously colorized RSSI as safe/warning/dangerous, while Classic unavailable RSSI uses `-100`. Recent fixes avoid a legacy `-50` fallback, and Radar now renders RSSI strength with neutral signal-quality tokens (`primary`, `secondary`, `outline`) instead of domain verdict colors. Follow-Me evidence also separates the overall `FOLLOW_ME_SCORE` from `RSSI_PATTERN`, and RSSI evidence is emitted from the structured RSSI component only. Details now shows Follow-Me score history separately from the RSSI chart.

Impact: strong RSSI no longer looks like a safety/risk verdict in the nearby list, RSSI evidence is only emitted when RSSI stability contributes to the latest Follow-Me score, and score history no longer has to be inferred from raw RSSI samples.

Status update: session export per-device `sampleStats` now includes `rssiTrend` with first/last RSSI window averages, delta, direction, and sample counts. Session Calibration also summarizes per-device RSSI trend direction and uses strengthening trends plus recent alert history in the next-review guidance. This gives calibration review a structured trend input instead of forcing reviewers to infer movement from latest RSSI or raw samples only.

Status update: long-session false-positive / known-safe regression tests now cover the in-app review queue and session export queue, so stale Follow-Me alerts, identity carryover evidence, strengthening RSSI trends, and high scores do not reappear as actionable review work after user suppression.

Required next work: use the dedicated identity/carryover verdict model in confidence tuning before raising Follow-Me confidence thresholds.

## P2 Findings

### P2-1: Tooling debt still exists in disabled/baselined areas

Files:

- `build.gradle.kts`
- `config/detekt/detekt.yml`
- `core/data/detekt-baseline.xml`
- `core/decoders/detekt-baseline.xml`

The quality gate is useful, but `core:data/src` and `core:decoders/src` still have temporary ktlint exclusions and detekt baselines.

Impact: new code can be guarded, but the most important modules still contain legacy style/debt.

Required next work: reduce baselines module by module after the evidence pipeline is stable.

### P2-2: Some diagnostics are logs only

Files:

- `core/data/src/main/java/io/blueeye/core/data/diagnostics/ScoreDiagnosticLogger.kt`
- `core/data/src/main/java/io/blueeye/core/data/repository/handler/ble/BleScanHandler.kt`
- `core/data/src/main/java/io/blueeye/core/data/repository/handler/ble/enricher/SensorEnricher.kt`

Several useful diagnostic facts go only to logs. Logs help development, but they are not user-facing evidence. Follow-Me observations and alert events are now exported for session review, but some scanner/enricher diagnostics still need structured session review data.

Impact: real-world calibration is less dependent on logcat for alert/scoring review, but deeper scanner/enricher diagnosis still needs structured session data.

Status update: session export schema `16` adds `decodedSignalDeviceCount`, `decodedSignalKindCounts`, and per-device `decodedSignal` summaries for decoded beacon type, formatted sensor data, and raw-payload presence. This makes the useful `SensorEnricher` / beacon recognition facts visible in export without reading logcat.

Required next work: use real session review labels and identity/carryover verdicts to tune the classifier, and only add deeper packet-structure export if field calibration still needs data that is not already present in raw payload/evidence.

## Recommended Next Closed Stage

Implement one of the remaining evidence/UX gaps before expanding confidence claims:

1. Use identity/carryover verdict outcomes to tune merge confidence and decide whether false carryovers need a merge-correction path.
2. Continue converting general classifier outputs into durable first-class evidence.
3. Extend calibration tests only where the next verdict model changes review behavior.

Do not raise confidence thresholds or add stronger user-facing claims before the relevant source data is preserved and reviewable.
