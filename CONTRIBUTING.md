# Contributing to BlueEye Tracker

Thanks for your interest in contributing.

This repository is currently in a **stability recovery phase**, so contribution quality matters more than contribution size.

## First read

Please read these documents before changing code:

1. [README.md](README.md)
2. [docs/STABILITY_RECOVERY_GUIDE.md](docs/STABILITY_RECOVERY_GUIDE.md)
3. [docs/STABLE_CORE_PREIMPLEMENTATION_AUDIT.md](docs/STABLE_CORE_PREIMPLEMENTATION_AUDIT.md)
4. [docs/QUALITY_GATE.md](docs/QUALITY_GATE.md)
5. [docs/ARCHITECTURE_CURRENT.md](docs/ARCHITECTURE_CURRENT.md)

## Contribution rules

- Prefer **small, reviewable changes**.
- Do **not** mix multiple recovery phases in one change.
- Do **not** re-enable intentionally disabled runtime paths without explicit justification.
- Preserve the rule that the app shows **evidence**, not overstated identity claims.
- Keep documentation in sync when changing project behavior.
- Leave the repository green: buildable, testable, and lint-clean.

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

Current project policy is intentionally simple:

- `main` is the active code line.
- `agent-control` is reserved for Local Agent infrastructure.
- Keep PRs focused on a single concern.
- Use clear titles and reference the relevant phase from the recovery guide.

Suggested PR title style:

- `phase-2: make scanner lifecycle idempotent`
- `docs: improve README and contribution guide`
- `tests: add regression coverage for stable core`

## What a good change includes

A good contribution usually includes:

- the smallest safe implementation,
- focused tests,
- no unrelated refactors,
- a short rationale in the PR description,
- documentation updates when behavior changes.

## Issues

When reporting bugs, include:

- device model,
- Android version,
- app build/commit SHA,
- exact steps to reproduce,
- expected behavior,
- observed behavior,
- logs/screenshots if available.

## Security

Please do not publish secrets, tokens, or sensitive personal data in issues or pull requests.
