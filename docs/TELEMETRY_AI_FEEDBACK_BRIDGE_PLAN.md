# Telemetry & AI feedback bridge plan

## Current status

T0 is **implemented, build-verified, and physically accepted**. The developer bridge can authorize the dedicated Google account, create a JSON artifact in Drive, send a self-addressed `BLUEEYE/BATCH_READY` Gmail control message, and let ChatGPT retrieve the exact referenced Drive file. Revoke/disconnect was verified, followed by successful reauthorization.

The app persists only the selected Google account email. OAuth access tokens remain memory-only. After process restart the bridge silently re-runs `AuthorizationClient.authorize()` for the persisted account; if the grant is still valid the UI returns to `Granted` without another consent flow. If Google requires interaction, the app falls back to an explicit reconnect state instead of opening consent UI automatically.

The validated implementation head before documentation finalization was `eb48b7c3cea379db3d215464efae4dff61ff54c0`. Focused telemetry compilation, ktlint, detekt, module tests, and `:app:assembleDebug` passed with JDK 21.

**T1+ automatic telemetry is intentionally blocked.** The Google Drive/Gmail path remains useful as a developer feasibility/debug bridge, but it must not become the production transport until the policy fit and trigger model are replaced or explicitly accepted.

## What T0 proves

```text
Tracker Android app
  -> Google AuthorizationClient
  -> Drive JSON artifact
  -> Gmail BLUEEYE/BATCH_READY
  -> ChatGPT Gmail/Drive connectors
  -> exact referenced JSON read
```

T0 also proves:

- dedicated-account selection works;
- `drive.file` and `gmail.send` are sufficient for the spike;
- the app does not need a public HTTP server, tunnel, or companion device for this developer path;
- revoke removes the app grant and later authorization can restore it;
- process restart no longer loses the remembered account/grant state in the UI;
- Drive file identity can be carried in the Gmail trigger and used to retrieve the exact artifact.

T0 does **not** prove a near-real-time Gmail event wake-up for ChatGPT. The available ChatGPT automation path is polling/watch based, so Gmail cannot currently be treated as a guaranteed immediate event source for this workflow.

## Fixed T0 implementation decisions

- Drive scope: `https://www.googleapis.com/auth/drive.file`.
- Gmail scope: `https://www.googleapis.com/auth/gmail.send`.
- Identity scope is used only to show/pin the selected technical account and self-address the control message.
- No full Drive or Gmail read/modify/delete scope is requested by Tracker.
- Access tokens are never persisted.
- Only the selected account email is persisted locally for silent authorization restore.
- Drive artifacts are ordinary app-created files so the same dedicated account can expose them to ChatGPT.
- T0 sends one small control email; Gmail is not used as a telemetry database.
- T0 creates only explicit test artifacts. It does not run minute telemetry, a durable outbox, or automatic background upload.

## Policy/productization gate

The original concept used Drive as the telemetry data plane and Gmail as the control plane. That technical design works, but it has production-policy risk:

- Google Workspace policy makes generic application-content backup/storage in Drive a problematic production use case. `drive.file` narrows access but does not change the permitted-purpose rules.
- `gmail.send` is a sensitive scope and public rollout would require the corresponding OAuth verification/compliance work.
- Gmail used primarily as a machine trigger is not a clean product fit for the approved Gmail-use categories.
- External OAuth projects left in Testing are prototype-only and have short-lived authorization behavior for these scopes.

Therefore **do not implement the former T1-T5 automatic Drive/Gmail telemetry pipeline as production architecture** without a fresh explicit architecture decision.

## Preferred next architecture direction

Keep the local telemetry contracts transport-agnostic:

```text
Tracker
  -> local reducer
  -> durable outbox
  -> TelemetryTransport
       -> production endpoint/storage + event/webhook
       -> optional developer Drive/Gmail bridge
  -> analysis
  -> bounded feedback
```

`TelemetryTransport` should remain the boundary. A future production transport should provide explicit retention, idempotency, authentication, bounded retry, and an actual event mechanism rather than depending on mailbox polling.

## Future telemetry model

If/when T1 resumes, use reduced, versioned envelopes rather than database dumps:

```text
schemaVersion
installationId
sessionId
sequence / sequenceRange
batchKind: DELTA | CHECKPOINT | URGENT_EVENT
from / to timestamps
appVersion + sourceRevision
checksum
```

Candidate reduced payloads include ingest rates/counters, queue drops/rejections, category/device transitions, parser failures, alert/Follow-Me/identity transitions, high-volume protocol counts, RSSI summaries, and bounded representative evidence.

Never make minute telemetry by repeatedly serializing the full Room database. Never leak exact GPS, private MAC/address material, raw captures, or active-probe evidence without an explicit diagnostic opt-in.

## Delivery requirements for a future T1+

A production outbox must:

- survive process death/reboot;
- use stable idempotency keys;
- track states such as `PENDING -> SENDING -> ACKNOWLEDGED`;
- retry transient failures with bounded backoff;
- stop retrying revoked-auth/schema/validation failures indefinitely;
- detect sequence gaps;
- never emit a control event until the referenced payload is durably readable;
- deduplicate duplicate events/analysis;
- remain completely optional to local scanning, persistence, classification, and alerts.

Fast sampling must live with the active scanning/session runtime. Android periodic WorkManager is not a one-minute timer; durable WorkManager retry is appropriate only for deferred recovery.

## Bridge screen requirements

The dedicated `Telemetry & AI Bridge` destination owns bridge state. It must keep showing:

- selected technical account;
- Drive and Gmail capability state separately;
- connect/change/disconnect;
- explicit test bridge action;
- last Drive/Gmail test identifiers;
- clear reconnect/error state;
- no automatic telemetry enablement merely by opening the screen.

A future production bridge screen may add transport state, outbox depth, last uploaded sequence/window, retention state, and last analysis event.

## T0 acceptance evidence

The accepted spike demonstrated:

```text
select dedicated account
 -> authorize email identity + drive.file + gmail.send
 -> create BlueEye/test/bridge-test-<timestamp>.json
 -> obtain Drive file id
 -> self-send BLUEEYE/BATCH_READY containing that id
 -> ChatGPT reads the exact referenced JSON
 -> revoke Google access
 -> app returns both scopes to Not granted
 -> reauthorize
 -> scopes return to Granted
```

A subsequent defect was found where app process restart reset the in-memory UI state. The fix persists only the account email and silently refreshes authorization on screen entry; tokens remain memory-only.

## Trigger conclusion

The Gmail message is still useful as a compact developer control envelope because it carries the exact Drive file identity. However, the current ChatGPT integration does not expose a guaranteed Gmail push event that immediately wakes this workflow. The demonstrated automation fallback is a scheduled/condition watch, not a real-time webhook.

Accordingly, a production control plane should prefer an explicit event/webhook-capable service. Gmail polling may remain a developer fallback but must not be presented as near-live event delivery.

## Next work

1. Keep T0 as the developer/debug bridge and regression fixture.
2. Do not add automatic minute Drive uploads or Gmail triggers yet.
3. Define the production `TelemetryTransport` contract and privacy envelope independent of Google.
4. Evaluate a small authenticated endpoint/storage plus event/webhook path.
5. Only after that decision, implement reducer + durable outbox + sequence/idempotency model.
6. Reuse the T0 Drive/Gmail test to compare production-path payload identity and analysis behavior where useful.

## Definition of done for T1+

The future bridge is complete only when bounded versioned telemetry can leave the phone without harming local operation; delivery is durable/idempotent; an explicit event mechanism can identify exactly which payload should be analyzed; failures/revocation recover safely; retention is bounded; private evidence stays out of engineering systems; and confirmed field evidence can complete the evidence -> fix -> verification -> later-evidence loop.

## References to re-check before T1 implementation

- Android authorization: https://developer.android.com/identity/authorization
- AuthorizationClient: https://developers.google.com/android/reference/com/google/android/gms/auth/api/identity/AuthorizationClient
- Drive scopes: https://developers.google.com/workspace/drive/api/guides/api-specific-auth
- Gmail scopes: https://developers.google.com/workspace/gmail/api/auth/scopes
- Google Workspace user-data policy: https://developers.google.com/workspace/workspace-api-user-data-developer-policy
- Google OAuth testing/production behavior: https://developers.google.com/identity/protocols/oauth2
- WorkManager periodic work: https://developer.android.com/develop/background-work/background-tasks/persistent/getting-started/define-work
