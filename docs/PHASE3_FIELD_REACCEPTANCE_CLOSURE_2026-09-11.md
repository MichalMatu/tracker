# Phase 3 final field reacceptance closure — 2026-09-11

Status: **PHASE 3 ACCEPTED / CLOSED; PHASE 4 UNBLOCKED**

This document records the final engineering acceptance result for Phase 3. It supersedes the pending-status sections of `PHASE3_FIELD_REACCEPTANCE_HANDOFF_2026-09-11.md` and older Phase 3 handoffs. The raw field artifacts remain private because they contain MAC addresses, GPS and system data.

This closure is an **engineering/stability acceptance**, not a claim that every classification, Follow-Me score or identity hypothesis observed during the field run is semantically correct. Detailed analysis of the captured devices, classifications, GPS/RSSI behavior and HCI observations is the next task and may produce Phase 4/product-quality follow-ups without reopening the healthy ingest pipeline unless causal evidence requires it.

## Accepted build and device

- Repository: `MichalMatu/tracker`
- Application source accepted for the installed build: `40eac7a504d363a05cd6c235c25146e01bb36ff2`
- `main` immediately before this docs-only closure: `d15f6985371ef3ffe0496d7a14454589d56978ee`
- Production application source remains unchanged from the accepted Phase 3 line; intervening `main` commits were documentation-only.
- Package: `io.blueeye`
- Device: Samsung `SM-S906B`
- Installed version: `versionCode=1`, `versionName=1.0`
- APK SHA-256: `17ebd807c526eda077e9e7f9e96e6306924890e4c201a3c5d3e50f9d76237a65`
- Signer certificate SHA-256: `fb07493cf97b0a84ec410f7f72f12938b7f9d47151546e15c049c49c27807b11`

The post-walk collector verified the exact APK/signature identity and preserved the same app process (`process_alive_before=1`, `same_process_preserved=1`). The scanner had been intentionally paused after returning, so `scanner_service_before=0` is expected and is not a lifecycle failure.

## Final private checkpoint

Private Local Agent checkpoint:

`~/agent-workspace/repos/tracker/checkpoints/phase3-field-reacceptance-20260911/20260911T142824Z`

The checkpoint is the evidence source for final acceptance. Do not commit its raw artifacts to the public repository.

Preserved artifacts included:

- Session Export: `18,176,015` bytes, SHA-256 `4eb23f6f0ee24220b65f7555e412dea78042d0ea5ab1685f537fbe0784ef6224`
- Room database integrity: `ok`
- Room DB SHA-256: `6464896c5996bafc3e9f55cffbab01cc060a36a8a03f704f5ed6fd083a696836`
- Room SHM SHA-256: `58517a6e9dfbae9e286d2fd6eb0af5cff79fb38fe69b22460c06bade58510b2c`
- Room WAL SHA-256: `b0ba226557fdb5bec7c8c3d731729898ec1ec90b8f5139a40cf3e15bee519fac`
- Android/Samsung bugreport captured successfully
- Bluetooth HCI snoop extracted successfully; selected capture `6,304,213` bytes, SHA-256 `4ffe7a092e2e6aed8689aee3104db5fcc33b3381177e660a1e1fa8f977e295c2`

The full bugreport was deleted after the required HCI artifacts were extracted, while the private extracted HCI evidence and safe aggregate reports were retained.

## Exact ingest reconciliation — PASS

Process-lifetime Session Export diagnostics survived the field run and were preserved before process loss/reset.

- raw BLE callbacks: `55,691`
- enqueue accepted: `51,466`
- coalesced: `4,225`
- enqueue rejected: `0`
- queue dropped: `0`

Exact input equation:

`55,691 raw = 51,466 accepted + 4,225 coalesced + 0 rejected`

Result: **PASS**.

Processing reconciliation:

- processing started: `51,466`
- processing succeeded: `51,466`
- processing failed: `0`
- in flight at export: `0`

Result: **PASS**.

Persisted-path/sample reconciliation:

- provisional discarded: `1,571`
- non-provisional successful path: `51,466 - 1,571 = 49,895`
- signal samples written: `17,390`
- signal samples throttled: `32,505`
- signal sample write failures: `0`
- `17,390 + 32,505 + 0 = 49,895`

Result: **PASS**.

Queue/latency observations:

- final queue depth: `0`
- queue high-water mark: `53`
- maximum queue wait: `5,181 ms`
- maximum processing duration: `5,068 ms`

The maxima warrant normal observability during future development but produced no queue drops, rejections, in-flight backlog or processing/write failures in this acceptance run. Do not retune the queue/coalescing path solely from these maxima.

### Device persistence counter note

A preliminary aggregate script printed `device_outcome_equation_pass=False`. This is **not a valid failure equation**: `persistedDeviceUpdatesTotal` and `deviceUpdateThrottledTotal` are not mutually exclusive outcomes. A throttled canonical device update can still record a Follow-Me/tracking mutation, yielding `deviceUpdated=true` and `deviceUpdateThrottled=true` for the same processed observation. Therefore those counters must not be summed and compared 1:1 with processed observations.

## Storage/export evidence — PASS

Final persisted data:

- devices: `873`
- signal samples: `15,201`
- Follow-Me observations: `3,844`
- alert evidence events: `40`
- identity continuity candidates: `5`
- Room `PRAGMA integrity_check`: `ok`

The Session Export also reported `15,201` signal samples with GPS, raw payload and scan metadata present for every exported sample.

`signalSamplesWrittenTotal=17,390` is a process-lifetime ingest counter, while the final persisted/exported database contains `15,201` signal-sample rows. These values are not required to be equal because the user cleared/reset database data before the intended field run while the app process remained alive; process-lifetime diagnostics are not reset by Room deletion. The exact ingest/sample outcome equation above remains internally reconciled.

The export's formal `session.startedAt/sampleCount` window was not active (`startedAt=0`, `sampleCount=0`). Therefore analysis scripts must use the top-level exported signal samples / Room timestamps and the known field-run timeline rather than treating the empty formal Session window as absence of field data.

## Runtime/logcat — PASS

A broad whole-system grep initially counted unrelated Android `SecurityException`, `IllegalStateException` and scan-failure strings. App-context analysis corrected this false signal.

App-scoped result:

- `FATAL EXCEPTION`: `0`
- OOM: `0`
- app-context `SecurityException`: `0`
- SQLite exception: `0`
- scan processing failure: `0`
- queue rejection: `0`

Result: **PASS**.

Process-death/kill strings present in the broader logs are not classified as an application crash by this evidence. No P0/P1 runtime regression was reproduced.

## Field diversity and product evidence

The captured data are sufficiently large and varied for the Phase 3 stability gate: `873` devices and `15,201` persisted samples across the outing. GPS was present on all persisted samples. Earlier aggregate inspection showed real movement rather than the stationary pre-walk capture.

Root classification counts at collection time were:

- SAFE: `839`
- SUSPICIOUS: `33`
- DANGEROUS: `1`

All attention-state devices had stored evidence (`attention_without_evidence=0`). There were `33` known-tracker-type devices and none lacked raw/evidence context in the aggregate check.

These counts are **not accepted here as proof that the semantic classifications are correct**. They are preserved as the starting point for the next detailed field-data analysis.

All `873` current identity carryover verdicts were `UNREVIEWED`, and five explicit identity-continuity candidates were persisted. This supports the intended reversible evidence path for long-gap identity hypotheses instead of treating such candidates as automatically approved identity merges.

## Bluetooth HCI cross-check — PARTIAL / NON-BLOCKING

The final Samsung bugreport contained real btsnoop data. Fresh candidates contained LE advertising events:

- aggregate parsed LE advertising reports: `25,066`
- aggregate HCI unique MACs: `432`
- Tracker/HCI unique-address intersection after coarse clock alignment: `319`

The btsnoop timestamps were not directly aligned with Tracker sample timestamps. An exploratory automatic alignment found an apparent offset of `-8,316,000 ms` (~`-2.31 h`) and placed the HCI population inside the Tracker sample window, but same-MAC matches within an arbitrary 5-second window remained `0`.

Therefore the HCI evidence proves that a substantial independent LE advertising trace was captured and that the two datasets share a large address population, but it **does not support exact per-packet/per-sample timing correlation** in this run. Do not interpret `0` five-second matches as application loss; the timestamp domains/capture semantics require a dedicated analysis before such a conclusion could be made.

The Phase 3 handoff required HCI comparison **where possible**. Core acceptance evidence is independently complete through Session Export + Room/WAL/SHM + app-scoped runtime logs, so this timestamp limitation is recorded as a non-blocking analysis limitation.

## Final decision

- Code/CI gates: **PASS**
- Exact installed-build identity: **PASS**
- Physical pre-smoke: **PASS**
- Same-process diagnostics preservation: **PASS**
- Raw/queue accounting: **PASS**
- Processing accounting: **PASS**
- Signal-sample outcome accounting: **PASS**
- Queue drops/rejections: **0 / 0**
- Room integrity and WAL/SHM preservation: **PASS**
- App-scoped crash/processing-error check: **PASS**
- HCI capture: **PASS**, exact timestamp correlation limited/non-blocking
- New P0/P1 regression: **none reproduced**

**Phase 3 is ACCEPTED and CLOSED. Phase 4 is UNBLOCKED.**

No application-code change is required for this closure. Structural cleanup already listed in `PHASE3_FINAL_CODE_QUALITY_AUDIT_2026-09-11.md` remains post-acceptance debt unless later analysis demonstrates a causal defect.

## Next work: actual captured-data analysis

The next task is not another acceptance walk. Analyze the preserved field dataset itself, including at least:

1. reconstruct the outing timeline from top-level signal-sample timestamps/GPS instead of the empty formal Session window;
2. segment movement vs stationary periods and BLE-density changes;
3. inspect the `1 DANGEROUS` and `33 SUSPICIOUS` classifications against their raw payloads, RSSI, movement evidence and Follow-Me scoring components;
4. analyze known tracker types and likely false-positive/true-positive patterns;
5. analyze RSSI continuity and repeated encounters across locations;
6. inspect the five long-gap identity candidates and rotating-address behavior without approving destructive merges from same-name proximity alone;
7. correlate Room, exported evidence and HCI address populations, explicitly accounting for the HCI/Tracker timestamp-domain mismatch;
8. turn findings into bounded Phase 4/product-quality recommendations only after the evidence is understood.

Raw GPS/MAC/payload data must remain private and must not be committed to the repository.