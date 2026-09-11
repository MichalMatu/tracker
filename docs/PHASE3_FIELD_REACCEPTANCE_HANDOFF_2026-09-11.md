# Phase 3 final field reacceptance handoff — 2026-09-11

Status: **PHASE 3 CLOSED; PHASE 4 UNBLOCKED**

This is the authoritative continuation record. The detailed final acceptance result is in `PHASE3_FIELD_REACCEPTANCE_CLOSURE_2026-09-11.md`; the pre-fix root-cause analysis is in `PHASE3_CAPTURED_DATA_ANALYSIS_2026-09-11.md`.

## Current source of truth

- Repository: `MichalMatu/tracker`
- Repository id: `tracker`
- Source branch: `main`
- Local Agent control branch: `agent-control`
- Immutable Local Agent binding: `be481b25-9d97-4205-b93f-95f5c5827441`
- Phase 3 blocker-fix PR: `#6`
- Tested PR head: `2e5223f22128059935c36bb24946cfef95eba958`
- Squash merge on `main`: `66b18fe525466618bba30a8860bb5d75f46ea1ec`
- Package: `io.blueeye`
- Target device: Samsung `SM-S906B` (Galaxy S22+)
- Final locally built/installed debug APK SHA-256: `e070afa0bda71685da8bce9fc66cf9526a67e7bf713963ca6338cd72fa6884c9`

The earlier accepted field-build SHA `40eac7a...` is historical evidence only. It produced the real dataset that exposed the Phase 3 product-level blockers and must not be described as the final accepted source.

## Final Phase 3 result

The original field capture established that queueing, coalescing, processing and Room persistence were healthy, but it exposed three end-to-end defects:

1. screen-off BLE delivery on the broad unfiltered scan path;
2. Follow-Me wall-clock gap / historical movement-latch scoring;
3. concurrent Apple-like identity over-merge.

PR #6 fixed those areas without retuning the healthy ingest queue/Room path.

Final software gates on `2e5223f2...`:

- Quality #148: PASS
- Secret Scan #178: PASS
- detekt/lint/unit tests: PASS
- debug APK build: PASS

Targeted S22+ reacceptance then confirmed:

- active-screen persistence growth: `17,033 → 17,054`;
- phone remained `Dozing` across the controlled 35 s screen-off interval;
- same app PID and foreground `ScannerService` remained alive;
- screen-off persisted samples increased `17,055 → 17,058`;
- logcat confirmed `BROAD → BACKGROUND_FILTERED` after sleep;
- logcat confirmed `BACKGROUND_FILTERED → BROAD` after wake;
- final app-PID log check found no fatal/AndroidRuntime/OOM/SQLite/Security/BLE-scan failure.

Result: **Phase 3 ACCEPTED / CLOSED**.

## Original real field evidence retained

Private raw evidence remains outside the public repository. Do not commit exact GPS, MACs, raw Session Export, Room/WAL/SHM, HCI snoop or full bugreport.

Historical field ingest facts remain useful:

- `55,691 raw = 51,466 accepted + 4,225 coalesced + 0 rejected`;
- queue drops `0`;
- processing `51,466 / 51,466 / 0` started/succeeded/failed;
- signal outcomes `17,390 written / 32,505 throttled / 0 failed`;
- Room integrity `ok`;
- field dataset `873` devices / `15,201` persisted signal samples.

The pre-fix `33 SUSPICIOUS + 1 DANGEROUS` classifications are not semantic acceptance evidence. Captured-data analysis showed that most were contaminated by the now-fixed duration and identity-correlation defects.

## HCI note

Real LE HCI snoop data were captured from the original field run. Exact btsnoop/app timestamp-domain alignment remains unresolved, so HCI is retained as a non-blocking population/payload cross-check rather than a per-packet loss oracle.

## Phase boundary

- Phase 3: **CLOSED**
- Phase 4: **UNBLOCKED**
- Required additional Phase 3 walk before Phase 4: **none**

Longer future field runs remain valuable normal product validation, but they are no longer a phase-boundary blocker.

## Continuation prompt

`Kontynuuj Tracker od Phase 4. Phase 3 jest zamknięta zgodnie z docs/PHASE3_FIELD_REACCEPTANCE_CLOSURE_2026-09-11.md. Zweryfikuj current main i binding Local Agent, nie wracaj do starych pre-fix verdictów jako bieżącego stanu.`
