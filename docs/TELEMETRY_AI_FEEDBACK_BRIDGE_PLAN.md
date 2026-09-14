# Telemetry and AI Feedback Bridge Implementation Plan

## Status

- **Active implementation plan**
- Date: 2026-09-14
- Scope: optional developer/advanced telemetry bridge for continuous field analysis and feedback
- Product rule: Tracker remains fully functional and local-first when this feature is disabled
- Initial user model: one dedicated secondary Google account used only for BlueEye automation
- Selected prototype integration: **Google Drive data plane + Gmail control/feedback plane**
- Preimplementation rule: complete the dedicated **plan -> code -> Google capabilities/limits** audit before production code for this workstream starts

## Goal

Create an opt-in bridge that lets Tracker continuously reduce and export useful runtime/field evidence, make new evidence available from the Internet without exposing the Android device itself, notify ChatGPT when a new analysis window is ready, and return actionable feedback to the operator.

The target loop is:

```text
Tracker scan/runtime
  -> local deterministic reduction
  -> durable local telemetry outbox
  -> Google Drive (ordinary app-created files)
  -> Gmail BATCH_READY control message
  -> ChatGPT analysis
       -> Gmail feedback / warning to operator
       -> GitHub issue / bounded source change when a software defect is supported
       -> no action when nothing meaningful changed
```

The bridge is an engineering/diagnostic capability first. It must not become a dependency of normal scanning, classification, alerts, persistence or offline use.

## Fixed design decisions

1. **Dedicated Google account.** Use a secondary account dedicated to BlueEye telemetry/feedback so the operator's normal Gmail and Drive remain isolated from experimental automation.
2. **Drive is the v1 data plane.** Tracker stores telemetry as ordinary files in My Drive using the narrow `https://www.googleapis.com/auth/drive.file` scope. Do not request full `drive` or `drive.readonly` access.
3. **Gmail is the v1 control and feedback plane.** Tracker requests only `https://www.googleapis.com/auth/gmail.send`. It must not request Gmail read/modify/delete access.
4. **Gmail is not telemetry storage.** Do not attach routine telemetry dumps merely to turn Gmail into a database or backup. Trigger messages contain only small control metadata and references to Drive data. Operator-facing analyst feedback is ordinary email.
5. **Use normal Drive files, not `appDataFolder`.** ChatGPT must be able to access the telemetry through the same dedicated Google account. The hidden Drive application-data folder is intentionally not used because it is only visible to the creating application.
6. **Keep a transport abstraction.** Google Drive is the selected prototype backend, but domain code depends on `TelemetryTransport`, not Drive-specific classes. A later production backend can replace Drive without changing telemetry generation.
7. **One Google identity, two explicit capabilities.** The selected technical account is used for both Drive and Gmail. Drive-file and Gmail-send authorization state must remain separately visible and recoverable.
8. **Local-first safety.** Failure of Google authorization, Drive, Gmail, network, ChatGPT or automation must never stop BLE collection, local persistence or local alerts.
9. **Immutable telemetry batches.** Once a batch is accepted to Drive, do not rewrite its content. Retry/deduplication is based on stable batch identity and sequence rather than mutable files.
10. **Bounded data.** Frequent telemetry uses compact deltas. Larger checkpoints are less frequent. Full Session Export remains a deliberate diagnostic artifact rather than the heartbeat.
11. **Version everything crossing the boundary.** Batch schema, envelope, analysis-window schema and future analyst response schema have explicit versions.
12. **No silent destructive automation.** Analysis may propose or create traceable engineering work, but user data and application state are never remotely deleted or mutated as a side effect of telemetry processing.

## Selected prototype architecture

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
          v
GoogleDriveTelemetryTransport
          |
          +----> My Drive / BlueEye / telemetry / <installation> / <session> /
                         |
                         +---- immutable DELTA / CHECKPOINT files
                         +---- analysis-window files
                         +---- small recovery/control metadata when required

                        CONTROL PLANE

Tracker
  |
  +----> Gmail: BLUEEYE/BATCH_READY
                    |
                    v
            ChatGPT event/watchdog
                    |
                    +----> read referenced Drive analysis window
                    +----> compare with previous analyzed sequence/checkpoint
                    +----> analyze
                    |
            +-------+-------+
            |               |
            v               v
     Gmail feedback      GitHub work
     / urgent warning    issue/change
```

The phone never needs a public IP, inbound port, Cloudflare tunnel or always-on companion device. All network communication is outbound from Android.

## Google Drive layout v1

Use ordinary app-created files owned by the dedicated Google account.

Suggested structure:

```text
My Drive/
  BlueEye/
    telemetry/
      <installation-id>/
        <session-id>/
          seq-000000000001.delta.json
          seq-000000000002.delta.json
          seq-000000000003.delta.json
          seq-000000000004.delta.json
          seq-000000000005.delta.json
          window-000000000001-000000000005.json
          seq-000000000015.checkpoint.json
```

Rules:

- use a stable app-created `BlueEye` root folder and persist its Drive file id locally;
- create a separate folder per installation and per session so no single folder grows without bound;
- use deterministic names containing the monotonic sequence range;
- v1 uses plain UTF-8 JSON so the ChatGPT/Drive integration can read it directly; compression is deferred until connector compatibility is proven;
- store schema version, installation id, session id, sequence/range, source revision and checksum in file content and, where useful, Drive `appProperties`;
- batches are immutable after successful upload;
- an analysis-window file may aggregate several small deltas so ChatGPT normally reads one bounded document per trigger rather than many tiny files;
- do not expose raw database files as the routine transport format;
- retention is bounded by age and/or total bytes and is implemented only inside the app-created telemetry tree;
- never touch unrelated Drive files.

## Cadence model

Frequent **collection**, Drive **publication**, and Gmail **triggering** are intentionally separate.

Initial engineering target while a user-started active scanning session is running:

```text
continuous scanner state
    -> compact local accumulator

~1 minute
    -> TelemetryBatch DELTA
    -> local durable outbox
    -> Drive upload when network permits

~5 minutes
    -> analysis-window JSON covering all newly acknowledged deltas
    -> one BLUEEYE/BATCH_READY Gmail control message

urgent meaningful anomaly
    -> immediate URGENT_EVENT batch/window
    -> immediate BLUEEYE/URGENT control message

~15-30 minutes
    -> broader CHECKPOINT
```

Why the split:

- one-minute deltas preserve enough temporal resolution for later analysis;
- sending an email for every one-minute delta would create unnecessary Gmail volume and can run into sending/abuse limits;
- a five-minute analysis window keeps near-live feedback while remaining bounded;
- urgent events bypass the normal trigger debounce;
- cadence remains configurable for engineering builds and must be tuned from real battery/network measurements.

**Android scheduling rule:** do not implement the one-minute cadence with `PeriodicWorkRequest`. Android WorkManager periodic work has a 15-minute minimum interval. Fast deltas must run inside the lifetime of the active scanning/session runtime already justified by the product. WorkManager is reserved for durable retry/recovery work that can run later.

---

# Phase T0 — Contracts, privacy and failure boundaries

- [ ] Define `TelemetryBatch` schema v1.
- [ ] Define `TelemetryEnvelope` containing schema version, installation id, session id, sequence, time range, app version/source revision, payload checksum and batch kind.
- [ ] Define batch kinds: `DELTA`, `CHECKPOINT`, `URGENT_EVENT`.
- [ ] Define `AnalysisWindow` schema containing sequence range, included batch ids, summary counters, optional checkpoint reference and source revision.
- [ ] Define exactly which fields may leave the phone automatically.
- [ ] Mark potentially sensitive fields explicitly: exact GPS, MAC/address material, raw advertisement payloads, aliases/notes and active-probe results.
- [ ] Default frequent telemetry to reduced/structured data rather than unrestricted raw database state.
- [ ] Decide which sensitive fields require a separate explicit diagnostic opt-in.
- [ ] Ensure no telemetry path can block scanner/database write paths.
- [ ] Define local outbox retention limits by count/age/bytes.
- [ ] Define Drive remote-retention policy independently from local outbox retention.
- [ ] Define retry/backoff and terminal failure behavior.
- [ ] Define the exact semantics of `uploaded`, `acknowledged`, `window ready`, `trigger sent`, `analysis processed`.

**Gate:** the outbound schema, privacy boundary and state machine can be reviewed independently of Google implementation.

# Phase T1 — Dedicated Telemetry & AI Bridge screen

Add a real application destination rather than growing `SettingsScreen` and `SettingsViewModel` further.

## Navigation/UI

- [ ] Add type-safe `Screen.TelemetryBridge` navigation destination.
- [ ] Add a `Telemetry & AI Bridge` card in Settings.
- [ ] Create `TelemetryBridgeScreen` and dedicated `TelemetryBridgeViewModel`.
- [ ] Keep business/auth/transport logic outside the composable.
- [ ] Add screen preview(s) and use existing Material/Dimens tokens.

## Screen state

Show at minimum:

- bridge enabled/disabled;
- selected dedicated Google account;
- `Connect Google account`;
- `Change account`;
- `Disconnect`;
- Drive `drive.file` authorization state;
- Gmail `gmail.send` authorization state;
- Drive root/folder initialization state;
- telemetry transport state;
- last generated batch sequence/time;
- pending outbox count and bytes;
- last successful Drive upload;
- last analysis-window sequence range;
- last Gmail trigger time/status;
- last transport/auth error;
- `Create test Drive payload`;
- `Send test trigger`;
- `Run end-to-end test` once both scopes are available.

The bridge is disabled by default. Opening the screen or authorizing the account must not silently enable background telemetry.

**Gate:** the screen is usable with no account configured and account authorization is visibly distinct from enabling telemetry.

# Phase T2 — Google account authorization

Use the current supported Google authorization stack for Android. Do not use WebView login and never collect or store a Google password.

Required prototype scopes:

```text
https://www.googleapis.com/auth/drive.file
https://www.googleapis.com/auth/gmail.send
```

Rules:

- [ ] allow the operator to choose the secondary Google account explicitly;
- [ ] show the selected account on the bridge screen;
- [ ] request permissions in context/incrementally where practical so the reason for Drive and Gmail access is clear;
- [ ] never request full Drive access;
- [ ] never request Gmail read, modify, compose or delete scopes from Tracker;
- [ ] persist only the minimum identity/authorization state required by the supported Google APIs;
- [ ] do not invent a custom long-lived token store when the supported Google SDK/authorization flow already owns token lifecycle;
- [ ] implement disconnect/revoke behavior;
- [ ] verify behavior after access-token expiry, account removal and revoked consent;
- [ ] treat Drive authorization and Gmail authorization failures separately;
- [ ] keep the bridge disabled if only part of the required capability set is available, while allowing explicit test actions for the capability that is available.

Google scope/productization note:

- `drive.file` is intentionally selected because it limits access to files the app creates/uses and is substantially narrower than full Drive access;
- `gmail.send` is a **sensitive** Gmail scope, so a public release requires an explicit OAuth verification/productization review even though the developer prototype can be tested with the dedicated account.

**Gate:** real S22+ test proves account selection, `drive.file`, `gmail.send`, revoke/reconnect and zero access to unrelated primary-account content.

# Phase T3 — Frequent telemetry reducer and durable outbox

Frequent capture and network delivery are separate concerns.

## Capture

- [ ] integrate the one-minute accumulator with the active scanning/session lifecycle rather than creating an independent perpetual background loop;
- [ ] emit a delta on cadence and also on controlled session finalization;
- [ ] allow a meaningful urgent condition to flush immediately;
- [ ] create a broader checkpoint approximately every 15-30 minutes;
- [ ] make cadence configurable for engineering builds after measurement;
- [ ] stop high-frequency generation promptly when the user ends the active session or disables the bridge.

## `TelemetryBatch` v1 candidate content

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

## Durable outbox

- [ ] persist a complete batch locally before attempting Drive upload;
- [ ] use monotonic per-installation/session sequence numbers;
- [ ] generate a stable batch id/idempotency key before network work starts;
- [ ] atomic state transition: pending -> sending -> uploaded -> included-in-window -> trigger-sent;
- [ ] bounded retention by age/count/bytes;
- [ ] exponential backoff with jitter for retryable network/server failures;
- [ ] survive process death/reboot;
- [ ] never lose a batch merely because the network is temporarily unavailable;
- [ ] never retry permanently invalid/auth-rejected payloads forever;
- [ ] persist Drive file id/checksum immediately after successful creation so a later retry does not create duplicates unnecessarily;
- [ ] recovery code must be able to reconcile a possible `upload succeeded but local receipt write was interrupted` case using deterministic identity/name/app properties.

**Gate:** airplane-mode, process-death and reboot tests demonstrate queued delivery without affecting normal scanning.

# Phase T4 — `TelemetryTransport` abstraction and Google Drive v1

Domain contract remains provider-independent:

```text
TelemetryTransport.send(batch): Result<TelemetryReceipt>
```

`TelemetryReceipt` should carry at least:

- batch id / sequence;
- remote object id (Drive file id for v1);
- accepted timestamp;
- checksum;
- parent/session container id;
- optional retrieval reference suitable for the analyst path.

## `GoogleDriveTelemetryTransport`

- [ ] initialize or recover the app-created `BlueEye/telemetry` hierarchy;
- [ ] persist Drive folder ids locally instead of repeatedly searching by human-readable names;
- [ ] create one immutable ordinary JSON file per accepted delta/checkpoint;
- [ ] use `drive.file` only;
- [ ] write only below the app-created telemetry hierarchy;
- [ ] return/persist Drive file id and checksum as the remote receipt;
- [ ] use deterministic file names and stable batch ids;
- [ ] handle duplicate retry safely;
- [ ] handle quota/rate errors with bounded backoff;
- [ ] partition files by installation/session to avoid one giant folder;
- [ ] keep payloads small enough for simple upload in v1 unless measurements prove resumable upload is required;
- [ ] do not compress v1 JSON until ChatGPT Drive-reading compatibility is verified;
- [ ] do not use `appDataFolder` for telemetry that ChatGPT must inspect;
- [ ] never delete or modify unrelated Drive content.

## Analysis-window publication

Every trigger interval, create an `AnalysisWindow` file after all included batches have Drive receipts.

Suggested content:

```text
schemaVersion
installationId
sessionId
windowId
fromSequence
toSequence
createdAt
sourceRevision
batchFileIds[]
checkpointFileId?
summary
checksum
```

The window is itself immutable. A Gmail trigger points at the window file id, not at an ambiguous folder name.

**Gate:** a test payload created by the physical phone is visible in the dedicated Drive account and can be read by the ChatGPT Drive connection using the exact resulting artifact/reference with matching checksum/content.

# Phase T5 — Gmail trigger/control message

After an `AnalysisWindow` is successfully created on Drive, send one small ordinary email from the dedicated Google account.

Default cadence: approximately one trigger per five-minute analysis window, not one email per one-minute delta. `URGENT_EVENT` may trigger immediately.

Suggested subject contract:

```text
BLUEEYE/BATCH_READY installation=<id> session=<id> seq=<from>-<to>
```

Suggested body:

```text
controlSchemaVersion=<n>
windowSchemaVersion=<n>
installationId=<id>
sessionId=<id>
fromSequence=<n>
toSequence=<n>
createdAt=<timestamp>
windowDriveFileId=<drive-id>
windowChecksum=<sha256>
sourceRevision=<git-sha>
summary=<small human-readable diagnostic summary>
```

Rules:

- [ ] send only after the Drive analysis-window write is acknowledged;
- [ ] do not attach the telemetry dump;
- [ ] use distinct subject namespaces for inbound telemetry triggers and outbound analyst feedback;
- [ ] include enough identity/sequence information to deduplicate repeated notifications;
- [ ] support resend of a missing trigger without uploading duplicate telemetry;
- [ ] add a configurable safety cap for routine trigger emails per day;
- [ ] if the trigger cap is reached, continue safe Drive/outbox operation and fall back to less frequent control messages rather than dropping telemetry;
- [ ] allow immediate urgent control messages outside the normal debounce when justified;
- [ ] default recipient may be the same dedicated account; verify self-send behavior on the real account before relying on it;
- [ ] create a one-time Gmail filter/notification strategy so `BLUEEYE/BATCH_READY` traffic does not spam the operator while `BLUEEYE/FEEDBACK` and `BLUEEYE/URGENT` remain visible.

Reserved prefixes:

```text
BLUEEYE/BATCH_READY
BLUEEYE/URGENT
BLUEEYE/FEEDBACK
BLUEEYE/ANALYSIS_ERROR
```

**Gate:** duplicate/resend trigger emails cannot create duplicate analysis/results for an already processed sequence range.

# Phase T6 — ChatGPT Drive/Gmail connectivity and trigger feasibility

This is a hard feasibility gate. Do not assume that an incoming Gmail message can wake this exact ChatGPT workflow until it is demonstrated end-to-end.

Required setup outside the Android app:

- connect the same dedicated technical Google account to ChatGPT Gmail access;
- connect the same account to ChatGPT Google Drive access;
- verify that a freshly app-created ordinary JSON file becomes readable through the Drive connection quickly enough for the intended feedback loop;
- verify that the event/automation mechanism can react to the expected Gmail message or document the actual latency/limitations.

Preferred path:

```text
new BLUEEYE/BATCH_READY message
  -> event trigger
  -> read control message
  -> fetch exact Drive analysis-window file
  -> compare with previous processed sequence/checkpoint
  -> analyze
```

Fallback path:

- scheduler/automation acts as a watchdog;
- searches the dedicated Gmail account for newest unprocessed control messages;
- optionally checks Drive recovery metadata/windows if a trigger is suspected missing;
- never duplicates already acknowledged analysis;
- scheduler frequency limits are treated as fallback latency, not the target near-live path.

If the event-trigger path cannot meet the required latency reliably, stop and reassess the architecture before adding more telemetry complexity.

**Gate:** one phone-generated test window produces exactly one ChatGPT run, and the run demonstrably reads the exact Drive data referenced by the control message.

# Phase T7 — Analysis behavior

Initial analysis should look for:

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

State requirements:

- [ ] keep the last successfully processed installation/session/sequence range;
- [ ] deduplicate repeated control messages;
- [ ] detect missing sequence ranges and request/recover context rather than silently pretending continuity;
- [ ] use checkpoints to rebuild context when necessary;
- [ ] preserve uncertainty and counter-evidence in conclusions;
- [ ] never treat AI analysis as the local detector's authoritative verdict.

**Gate:** a controlled generated anomaly produces one traceable analysis result and a normal control dataset does not produce noisy warnings.

# Phase T8 — Operator feedback loop

ChatGPT feedback is sent to the dedicated Gmail account so the operator can see it through normal Android Gmail notifications without involving the primary mailbox.

Feedback classes:

- `INFO` — useful observation, no action required;
- `WATCH` — pattern worth monitoring across subsequent windows;
- `ACTION` — probable application/configuration defect worth engineering work;
- `URGENT` — telemetry indicates an immediate runtime/data-quality problem that materially invalidates the current session.

Every feedback message should include:

- affected session and sequence range;
- what changed;
- evidence supporting the conclusion;
- uncertainty/counter-evidence;
- recommended next action;
- GitHub issue/commit reference when engineering work was created.

Prefer one Gmail thread per field session where practical instead of generating unrelated inbox noise.

**Gate:** the operator can understand why a warning was emitted without opening raw telemetry.

# Phase T9 — Engineering feedback and autonomous improvement loop

The analyst may convert repeated, well-supported problems into repository work.

```text
field evidence
  -> analysis
  -> confirmed software problem
  -> privacy-safe regression fixture where possible
  -> GitHub issue or bounded source change
  -> focused tests
  -> canonical CI
  -> physical validation when hardware behavior is involved
  -> subsequent telemetry verifies improvement/regression
```

Rules:

- [ ] preserve exact source revision/app version in every batch so regressions can be correlated with code;
- [ ] do not tune classifiers from one ambiguous sample;
- [ ] prefer creating a privacy-safe fixture/regression case before changing deterministic rules;
- [ ] deduplicate GitHub issues by problem signature/session evidence;
- [ ] keep AI-generated assessment separate from deterministic local verdict;
- [ ] Local Agent remains the hardware/Mac execution worker; telemetry analysis does not grant it broader authority;
- [ ] never commit raw private telemetry, exact GPS, private MACs or database dumps to GitHub;
- [ ] never let a remote analysis path mutate the local production database directly.

**Gate:** at least one real defect can complete the full evidence -> fix -> CI/device verification -> subsequent telemetry confirmation loop without manual data shuffling.

# Phase T10 — Retention, quotas and cleanup

Frequent telemetry creates many Drive objects even when each file is small, so lifecycle policy is part of the design rather than an afterthought.

- [ ] record local and remote bytes/files created per hour of active scanning;
- [ ] partition Drive by installation/session/date as necessary;
- [ ] define a default remote retention window for developer telemetry;
- [ ] prune only app-created telemetry objects and only after successful analysis/checkpoint safety conditions are met;
- [ ] retain enough checkpoints to reconstruct recent context;
- [ ] implement a hard remote byte/file cap with visible status;
- [ ] handle Drive quota/rate errors without blocking collection;
- [ ] track Gmail trigger counts and enforce a conservative daily cap;
- [ ] expose cleanup/retention status in the bridge screen;
- [ ] make manual `Delete BlueEye telemetry` an explicit destructive action with confirmation, scoped only to the app-created tree.

**Gate:** a multi-hour and multi-day test cannot grow local or Drive storage without a known bound.

# Phase T11 — Productization decision

Only after the developer loop is reliable:

- [ ] measure battery/network cost over 30-60 minute and multi-hour sessions;
- [ ] measure Drive file count and storage growth;
- [ ] measure Gmail trigger volume and analysis latency;
- [ ] decide whether the feature remains developer-only or becomes an advanced user opt-in;
- [ ] design explicit consent/privacy copy;
- [ ] decide whether Google Drive remains the production telemetry backend;
- [ ] complete OAuth verification requirements before public distribution, especially for the sensitive `gmail.send` scope;
- [ ] document Google Workspace User Data / Limited Use compliance;
- [ ] define account deletion/revocation behavior and remote retention controls;
- [ ] ensure turning the feature off stops future network transmission promptly while preserving normal local Tracker operation;
- [ ] ensure the feature remains useful if ChatGPT automation is disconnected or unavailable.

**Gate:** no public-facing rollout until privacy, account authorization, retention, reliability, trigger feasibility and battery cost are explicitly accepted.

---

# Mandatory preimplementation audit — plan -> code -> Google

Before T1/T2 implementation begins, run one explicit audit and record the result in this document or a linked dated audit file.

## A. Plan audit

Verify:

- the data/control split is still necessary and minimal;
- one-minute delta / five-minute trigger cadence is justified;
- every remote field has a concrete analysis purpose;
- the bridge cannot become required for core Tracker operation;
- the proposed feedback/autonomous-development authority is bounded and auditable.

## B. Existing-code audit

Inspect current Tracker implementation for:

- actual scanner/session lifecycle owner;
- existing foreground-service/background-work behavior;
- Room transaction/write pressure;
- existing database/session export code that can be reused without creating a second giant serializer;
- existing DataStore/settings patterns;
- Hilt boundaries and module ownership;
- current network stack/dependencies;
- process-death/reboot recovery points;
- current session identity/source revision availability;
- where a telemetry accumulator can observe events without adding work to the hottest ingest path;
- whether Settings navigation should use a separate feature module or remain in `feature:settings` initially.

No implementation decision is accepted merely because it looks good on paper; it must fit the current code and test architecture.

## C. Google/Android capability audit

Verify against current official documentation and a real test project/account:

- current Android Google authorization API and token lifecycle;
- whether both scopes can be requested incrementally with a clean account-selection UX;
- OAuth test-user behavior and refresh-token/session expiry in Testing vs Production publishing state;
- exact verification requirements for `gmail.send`;
- `drive.file` create/list/update/delete semantics for app-created files/folders;
- ability to recover app-created folder/file ids after reinstall/re-authentication;
- Drive upload/search/list latency and quotas;
- whether fresh plain JSON uploaded by Tracker is immediately readable by the ChatGPT Drive connection;
- whether Gmail self-send reliably creates the event/message shape expected by ChatGPT;
- actual Gmail trigger/webhook availability and latency in ChatGPT;
- Gmail API and account sending limits for the selected cadence;
- Gmail filtering strategy so control traffic does not become operator spam;
- Android Doze/background/foreground-service behavior during a real active scan;
- WorkManager's 15-minute periodic minimum and its use only for retry/recovery, not one-minute sampling;
- failure behavior when network changes, account consent is revoked, or Android kills/restarts the process.

Any failed feasibility item must change the architecture before implementation continues; do not paper over it with retries.

---

# Implementation order

1. **Preimplementation audit:** plan -> current code -> Google/Android capabilities and limits.
2. T0 schema/privacy/state-machine contract.
3. T1 dedicated screen/navigation/ViewModel skeleton.
4. T2 select the dedicated account and authorize `drive.file` + `gmail.send`.
5. **End-to-end Google spike before building telemetry:** create one test JSON on Drive, send one Gmail control message referencing it, prove ChatGPT can read the exact file and return one feedback email.
6. T3 compact reducer + durable local outbox.
7. T4 productionize `GoogleDriveTelemetryTransport` and Drive hierarchy/idempotency.
8. T5 five-minute control windows + urgent Gmail trigger path.
9. T6 prove event trigger reliability; add scheduler watchdog only as fallback.
10. T7 analysis state/deduplication/anomaly rules.
11. T8 ChatGPT -> Gmail feedback.
12. T9 connect supported findings to GitHub/Local Agent engineering loop.
13. T10 retention/quota cleanup.
14. T11 decide whether/how to productize.

## First implementation slice

The first slice now proves the entire Google/ChatGPT bridge before any high-frequency telemetry work:

```text
Settings
  -> Telemetry & AI Bridge
  -> select dedicated Google account
  -> grant drive.file
  -> grant gmail.send
  -> create BlueEye/test/bridge-test.json on Drive
  -> receive exact Drive file id
  -> send BLUEEYE/BATCH_READY test mail with that file id
  -> ChatGPT trigger/manual test reads the exact JSON from Drive
  -> ChatGPT sends BLUEEYE/FEEDBACK test reply
  -> disconnect/revoke
```

Acceptance conditions:

- no primary Google account is involved;
- no full Drive scope is requested;
- no Gmail read/modify scope is requested by Tracker;
- no phone API, inbound port or external device is required;
- the Drive file remains readable through the ChatGPT-side connection;
- the trigger/feedback path is proven before implementing minute-level telemetry;
- revoke/disconnect cleanly stops further Google operations.

No automatic minute-level telemetry, perpetual worker or autonomous source modification belongs in this first slice.

## Definition of done

The workstream is complete when Tracker can continuously produce bounded, versioned diagnostic telemetry without disrupting local operation; reliably persist it through a replaceable `TelemetryTransport` whose v1 backend is narrow-scope Google Drive; create bounded analysis windows; signal new windows through send-only Gmail without using Gmail as telemetry storage; let ChatGPT consume only new Drive evidence; return understandable Gmail feedback to the operator; and turn confirmed software problems into traceable engineering work while preserving privacy, local-first behavior, deterministic source-of-truth logic and strict Google/Android capability boundaries.

## External constraints/reference set for the implementation audit

Re-check these immediately before implementation because platform behavior and quotas can change:

- Google Drive API scopes: https://developers.google.com/workspace/drive/api/guides/api-specific-auth
- Google Drive file creation/upload: https://developers.google.com/workspace/drive/api/guides/create-file
- Google Drive folders: https://developers.google.com/workspace/drive/api/guides/folder
- Google Drive application-data folder semantics: https://developers.google.com/workspace/drive/api/guides/appdata
- Gmail API scopes: https://developers.google.com/workspace/gmail/api/auth/scopes
- Gmail sending: https://developers.google.com/workspace/gmail/api/guides/sending
- Google Workspace User Data and Developer Policy: https://developers.google.com/workspace/workspace-api-user-data-developer-policy
- Android WorkManager periodic work: https://developer.android.com/develop/background-work/background-tasks/persistent/getting-started/define-work
- Android foreground-service/background-work rules: https://developer.android.com/develop/background-work/services/fgs
