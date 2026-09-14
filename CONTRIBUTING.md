# Contributing to BlueEye Tracker

BlueEye is developed evidence-first: keep changes small, testable and consistent with the product's claim boundaries.

## Read first

1. [docs/README.md](docs/README.md)
2. [docs/PRODUCT_GOAL.md](docs/PRODUCT_GOAL.md)
3. the active plan relevant to the change
4. [docs/ARCHITECTURE_CURRENT.md](docs/ARCHITECTURE_CURRENT.md)
5. [docs/QUALITY_GATE.md](docs/QUALITY_GATE.md)

AI/agent-specific rules live in [AGENTS.md](AGENTS.md).

## Rules

- Prefer one clear concern per change.
- Do not incidentally broaden scanner/parser/scoring behavior during unrelated UI work.
- Keep passive scanning the default and active probing explicit.
- Preserve local operation when cloud/AI integrations are disabled.
- Do not make stronger identity, intent, distance or location claims than the evidence supports.
- Do not commit private field telemetry (exact GPS, MACs, databases, HCI/bugreports or raw private captures).
- Update an existing canonical document instead of creating a dated handoff/status file.

## Development

Use JDK 21; project bytecode target remains JVM 17.

```bash
./gradlew qualityCheck
./gradlew :app:assembleDebug
git diff --check
```

Run `gitleaks` when available. Hardware-dependent changes also need explicit device evidence.

## Git

`main` is the application source of truth. `agent-control` is Local Agent infrastructure only. Follow the current task's branch instruction; the repository's normal AI-assisted workflow is direct work on `main` unless the user explicitly asks for a branch/PR.

## Bug reports

Include device/Android version, app commit SHA, steps, expected vs observed behavior and safe logs/screenshots when relevant. Never post raw private captures publicly.
