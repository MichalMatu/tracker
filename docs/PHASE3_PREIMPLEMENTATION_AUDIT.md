# Phase 3 Preimplementation Audit — Ingest Accounting

Status: IMPLEMENTATION INPUT
Source baseline: `23b7f21dc31fb504dd89798ad3447f7615e9d1d4`
Scope: BLE Stable Core ingest only; no lifecycle, identity, alert, RFCOMM, Classic, or Radar/history redesign.

## Existing path

The current automatic Stable Core path is:

`BleScanSource callback -> BleScanner Channel<ScanEvent>(4096, DROP_OLDEST) -> sequential processor -> ScanResultExtractor -> DeviceRepositoryImpl -> BleScanHandler -> DevicePersister -> Room`

`BleScanSource` emits every non-null `ScanResult`; batch callbacks are expanded into individual results. `BleScanner` immediately attempts `trySend` into one shared sequential channel. The channel can evict the oldest queued event on overflow. The local drop counter is logged, but is not connected to runtime diagnostics.

`DeviceRepositoryImpl.recordBleResult()` runs only after an event has survived the queue and extraction. Therefore the existing `bleResultsPerMinute` is not a raw Android callback rate and cannot be reconciled with queue loss.

`BleScanHandler` performs MAC/carryover resolution, a Room lookup, watchlist logic, classification, enrichment, Follow-Me scoring and alert decision work before persistence. A carryover result marked `isProvisional` intentionally exits before `DevicePersister`.

`DevicePersister` throttles device-row scan updates at 1 second for ordinary devices and signal samples at 2 seconds. Tactical devices use the 100 ms priority interval. This throttling occurs after the expensive handler work and is not ingest coalescing.

A signal-sample insert failure is currently logged and swallowed, so it is a silent accounting gap even though the scan handler itself can still return success.

## Counter semantics

Phase 3 instrumentation must keep these meanings distinct:

- `rawBleCallbacks`: each non-null Android BLE `ScanResult` delivered to the callback, including each item expanded from a batch.
- `enqueueAccepted`: a scan event accepted by the ingest queue. Under the current `DROP_OLDEST` policy this can coincide with another older event being dropped.
- `enqueueRejected`: `trySend` failed; the new event did not enter the queue.
- `queueDropped`: an already accepted queued event became undeliverable, currently principally `DROP_OLDEST` overflow.
- `coalesced`: a received event intentionally replaced/merged into a pending event while preserving the newest useful observation. This is exactly zero before the later coalescing implementation.
- `processingStarted`: an event was dequeued and heavy processing began.
- `processingSucceeded`: extraction plus repository handling completed successfully.
- `processingFailed`: extraction or repository handling failed.
- `provisionalDiscarded`: MAC/carryover correlation deliberately withheld persistence because evidence was still provisional.
- `persistedDeviceUpdate`: one processed scan event caused at least one primary `DeviceEntity` mutation. Multiple DAO calls for the same scan count as one persistence event.
- `deviceUpdateThrottled`: the normal scan-row update was intentionally suppressed by `ScanThrottler`; a separate tracking-state write may still have occurred.
- `signalSampleWritten`: `SignalSampleEntity.insert` completed successfully.
- `signalSampleThrottled`: an otherwise persisted scan intentionally skipped a signal sample due to sample throttling.
- `signalSampleWriteFailed`: a sample insert was attempted and failed.
- `queueDepth`: accepted pending events not yet dequeued, adjusted for dropped events.
- `queueHighWaterMark`: maximum observed queue depth; an operational approximation, not a Channel internal API guarantee.
- `queueWaitMs`: monotonic time from raw callback receipt to processor dequeue.
- `processingDurationMs`: monotonic time from processor dequeue through extraction/repository completion.

## Reconciliation invariants

For a settled synthetic burst with no in-flight work:

`rawBleCallbacks = enqueueAccepted + enqueueRejected + coalescedBeforeQueue` for the selected queue design.

Under the current pre-coalescing channel:

`enqueueAccepted = processingStarted + queueDropped` once queue depth returns to zero.

`processingStarted = processingSucceeded + processingFailed` once no event is in flight.

Persistence is intentionally not one-to-one with processed events because provisional correlation and persistence/sample throttling are valid discard reasons. Those reasons must be counted rather than inferred.

## Implementation boundary

1. Add the precise diagnostics domain model/store operations.
2. Instrument the current `DROP_OLDEST` behavior first and prove counter reconciliation in focused tests.
3. Only then replace the queue policy with bounded latest-per-device coalescing and explicit rejection on true capacity exhaustion.
4. Preserve newest observation timestamp/evidence when coalescing.
5. Expose the counters in Settings/export without adding ingest business logic to `SettingsViewModel`.
6. Run synthetic pressure tests, full `qualityCheck`, `:app:assembleDebug`, then canonical exact-SHA GitHub Actions.
7. Stop Phase 3 at a field-build handoff; hardware walk data is the next evidence source, not a reason to begin Phase 4.
