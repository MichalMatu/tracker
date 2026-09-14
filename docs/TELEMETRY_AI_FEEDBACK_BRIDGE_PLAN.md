# Telemetry & AI feedback bridge plan

## Status

- **Active implementation plan; implementation not yet accepted.**
- Selected prototype: **Google Drive data plane + Gmail control/feedback plane**.
- Use one dedicated secondary Google account, isolated from the operator's normal account.
- Complete the mandatory **plan -> current code -> Google/Android capabilities** audit before production implementation.

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

## Mandatory preimplementation audit

### 1. Plan

Verify that the Drive/Gmail split is still the smallest viable design, the cadence is justified, every outbound field has an analysis purpose, privacy boundaries are explicit and autonomous engineering authority is bounded.

### 2. Current Tracker code

Inspect the real owners of scanner/session lifetime, foreground service/background work, Room write pressure, export serialization, DataStore/settings, Hilt/module boundaries, process-death recovery and source/session identity. Find the cheapest place to accumulate telemetry without adding work to the hottest ingest callback.

Reuse existing export/reducer concepts where appropriate, but do not create a second giant serializer or run full exports every minute.

### 3. Current Google/Android capabilities

Verify against current official docs and a real test account/project:

- current Android Google authorization API and account-selection UX;
- incremental authorization/token expiry/revocation behavior;
- OAuth test-user vs production publishing behavior;
- verification requirements for `gmail.send`;
- exact `drive.file` create/list/update/delete/rediscovery semantics;
- Drive latency/quotas/file-count behavior;
- whether a freshly uploaded plain JSON file is immediately readable through the ChatGPT Drive connection;
- whether a self-sent Gmail control message has the expected shape;
- whether ChatGPT can actually trigger on Gmail events with acceptable latency;
- Gmail sending/abuse limits for the intended cadence;
- Android foreground-service/Doze/process-death behavior during a real scan.

If a feasibility item fails, change the architecture before building more telemetry complexity.

## Implementation order

### T0 — End-to-end Google/ChatGPT spike

Before minute telemetry exists:

```text
select dedicated account
 -> authorize drive.file
 -> authorize gmail.send
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
- assuming a Gmail message can wake this exact ChatGPT workflow before proving it;
- one email per one-minute delta;
- WorkManager used as a one-minute timer;
- full DB/session export in every heartbeat;
- mutable/overwritten batches that break idempotency;
- thousands of tiny Drive files without retention/analysis windows;
- auth failure blocking scanning;
- feedback emails recursively triggering new analyses;
- ChatGPT conclusions replacing the deterministic local verdict;
- autonomous fixes without regression evidence or bounded verification.

## Definition of done

The bridge is done only when the phone can produce bounded versioned telemetry without harming local operation, Drive delivery is durable/idempotent, Gmail reliably signals new analysis windows without storing the telemetry itself, ChatGPT consumes only new referenced data, feedback reaches the dedicated account, failures/revocation recover safely, retention is bounded, and confirmed engineering problems can complete the evidence -> fix -> verification -> later-evidence loop.

## Official references to re-check immediately before implementation

- Drive scopes: https://developers.google.com/workspace/drive/api/guides/api-specific-auth
- Drive file creation: https://developers.google.com/workspace/drive/api/guides/create-file
- Drive app-data semantics: https://developers.google.com/workspace/drive/api/guides/appdata
- Gmail scopes: https://developers.google.com/workspace/gmail/api/auth/scopes
- Gmail sending: https://developers.google.com/workspace/gmail/api/guides/sending
- Google Workspace user-data policy: https://developers.google.com/workspace/workspace-api-user-data-developer-policy
- WorkManager periodic work: https://developer.android.com/develop/background-work/background-tasks/persistent/getting-started/define-work
- Android foreground services: https://developer.android.com/develop/background-work/services/fgs
