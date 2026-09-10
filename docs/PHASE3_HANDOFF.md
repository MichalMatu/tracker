# Phase 3 New-Chat Handoff — Ingest Observability

> **Historical kickoff note (2026-09-08):** Phase 3 software implementation has now landed. The old pipeline facts below describe the preimplementation state and must not be treated as current runtime truth. For current state use `STABILITY_RECOVERY_GUIDE.md`, `PHASE3_TARGETED_PHYSICAL_ACCEPTANCE.md`, `PHASE3_PRE_FIELD_GOLDEN.md`, `PHASE3_FIELD_COLLECTION.md` and `PHASE3_CODE_QUALITY_REVIEW.md`. Phase 3 remains open only for field validation/reconciliation.

Status: **HISTORICAL KICKOFF — TARGETED PHYSICAL ACCEPTANCE PASS; VARIED-DENSITY WALK PENDING**
Repository: `MichalMatu/tracker` (`tracker`)
Phase 2 implementation commit: `84b31d08bac81b6f46e44d11ccfe06071438b66a`
Phase 2 closure-audit source baseline: `53c56fa54e92a713e555979f2d1778dc44dfaf98`
Local Agent binding: `be481b25-9d97-4205-b93f-95f5c5827441`

The closure documentation commit containing this file is intentionally not hard-coded as its own HEAD. At the start of the next chat, fetch current `main` and treat that exact SHA as source of truth before doing any work.

## Current acceptance checkpoint — 2026-09-10

Application source `745fdf30271459a20e380763ea69b8ed2e601839` has exact-SHA green CI and targeted Samsung S22+ physical acceptance for the previously open large-export OOM, Settings performance under active Radar load, and short process-lifetime ingest accounting. The accounting reconciles `443 = 417 accepted + 26 coalesced + 0 rejected`, with zero queue drops and zero processing/sample write failures. Earlier >430-second passive-BLE watchdog survival with two 240-second refreshes remains accepted.

The only remaining Phase 3 field gate is one varied-density walk with preserved Session Export JSON plus Room/WAL/SHM continuity evidence. Do not start Phase 4 yet.

## Read first

1. `AGENTS.md`
2. `docs/STABILITY_RECOVERY_GUIDE.md` and its `NEXT ACTION`
3. `docs/PHASE3_PRE_FIELD_GOLDEN.md`
4. `docs/PHASE2_CLOSURE_AUDIT.md`
5. this file
6. `docs/SANDBOX_EXECUTION_FLOW.md`
7. `docs/QUALITY_GATE.md`
8. `docs/ARCHITECTURE_CURRENT.md`
9. `docs/PIPELINE_AUDIT.md` for historical pipeline context

Do not reconstruct Phase 1/2 from memory; use repository documents and exact current SHA.

## Phase 2 state inherited by Phase 3

Phase 2 is complete. Preserve this lifecycle contract:

- `ScannerService` is the single owner of global `BleScanner` lifecycle.
- Start and Stop are idempotent; Stop tears down scanner resources.
- `onDestroy()` cannot leave a ghost scanner.
- restart policy is explicit `START_NOT_STICKY`.
- Bluetooth ON alone does not restart scanning after OFF teardown.
- process relaunch remains Idle until explicit Start.
- focused -> passive transition is deterministic.
- technical restart does not reset unrelated Follow-Me logical state.
- lifecycle transitions are exposed in diagnostics/export.

Physical acceptance passed on Samsung SM-S906B / Android 16, including Bluetooth OFF/ON, background/foreground, force-stop/relaunch, screen sleep/wake, database integrity, zero app FATAL/ANR and earlier 20-cycle Start/Stop testing. Do not change this lifecycle contract casually during Phase 3.

## Phase 3 problem statement

The next problem is **observability and boundedness of ingest**, not scanner ownership. Current confirmed pipeline facts:

- `BleScanner` owns a sequential `Channel<ScanEvent>` with capacity `4096`.
- overflow policy is `BufferOverflow.DROP_OLDEST`.
- `onUndeliveredElement` and enqueue failure increment local `droppedScanEvents`.
- the local drop counter is logged but is not wired to `ScannerRuntimeDiagnosticsStore.recordDroppedQueueEvents(...)`.
- one processing coroutine consumes events sequentially and calls `repository.handleScanResult(...)` / Classic equivalent.
- `DeviceRepositoryImpl.handleScanResult(...)` records the BLE result in runtime diagnostics before delegating to `BleScanHandler`.
- diagnostics expose rolling BLE/Classic input rates and a `droppedQueueEvents` field, but do not explain the complete received -> queued/coalesced -> processed -> persisted path.

## Phase 3 target

Make accounting explicit:

`received -> enqueue accepted/rejected -> coalesced -> processed -> persisted device update -> signal sample written -> intentionally dropped/failed`

For a defined window or synthetic burst, be able to state raw callbacks, queued, dropped, coalesced, processed, persisted device updates, signal samples written, queue pressure/high-water approximation, processing latency/backlog and the reason for intentional discard.

## Recommended implementation order

1. **Preimplementation audit, no behavior change:** trace `BleScanSource` -> `BleScanner` -> `DeviceRepositoryImpl` -> `BleScanHandler` -> persistence/signal samples and define exact counter semantics.
2. **Diagnostics model/store:** add precise domain fields/store operations without conflating callbacks, queue events and persistence.
3. **Instrument current behavior first:** wire the existing drop producer and add current-queue counters without changing `DROP_OLDEST` or coalescing yet.
4. **Synthetic pressure tests:** exceed expected dense-city traffic and reconcile counts, not merely absence of exceptions.
5. **Bounded/coalesced design:** only after counters are trustworthy, introduce per-device coalescing/debounce or another bounded policy while preserving newest observation time/evidence.
6. **UI/export diagnostics:** expose counters without growing `SettingsViewModel`; prefer small mappers/models/cards.
7. **Full gates:** focused tests -> affected modules -> `qualityCheck` -> `:app:assembleDebug` -> exact-SHA GitHub Actions; phone only when Android/hardware evidence is required.

## Explicitly out of scope

Do not mix in identity/carryover heuristic redesign (Phase 5), alert ownership (Phase 4), Radar/history semantics (Phase 6), RFCOMM/Classic/active-probe reintroduction (Phase 8), broad Compose decomposition, wholesale detekt/ktlint cleanup, or refactoring `ScannerService` merely because it is 423 LOC. If instrumentation proves a direct blocker, isolate the minimum exception and document it.

## Known debt to protect against

See `PHASE2_CLOSURE_AUDIT.md`. Key hotspots: `DeviceEvidenceFactory` 733 LOC / 27 functions; `DeviceCorrelationStrategy` 581 / 21; `DatabaseExporter` 572 / 22; `SettingsViewModel` 543 / 29; `ScannerService` 423 / 25. Inactive RFCOMM code retains `GlobalScope`; a Bose handler retains `Thread.sleep`. Do not turn Phase 3 into a repository-wide refactor.

## Worker model — sandbox + GitHub + Local Agent

Use **sandbox-first hybrid execution**.

### ChatGPT sandbox — default engineering worker

Use for source inspection, architecture analysis, patch preparation, static checks, focused JVM/Kotlin tests and Android Gradle work when the compatible offline pack is restored. Persistent bootstrap assets live in ChatGPT Library under `/Tracker/Sandbox/`. Verify bundle checksums and exact source SHA before execution. Fresh flow: restore sandbox kit -> exact-SHA source -> `tools/sandbox/bootstrap-sandbox.sh` -> source `env.sh` -> `tools/sandbox/sandbox-doctor.sh` -> `run-sandbox-check.sh` / `run-sandbox-gradle.sh`. Broad `qualityCheck` and `assembleDebug` run sequentially on the bounded 2-worker profile. Build runtime is JDK 21; bytecode remains JVM 17.

### GitHub Actions — canonical networked gate

Use GitHub Actions for canonical exact-SHA `qualityCheck`, APK build, secret scan, rolling tester release and network dependency resolution. A sandbox PASS is not a substitute for canonical CI.

### Local Agent / Mac — hardware and machine evidence

Use Local Agent only for ADB, physical Android phone, screen/Bluetooth behavior, Mac-specific reproduction or evidence unavailable elsewhere. Before queueing, verify `agent-control`, repository identity and binding. Every Local Agent task for this conversation must contain exactly `"agent_binding": "be481b25-9d97-4205-b93f-95f5c5827441"`. Do not use Mac as default CI when sandbox/GitHub exact-SHA evidence can answer the question, and do not race an active task modifying `main`.

## Phase 3 exit criterion

A synthetic stress run and normal diagnostics can state exactly how many events were received, queued/coalesced, processed, persisted or intentionally discarded, with no silent queue-loss path. All focused/full gates are green and documentation advances to the next phase. Stop there and hand off to Phase 4; do not begin alert refactoring inside Phase 3.
