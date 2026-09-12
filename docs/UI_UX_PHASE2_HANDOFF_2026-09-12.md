# UI/UX Phase 2 Handoff — 2026-09-12

## Purpose

This is the continuation handoff for a new ChatGPT window. It replaces conversational context as the source for what to do next on the current UI/UX redesign.

## Hard repository binding

Work only on:

- repository: `MichalMatu/tracker`
- local repository id: `tracker`
- working branch: `ui/radar-details-redesign`
- Local Agent binding: `be481b25-9d97-4205-b93f-95f5c5827441`
- Local Agent chat: `chat-0d1a9278`

Do not infer or switch repository identity. Before Local Agent mutation, verify `.agent/status/daemon.json` is idle and still bound to this repository. Never invoke local Codex from Local Agent tasks.

## Authoritative baseline

- accepted `main` baseline: `093b4257859abdbcc683f1620969b06195ade7a6`
- Phase 3: **CLOSED / ACCEPTED**
- authoritative Phase 3 closure: `docs/PHASE3_FIELD_REACCEPTANCE_CLOSURE_2026-09-11.md`
- active execution plan: `docs/UI_UX_REDESIGN_PLAN.md`
- Phase 1 acceptance: `docs/UI_UX_PHASE1_ACCEPTANCE_2026-09-12.md`
- P2B accepted code checkpoint: `0d40dd1418456f42008a35222ae30c0a2498d41d`
- draft PR: `#7` (`ui/radar-details-redesign` -> `main`)

Documentation commits may be newer than the code checkpoint above. When resuming, fetch the branch and inspect the current HEAD; do not reset past documentation-only commits.

## Current phase status

### Phase 0 — CLOSED

Baseline/guardrails were completed before UI mutation.

Private physical baseline on Samsung SM-S906B was captured without committing sensitive telemetry. Baseline Radar `gfxinfo`:

- 644 frames
- 87 janky frames = 13.51%
- p95 = 22 ms
- p99 = 44 ms

The preserved Room database contained 967 devices and 43,705 signal samples, but only 16 devices were in the final 180-second recent window. Therefore the 100+ acceptance scenario is deterministic test-only data, not a claim about that one live window.

### Phase 1 — CLOSED / ACCEPTED

Radar cards were simplified and stabilized:

- whole card opens Details; redundant Details action removed;
- full Evidence/source/reason/value dump removed from Radar cards;
- calibration removed from Radar;
- compact fixed structure retained: name/RSSI, vendor/type, Seen/technology, at most bounded important status indicators;
- Watchlist retained as a compact quick action;
- `stableLiveHeight()` removed from Radar cards;
- 120-card instrumentation stability test added and passed on S22+;
- live card geometry remained stable across updates.

Accepted physical P1 measurement before P2A:

- janky frames = 11.59%
- p95 = 21 ms
- p99 = 38 ms

Do not reopen Phase 1 without a concrete new regression.

## Phase 2 — IN PROGRESS

Goal: reduce Radar presentation/data-path cost without changing scanner, parser, identity, Follow-Me, scoring or persistence semantics.

### P2A — ACCEPTED: move presentation transformation off the UI thread

`RadarViewModel` now applies `flowOn(Dispatchers.Default)` to the combined Radar transformation path.

The corrected physical benchmark explicitly navigated to Radar, started scanning, confirmed 5 live rows and ran the same 16-swipe `gfxinfo` scenario:

- total frames rendered: 813
- janky frames: 30 = **3.69%**
- legacy janky: 54 = 6.64%
- p50 = 6 ms
- p90 = 9 ms
- p95 = 11 ms
- p99 = 34 ms
- crash/error logcheck: PASS

This is strong evidence that moving the transformation off main improved the real-device path. It is **not** a 100+ live-device proof because this run had 5 fresh rows.

P2A CI at its tested checkpoint passed Quality and Secret Scan.

### P2B — ACCEPTED: remove dead Radar presentation work

After Phase 1, `RadarUiMapper` still constructed presentation data no longer rendered by the compact card. P2B removed that dead contract/work:

- removed `sensorData` from `RadarUiItem`;
- removed `connectionInfo` from `RadarUiItem`;
- removed `badges` from `RadarUiItem`;
- removed `evidenceInfo` from `RadarUiItem`;
- removed unused `RadarUiConnectionInfo`, `RadarBadgeInfo`, `RadarEvidenceInfo`, `RadarEvidenceChipInfo` and `RadarParsedSensorData` presentation models;
- removed the unused `RadarEvidenceUiFormatter` path;
- removed the unused `RadarSensorDataParser` path from Radar presentation;
- removed obsolete connection/badge/sensor formatting from `RadarUiFormatter`;
- retained active-probe feedback through the still-rendered `isProbing` state;
- retained full `Device.evidence` for `RadarUiSectionMapper`, so section classification was not intentionally changed;
- updated test fixtures to the actual compact-card contract rather than disabling tests.

Exact accepted P2B code checkpoint:

`0d40dd1418456f42008a35222ae30c0a2498d41d`

Verification on that SHA:

- `:feature:radar:detekt` PASS
- `:feature:radar:testDebugUnitTest` PASS
- `:feature:radar:compileDebugKotlin` PASS
- `:app:assembleDebug` PASS
- local git tree clean after verification
- GitHub Quality #170 PASS
- GitHub Secret Scan #200 PASS

## Important remaining cost

Phase 2 is **not closed**.

Current upstream path still performs full recent-device mapping:

`Room devices SELECT * -> DeviceEntity list -> DeviceRepositoryImpl.toDomain() -> DeviceMapper / DeviceEvidenceFactory -> GetScannedDevicesUseCase -> sample(750 ms) -> Radar mapping/sectioning`

Important consequence: `sample(750 ms)` limits downstream presentation emissions, but it does not by itself prevent Room invalidations and full entity-to-domain/evidence mapping upstream.

P2A moved this CPU work away from main; P2B removed dead presentation work. The next meaningful optimization should target the upstream full-device/full-evidence path rather than micro-tuning Compose.

## Recommended next step — P2C

Start with a read-only implementation audit and then make the smallest behavior-preserving change that reduces work **before or during** full entity-to-domain mapping.

Preferred direction:

1. inspect `DeviceEntity`, `DeviceMapper`, `DeviceEvidenceFactory`, `DeviceRepositoryImpl.getRecentDevices`, `DeviceSearchDao`, `RadarUiMapper` and `RadarUiSectionMapper` together;
2. identify the exact minimal fields/decision flags Radar needs for filtering, ordering, visible card content and section classification;
3. design a lightweight Radar-specific projection/query/domain contract (`RadarDeviceSummary` or equivalent) rather than loading `SELECT *` plus full technical/GATT/raw fields;
4. preserve existing section semantics with explicit tests before replacing the old path;
5. only use `conflate()`/cadence changes as secondary improvements; do not use them to hide avoidable full `DeviceEvidenceFactory` construction;
6. measure the new path on S22+ and with the deterministic 120-item scenario before calling Phase 2 complete.

Do **not** jump directly into Details redesign until Phase 2 has a clear closure decision.

## Phase 2 open checklist

Still open from the main plan:

- lightweight Radar projection/model;
- avoid full technical/GATT/raw loading for Radar where possible;
- avoid complete `DeviceEvidenceFactory` construction solely for Radar where possible while preserving section behavior;
- preserve only the minimum decision flags/data required for Radar sectioning;
- review the 750 ms presentation cadence after structural data-path cleanup;
- verify whether one device update causes avoidable full-list work;
- repeat performance measurement with a dense controlled population;
- 30–60 minute dense-scan UI soak before final Phase 2 closure.

Already established and should not be redone without reason:

- stable list key is fingerprint/stable identity;
- Phase 1 120-card structural stability test passes;
- P2A `flowOn(Dispatchers.Default)` physical direction is accepted;
- P2B dead presentation contract is removed and green.

## Product/UI direction after Phase 2

When Phase 2 is closed, continue the existing plan rather than redesigning from scratch:

1. Details summary / Why it matters
2. prominent Tracking & Signal with RSSI graph retained
3. Key evidence
4. Identity
5. Actions / Review
6. History
7. collapsed Technical details
8. All evidence
9. later per-device Sightings map

Raw UUID/payload/GATT information remains accessible but should not dominate the surface. Local deterministic verdict and future optional AI analysis must remain clearly separate.

## Privacy / field-data rule

Do not commit private field captures, exact GPS, MAC addresses, raw HCI/bugreport/Room databases or screenshots containing sensitive telemetry. Checkpoint such artifacts only in the Local Agent private workspace when needed.

## Resume procedure

At the beginning of the next chat:

1. verify repository/binding and Local Agent idle state;
2. fetch `main` and `ui/radar-details-redesign`;
3. verify `main` has not unexpectedly moved relative to the accepted baseline or account for any legitimate movement;
4. inspect current branch HEAD and PR #7 status;
5. read this handoff, `docs/UI_UX_REDESIGN_PLAN.md`, and `docs/UI_UX_PHASE1_ACCEPTANCE_2026-09-12.md`;
6. confirm P2B code checkpoint `0d40dd1418456f42008a35222ae30c0a2498d41d` is an ancestor of current branch HEAD;
7. do not rerun old Phase 3 field acceptance;
8. continue P2C from the upstream Radar data-path audit described above.

## Continuation prompt

Use the prompt below in a new chat:

> Kontynuuj Tracker na repozytorium `MichalMatu/tracker` zgodnie z `docs/UI_UX_PHASE2_HANDOFF_2026-09-12.md` i `docs/UI_UX_REDESIGN_PLAN.md`. Pracuj wyłącznie na `ui/radar-details-redesign`; `main` jest zaakceptowanym baseline i ma pozostać bezpieczny. Najpierw zweryfikuj hard binding Local Agent, aktualny HEAD brancha, PR #7 oraz że P2B checkpoint `0d40dd1418456f42008a35222ae30c0a2498d41d` jest przodkiem bieżącego HEAD. P0 i P1 są zamknięte; P2A i P2B są zaakceptowane i zielone, nie powtarzaj ich testów bez nowego powodu. Kontynuuj od P2C: zrób preimplementation audit upstream Radar data path (`DeviceSearchDao` / Room projection, `DeviceRepositoryImpl`, `DeviceMapper`, `DeviceEvidenceFactory`, `GetScannedDevicesUseCase`, `RadarUiMapper`, `RadarUiSectionMapper`) i zaprojektuj najmniejszą behavior-preserving lekką projekcję Radaru, która ograniczy `SELECT *`, pełne `Device` i koszt Evidence, zachowując identyczne filtrowanie/sekcje/badges. Najpierw audit i testy kontraktu, potem mały commit, CI i S22+ measurement. Nie przechodź jeszcze do Details, dopóki Phase 2 nie ma closure decision.
