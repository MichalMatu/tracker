# Current Architecture

Status after tooling review: the project is modern Android in framework choices, but not yet clean in dependency direction.

Pipeline-specific issues are tracked in [PIPELINE_AUDIT.md](PIPELINE_AUDIT.md).

## Current Modules

| Module | Current role |
| --- | --- |
| `app` | Android entry point, Hilt wiring, Navigation Compose |
| `core:model` | Shared models |
| `core:domain` | Repository contracts and use cases |
| `core:data` | Room, Bluetooth scanning, foreground service, classification, session/tracking logic |
| `core:decoders` | BLE decoder library |
| `core:ui` | Theme, dimensions, shared UI utilities |
| `feature:radar` | Radar UI and ViewModel |
| `feature:details` | Device details UI and ViewModel |
| `feature:settings` | Settings UI and ViewModel |
| `feature:watchlist` | Watchlist UI and ViewModel |

## Positive Signals

- Kotlin-first Android project.
- Compose screens, no Fragment UI workflow.
- Hilt is already used.
- `core:domain` is physically separate now and has no Android imports in the checked source.
- `feature:radar` no longer depends on `core:data`; scanner control goes through `ScannerRuntimeController`.
- `feature:watchlist` no longer depends on `core:data`; public-safety-style signals go through `PublicSafetySignalMonitor`.
- `feature:details` and `feature:settings` now depend on domain-facing repository contracts instead of `core:data`.
- Evidence is a domain contract carried into Radar, Details, watchlist, export, and alert-history UI, including passive name/model/appearance/Class-of-Device/service/manufacturer context.
- Navigation Compose uses serializable route types.
- Basic Gradle quality gate exists.

## Main Problems

### P0: Active Probing Must Stay Explicit

Passive BLE/Classics observation is the default. Active GATT collection is behind the domain-facing `ActiveCollectionRepository`, has explicit Radar/Settings UI, and requires confirmation before enabling automatic collection. The remaining risk is periodic RFCOMM or broader active probing: do not re-enable it without a separate opt-in boundary and visible active-evidence labeling.

### P1: Scanner Service Boundary Is Leaky

`ScannerService` lives in `core:data` but uses package `io.blueeye.service`. `feature:radar` now uses the domain-facing `ScannerRuntimeController`, but app entry code still imports service controls directly.

### P1: `core:data` Is Too Broad

`core:data` currently owns scanning, repositories, database, classification, tracking sessions, alerts, and foreground service control. This is workable for a prototype, but it is too large for long-term change safety.

### P2: Tooling Debt Is Baseline-Gated

Detekt baselines exist for current debt. Ktlint excludes `core:data/src` and `core:decoders/src` for now. This is acceptable as a temporary quality gate, but not as the final standard.

## Recommended Next Refactor Order

1. Keep active probing explicit and add a separate RFCOMM opt-in boundary only if product evidence shows it is worth the cost.
2. Use real session review outcomes for Follow-Me and identity/carryover tuning before raising confidence thresholds.
3. Continue converting general classifier outputs into explicit evidence where the source can be preserved.
4. Split oversized `core:data` areas after the product flows stabilize.
5. Pay down detekt/ktlint baselines module by module.
