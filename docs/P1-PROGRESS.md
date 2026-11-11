# P1 execution ledger

Plan: `docs/superpowers/plans/2026-11-11-p1-handoff.md`.
Base: a1743f1. User explicitly requested continued implementation and Mac/Windows startup delivery.

- Pre-flight: codebase graph transport was closed; exact source and local SDK inspection are the fallback.
- Ruling: use the existing isolated project branch and execute inline, preserving the user's explicit continued-work authorization instead of requesting another routine approval.
- Ruling: use the original native Minecraft packet codecs / registry context for the frontend rather than assuming a Mineflayer port can represent SAO client state.
- Task 1: starting SDK contract inspection. The earlier original host PID 68948 is no longer present; do not claim it remains online. Future original-instance tests require a currently observed authenticated process.

- Task 1: mapped SDK sources generated in ignored Gradle caches; selected API views in chat work/p1-sdk. Native login/configuration/registry+tags and initial chunks/inventory/entity snapshots compile. Input routing remains intentionally unfinished; no P1 artifact released.
- Core behavioral tests: PeerLifecycle3, PendingBudget2, EndpointPolicy2, ReadPause1 have RED/GREEN evidence. BridgeLoader child-first identity and stable wire-ID mirror filtering tests pass. Viewer metadata4 and sandbox2 tests have RED/GREEN evidence.
- Ruling: preserve original auth/keepalive/cookies locally and filter by stable packet wire IDs, not getSimpleName (production classes are intermediary-named).
- Ruling: pause source reads only for a bounded snapshot barrier; a two-second independent event-loop timeout restores reads even if the game thread stalls. Viewer disconnect must never await its socket on the original source loop.
- Integration fixture: private vanilla server bound127.0.0.1:25586, RCON127.0.0.1:25589, own secret in work/p1-integration/fixture.json (never print). Server exec session55977. No original SAO process is active.
- First production-profile host fixture launch58908 exited before connecting because viewer ConnectionGuardMixin config did not reference its generated refmap. Correcting explicit refmap/AP source-set configuration; startup was fail-closed. Viewer directory work/p1-integration/host is isolated.
