# Phase 3 final field reacceptance handoff — 2026-09-11

Status: **CODE/CI/PRE-SMOKE GREEN; FINAL VARIED-DENSITY FIELD WALK + CAPTURE ANALYSIS PENDING**

This is the authoritative continuation record for the next chat. It supersedes the current-status sections of older Phase 3 handoffs. Historical documents remain evidence only.

## Source of truth

- Repository: `MichalMatu/tracker`
- Repository id: `tracker`
- Source branch: `main`
- Local Agent control branch: `agent-control`
- Immutable Local Agent binding: `be481b25-9d97-4205-b93f-95f5c5827441`
- Accepted install/source SHA: `40eac7a504d363a05cd6c235c25146e01bb36ff2`
- Production application source is unchanged from milestone `630e184dbc328672a961e2826208fddd77a68f02`; descendants through `40eac7a...` add documentation and the bounded export-schema test correction only.
- Package: `io.blueeye`
- Installed version: `versionCode=1`, `versionName=1.0`
- Installed/tested device: Samsung `SM-S906B` (Galaxy S22+)
- APK SHA-256: `17ebd807c526eda077e9e7f9e96e6306924890e4c201a3c5d3e50f9d76237a65`
- APK signer DN: `C=US, O=Android, CN=Android Debug`
- APK signer certificate SHA-256: `fb07493cf97b0a84ec410f7f72f12938b7f9d47151546e15c049c49c27807b11`

At the start of a new chat, fetch current `main` and compare it with `40eac7a...`. Documentation-only descendants do not require reinstalling the phone. Any application-source change invalidates the exact installed-build claim and requires a new build/install identity check.

## Exact-SHA software gates

For `40eac7a504d363a05cd6c235c25146e01bb36ff2`:

- Secret Scan #139 / `34576372527`: **success**
- Quality #109 / `34576372528`: **success**
- Android UI Smoke #39 / `34576372552`: **success**
- Sandbox Pack #64 / `34576372547`: **success**
- Tester Release #67 / `34576611435`: **success**

Do not rerun broad suites merely for reassurance unless source changes.

## Physical pre-field smoke — accepted

The exact installed build passed targeted ADB checks on the S22+:

- fresh install and launch: PASS;
- scanner Stop removes `ScannerService` and remains stopped without auto-restart: PASS;
- explicit Start recreates one foreground scanner and reaches `RUNNING`: PASS;
- Stable Core suppresses continuous Classic discovery: PASS;
- no app `FATAL EXCEPTION`, `SecurityException`, `IllegalStateException`, SQLite exception or OOM in the checked windows: PASS;
- screen-off/Dozing for 8 s preserved the app process and foreground `ScannerService`: PASS;
- wake returned with the scanner still alive: PASS.

The earlier `scan-toggle-v1` failure was a test-harness assumption: it expected visible `Paused` text after Stop even though persisted device cards remain on screen. Service/lifecycle evidence proved Stop itself correct.

## What the application records

For persisted signal samples, `SignalSamplePersister` records the useful BLE observation context, including:

- logical fingerprint and observed MAC;
- technology, advertised name/type/vendor;
- RSSI and observation timestamp;
- latitude, longitude and location accuracy when a fix is available;
- manufacturer id/data and service UUID/data;
- appearance, Tx power, connectability, PHY and advertising interval;
- beacon/decoded sensor context and raw advertising payload where available;
- tracking/follow-me state and public-safety classification context.

Samples are intentionally throttled. Tracker does **not** persist every individual advertisement as a Room row; raw ingest/coalescing diagnostics explain the bounded pipeline behavior.

GPS is functional input, not only export metadata. `LocationProvider.getFreshCoordinates()` is used both when writing signal samples and in Follow-Me movement tracking. Current policy reuses a fresh location for 10 s, backs off active-fix retries for 10 s, times out an active fix after 2 s and can use a sufficiently fresh last-known fix. Nullable GPS in a sample means no usable fix was available at that write; it is not silently fabricated.

The Session Share export is the primary durable field artifact. It contains devices, signal samples, GPS-bearing samples, raw/decoded Bluetooth context, Follow-Me observations, alert-evidence history, identity-continuity candidates, session review summaries and the process-lifetime scanner/ingest diagnostics appended by Settings.

## Bluetooth HCI snoop — enabled and verified

The Samsung developer option for Bluetooth HCI snoop is enabled. ADB `dumpsys bluetooth_manager` showed active `snoop_logger_tracing`.

Important boundaries:

- HCI snoop captures host/controller HCI traffic; it is a lower-level Bluetooth evidence source than Tracker/logcat, but it is **not** a raw over-the-air RF sniffer.
- It does not replace GPS.
- Its value is independent cross-checking of Bluetooth events against Tracker's callbacks, ingest accounting, persistence and UI interpretation.

Normal unprivileged ADB cannot directly read Samsung's protected snoop path:

- `/data/misc/bluetooth/logs/btsnoop_hci.log` -> permission denied;
- `/data/misc/bluedroid/btsnoop_hci.log` -> permission denied;
- `/data/log/bt/btsnoop_hci.log` -> permission denied;
- `/sdcard/btsnoop_hci.log` -> absent.

Do not root the phone or bypass Android protections for this test.

### HCI capture procedure after the walk

Preserve evidence in this order:

1. **Do not reboot, uninstall, clear app data, disable Bluetooth or disable HCI snoop.**
2. Keep the same app process alive if possible; ingest totals are process-lifetime diagnostics.
3. Open Settings -> Session export -> **Share** and preserve the complete `blueeye-session-export.json`.
4. Collect ADB evidence: package/build identity, process/service state, relevant logcat and final diagnostics/UI snapshot.
5. Collect Room `tracker_database` plus matching `-wal`/`-shm` through the supported debug/ADB route if available. Do not mutate the database.
6. Generate an Android/Samsung **bug report** while HCI snoop is still enabled. This is the supported route for protected Bluetooth snoop data on this device.
7. Extract `btsnoop_hci.log` or Samsung's equivalent Bluetooth snoop artifact from the bugreport and record its hash/time range. The full bugreport is highly sensitive; keep it private and delete it after the needed HCI artifact is safely extracted.
8. Analyze HCI with Wireshark/tshark or an equivalent parser, then correlate timestamps with Tracker signal samples/export.
9. Only after all artifacts are secured may HCI snoop be disabled or the app/session be reset.

Never commit raw Session Export, Room/WAL/SHM, HCI snoop or full bugreport to the public repository. They can contain MAC addresses, GPS and other sensitive device/system data.

## Final field walk

Use the already-installed `40eac7a...` APK. Do not reinstall or clear data immediately before/during the run unless the test explicitly requires a new session.

Collect varied BLE density rather than repeating already accepted lifecycle torture:

- start in a relatively stable/low-density area;
- walk through normal street density;
- spend time in a denser public/Bluetooth environment;
- return to a quieter area;
- include ordinary screen-off/background time;
- leave Bluetooth, Tracker scanner and HCI snoop enabled.

Do not deliberately toggle Bluetooth, kill the app or exercise unrelated controls during this final walk unless a real failure forces it. If an intrusive alert occurs, first use the application's Stop/Acknowledge/global control and record whether it works.

## Required post-walk analysis

Use the Session Export as the primary product record, Room as storage ground truth, ADB/logcat as runtime evidence and HCI as an independent Bluetooth cross-check.

At minimum verify:

- `raw = accepted + coalesced + rejected`;
- `queueDroppedTotal == 0`;
- accepted processing reconciles with succeeded/failed/in-flight state;
- signal samples reconcile as written + throttled + failed;
- no unexplained sample-write/processing failure;
- queue high-water, maximum queue wait and processing latency are sane;
- GPS coverage and accuracy distribution across the walk;
- RSSI/device continuity across changes in physical location;
- long-gap identity candidate evidence for rotating-address consumer devices without extending destructive carryover past 30 s;
- whether suspicious/known-tracker UI conclusions agree with stored evidence/raw payload context;
- where possible, HCI Bluetooth observations versus Android/Tracker observations, with differences explained rather than assumed to be app loss.

Historical healthy ingest reference from the prior full walk:
`20,819 raw = 18,005 accepted + 2,814 coalesced + 0 rejected`, `0` queue drops, `18,005/18,005/0` processing started/succeeded/failed and `0` signal-sample write failures.

Do not retune queue/coalescing solely because classification/identity evidence looks surprising.

## Phase boundary

Phase 4 remains blocked until this final field collection is preserved and analyzed. If the walk reconciles and no new P0/P1 regression appears, Phase 3 can be closed. Structural cleanup listed in `PHASE3_FINAL_CODE_QUALITY_AUDIT_2026-09-11.md` is post-acceptance debt unless new field evidence proves one of those hotspots is causal.

## First prompt for the next chat

A concise continuation prompt is:

`Kontynuuj Tracker zgodnie z docs/PHASE3_FIELD_REACCEPTANCE_HANDOFF_2026-09-11.md. Najpierw zweryfikuj current main i binding Local Agent. Nie zmieniaj kodu. Zbierz po spacerze Session Export + ADB/logcat + Room/WAL/SHM, potem HCI snoop z bugreportu i wykonaj pełną korelację Phase 3.`
