# UI/UX Next Walk Handoff — 2026-09-13

## Current checkpoint

- Repository: `MichalMatu/tracker`
- Branch: `main`
- Current source checkpoint after documentation sync: see current `main` HEAD.
- Local Agent binding: `be481b25-9d97-4205-b93f-95f5c5827441`
- Target device: Samsung SM-S906B (S22+)
- Phase 3 field reacceptance: ACCEPTED / CLOSED.

## Current UI state

The Radar/Details redesign is landed on `main`.

Details now follows the decision-first structure: summary, signal/history, key evidence, identity, actions/review, history, technical details, then all evidence.

Technical Details is progressive disclosure: it is collapsed by default and expands explicitly. Connection controls, sensor data, radio metadata, device metadata, services and the `Raw Data` action are inside the expanded section. Raw Data is no longer a top-bar action.

No scanner, parser, identity, Follow-Me, persistence or scoring change is intended for this UI cleanup.

## Next physical session

Install and verify the exact current `main` build on the S22+ before the walk. Do not mix APK/source versions during the comparison.

Primary checks:

1. Radar opens and scanning remains healthy.
2. Details first viewport communicates device identity, current RSSI/last seen and decision context without technical BLE fields dominating the surface.
3. Technical Details starts collapsed.
4. Expanding Technical Details exposes connection/radio/metadata/services and Raw Data.
5. Raw Data dialog still opens and copy/export actions work.
6. Live scanning does not cause obvious vertical jumps while scrolling Details.
7. Dark mode and normal Android font scale remain usable.
8. No crash, ANR or fatal Tracker exception during the walk.

For the field walk, use the existing `FIELD_SESSION_CHECKLIST.md`. Record the exact tested source SHA and keep private GPS, MAC addresses, HCI/bugreport and private Room/database artifacts out of the public repository.

## Stop conditions

Stop the walk and preserve evidence if there is a crash, ANR, scanner interruption, broken Details navigation, broken Raw Data action or a reproducible layout jump. Do not tune parser/scoring/identity behavior from a UI-only observation.

## Next implementation direction

After the walk, use the evidence to decide whether to continue Details UX work (Tracking & Signal / lazy composition / technical peek) or close the current UI slice and move to the next planned milestone. Do not reopen accepted Radar performance work without a concrete regression.
