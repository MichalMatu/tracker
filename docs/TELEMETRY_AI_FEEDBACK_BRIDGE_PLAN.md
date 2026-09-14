# Telemetry and AI Feedback Bridge Implementation Plan

## Status

- **Active implementation plan**
- Date: 2026-09-14
- Scope: optional developer/advanced telemetry bridge for continuous field analysis and feedback
- Product rule: Tracker remains fully functional and local-first when this feature is disabled
- Initial user model: a dedicated secondary Google account used only for BlueEye automation

## Goal

Create an opt-in bridge that lets Tracker continuously reduce and export useful runtime/field evidence, notify an external analyst when new evidence is ready, and return actionable feedback to the operator without turning Gmail into the telemetry database or weakening Tracker's local-first behavior.

The target loop is:

```text
Tracker scan/runtime
  -> local deterministic reduction
  -> durable local telemetry outbox
  -> external telemetry transport/storage
  -> new-batch notification
  -> ChatGPT analysis
       -> operator feedback by email
       -> GitHub issue / source change when a software defect is supported
       -> no action when nothing meaningful changed
```

The bridge is an engineering/diagnostic capability first. It must not become a dependency of normal scanning, classification, alerts, persistence or offline use.

## Fixed design decisions

1. **Dedicated Google account.** Use a secondary account dedicated to BlueEye telemetry/feedback so the operator's normal mailbox is isolated from experimental automation.
2. **Least privilege.** Tracker requests only the Gmail permission needed to send mail (`gmail.send`) for the first implementation. It must not request read, modify or delete mailbox permissions.
3. **Gmail is not telemetry storage.** Gmail is used for notification/trigger messages and human-readable feedback, not as a database or backup destination for arbitrary Tracker dumps. Google Workspace policy explicitly disallows using Gmail API to store or backup data other than email messages.
4. **Separate data plane and control plane.** Telemetry payloads use a transport/storage abstraction. Gmail carries small control notifications such as `batch ready`, plus operator-facing feedback.
5. **Local-first safety.** Failure of OAuth, Gmail, network, remote storage, ChatGPT or automation must never stop BLE collection or local alerts.
6. **No silent destructive automation.** Analysis may propose or create traceable engineering work, but user data and application state are never remotely deleted or mutated as a side effect of telemetry processing.
7. **Bounded data.** Frequent telemetry uses compact deltas. Larger checkpoints are less frequent. Full Session Export remains a deliberate diagnostic artifact rather than the normal heartbeat.
8. **Version everything crossing the boundary.** Batch schema, transport envelope and future analyst response schema have explicit versions.

## Proposed architecture

```text
                         DATA PLANE

BLE / Classic / runtime state
          |
          v
TelemetryReducer
          |
          v
TelemetryBatch v1
          |
          v
Durable TelemetryOutbox
          |
          v
TelemetryTransport
          |
          +----> HTTPS receiver / object storage
                         |
                         +---- batch id + retrieval reference

                        CONTROL PLANE

Tracker / receiver
      |
      +----> Gmail: BLUEEYE/BATCH_READY
                         |
                         v
                   ChatGPT trigger
                         |
                         +----> fetch new batch
                         +----> compare with prior state
                         +----> analyze
                         |
                 +-------+-------+
                 |               |
                 v               v
        Gmail feedback       GitHub work
        / urgent alert       issue/change
```

The first data-plane implementation should remain replaceable. Candidates include a small HTTPS receiver on the operator's Raspberry Pi exposed through a secure tunnel, or a minimal managed HTTPS/object-storage endpoint. The application contract must not depend on Cloudflare, Google Drive or any one storage provider.

## Phase T0 — Contracts, privacy and failure boundaries

- [ ] Define `TelemetryBatch` schema v1.
- [ ] Define `TelemetryEnvelope` containing schema version, installation id, session id, sequence, time range, app version/source revision, payload checksum and batch kind.
- [ ] Define batch kinds: `DELTA`, `CHECKPOINT`, `URGENT_EVENT`.
- [ ] Define exactly which fields may leave the phone automatically.
- [ ] Mark potentially sensitive fields explicitly: exact GPS, MAC/address material, raw advertisement payloads, aliases/notes and active-probe results.
- [ ] Default frequent telemetry to reduced/structured data rather than unrestricted raw database state.
- [ ] Decide which sensitive fields require a separate explicit diagnostic opt-in.
- [ ] Ensure no telemetry path can block scanner/database write paths.
- [ ] Define retention limits for local queued batches.
- [ ] Define retry/backoff and terminal failure behavior.

**Gate:** the outbound schema and privacy boundary can be reviewed independently of transport implementation.

## Phase T1 — Dedicated Telemetry & AI Bridge screen

Add a real application destination rather than growing `SettingsScreen` and `SettingsViewModel` further.

### Navigation/UI

- [ ] Add type-safe `Screen.TelemetryBridge` navigation destination.
- [ ] Add a `Telemetry & AI Bridge` card in Settings.
- [ ] Create `TelemetryBridgeScreen` and dedicated `TelemetryBridgeViewModel`.
- [ ] Keep business/auth/transport logic outside the composable.
- [ ] Add screen preview(s) and use existing Material/Dimens tokens.

### Screen state

Show at minimum:

- bridge enabled/disabled;
- connected Google account;
- `Connect Google account`;
- `Change account`;
- `Disconnect`;
- Gmail send permission state;
- telemetry transport state;
- last batch sequence/time;
- pending outbox count;
- last successful upload;
- last trigger email;
- last transport/auth error;
- `Send test telemetry`;
- `Send test feedback trigger` where useful during development.

The bridge is disabled by default.

**Gate:** the screen is usable with no account configured and cannot accidentally enable remote telemetry merely by opening it.

## Phase T2 — Google account authorization and send-only Gmail

- [ ] Use the current Google authorization stack appropriate for Android; do not use a WebView login or store Google passwords.
- [ ] Request only `https://www.googleapis.com/auth/gmail.send` in the initial version.
- [ ] Allow the operator to select the secondary Google account explicitly.
- [ ] Persist only the minimum account/authorization state required by the supported Google APIs.
- [ ] Never request Gmail read/modify/delete access in Tracker for this feature.
- [ ] Implement revoke/disconnect behavior.
- [ ] Add a test action that sends one ordinary diagnostic email from the selected account.
- [ ] Verify behavior after token expiry, account removal and revoked consent.

Suggested first physical acceptance test:

```text
open Telemetry & AI Bridge
  -> choose dedicated Google account
  -> grant send-only scope
  -> Send test telemetry
  -> one test email arrives
  -> disconnect
  -> sending is no longer possible
```

**Gate:** real S22+ test proves send-only authorization and account switching without access to the primary mailbox.

## Phase T3 — Frequent telemetry reducer and durable outbox

Frequent capture and network delivery are separate concerns.

### Capture cadence

Initial development target:

- collect/update local counters continuously or on meaningful state changes;
- build compact delta batches approximately every 1 minute while an active session is running;
- permit urgent event batches immediately when a high-value condition occurs;
- create a broader checkpoint approximately every 15-30 minutes;
- make cadence configurable for engineering builds after measurement.

The cadence is a starting point, not a product promise. Battery, Android background execution and data volume must be measured on-device.

### TelemetryBatch v1 candidate content

- ingest counts and rates;
- scanner/runtime state;
- queue accepted/dropped/rejected/failure counters;
- number of recent/active devices by review category;
- device classification transitions;
- new alert evidence events;
- Follow-Me status/score transitions;
- identity/carryover candidate transitions;
- parser/decoder failures and unknown structures;
- false-positive calibration changes;
- Apple Find My / other high-volume family counts;
- RSSI summary changes rather than every raw RSSI callback;
- memory/runtime diagnostics that are cheap and meaningful;
- sequence/timestamp continuity diagnostics;
- selected representative evidence when needed to explain an anomaly.

Do not duplicate the entire database every minute.

### Outbox

- [ ] Persist batches locally before attempting upload.
- [ ] Monotonic per-installation/session sequence numbers.
- [ ] Idempotency key for remote delivery.
- [ ] Atomic state transition: pending -> sending -> acknowledged or retryable failure.
- [ ] Bounded retention by age/count/bytes.
- [ ] Exponential backoff for network/server failures.
- [ ] Survive process death/reboot.
- [ ] Never lose a batch merely because the network is temporarily unavailable.
- [ ] Never retry permanently invalid/auth-rejected payloads forever.

**Gate:** airplane-mode and process-death tests demonstrate queued delivery without affecting normal scanning.

## Phase T4 — Transport abstraction and first remote endpoint

Create a small domain contract such as:

```text
TelemetryTransport.send(batch): Result<TelemetryReceipt>
```

`TelemetryReceipt` should carry at least:

- batch id / sequence;
- accepted timestamp;
- checksum/etag equivalent;
- optional retrieval reference suitable for the analyst path.

Requirements:

- [ ] HTTPS only outside local development.
- [ ] authentication independent from Google account authentication;
- [ ] idempotent duplicate handling;
- [ ] bounded upload size;
- [ ] integrity/checksum validation;
- [ ] server rejects incompatible schema versions explicitly;
- [ ] no provider-specific types above the data layer.

Select the first backend after a minimal spike. Prefer the smallest option that can expose new batches to the analysis workflow without requiring the phone to be directly reachable.

**Gate:** a test batch sent from the physical phone can be retrieved independently and verified byte-for-byte/checksum-for-checksum.

## Phase T5 — Gmail trigger/control message

After a telemetry batch is acknowledged, send a small ordinary email from the dedicated Google account.

Suggested subject contract:

```text
BLUEEYE/BATCH_READY installation=<id> session=<id> seq=<n>
```

Suggested body:

```text
schemaVersion=<n>
batchKind=<DELTA|CHECKPOINT|URGENT_EVENT>
sequence=<n>
createdAt=<timestamp>
checksum=<sha256>
retrievalReference=<opaque/signed reference>
summary=<small human-readable diagnostic summary>
```

Rules:

- [ ] Trigger email is sent only after remote batch acknowledgement.
- [ ] Do not attach the full telemetry dump merely to use Gmail as storage.
- [ ] Use distinct subject namespaces for inbound telemetry triggers and outbound analyst feedback.
- [ ] Include enough identity/sequence information to deduplicate repeated notifications.
- [ ] Support resend of a missing trigger without uploading a duplicate batch.

Reserved prefixes:

```text
BLUEEYE/BATCH_READY
BLUEEYE/URGENT
BLUEEYE/FEEDBACK
BLUEEYE/ANALYSIS_ERROR
```

**Gate:** duplicate trigger emails cannot cause duplicate analysis/results for the same batch sequence.

## Phase T6 — ChatGPT trigger and analysis loop

First verify which event mechanism is actually available for the connected Gmail account. Do not assume that a Gmail event can wake this exact conversation until it is demonstrated end-to-end.

Preferred path:

```text
new BLUEEYE/BATCH_READY message
  -> event trigger
  -> read control message
  -> fetch referenced telemetry batch
  -> compare with previous analyzed sequence/checkpoint
  -> run analysis
```

Fallback path:

- scheduler/automation acts as a watchdog;
- searches for the newest unprocessed notification/batch;
- does not duplicate already acknowledged analysis;
- current scheduler frequency limits are treated as fallback latency, not as the target near-real-time path.

Analysis should initially look for:

- sudden false-positive growth;
- one protocol/vendor flooding Radar or storage;
- alert storms;
- parser/decoder failures;
- unexpected Unknown/Noise growth;
- queue drops/rejections;
- gaps in sequence/time;
- suspicious classification transitions;
- divergence between local verdict and accumulated evidence;
- regressions after a known source revision;
- evidence worth turning into a privacy-safe regression fixture.

**Gate:** one controlled generated anomaly produces exactly one analysis event with traceable input batch sequence.

## Phase T7 — Operator feedback loop

ChatGPT feedback is sent to the dedicated Gmail account so the operator can see it through normal Android Gmail notifications without involving the primary mailbox.

Feedback classes:

- `INFO` — useful observation, no action required;
- `WATCH` — pattern worth monitoring across subsequent batches;
- `ACTION` — probable application/configuration defect worth engineering work;
- `URGENT` — telemetry indicates an immediate runtime/data-quality problem that materially invalidates the current session.

Every feedback message should include:

- affected session/batch range;
- what changed;
- evidence supporting the conclusion;
- uncertainty/counter-evidence;
- recommended next action;
- GitHub issue/commit reference when engineering work was created.

Prefer one thread per field session where practical instead of generating unrelated inbox noise.

**Gate:** operator can understand why an alert was emitted without opening raw telemetry.

## Phase T8 — Engineering feedback and autonomous improvement loop

The analyst may convert repeated, well-supported problems into repository work.

```text
field evidence
  -> analysis
  -> confirmed software problem
  -> GitHub issue or bounded source change
  -> focused tests
  -> canonical CI
  -> physical validation when hardware behavior is involved
  -> subsequent telemetry verifies improvement/regression
```

Rules:

- [ ] Preserve exact source revision/app version in every batch so regressions can be correlated with code.
- [ ] Do not tune classifiers from one ambiguous sample.
- [ ] Prefer creating a privacy-safe fixture/regression case before changing deterministic rules.
- [ ] Deduplicate GitHub issues by problem signature/session evidence.
- [ ] Keep AI-generated assessment separate from deterministic local verdict.
- [ ] Local Agent remains the hardware/Mac execution worker; telemetry analysis does not grant it broader authority.
- [ ] Never commit raw private telemetry, exact GPS, private MACs or database dumps to GitHub.

**Gate:** at least one real defect can complete the full evidence -> fix -> CI/device verification -> subsequent telemetry confirmation loop without manual data shuffling.

## Phase T9 — Productization decision

Only after the developer loop is reliable:

- [ ] measure battery/network cost;
- [ ] measure data volume over 30-60 minute and multi-hour sessions;
- [ ] decide whether the feature remains developer-only or becomes an advanced user opt-in;
- [ ] design explicit consent/privacy copy;
- [ ] decide production telemetry backend independently from the prototype backend;
- [ ] handle OAuth verification requirements before public distribution with Gmail sensitive scopes;
- [ ] define account deletion/revocation behavior and remote retention controls;
- [ ] ensure turning the feature off stops future network transmission immediately while preserving normal local Tracker operation.

**Gate:** no public-facing rollout until privacy, account authorization, retention, reliability and battery cost are explicitly accepted.

## Implementation order

1. T0 schema/privacy contract.
2. T1 dedicated screen/navigation/ViewModel skeleton.
3. T2 secondary Google account + `gmail.send` + physical test email.
4. T3 compact reducer + durable local outbox.
5. T4 first transport/backend spike and end-to-end batch acknowledgement.
6. T5 trigger/control email after acknowledged upload.
7. T6 verify real ChatGPT trigger path; add scheduler watchdog only as fallback.
8. T7 ChatGPT -> Gmail feedback.
9. T8 connect supported findings to GitHub/Local Agent engineering loop.
10. T9 decide whether/how to productize.

## First implementation slice

Keep the first slice deliberately small:

```text
Settings
  -> Telemetry & AI Bridge
  -> select dedicated Google account
  -> grant gmail.send
  -> Send test telemetry
  -> receive one test diagnostic email
  -> disconnect/revoke
```

No automatic telemetry upload, background worker or autonomous source modification belongs in this first slice. Once this works on the physical S22+, add the outbox and real data plane incrementally.

## Definition of done

The workstream is complete when Tracker can continuously produce bounded, versioned diagnostic telemetry without disrupting local operation; reliably deliver it through a replaceable authenticated transport; signal new batches through a minimal trigger channel; allow ChatGPT to analyze only new evidence; return understandable Gmail feedback to the operator; and turn confirmed software problems into traceable engineering work while preserving privacy, local-first behavior and deterministic source-of-truth logic.
