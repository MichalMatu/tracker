# Contributing to BlueEye Tracker

Thanks for your interest in contributing.

BlueEye Tracker is operated **stability- and evidence-first**. Small changes are preferred, but the current workstream is no longer governed by the old Phase 1-3 recovery `NEXT ACTION` documents.

## First read

Please read these documents before changing code:

1. [README.md](README.md)
2. [docs/README.md](docs/README.md)
3. [docs/PRODUCT_GOAL.md](docs/PRODUCT_GOAL.md)
4. the active execution plan named by the documentation index — currently [docs/UI_UX_REDESIGN_PLAN.md](docs/UI_UX_REDESIGN_PLAN.md)
5. [docs/QUALITY_GATE.md](docs/QUALITY_GATE.md)
6. [docs/ARCHITECTURE_CURRENT.md](docs/ARCHITECTURE_CURRENT.md)

Historical stabilization guides/handoffs remain useful provenance, but they must not override the current documentation index and active plan.

## Contribution rules

- Prefer **small, reviewable changes**.
- Keep one clearly named concern per change/PR.
- Do **not** re-enable intentionally disabled runtime paths without explicit justification and validation.
- Preserve the rule that the app shows **evidence**, not overstated identity, intent, ranging or location claims.
- Keep local collection/parsing/detection functional without cloud or AI dependencies.
- Keep documentation in sync when changing project behavior or the canonical work plan.
- Leave the repository green: buildable, testable and lint-clean.
- Do not commit private field telemetry such as exact GPS, MACs, Room/WAL/SHM, HCI snoop or bugreports.

## Development setup

- Use **JDK 21** as the build runtime/toolchain.
- Current Android/Kotlin bytecode target remains **JVM 17**.
- Main CI commands:

```bash
./gradlew qualityCheck
./gradlew :app:assembleDebug
```

Optional local secret scan:

```bash
gitleaks git --config .gitleaks.toml --redact --verbose
```

## Branch and PR policy

- `main` is the accepted source baseline.
- `agent-control` is reserved for Local Agent infrastructure and never for application source.
- Follow the maintainer/current-task branch instruction; use a dedicated work branch when explicitly requested or when the active workstream defines one.
- Keep temporary branches focused and remove merged branches when they no longer serve a purpose.
- Reference the relevant active-plan phase/checklist item in substantial PRs when applicable.

Suggested PR title style:

- `ui: simplify Radar device cards`
- `perf: use lightweight Radar projection`
- `docs: refresh project status and roadmap`
- `tests: add regression coverage for signal history`

## What a good change includes

A good contribution usually includes:

- the smallest safe implementation,
- focused tests,
- no unrelated refactors,
- a short rationale in the PR description,
- documentation updates when behavior or current-plan status changes,
- explicit physical-device evidence when the behavior can only be validated on Android hardware.

## Issues

When reporting bugs, include:

- device model,
- Android version,
- app build/commit SHA,
- exact steps to reproduce,
- expected behavior,
- observed behavior,
- logs/screenshots if available and safe to share.

Do not post raw captures containing private device/location data publicly.

## Security

Please do not publish secrets, tokens, signing material, sensitive personal data or private field captures in issues or pull requests.
