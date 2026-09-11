# Captured field-data analysis handoff — 2026-09-11

Status: **PHASE 3 CLOSED; DEEP FIELD-DATA ANALYSIS NEXT**

This is the continuation record after `PHASE3_FIELD_REACCEPTANCE_CLOSURE_2026-09-11.md`. It supersedes the pending-walk action in `PHASE3_FIELD_REACCEPTANCE_HANDOFF_2026-09-11.md`.

Do **not** repeat the Phase 3 field walk merely for reassurance. Do **not** change application code before the captured evidence is understood. Phase 4 is unblocked, but the first useful next step is to analyze the real dataset and turn findings into bounded product/Phase 4 work.

## Private evidence source

Use the private checkpoint:

`~/agent-workspace/repos/tracker/checkpoints/phase3-field-reacceptance-20260911/20260911T142824Z`

Primary artifacts:

- `blueeye-session-export.json`
- `room/tracker_database`, plus matching WAL/SHM
- extracted HCI/btsnoop artifacts and private HCI correlation JSON
- safe aggregate analysis JSON files
- collected app/system log snapshots

Never commit raw GPS, MAC addresses, advertising payloads, HCI data or the Room database to the public repository.

The full bugreport was deleted after HCI extraction and is not required for the next analysis.

## Important interpretation constraints

1. The export's formal Session window is empty (`session.startedAt=0`, `session.sampleCount=0`). This does **not** mean the field data are absent. Use the top-level `signalSamples`, Room timestamps and the known outing timeline.
2. Process-lifetime ingest counters include activity that predates the cleared Room dataset. Use them for pipeline accounting, not to infer that every `signalSamplesWrittenTotal` row must still exist in Room.
3. The field run contained an intentional long stationary interval while the phone was in a pool locker. Do not treat stationary GPS/device persistence during that interval as a defect. Segment the outing before drawing Follow-Me conclusions.
4. The HCI timestamps are not directly aligned with Tracker timestamps. A coarse alignment showed a large shared address population, but exact same-MAC/per-second correlation has not been established. Treat HCI as an independent address/payload evidence source until its clock domain is understood.
5. `persistedDeviceUpdatesTotal` and `deviceUpdateThrottledTotal` can overlap; do not use their sum as a processing-outcome equation.
6. Phase 3 acceptance proves the ingest/storage/runtime path was healthy. It does **not** prove the semantic correctness of `DANGEROUS`, `SUSPICIOUS`, tracker-type or identity-continuity classifications.

## Stable acceptance facts

- `55,691 raw = 51,466 accepted + 4,225 coalesced + 0 rejected`
- queue dropped: `0`
- processing: `51,466 started / 51,466 succeeded / 0 failed`
- provisional discarded: `1,571`
- signal sample outcomes: `17,390 written / 32,505 throttled / 0 failed`
- final Room integrity: `ok`
- final Room: `873` devices, `15,201` signal samples, `3,844` Follow-Me observations, `40` alert-evidence events, `5` identity-continuity candidates
- final root tracking state: `839 SAFE / 33 SUSPICIOUS / 1 DANGEROUS`
- attention devices without evidence: `0`
- app-context fatal/OOM/SecurityException/SQLite/scan-processing failures: `0`
- HCI: real LE trace captured; aggregate parser found `25,066` LE advertising reports and `432` HCI MACs, with `319` unique addresses also present in Tracker after exploratory coarse alignment

## Analysis objectives

### 1. Reconstruct the outing

Build a timestamped timeline from top-level samples. Identify movement/stationary phases from GPS and sample dynamics. The real-world sequence was approximately:

- outbound walk to the pool;
- about one hour with the phone stationary in a locker while scanning continued;
- return movement including a shop before arriving home.

Do not force those approximate durations onto the data; use them as context and let timestamps/GPS identify actual boundaries.

For each phase calculate at least:

- duration;
- number of persisted samples;
- unique logical fingerprints and observed MACs;
- BLE sample/device density over time;
- GPS availability and accuracy distribution;
- RSSI distribution;
- new devices entering and devices persisting/reappearing.

### 2. Review every attention-state device

Analyze all `33 SUSPICIOUS` and the `1 DANGEROUS` device individually using pseudonymous IDs in any public/safe report.

For each inspect:

- device type/name/vendor classification basis;
- following score and component scores;
- evidence sources/confidence;
- first/last seen and observation duration;
- whether `userMoved` was actually true during contributing observations;
- encounter structure and gaps;
- RSSI trend/stability, treating RSSI as noisy proximity evidence rather than distance;
- distinct observed MACs and rotation evidence;
- raw manufacturer/service payload stability;
- GPS phase/location changes;
- whether the device was mainly present during the stationary locker period versus genuinely accompanying movement.

Classify the *analysis result* separately from the app's stored status, for example: plausible follow, stationary-environment false positive, public-environment transient, insufficient evidence, or needs manual review.

### 3. Known tracker-type review

Review all devices classified as tracker-like (`TRACKER` / AirTag / SmartTag / Tile or equivalent). Determine whether classification comes from a robust protocol/payload signature, name-only evidence, or another heuristic.

Pay special attention to devices whose name/type suggests a tracker but whose movement evidence does not support following. Separate "is probably a tracker product" from "is probably following this user".

### 4. Identity continuity / rotating addresses

Inspect all five persisted identity-continuity candidates and broader logical fingerprints with multiple observed MACs.

For each candidate evaluate:

- time gap;
- name stability;
- payload stability;
- manufacturer/service data;
- RSSI continuity;
- GPS/phase context;
- whether multiple same-model devices in a dense public environment could explain the match.

Do not approve destructive identity merging from same-name proximity alone. Long-gap evidence should remain reversible/explicit unless stronger features justify a future policy change.

### 5. GPS and movement-quality analysis

Quantify GPS accuracy by phase and identify how coarse fixes affect `userMoved`, Follow-Me duration and apparent continuity. Distinguish actual motion from GPS jitter and from stale/reused fixes.

Specifically investigate whether the long locker interval causes stale movement state or Follow-Me score carryover into otherwise stationary devices.

### 6. HCI correlation

First establish the actual btsnoop timestamp semantics before comparing samples within seconds. Explore candidate clock/epoch/timezone/boot-time interpretations rather than relying on the exploratory `-8,316,000 ms` offset.

Then, where defensible, compare:

- HCI addresses versus Tracker observed MACs;
- HCI advertising-report counts versus Tracker raw callback/coalescing behavior over matched intervals;
- payload/manufacturer/service signatures for selected attention-state devices;
- whether missing Tracker observations can be explained by Android scan delivery, coalescing/filtering or sample throttling rather than assuming application loss.

If exact temporal alignment cannot be proven, limit conclusions to address/payload population overlap.

### 7. Produce actionable findings

End with a prioritized set of findings in three buckets:

- **confirmed defect** — evidence demonstrates incorrect behavior and identifies the likely layer;
- **heuristic/product tuning opportunity** — pipeline is healthy but scoring/classification can be improved;
- **expected/no action** — observed behavior follows intended throttling, Android/HCI semantics or environmental conditions.

Do not modify code until this evidence review identifies a bounded change with clear expected benefit and regression risk.

## Public documentation boundary

It is safe to commit aggregate counts, pseudonymous identifiers, conclusions and bounded recommendations. Do not commit raw MAC addresses, exact GPS coordinates, raw advertising payloads, HCI captures, private checkpoint paths beyond the already-documented local location, or system bugreport contents.

## Continuation prompt

`Kontynuuj Tracker zgodnie z docs/PHASE3_CAPTURED_DATA_ANALYSIS_HANDOFF_2026-09-11.md. Phase 3 jest zamknięta. Nie zmieniaj kodu. Przeanalizuj prywatny checkpoint 20260911T142824Z: najpierw odtwórz pełną oś czasu i fazy ruch/stacjonarnie, potem przeanalizuj wszystkie SUSPICIOUS/DANGEROUS, trackery, identity continuity i na końcu HCI. Oddziel defekty od tuningu heurystyk i zachowania oczekiwanego.`