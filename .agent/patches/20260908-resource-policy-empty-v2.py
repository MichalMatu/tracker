from pathlib import Path


def replace_once(path: str, old: str, new: str) -> None:
    p = Path(path)
    text = p.read_text()
    count = text.count(old)
    assert count == 1, (path, count, old)
    p.write_text(text.replace(old, new))


flow = "LOCAL_AGENT_FLOW.md"
replace_once(
    flow,
    '- use `resources: []` for normal Android/Kotlin source work, Gradle builds, tests, lint, Detekt, ktlint and repository-local Git operations;\n- use `resources: ["device:android-phone"]` for ADB/install/logcat or tests requiring the connected physical phone;\n- use `resources: ["machine"]` only for genuine whole-host operations such as host-global toolchain mutation;\n- `memory_limit_mb` is independent from resource classification.',
    '- every executable task in this repository uses `resources: []`, including Android/Kotlin work, Gradle builds/tests/lint and ADB/install/logcat or physical-phone validation;\n- detect and verify the intended Android device inside the task instead of reserving it as a scheduler resource;\n- do not declare named resources or `machine` from this repository; host-global maintenance belongs to the supervisor/administration path;\n- `memory_limit_mb` is independent from resource classification.',
)
replace_once(
    flow,
    "6. Classify resources conservatively.",
    "6. Set `resources: []`; this repository does not use named or `machine` resource reservations.",
)

autopilot = "LOCAL_AGENT_AUTOPILOT.md"
replace_once(autopilot, "3. classify resources;", "3. set `resources: []`;")
replace_once(
    autopilot,
    '- normal Kotlin/Compose/Gradle/build/test/lint work: `resources: []`;\n- work requiring the connected physical Android phone through ADB/install/logcat: `resources: ["device:android-phone"]`;\n- genuine host-global mutation only: `resources: ["machine"]`.',
    '- every Local Agent task in this repository uses `resources: []`, including ADB/install/logcat and physical-phone work;\n- detect and verify the intended device inside the task; do not declare named resources or `machine` from this repository.',
)

for path in (flow, autopilot):
    text = Path(path).read_text()
    assert "device:android-phone" not in text
    assert 'resources: ["machine"]' not in text
