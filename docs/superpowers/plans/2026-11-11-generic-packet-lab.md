# Generic packet feasibility implementation plan

> **For agentic workers:** Use superpowers:executing-plans inline. The user approved the two validation gates and previously requested autonomous continued implementation; preserve that authorization without another routine approval gate.

**Goal:** Establish what opaque native packet forwarding and ordered replay actually solve.
**Architecture:** Guarded experimental seams in the existing native frontend; a separate fixture mod owns all test-payload semantics. The bridge knows only wire framing, ownership and bounded history.
**Tech Stack:** Java21, Minecraft1.21.4, Fabric0.20.1/API0.119.4, native codecs/Netty, JUnit5.
**Spec:** `../specs/2026-11-11-generic-packet-lab-design.md`.

## Global Constraints

- One original server connection; private marked fixtures only; no real SAO experiment.
- No per-test-channel adapter in relay code; no official authentication values in evidence.
- Failures must close experimental viewers, not the original game transport.
- A passed live-forwarding test is not proof of complete state migration.

## Review Focus

- Unregistered host payload must retain bytes rather than become DiscardedPayload.
- Old owner frames after transfer must be rejected.
- Expired and purely local state must not be mislabeled as correctly recovered.
- Oversized history must refuse catch-up rather than replay a truncated state.
- Experimental behavior must be unavailable in an unmarked real instance.

## Task 1: framing, ownership and bounded journal

Files: relay-core OpaqueFrames, PacketJournal and tests; bridge raw capture / experimental transport; viewer generic envelope.
- [x] Write RED tests for arbitrary binary payload, truncated framing, journal ordering/copy isolation/overflow, reserved channel exclusions and ownership.
- [x] Implement opaque byte transport and bounded journal; run GREEN tests.
- [x] Add marked-fixture-only lab mode, retain normal controller actions and suppress duplicate host gameplay writes while an experimental controller owns input.

## Task 2: independent fixture and native integration

Files: packet-lab fixture mod; integration-probe test operations; scratch private Fabric server/clients.
- [x] Build a test mod with bidirectional opaque payload, absolute state, receipt-relative effect and local-only state.
- [x] Start private backend/host, attach bridge, generate pre-join state, then launch native viewers.
- [x] Check exact request/reply bytes, native local swing, journal-restored absolute state, stale-event and local-state counterexamples; verify backend retains one player.

## Task 3: evidence and review

Files: docs/GENERIC-PACKET-LAB.md, P1-PROGRESS ledger.
- [x] Record actual pass/fail results with boundaries and pending platform acceptance.
- [x] Independent read-only review of experimental changes, address substantive findings and rerun affected checks.
- [x] Recommend architecture from measured evidence; do not package the superseded thin-input route as completed SAO handoff.
