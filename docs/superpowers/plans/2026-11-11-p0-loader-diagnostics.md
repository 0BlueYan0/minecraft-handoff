# P0 Loader Diagnostics and Control Ownership Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task in this chat. Steps use checkbox (`- [x]`) syntax for tracking. This plan does not request subagents.

**Goal:** Build an additive Fabric diagnostic mod and test the control ownership model, producing evidence for the later Minecraft handoff implementation.

**Architecture:** Keep the ownership state machine independent of Minecraft. A client-only Fabric adapter reports lifecycle events and a small allowlisted diagnostic record. P0 has no remote listener, gameplay replication, token handling, or claim of upstream session persistence.

**Tech Stack:** Java 21; Minecraft 1.21.4; Fabric Loader 0.20.1; Fabric API 0.119.4+1.21.4; Gradle Wrapper; Fabric Loom; JUnit Jupiter. Select a released Loom/compatible Gradle pair from the official metadata during setup, then pin both exact versions in the build and record them in README; do not use a moving SNAPSHOT.

**Spec:** `../specs/2026-11-11-minecraft-handoff-design.md`

## Global Constraints

- Minecraft 1.21.4、Fabric Loader 0.20.1、Java 21。
- 現有 Fabric API 為 0.119.4+1.21.4。
- 常駐設備採用 M1 Mac mini、8 GB 統一記憶體。
- 原始遊戲與驗證流程保留。第一版使用原啟動器；替代參數啟動不是必要條件。
- 日誌不記錄啟動器憑證、原始自訂 payload、聊天或其他玩家資料。
- P0 不建立接力遊戲入口，不維持替代玩家連線，不宣稱可接管遊戲。
- Work in this new project only; do not change the original instance during build/test. Exclude logs, credentials, third-party JARs, generated runtime files and Gradle caches from Git.

## Review Focus

- A failed replacement must leave the existing controller active: test `failedPreparationPreservesController` in Task 1.
- A late readiness message from a canceled preparation must not grant control: test `canceledTicketCannotAcquireControl` in Task 1.
- An old controller's delayed input must be rejected after a transfer: test `oldLeaseRejectedAfterTransfer` in Task 1.
- Diagnostics must not expose sensitive property values or enumerate unrelated properties: test `diagnosticsContainOnlyExplicitFields` in Task 2.
- A built JAR must resolve the real client entrypoint, supported versions and embedded core classes: test `verifyModArtifact` in Task 3; actual launcher loading remains a separate manual acceptance test.

---

## File Structure

- `settings.gradle`, `build.gradle`, `gradle.properties`, Gradle Wrapper: pinned build and artifact verification.
- `.gitignore`: generated and sensitive local files.
- `src/main/java/tw/handoff/core/ControlSession.java`: ownership state machine, no Minecraft dependency.
- `src/main/java/tw/handoff/core/DiagnosticSnapshot.java`: allowlisted immutable diagnostic fields and serialization.
- `src/client/java/tw/handoff/client/HandoffClient.java`: Fabric entrypoint, JOIN/DISCONNECT events and periodic heap reporting.
- `src/main/resources/fabric.mod.json`: client-only additive mod metadata, ID `minecraft_handoff`.
- `src/test/java/tw/handoff/core/ControlSessionTest.java`: state transition behavior.
- `src/test/java/tw/handoff/core/DiagnosticSnapshotTest.java`: diagnostic field boundaries.
- `README.md`: build, installation/removal, Mac acceptance and current limitations.

### Task 1: Ownership model with behavior tests

**Interfaces:** `ControlSession` exposes `Ticket prepare(String clientId)`, `boolean ready(Ticket ticket)`, `void cancel(Ticket ticket)`, `void disconnect(String clientId)`, `boolean acceptsInput(String clientId, long epoch)`, and `Optional<Lease> controller()`. Nested immutable `Ticket` contains client ID and preparation sequence; `Lease` contains client ID and ownership epoch. Use a single coordinator thread; P0 does not implement concurrent network handling.

- [x] Configure Java 21 build, pinned toolchain dependencies and JUnit; create tests before the production model.
- [x] Implement tests: initial controller is empty; first ready ticket obtains epoch 1; preparing B does not revoke A; failed/canceled preparation leaves A; an invalid ticket cannot revoke a newer preparation; ready B advances epoch; stale A input fails; disconnecting the controller clears ownership; disconnecting a noncontroller preserves ownership; a disconnected candidate's ticket cannot grant control; blank IDs are rejected.
- [x] Run `./gradlew test --tests '*ControlSessionTest'`; record expected feature-missing failures. On Windows use `gradlew.bat`.
- [x] Implement the state machine with monotonically increasing sequences; old tickets never become valid again. Readiness changes model ownership atomically; document that the later gameplay layer must establish a synchronization boundary before calling it.
- [x] Rerun tests; require zero failures. Commit only the new project files for this task.

### Task 2: Additive client-only diagnostic mod

**Interfaces:** `DiagnosticSnapshot` contains only schema version `1`, application version, Minecraft version, Fabric Loader version, curated mod versions, heap used/max bytes and lifecycle state. `toJson()` emits only those fields. The curated mod IDs are `fabricloader`, `fabric-api`, `saoui`, `mcef`, `minecraft_handoff`; no inventory of arbitrary files or system properties. `HandoffClient implements ClientModInitializer` consumes this snapshot.

- [x] Add `DiagnosticSnapshotTest`: valid JSON and schema 1; allowlist output only; correct escaping; null/missing optional mod versions represented as absent; negative memory values rejected. Supply sensitive-looking unrelated test data to confirm the serializer has no generic property map input or arbitrary diagnostic dump path.
- [x] Run the test and record the expected missing-feature failure.
- [x] Implement the immutable snapshot and JSON serialization using a declared library or a well-defined serializer; do not hand-build unescaped JSON strings.
- [x] Add client entrypoint using public Fabric lifecycle events. Record initialization and JOIN/DISCONNECT counts, not server address or username. Every 60 seconds record JVM heap used/max and current lifecycle; avoid blocking client tick. Use a namespaced logger, do not read token values or network payloads.
- [x] Create metadata: ID `minecraft_handoff`, version `0.1.0`, environment `client`, Minecraft `1.21.4`, Java `>=21`, loader `>=0.20.1`, Fabric API dependency. Do not add gameplay mixins or modify the original mod metadata.
- [x] Run full test suite and compile/remap the mod. Commit the adapter and tests after success.

### Task 3: Verify artifact and document actual acceptance

**Interfaces:** Gradle task `verifyModArtifact` consumes `remapJar` and verifies the final distributable, not just development classes. README gives Windows and Mac build/install commands and clearly labels P0's limits.

- [x] Add artifact checks for metadata, the exact entrypoint class, `ControlSession` and `DiagnosticSnapshot`; reject accidental inclusion of runtime logs, tokens, third-party mod JARs or test classes. Confirm the checks fail on a temporary malformed archive before accepting the validator.
- [x] Run `gradlew.bat clean test build verifyModArtifact`; require exit 0, inspect test summary and final JAR contents. Record exact build versions and SHA-256 of the distributable.
- [x] Document manual launcher test: close game; copy only the new JAR into mods; launch normally; confirm the initialized message and original SAO login; check JOIN/DISCONNECT and memory records; remove only this new JAR to roll back. Do not package launcher credentials or the third-party modpack.
- [x] Document Mac validation as pending until performed on the user's M1. Do not equate Windows compilation with successful launcher loading or actual handoff.
- [x] Deliver source and JAR with explicit implemented/pending status. P1 planning begins after P0 acceptance; P1 needs actual Minecraft snapshot and operation-transfer research.

## Verification Boundaries

Unit tests prove ownership model and diagnostic boundaries. Artifact checks prove the built JAR's packaging. Only loading through the original launcher proves that launcher's additive-mod compatibility. Only later live end-to-end tests can prove that A can leave, B can take over and the upstream session remains connected.

## Build References

- Fabric official 1.21.4 example properties: https://raw.githubusercontent.com/FabricMC/fabric-example-mod/1.21.4/gradle.properties
- Fabric official build example: https://raw.githubusercontent.com/FabricMC/fabric-example-mod/1.21.4/build.gradle

The official example currently references a moving Loom snapshot; this plan requires replacing that with an explicitly verified released version during setup.

## Execution status — 2026-11-11

All three implementation tasks completed. Launcher/Mac acceptance remains pending as documented in README and docs/P0-VALIDATION.md; the checked installation step means the procedure was documented, not that Mac execution occurred.
