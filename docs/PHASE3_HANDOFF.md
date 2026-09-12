> **HISTORICAL STATUS NOTICE:** Phase 3 is ACCEPTED / CLOSED and Phase 4 is UNBLOCKED. This file is retained as stabilization provenance; do not use any older NEXT ACTION, pending-walk status, pre-field build, or blocked-phase statement below as current instructions. Current work starts from docs/README.md and docs/UI_UX_REDESIGN_PLAN.md.

<!-- PHASE3-2026-09-11-FINAL-OVERRIDE -->
> **2026-09-11 FINAL FIELD-REACCEPTANCE OVERRIDE — read this first.**
>
> The installed/accepted application build is source SHA `40eac7a504d363a05cd6c235c25146e01bb36ff2` on Samsung `SM-S906B`. Production application source is unchanged from milestone `630e184dbc328672a961e2826208fddd77a68f02`; later changes through `40eac7a...` are documentation plus the bounded export-schema test correction. Exact-SHA Secret Scan, Quality, Android UI Smoke, Sandbox Pack and Tester Release are green, and targeted ADB pre-smoke (Start/Stop + screen-off/Dozing) passed.
>
> **Authoritative continuation document:** `docs/PHASE3_FIELD_REACCEPTANCE_HANDOFF_2026-09-11.md`. The final structural/code-quality second pass is `docs/PHASE3_FINAL_CODE_QUALITY_AUDIT_2026-09-11.md`. The historical handoff below remains evidence only.
>
> NEXT ACTION is the final varied-density walk and preservation/correlation of Session Export + ADB/logcat + Room/WAL/SHM + Bluetooth HCI snoop from a system bugreport. Do not change application code before that evidence is captured unless a new P0/P1 failure is reproduced.

# Phase 3 New-Chat Handoff — Field Walk Findings and Targeted Stabilization

Status: **FIELD WALK COMPLETED; BLOCKING FIELD DEFECTS FOUND; PHASE 3 NOT CLOSED**
Repository: `MichalMatu/tracker` (`tracker`)
Current application source at handoff: `4e3751598f9df679cd7e8e55482e17fb35fdc622` (`Harden long field scan export`)
Local Agent binding: `be481b25-9d97-4205-b93f-95f5c5827441`
Local Agent chat bridge: `chat-11f92ede`
Control branch: `agent-control`
Source branch: `main`

> At the start of a new chat, fetch current `main` and re-verify the exact SHA before any source change. Do not assume this handoff commit is still HEAD.

## Current situation — 2026-09-10

A varied-density physical walk was performed on Samsung S22+ using the physically installed build from source `4e3751598f9df679cd7e8e55482e17fb35fdc622`. The user deliberately cleared the old observation database before the walk so this run would start from a clean field dataset. Do not interpret pre-walk database preservation as a current product requirement; retention policy is now explicitly part of the follow-up audit.

The raw Session Export is sensitive telemetry containing MAC addresses and GPS and is intentionally **not committed to this public repository**. The current chat attachment was named `BlueEye session export v2.json`. This handoff records only the minimum anonymized evidence needed to continue safely.

The walk was useful but **cannot close Phase 3** because the user had to stop/kill the application to silence an uncontrollable alert, and the field data exposed false-positive classification and identity-continuity defects. Phase 4 remains blocked until these stabilization blockers are understood and the required minimum fixes are physically reaccepted.

## Field evidence already established

The ingest pipeline itself survived the captured field window without unexplained loss:

- raw BLE callbacks: `20,819`
- enqueue accepted: `18,005`
- coalesced: `2,814`
- enqueue rejected: `0`
- queue dropped: `0`
- processing started/succeeded/failed: `18,005 / 18,005 / 0`
- persisted device updates: `9,197`
- device updates intentionally throttled: `8,317`
- signal samples written: `6,138`
- signal samples intentionally throttled: `10,899`
- signal sample write failures: `0`
- queue high-water mark: `87`
- maximum observed queue wait: `5,988 ms`
- maximum observed processing duration: `5,754 ms`
- end-state diagnostics recorded scanner `Idle`, lifecycle `DESTROYED`, with no scanner last error.

Do **not** retune the Phase 3 queue/coalescing/throttle design merely because this field run found other defects. First preserve the fact that accounting reconciles and there were zero rejected/dropped/failed events. The latency tail is worth explaining during the re-audit, but it is not evidence by itself that the bounded ingest design is wrong.

## Blocking defects discovered during the walk

### P0 — currently playing alert cannot be stopped

Observed behavior: an alert became so intrusive that the user had to terminate the application to silence it.

Current code already explains the failure mode:

- `AndroidAlertDispatcher.playAlertSound()` creates a `Ringtone`, calls `play()`, and does not retain ownership of that active object.
- `AlertDispatcher` exposes `dispatch()` and `refreshDiagnostics()` but no `stopSound()`, `cancelVibration()`, `acknowledge()` or `cancelAll()` contract.
- disabling alerts therefore prevents future dispatches but does not guarantee immediate cancellation of an already playing sound/vibration/notification.
- this failure was already listed as a P0 risk in `STABILITY_RECOVERY_GUIDE.md`; the field walk now physically reproduces it.

### P0/P1 — false positive: bare `X5` classified as public-safety/tactical audio

Observed field device named `X5` was classified as `TACTICAL_AUDIO`, surfaced in the `PUBLIC_SAFETY` review category, and reported as consistent with `Invisio V60 II` on name-only evidence.

Exact source cause is visible in current code:

- `TacticalNamePatterns.INVISIO_EXTENDED_PATTERNS` contains an unqualified `Regex("X5", IGNORE_CASE)`.
- the registry maps any match from that whole pattern group to `TACTICAL_AUDIO` / `"Invisio V60 II"`.

This is too broad and the model mapping is semantically wrong: an ambiguous bare token must not become a specific V60 II identity or a public-safety-like user-facing alert without stronger corroboration.

The re-audit must inspect **all** similarly broad/unanchored tactical/public-safety name patterns, not only `X5`. Particular attention is required for short model fragments and patterns that can accidentally match random/hex/MAC-like advertised names (for example body-camera family fragments such as `D3/D4/D5`, other two/three-character tokens, and generic model numbers). Do not solve this by blindly deleting the classifier; define the evidence boundary first.

### P1 — one physical device can fragment into multiple fingerprints after address rotation

The field export contains repeated named consumer devices that appear under more than one logical fingerprint during the same walk. Examples include `OPPO Enco Buds3 Pro` and `JBL Tune 520BT-LE`.

Current correlation code has a `30,000 ms` carryover window. The captured duplicate appearances can be separated by longer gaps than that, so some fragmentation is expected under the present contract. Do **not** simply increase the window: a longer fuzzy-merge window can create destructive false merges in dense environments.

The audit must distinguish four different phenomena before changing behavior:

1. genuine BLE MAC rotation for one physical device,
2. multiple physical devices sharing the same consumer product name,
3. UI showing an observed/rotating MAC where a stable logical fingerprint was expected,
4. incorrect destructive/over-aggressive correlation joining unrelated advertisements.

Any identity fix must preserve the recovery rule: weak correlation is evidence/candidate continuity, not permission to destroy source history.

### P1 — Apple signal decoder can present protocol/frame interpretation as a physical product model

The captured data includes a device named `MacBook Air` whose advertisements are decoded in some samples with labels such as `Apple Vision Pro`, while other advertisements associated with the same logical identity use other Apple signal labels.

Audit whether Apple decoders are conflating a frame subtype/state signature with a physical hardware model. A protocol-level observation should not overwrite or outrank a trustworthy advertised/user identity unless evidence is actually model-specific.

### UX/stability follow-ups — retention and startup loading spinner

The user explicitly wants old ordinary scan data removed instead of accumulating indefinitely. Before implementing, audit the existing cleanup job and all data classes it preserves/deletes. Product direction for the fix:

- ordinary transient observation/sample history should have an explicit bounded retention policy,
- intentional user state such as watchlist membership, aliases, calibration/verdicts or other user-owned annotations must not be silently destroyed,
- a clean/new field session should not require manual database surgery,
- avoid preserving large stale scan datasets only because `adb install -r` preserves application data.

There is also a visible startup-loading bug: the loading spinner appears offset to the left instead of centered in the application content. Treat this as an isolated Compose layout defect. Audit the root loading container/alignment first; do not redesign unrelated screens.

---

# FIRST ACTION IN THE NEW CHAT — TARGETED READ-ONLY RE-AUDIT

**Do not begin by patching. Do not change behavior during this step.**

Perform a fresh, source-of-truth re-audit of current `main` targeted specifically at the failures above. The output of this first step must be a compact evidence table or report containing, for each defect: exact call/data path, exact current source locations, proven failure mechanism, ambiguity still unresolved, smallest safe correction boundary, regression evidence required, and whether physical-device verification is mandatory.

The re-audit must cover at least the following paths.

## 1. Alert ownership and cancellation audit

Trace end to end:

`Settings UI -> SettingsPreferencesRepository/trackerAlerts -> TrackerAlertService/TacticalAlertService -> AlertDispatcher -> Android notification + Ringtone + vibration`

Answer from code, not assumptions:

- Who owns the currently playing `Ringtone`?
- Who owns active vibration and how is it cancelled?
- What happens immediately when `detectionEnabled`, sound or vibration is switched OFF while an alert is already active?
- Can one logical detection create multiple user-facing alert side effects?
- Are cooldown keys stable across rotating MACs or tied to transient addresses?
- Is there any notification action/UI path that acknowledges and stops the alert?
- What exact lifecycle should `cancelAll()/acknowledge()` have, and which layer should own it?

The likely correction is one authoritative alert runtime owner that can synchronously stop/cancel current side effects, but **confirm the smallest coherent design from current source before implementing it**.

## 2. Tactical/public-safety false-positive audit

Trace:

`advertised name/raw payload/service UUID -> tactical classifier -> TacticalOuiRegistry/name patterns -> evidence -> TacticalAlertService/public-safety review/alert`

Audit every name pattern for:

- bare short tokens,
- unanchored regexes,
- numeric/model fragments with high collision probability,
- accidental matches against MAC-like or generated names,
- one ambiguous family token being mapped to a different specific model,
- name-only evidence escalating too far into `PUBLIC_SAFETY_SIGNAL`.

Use the `X5 -> Invisio V60 II` field failure as the first regression fixture. Also inspect the field evidence for body-camera-like false positives caused by short patterns matching arbitrary names. The fix should make ambiguous names evidence-only or require brand/context/corroboration; do not weaken strong vendor/service/payload evidence unnecessarily.

## 3. Identity/fingerprint audit

Trace:

`BleScanResultData -> AddressCarryoverTracker -> DeviceCorrelationStrategy -> MacAddressResolver -> DevicePersister -> Room DeviceEntity -> Device mapper -> Radar/Details identity presentation`

Replay/explain at least the captured duplicate-name cases (`OPPO Enco Buds3 Pro`, `JBL Tune 520BT-LE`) and inspect:

- the 30-second carryover expiry,
- exact same-name proximity shortcut behavior,
- payload/UUID/interval/RSSI weights,
- Apple shadow matching,
- stable logical fingerprint vs latest observed MAC,
- how identity evidence is persisted and later displayed,
- what happens after screen-off gaps or sparse advertisements.

Do not increase the correlation window until false-merge risk has been measured. Prefer reversible candidate/carryover evidence when confidence is not strong enough for a deterministic merge.

## 4. Decoder semantic audit

Trace the Apple/manufacturer decoder path producing model/beacon labels and establish which outputs mean:

- physical device model,
- protocol/service family,
- advertisement frame subtype,
- device state.

Find why a known/named MacBook Air sample can be rendered with `Apple Vision Pro` beacon/model text. Define precedence rules so a weak decoder guess does not replace a stronger identity signal.

## 5. Retention + spinner audit

For retention, locate the existing cleanup scheduler/DAO cascade behavior and document exactly:

- current retention duration,
- first cleanup timing and recurrence,
- which device/sample/evidence/follow-me rows are removed,
- which user-owned/watchlist/calibration state survives,
- why stale data can remain visible long enough to be annoying before a new field run.

Then propose the smallest product-safe retention/session-reset behavior consistent with the user's request for disposable old observation history.

For the spinner, locate the startup loading composable and parent constraints/alignment. Prove why it sits left and propose the one-line/small-layout fix if possible.

## 6. Re-audit the field latency tail without changing the healthy ingest accounting

Explain the observed `maxQueueWaitMs=5988` and `maxProcessingDurationMs=5754` from code/log evidence if possible. Check whether alert/classification/Room/location/export work can still block the single processing consumer. Do not change queue capacity/coalescing unless a direct bottleneck is proven.

---

# Implementation plan after the re-audit

Only after the targeted audit has established exact failure boundaries, implement in small independent stabilization changes, each with focused regression evidence:

1. **P0 alert cancellation/ownership** — currently active sound/vibration/notification must be immediately stoppable; global OFF must stop existing side effects, not only future dispatches. Preserve existing Phase 2 scanner lifecycle semantics.
2. **Classifier false positives** — fix `X5` first, then other audited high-collision short-name patterns; ensure ambiguous name-only evidence cannot masquerade as a specific tactical model or emit a strong public-safety conclusion without corroboration.
3. **Identity continuity/presentation** — fix only the failure mode proven by replay. Separate logical fingerprint from observed MAC in UI/exports where currently ambiguous; avoid long-window destructive fuzzy merges.
4. **Decoder semantics** — separate Apple protocol/frame labels from physical-model identity and establish evidence precedence.
5. **Retention/session hygiene** — bound disposable scan history while preserving intentional user state; make starting a clean field session straightforward.
6. **Startup spinner** — center it with the smallest Compose layout correction and add a UI assertion/screenshot test if practical.
7. **Latency tail** — optimize only if the audit identifies a named expensive operation on the ingest consumer path.

Do not bundle all seven into one giant refactor. Prefer one named failure mode per commit/change group, with tests that fail before and pass after.

## Verification sequence after each meaningful source change

- focused unit/regression tests for the changed subsystem,
- affected module tests/detekt,
- sandbox `qualityCheck` / `assembleDebug` as appropriate,
- canonical exact-SHA GitHub Actions,
- Local Agent only for hardware/ADB evidence that the sandbox cannot produce.

Before the next full walk, physically verify on the Samsung S22+ at minimum:

- active alert can be stopped immediately without killing the app,
- global alert OFF stops an already active sound/vibration,
- known `X5` regression fixture no longer produces the bad public-safety/Invisio conclusion,
- scanner Start/Stop and screen-off behavior remain intact,
- Share/export still parses and does not OOM,
- exact installed source SHA/signing identity is recorded.

Only then repeat a clean varied-density walk and reconcile the final Room/WAL/SHM + Session Export evidence. Phase 3 remains open until that reacceptance passes.

---

# Execution model — Sandbox + GitHub + Local Agent + Local Chat Bridge

Use a **sandbox-first hybrid workflow**. Choose the worker based on evidence required; do not default everything to the Mac.

## ChatGPT sandbox

The sandbox is the default isolated engineering environment for source inspection, patch preparation, static analysis and most JVM/Gradle tests. It does **not** have access to the physical Samsung phone or the user's live Mac peripherals.

Tracker's persistent sandbox bootstrap assets are documented in `docs/SANDBOX_EXECUTION_FLOW.md` and stored in ChatGPT Library under `/Tracker/Sandbox/`. A fresh sandbox is ephemeral: restore the known kit, obtain the exact intended source SHA, run `tools/sandbox/bootstrap-sandbox.sh`, source the generated environment, run `tools/sandbox/sandbox-doctor.sh`, then use the repository sandbox test/Gradle wrappers. Build runtime is JDK 21 while generated JVM bytecode target remains 17. Run broad heavy gates sequentially under the repository's bounded worker settings rather than spawning uncontrolled parallel Gradle work.

A sandbox PASS is useful engineering evidence, but canonical networked CI remains GitHub Actions.

## GitHub / GitHub Actions

Use GitHub as source-of-truth for `main`, diffs, commits and exact-SHA workflow status. Direct GitHub edits are appropriate for small deterministic docs/source diffs when the exact change is fully understood and CI can verify it. Do not claim a source SHA is accepted until the relevant workflows for that exact SHA are observed.

## Local Agent / Mac / Samsung phone

Use Local Agent for deterministic commands that require the user's Mac, ADB, installed APK inspection, persistent signing, physical screen/Bluetooth behavior or other local-device evidence. **Never launch/delegate local Codex from a Local Agent task. ChatGPT remains the planner; Local Agent executes deterministic commands only.**

Hard binding for this project is immutable:

- repository_id: `tracker`
- repository: `MichalMatu/tracker`
- agent_binding: `be481b25-9d97-4205-b93f-95f5c5827441`
- chat bridge: `chat-11f92ede`

Before every Local Agent queue/check, fetch BOTH `agent-control:.agent/binding.json` and `agent-control:.agent/status/daemon.json` and verify the exact repository/binding. Check active local tasks before editing the same branch. Every new Local Agent task JSON must contain exactly:

```json
"agent_binding": "be481b25-9d97-4205-b93f-95f5c5827441",
"resources": []
```

Source/docs belong on `main`; Local Agent control/status/tasks/payloads/results belong on `agent-control`. Use `allow_write:false` unless source/docs modification is actually required. New or changed payload means a new unique task ID. If a task is active and healthy, do not poll faster than two minutes; normally use 5–10 minute wakes for multi-minute builds. If exact evidence proves a task cannot succeed, cancel that exact task through repository control rather than waiting for timeout.

## Local Chat Bridge

The bridge lets this chat wake and continue the hard-bound Local Agent workflow. Bridge controls such as `[LAB:NEXT=5m]`, `[LAB:PAUSE]`, `[LAB:RESUME]` and `[LAB:STOP]` are conversation-scoped only; they never change repository identity or the global Master switch. A new chat must keep the exact tracker binding and must never infer or switch to another repository.

---

## Read first in the new chat

1. `AGENTS.md`
2. this `docs/PHASE3_HANDOFF.md`
3. `docs/STABILITY_RECOVERY_GUIDE.md`
4. `docs/PHASE3_TARGETED_PHYSICAL_ACCEPTANCE.md`
5. `docs/PHASE3_PRE_FIELD_GOLDEN.md`
6. `docs/PHASE3_FIELD_COLLECTION.md`
7. `docs/PHASE3_CODE_QUALITY_REVIEW.md`
8. `docs/SANDBOX_EXECUTION_FLOW.md`
9. `docs/QUALITY_GATE.md`
10. `docs/ARCHITECTURE_CURRENT.md`

Preserve the accepted Phase 2 scanner contract, especially explicit `START_NOT_STICKY`, idempotent Start/Stop, no ghost scanner after service teardown, and no automatic restart just because Bluetooth returns. Do not rewrite scanner lifecycle while fixing alert/identity/classification defects unless the targeted audit proves a direct dependency.

## Definition of done for the next stabilization cycle

The next cycle is done only when the re-audit is recorded, the field-proven defects are fixed with regression evidence, sandbox/canonical CI are green for the exact source SHA, the required short Samsung physical regressions pass, and a new clean varied-density walk can complete without killing the application, uncontrollable alerts, known classifier false positives or unexplained identity churn. Only then formally close Phase 3 and unblock the next phase.
