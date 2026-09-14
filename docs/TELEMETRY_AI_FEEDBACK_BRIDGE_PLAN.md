# Telemetry & AI feedback bridge plan

## Status

- **T0 is code-complete and build-verified on the working branch; physical end-to-end acceptance is still pending.**
- Working branch: `feature/telemetry-ai-bridge`, forked from `main` at `6e07e2bc5063a7fda47a0e5a2ec8dbdaca1a0e7a`.
- Verified T0 code SHA: `489f90f1c53bb7feafa1bb0d16a486919e7990c5`.
- Local Agent verification on that exact SHA passed focused compile/ktlint/detekt/lint/unit tests and then full `qualityCheck` + `:app:assembleDebug`.
- Selected developer prototype: **Google Drive data plane + Gmail control/feedback plane**.
- Use one dedicated secondary Google account, isolated from the operator's normal account.
- The mandatory **plan -> current code -> Google/Android capabilities** audit was repeated on 2026-09-14 before implementation; result: **PASS WITH GATES**.
- T1+ automatic telemetry remains blocked until the physical OAuth -> Drive -> Gmail -> ChatGPT path is proven and the Google policy/productization fit is explicitly accepted or the affected transport plane is replaced.

## Goal

Give the developer installation a near-live, opt-in evidence loop without exposing the Android phone as a server or making cloud/AI part of normal Tracker operation.

```text
Tracker
  -> local reducer
  -> durable outbox
  -> Drive telemetry/analysis window
  -> Gmail BATCH_READY
  -> ChatGPT analysis
       -> Gmail feedback/warning
       -> GitHub/Local Agent engineering work when evidence supports it
```

If the bridge is disabled or every external service fails, scanning, persistence, local classification and alerts must keep working.

## Fixed v1 decisions

- Drive scope: `https://www.googleapis.com/auth/drive.file` only; no full Drive/read-all scope.
- Gmail scope: `https://www.googleapis.com/auth/gmail.send` only; Tracker never needs Gmail read/modify/delete.
- Request the non-sensitive email identity scope only so the bridge screen can show the selected technical account and self-address the control message.
- Use ordinary app-created Drive files, not hidden `appDataFolder`, because ChatGPT must be able to access the same files through the dedicated account.
- Gmail carries small control messages and human feedback; it is **not** a telemetry database/backup.
- Domain code depends on `TelemetryTransport`; `GoogleDriveTelemetryTransport` is the first implementation, not a permanent architecture lock-in.
- Telemetry batches are immutable after acknowledged upload.
- Every remote schema and sequence range is versioned/deterministic.
- No external analysis path may directly mutate the local production database or silently delete user data.

## Data model and Drive layout

Core envelope fields:

```text
schemaVersion
installationId
sessionId
sequence / sequenceRange
batchKind: DELTA | CHECKPOINT | URGENT_EVENT
from / to timestamps
appVersion + sourceRevision
checksum
```

Frequent payloads should contain reduced information such as ingest rates/counters, queue drops/rejections, device/category transitions, alert/Follow-Me/identity transitions, parser failures, high-volume protocol counts, RSSI summaries and bounded representative evidence. Do not dump the whole Room database every minute.

Suggested Drive tree:

```text
My Drive/BlueEye/telemetry/<installation>/<session>/
  seq-000001.delta.json
  seq-000002.delta.json
  window-000001-000005.json
  seq-000015.checkpoint.json
```

Rules:

- persist created folder/file ids locally but be able to rediscover app-created objects after re-auth/reinstall;
- mark app-created Drive objects with app properties so rediscovery never relies on a human-readable name alone;
- deterministic names and monotonic sequence numbers;
- plain UTF-8 JSON for v1 so the Drive connector can read it directly; defer gzip until compatibility/value is proven;
- separate installation/session folders;
- bounded retention by age/bytes/file count;
- never touch unrelated Drive content.

## Cadence

Initial engineering target during a user-started active scan session:

```text
continuous -> cheap local accumulator
~1 min      -> DELTA to durable outbox, upload when possible
~5 min      -> analysis-window JSON + one BATCH_READY email
urgent      -> immediate URGENT_EVENT/window + urgent control email
15-30 min   -> broader CHECKPOINT
```

These are tunable engineering defaults, not product promises.

**Do not use `PeriodicWorkRequest` for one-minute sampling.** Android periodic WorkManager has a 15-minute minimum. Fast deltas must live with the active scanning/session runtime; WorkManager is for durable retry/recovery that may happen later.

## Local outbox / delivery state

Each batch needs a stable idempotency key and durable state such as `PENDING -> SENDING -> ACKNOWLEDGED` or retry/terminal failure. Requirements:

- survive process death/reboot;
- exponential bounded retry for transient network/Drive errors;
- no endless retry for revoked auth/schema/validation failures;
- sequence-gap detection;
- no Gmail trigger until the referenced Drive window is acknowledged/readable;
- duplicate trigger must not produce duplicate analysis/feedback.

## App UI

Add a dedicated `Telemetry & AI Bridge` destination/ViewModel rather than growing `SettingsViewModel` further.

Show at minimum:

- bridge enabled state;
- selected technical Google account;
- Drive and Gmail authorization state separately;
- connect/change/disconnect;
- transport state, pending outbox count, last uploaded sequence/window;
- last trigger and last error;
- test bridge action;
- retention/cleanup status once automatic telemetry exists.

Opening the screen must not silently enable telemetry.

## Preimplementation audit — 2026-09-14

Result: **PASS WITH GATES**. Implementation may start with T0 only; automatic telemetry remains blocked until T0 is proven end to end.

### Current-code findings

1. `SettingsViewModel` is already large and carries `TooManyFunctions` suppression. The bridge gets its own destination and ViewModel; no Google/OAuth/telemetry state is added to `SettingsViewModel`.
2. `SettingsScreen` currently implements internal sections rather than a separate navigation destination. T0 uses a real type-safe application route so the bridge lifecycle is independent of Settings state.
3. `SessionStatsProvider` reloads all devices and signal samples every five seconds and filters them in memory. It must **not** become the telemetry source; the future reducer must consume cheap deltas/counters outside the hottest ingest callback.
4. `DatabaseExporter` is a full review/export serializer. It is useful as a schema/evidence reference but must not be called every minute or copied into a second full-database serializer.
5. No Google identity dependency or general HTTP client exists today. T0 uses Google Play services `AuthorizationClient` plus small direct HTTPS REST calls so the spike does not introduce a large Google API client stack before feasibility is proven.
6. `INTERNET` permission already exists. No new Android runtime permission is required for T0.
7. Any new Android feature module must be added to the repository `qualityCheck` Android project list; otherwise the normal broad gate would silently omit its lint/unit tests.

### Current Google/Android findings

1. Google recommends Credential Manager for authentication and `AuthorizationClient` for authorization to Google user data. This bridge does not need a backend identity session, so T0 uses `AuthorizationClient` directly.
2. Explicit account selection can be requested with `AuthorizationRequest.Prompt.SELECT_ACCOUNT`; this fits the dedicated-secondary-account requirement without the deprecated Google Sign-In API.
3. Mobile access tokens are short-lived (about one hour). Tracker must not persist them. Re-run `authorize()` when an operation needs a token; while grants remain valid this should normally resolve without user interaction.
4. `drive.file` is non-sensitive and per-file/app-file scoped. `gmail.send` is sensitive and will require OAuth verification for public production use.
5. An External OAuth app left in `Testing` has authorizations that expire after seven days for these scopes. Treat that as an expected prototype limitation, not a retryable transport bug.
6. Gmail `messages.send` accepts an RFC-2822/MIME message encoded as base64url. T0 sends only a small self-addressed control message and never stores telemetry in Gmail.
7. Drive is used for reduced analysis artifacts, not as a generic backup service. The implementation must stay scoped to user-enabled analysis output and app-created files.
8. Fresh Drive-file visibility through the ChatGPT Drive connection and Gmail-event latency into this exact workflow cannot be guaranteed from API documentation. They are explicit physical/end-to-end acceptance gates.

### Gates before T1+

T0 must prove all of the following with the dedicated account before minute-level telemetry is implemented:

- account chooser reliably selects the intended secondary account;
- `drive.file` upload creates an ordinary JSON file visible to the same account;
- app can rediscover only its own marked Drive objects;
- `gmail.send` can self-send `BLUEEYE/BATCH_READY` without read/modify scopes;
- ChatGPT Drive access can read the exact just-created file by identity/content;
- the chosen ChatGPT/Gmail trigger path has acceptable latency or is explicitly replaced;
- disconnect/revoke causes later bridge operations to stop and reauthorization behaves predictably.

## Implementation order

### T0 — End-to-end Google/ChatGPT spike

Before minute telemetry exists:

```text
select dedicated account
 -> authorize drive.file + gmail.send + email identity
 -> create BlueEye/test/bridge-test.json
 -> obtain exact Drive file id
 -> send BLUEEYE/BATCH_READY referencing it
 -> prove ChatGPT reads that exact JSON
 -> prove ChatGPT sends one feedback email
 -> revoke/disconnect and verify further operations stop
```

This is the first acceptance gate. It also proves that no public phone API, Cloudflare tunnel or companion device is required.

### T1 — Contracts/privacy

- version `TelemetryBatch`, envelope and `AnalysisWindow`;
- classify sensitive fields (exact GPS, MAC/address material, raw payloads, notes/aliases, active-probe results);
- define normal vs explicit-diagnostic opt-in fields;
- define retention and failure state machine.

### T2 — Bridge screen/auth

Implement the dedicated screen/ViewModel and separate Drive/Gmail capability states using the dedicated account.

### T3 — Reducer/outbox/Drive transport

Build compact DELTA/CHECKPOINT/URGENT batches, durable idempotent outbox, Drive hierarchy, checksums and bounded retry. Validate airplane mode, process death, revoked auth and duplicate sends without affecting scanning.

### T4 — Gmail control plane

After an analysis window is acknowledged on Drive, send a small message such as:

```text
Subject: BLUEEYE/BATCH_READY installation=<id> session=<id> seq=<range>
Body: schemaVersion, batchKind, sequenceRange, checksum, driveFileId, short summary
```

Reserve distinct namespaces for inbound control vs outbound analyst feedback (`BLUEEYE/BATCH_READY`, `BLUEEYE/URGENT`, `BLUEEYE/FEEDBACK`, `BLUEEYE/ANALYSIS_ERROR`) to prevent loops.

### T5 — ChatGPT trigger/state

Preferred: Gmail event -> exact Drive window -> deduplicated analysis. Fallback: scheduler/watchdog checks for unprocessed control messages/windows. The watchdog's slower cadence is not a substitute for a near-live event path.

Track last processed installation/session/range, detect missing sequences, rebuild context from checkpoints and never pretend continuity when data is missing.

If event triggering cannot meet the required latency/reliability, stop and reassess instead of hiding the problem behind retries.

### T6 — Operator feedback

Feedback levels: `INFO`, `WATCH`, `ACTION`, `URGENT`. Each message should include affected sequence/session range, what changed, supporting/counter evidence, uncertainty and recommended next action. Prefer one thread per field session where practical.

### T7 — Engineering feedback loop

For well-supported defects:

```text
field evidence -> analysis -> privacy-safe fixture -> bounded fix
 -> focused tests -> CI/device verification -> later telemetry confirms/regresses
```

Deduplicate issues by problem signature; keep raw private telemetry out of GitHub; never tune a classifier from one ambiguous sample.

### T8 — Retention/quotas/productization

Measure battery/network cost, Drive bytes/file count, trigger volume and analysis latency. Add hard local/remote bounds and explicit cleanup scoped only to app-created telemetry. Public rollout requires a deliberate privacy/consent/retention decision and the necessary Google OAuth verification/compliance work.

## Main pitfalls to prevent

- requesting full Drive/Gmail scopes for convenience;
- persisting access tokens or embedding a client secret in the APK;
- using deprecated Google Sign-In instead of current authorization APIs;
- assuming a Gmail message can wake this exact ChatGPT workflow before proving it;
- one email per one-minute delta;
- WorkManager used as a one-minute timer;
- full DB/session export in every heartbeat;
- reusing `SessionStatsProvider` as a high-frequency telemetry source;
- mutable/overwritten batches that break idempotency;
- thousands of tiny Drive files without retention/analysis windows;
- treating Drive as generic application backup rather than explicit analysis output;
- auth failure blocking scanning;
- feedback emails recursively triggering new analyses;
- ChatGPT conclusions replacing the deterministic local verdict;
- autonomous fixes without regression evidence or bounded verification.

## Definition of done

The bridge is done only when the phone can produce bounded versioned telemetry without harming local operation, Drive delivery is durable/idempotent, Gmail reliably signals new analysis windows without storing the telemetry itself, ChatGPT consumes only new referenced data, feedback reaches the dedicated account, failures/revocation recover safely, retention is bounded, and confirmed engineering problems can complete the evidence -> fix -> verification -> later-evidence loop.

## Official references to re-check immediately before implementation

- Android authorization: https://developer.android.com/identity/authorization
- AuthorizationClient reference: https://developers.google.com/android/reference/com/google/android/gms/auth/api/identity/AuthorizationClient
- Drive scopes: https://developers.google.com/workspace/drive/api/guides/api-specific-auth
- Drive file creation: https://developers.google.com/workspace/drive/api/guides/create-file
- Drive folders: https://developers.google.com/workspace/drive/api/guides/folder
- Gmail scopes: https://developers.google.com/workspace/gmail/api/auth/scopes
- Gmail sending: https://developers.google.com/workspace/gmail/api/guides/sending
- Google OAuth testing/production behavior: https://developers.google.com/identity/protocols/oauth2
- Google Workspace user-data policy: https://developers.google.com/workspace/workspace-api-user-data-developer-policy
- WorkManager periodic work: https://developer.android.com/develop/background-work/background-tasks/persistent/getting-started/define-work
- Android foreground services: https://developer.android.com/develop/background-work/services/fgs
