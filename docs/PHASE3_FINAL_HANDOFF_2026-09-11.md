> **SUPERSEDED FOR CURRENT STATUS:** continue from `PHASE3_FIELD_REACCEPTANCE_HANDOFF_2026-09-11.md`. This file remains historical provenance for the stabilization package.

# Phase 3 final continuation handoff — 2026-09-11

## Binding and source of truth

- Repository: `MichalMatu/tracker` (`tracker`)
- Source branch: `main`
- Local Agent control branch: `agent-control`
- Immutable Local Agent binding: `be481b25-9d97-4205-b93f-95f5c5827441`
- Chat bridge that produced this handoff: `chat-2d0127ac`
- Application milestone SHA to verify first: `630e184dbc328672a961e2826208fddd77a68f02`
- Commit: `Stabilize retention and location latency`
- Phase 4 remains BLOCKED until final exact-SHA CI and targeted physical reacceptance are complete.

Before every Local Agent queue/check fetch `agent-control:.agent/binding.json` and `agent-control:.agent/status/daemon.json`; verify the exact tuple above. Every task JSON must contain exactly the binding above and `"resources": []`. Never inspect or execute another repository.

## What is closed in code

The Phase 3 field blockers have been addressed in source through the milestone SHA:

1. Alert ownership/cancellation: per-alert acknowledge/stop, owned ringtone/vibration cancellation, global OFF cleanup, no NotificationManager `cancelAll` dependency.
2. Bare `X5` false tactical/public-safety classification: fixed with INVISIO context requirement; exact-SHA regression previously accepted.
3. Apple NearbyInfo model confusion: protocol/action nibble no longer overwrites physical model identity; accepted exact-SHA regression.
4. Same-name destructive carryover: same name + RSSI alone no longer merges; independent BLE corroboration required within unchanged 30 s destructive window.
5. Device merge safety: user-owned DeviceEntity state and watchlist configuration are preserved/validated transactionally; conflicting meaningful state aborts destructive merge.
6. Long-gap identity continuity: reversible identity candidate relations/evidence are persisted and exported; long-gap evidence does not extend the 30 s destructive window and does not auto-merge fingerprints.
7. Retention: ordinary stale device rows are deleted only when they are truly neutral/transient; watchlist/user state and both sides of identity-candidate relations are protected. Signal samples are bounded to 7 days; follow-me/alert/identity evidence to 30 days.
8. Latency tail: active location-fix retries have a 10 s backoff, preventing immediate repeated 2 s active-fix waits when the previous fresh fix failed.
9. Startup spinner and the earlier scanner lifecycle regressions were already fixed/accepted before this milestone.

## Exact local verification for the final retention/latency package

Terminal Local Agent result:

- `tracker-phase3-final-stabilization-static-gate-v2`
- base: `fceced07617c13d00ccd5fae59b913de208fca63`
- applied the exact retention + latency + ktlint cleanup payload later published as `630e184...`
- `git diff --check`: PASS
- `:core:data:detekt`: PASS
- `:feature:settings:ktlintMainSourceSetCheck`: PASS
- marker: `PHASE3_FINAL_STABILIZATION_STATIC_GATE_V2_PASS`

Publish result:

- task: `tracker-phase3-retention-latency-publish-v1`
- terminal status: DONE/PASS
- pushed exact SHA: `630e184dbc328672a961e2826208fddd77a68f02`
- 6 files changed, 47 insertions, 6 deletions
- post-push workspace clean and `origin/main` matched exact SHA.

## GitHub Actions state at handoff

For exact SHA `630e184dbc328672a961e2826208fddd77a68f02`:

- Secret Scan #137, run `34575320852`: **completed / success**
- Sandbox Pack #62, run `34575320904`: **completed / success**
- Quality #107, run `34575320891`: **in_progress at handoff**
- Android UI Smoke #38, run `34575320942`: **in_progress at handoff**

The immediately previous identity SHA `fceced07617c13d00ccd5fae59b913de208fca63` had a Quality failure caused only by one ktlint formatting rule in `DatabaseExporter.kt`; that formatting issue is included and locally verified in `630e184...`. Do not treat the old Quality failure as an unresolved functional regression.

## First action in the next chat

1. Fetch current `main`; verify whether it is still the docs-only descendant of application SHA `630e184...` or whether source moved.
2. Check exact-SHA workflow runs for `630e184dbc328672a961e2826208fddd77a68f02`.
3. If Quality and Android UI Smoke both complete success, treat code stabilization as ready for targeted physical reacceptance. Do **not** rerun broad local suites merely for reassurance.
4. If Quality fails, inspect only the failing job/log and fix the exact issue; do not restart a broad refactor.
5. If UI Smoke fails, distinguish emulator infrastructure from an application failure before changing source.

## Remaining Phase 3 work if CI is green

Only targeted reacceptance remains before another full clean field walk:

- install/confirm APK built from the exact accepted source SHA on Samsung S22+;
- verify active alert can be stopped without killing the app and global detection OFF cancels active effects;
- preserve the prior accepted scanner Start/Stop, screen-off and Bluetooth ON/OFF lifecycle behavior;
- perform a clean varied-density field walk;
- inspect new identity-candidate evidence for the OPPO/JBL-style address-rotation cases without exposing raw MAC/GPS in user-facing discussion;
- confirm retention/session behavior is sane and latency tail is materially improved/no longer dominated by repeated location waits;
- reconcile ingest accounting again (raw = accepted + coalesced + rejected, zero unexplained drops/failures).

Phase 4 stays blocked until that targeted physical reacceptance and final walk are accepted.

## Important evidence and boundaries

- Original field run ingest was healthy: 20,819 raw = 18,005 accepted + 2,814 coalesced + 0 rejected; 0 queue drops; 18,005/18,005 processing success; 0 sample-write failures.
- Do not retune queue capacity/coalescing without new evidence; the bounded ingest accounting reconciled.
- Long-gap identity remains candidate/evidence-only. Never extend the destructive same-name carryover beyond 30 s as a shortcut.
- Preserve user-owned state during merges/cleanup; no silent alias/note/calibration/verdict conflict resolution.
- Private field export contains MAC/GPS. Keep user-facing evidence anonymized.
- Do not claim tactile vibration acceptance from dumpsys alone; physical sensory confirmation was never explicitly obtained.

## Local Agent evidence names useful for continuation

- `tracker-phase3-final-stabilization-static-gate-v2` — final local static PASS for the published retention/latency diff.
- `tracker-phase3-retention-latency-publish-v1` — exact publish PASS to `630e184...`.
- Earlier accepted identity/merge tasks and patches remain on `agent-control`; use them as provenance only, not as instructions to redo completed work.

This file supersedes the older current-status sections of `docs/PHASE3_HANDOFF.md`; that document remains valuable for the original field evidence and detailed historical audit rationale.
