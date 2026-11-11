# SAO current-state snapshot implementation

> Execute inline using executing-plans. User requested continued implementation and offered live testing; original official authentication must remain untouched.

Goal: read SAOUI2.51.13 current data through a version-bound, explicit schema; prove serialization and local restoration without replaying gameplay events. First live step is read-only in the original game.

Evidence input: installed SAOUI-2.51.13.jar SHA2562e73a789779e14630e68b5053732eaaaba1fe99dadeff2c2a3f6efd682a58979. REA identifies the archive; Java provider unavailable, installed Vineflower1.11.1 supplies bounded offline decompilation in work/sao-state-re. Decompiled vendor source is analysis-only and must not enter deliverables.

Scope: Mux capabilities, skill layout/remaining cooldowns, equipment current immutable records, party current record, quest HUD semantic fields. Skip transient animations, popups, typing, sounds, authentication, and arbitrary object graphs. Unknown artifact versions fail closed. More SAO systems stay explicitly unsupported until inspected.

- [x] Write failing unit tests for recursive bounded values, whitelisted-only capture, state round-trip and cooldown aging; add installed-artifact bytecode contract verification.
- [x] Add pure-Java sao-state module with exact field schema, controlled record conversion, capture/prepare/apply separation, and no network or auth access.
- [x] Build an independent read-only Attach probe that validates PID/gameDirectory, captures on game thread, writes local private scratch output and prints only aggregate counts.
- [ ] Run contract and regression checks, then capture actual authenticated game state if the user has joined; ask for minimal user test only when needed.
  - Partial: contract/regression checks pass (state-final-check 20:20). Authenticated SAO capture NOT done; server unreachable as of 2026-11-11.
- [x] Validate local restoration in isolated fixtures before wiring it into native peer readiness. Do not report an unimplemented state bootstrap as complete SAO handoff.
  - Done only on the marked full-mods local fixture via SaoFixtureRoundtrip with self-made data. Peer readiness wiring not started.
- [ ] Independent review, evidence/limitations ledger and next concrete acceptance step.
  - Partial: first review fixed rollback Set type and null required collections. Final probe bootstrap four-field args, fixture --fixture-no-resourcepacks and timer preparation-time changes still need a full re-review. Evidence logs: ignored handoff-evidence/sao-state/.
