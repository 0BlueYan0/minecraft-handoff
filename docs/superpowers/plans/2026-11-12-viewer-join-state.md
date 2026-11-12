# Viewer 中途加入 SAO 狀態同步：實作計畫

> **For agentic workers:** 這個 repo 在 herdr 裡執行（`HERDR_ENV=1`），照通用規則第 10 類開 worker 與 reviewer，不用 subagent-driven-development。步驟用 `- [ ]` 追蹤。

**Goal:** Windows viewer 中途加入時，拿到常駐端的 SAO 推送狀態與時間值，驗證成功送出 ACK 之後才取得控制權。

**Architecture:** 常駐端在 S2C 擷取點維護一張「最後一筆表」（`SaoReplayTable`），viewer 加入時依序重播表內 frame，再送 `sao-state` 的欄位快照（分段送）。viewer 在 JOIN 之後套用，成功回 ACK，常駐端的 `JoinGate` 收到 ACK、往返時間合格、表沒有缺塊，才 `grant`。通用 C2S 轉送從 `PacketLab` 升成正式模式，給拿到控制權之後的 A 類請求用。

**Tech Stack:** Java 21、Fabric Loader 0.20.1、Fabric API 0.119.4+1.21.4、Minecraft 1.21.4、Gson 2.11.0、JUnit 5、Gradle Wrapper 8.12。

**Spec:** `docs/superpowers/specs/2026-11-12-viewer-join-state-design.md`

## Global Constraints

- 建置一律用 `CLAUDE_HANDOFF.md`「環境與建置」的 JDK 21 與 `HANDOFF_SAO_JAR`。
- 不碰啟動器驗證，不複製或偽造 token，viewer 不登入 SAO。不要重跑 launcher-helper 0.1.1 的重啟方案。
- 不重播完整封包歷史。正式模式不得呼叫 `PacketLab.replay`。
- 觀看中的 viewer 不對伺服器送出任何封包。
- 時間值只扣常駐端量到的經過時間與 viewer 本機等待，不加傳輸時間。viewer 的冷卻、武器冷卻、護盾鎖定不可比常駐端短。
- 補不到任何一塊就不交出控制權，viewer 畫面標出缺的那一塊。
- 不印、不寫入 repo 任何 SAO 的 HMAC 常數或 launcher 驗證值。
- 碰使用者遊戲 JVM 或 SAO 伺服器的動作，每次先用選單問（`CLAUDE.md`）。本計畫的自動測試只用本機假資料與私有 fixture，不得寫成實服接管成功。
- UI 文字用繁體中文。

## Review Focus

1. 擷取點的 frame 已經進表、但遊戲執行緒還沒處理，就在這時取欄位快照，viewer 會先套用新 frame 再被舊欄位蓋掉。避免方法是表快照與欄位快照在同一個 `minecraft.execute` 工作裡取，這個工作是在上游暫停讀取之後才排進佇列，先前讀到的 SAO frame 處理工作都排在它前面（Task 5 測試 `tableAndFieldsCapturedInOneGameTask`）。
2. `quest_hud` 同一個 `hudId` 先經直接通道、後經 `saocore:mux` 送來。預期兩種形式共用同一個 key，只留後到的（Task 1 測試 `questHudDirectAndMuxShareKey`）。
3. viewer 在 ACK 之前按 F6。預期常駐端回 `E:` 並帶缺的那一塊，不 `grant`（Task 2 測試 `takeBeforeAckIsRefusedWithBlocker`）。
4. 舊 viewer 有控制權時新 viewer 加入，新 viewer 套用失敗。預期舊 viewer 繼續有控制權（Task 5 測試 `failedNewViewerKeepsOldOwner`）。
5. 分段快照中途斷線或段數不對。預期 viewer 回 `N:` 並停在觀看，不套用半份（Task 2 測試 `reassemblerRejectsGapsAndOverflow`）。

## 分工與依賴

| 份 | 內容 | 改的檔案 | 依賴 |
|---|---|---|---|
| T1 | 最後一筆表 | relay-core 新檔 | 無 |
| T2 | 控制訊息、分段、JoinGate | relay-core | 無 |
| T3 | 時間值快照 | sao-state | 無 |
| T4 | 通用轉送正式模式 | relay-bridge `PacketLab`、`HostBridge` 寫出過濾、viewer-mod `ViewerClient` 旗標 | 無 |
| T5 | 常駐端加入流程 | relay-bridge `HostBridge`、`RemoteControl`、`NativePeer`、build.gradle | T1、T2、T3、T4 |
| T6 | viewer 套用與 ACK | viewer-mod | T2、T3 |
| T7 | 本機 fixture 整合驗收 | packet-lab、integration-probe | T5、T6 |

T4 與 T5 都改 `HostBridge`，所以 T5 排在 T4 通過 review 之後。

reviewer 模型（通用規則第 4 類）：T1、T2、T3、T6 用 Sonnet（四種都不符合）。T4 用 Opus（資安，改變哪些封包能送到實服）。T5 用 Opus（跨模組，用到 relay-core 與 sao-state 的介面）。T7 用 Sonnet。

---

### Task 1: SaoReplayTable

**Files:**
- Create: `relay-core/src/main/java/tw/handoff/relay/core/SaoReplayTable.java`
- Test: `relay-core/src/test/java/tw/handoff/relay/core/SaoReplayTableTest.java`

**Interfaces:**
- Consumes: `OpaqueFrames.inspect(byte[])`、`OpaqueFrames.payload(int,String,byte[])`（測試組 frame 用）。
- Produces:
  - `public SaoReplayTable(int customPayloadPacketId, int maxBytes)`
  - `public synchronized void observe(byte[] frame)`：完整的 clientbound play frame。非 custom payload 或不在表內的通道直接略過。
  - `public synchronized Replay replay()`
  - `public record Replay(List<byte[]> frames, Set<String> missing)`：frames 依收到順序、去重（同一筆 frame 屬於多個 key 只出現一次）。`missing` 可能含 `"saoblessing"`、`"replay-budget"`。

**規則（照設計文件 B 類表）：**
- `saocore:mux` 的 data 是 `[1 byte 長度][子通道名][內容]`。子通道 `:hello` 存到 key `mux:hello`。子通道 `saogui:main` 的內容是 JSON，取 `op`：`INIT` 同時更新 key `skillbar`、`mana`、`level`。`USE_SKILL_OK` 更新 `skillbar`。`MANA_UPDATE` 更新 `mana`。`LEVEL_UP` 更新 `level`。`SHIELD_UPDATE`、`SKILL_VARIANT`、`SETTINGS`、`UI_STYLE`、`PROJECTION_LIST` 各自一個 key（`saogui:` 加操作碼）。其他操作碼不存。
- `quest_hud` 的二進位內容開頭是 `[1 byte packetType][varint 長度 + UTF-8 hudId]`。直接通道 `capuodev:quest_hud` 與 mux 子通道 `capuodev:quest_hud` 共用 key `quest:`+hudId。`packetType==0` 存，`==1` 且 hudId 是 `*` 刪掉全部 `quest:`，`==1` 單一 id 刪那一筆，其他值略過。
- `saoblessing:main` 的 data 是 UTF-8 JSON，取 `op`。`SERVER_HELLO` 不存。`SESSION_START`、`SESSION_END` 清空 blessing 紀錄後把這筆放第一筆。其他操作碼附加在後。超過 256 筆就清空並標記 overflow，直到下一次 `SESSION_START` 或 `SESSION_END` 才解除。
- 表內 frame 總位元組超過 `maxBytes` 就清空全部並標記 `replay-budget`，此後 `missing` 一直含它（這條連線不再補得到）。
- 解析失敗（JSON 壞掉、長度不對）的 frame 略過，不丟例外。

- [ ] **Step 1: 寫失敗的測試**

```java
@Test void latestFramePerKeyInArrivalOrder()       // INIT, MANA_UPDATE, USE_SKILL_OK → frames == [INIT, MANA_UPDATE, USE_SKILL_OK]；再來一個 INIT → frames == [INIT2]
@Test void questHudRemoveSingleAndAll()             // 存 a、b；REMOVE b → [a]；REMOVE "*" → []
@Test void questHudDirectAndMuxShareKey()           // 直接通道 a(v1) 再 mux a(v2) → frames == [mux a(v2)]
@Test void blessingKeepsSessionLogAndResets()       // START, CHARGE{value}, CHARGE{max} → 3 筆依序；SESSION_END → [END]
@Test void blessingOverflowMarksMissingUntilSession() // 257 筆 CHARGE → missing 含 "saoblessing"；SESSION_START 後 missing 不含
@Test void excludedOpsAreNotStored()                // ITEM_SKILL_COOLDOWN, ASK, SERVER_HELLO, DISCOVERY_REGION, MOB_HB_TICK → frames 為空
@Test void budgetOverflowIsPermanent()              // maxBytes=64，塞超過 → missing 含 "replay-budget"，之後新 frame 也不解除
@Test void malformedFramesIgnored()                 // mux 長度欄超出、JSON 壞掉 → 不丟例外、frames 不變
```

- [ ] **Step 2: 跑測試確認失敗**

Run: `.\gradlew.bat :relay-core:test --tests "tw.handoff.relay.core.SaoReplayTableTest" --no-daemon --console=plain`
Expected: 編譯失敗，`SaoReplayTable` 不存在。

- [ ] **Step 3: 實作 `SaoReplayTable`**

每筆存入時給遞增序號，key 對應 `(序號, frame)`。`replay()` 把所有 key 的值與 blessing 紀錄合併，依序號排序後去重。JSON 用 Gson `JsonParser`。

- [ ] **Step 4: 跑測試確認通過**

Run: 同 Step 2。Expected: 8 tests PASS。另跑 `:relay-core:test` 全部通過。

---

### Task 2: 控制訊息、分段與 JoinGate

**Files:**
- Modify: `relay-core/src/main/java/tw/handoff/relay/core/ControlWire.java`
- Create: `relay-core/src/main/java/tw/handoff/relay/core/StateChunks.java`
- Create: `relay-core/src/main/java/tw/handoff/relay/core/JoinGate.java`
- Test: `relay-core/src/test/java/tw/handoff/relay/core/StateChunksTest.java`、`JoinGateTest.java`

**Interfaces:**
- Produces（訊息都包在 `BrandPayload`，前綴 `HANDOFF2:`）：

| 方向 | 格式 | 意思 |
|---|---|---|
| 常駐端→viewer | `Q:<nonce>` | 量往返時間 |
| viewer→常駐端 | `q:<nonce>` | 回應 |
| 常駐端→viewer | `S:<index>:<total>:<base64>` | 快照分段，index 從 0 |
| 常駐端→viewer | `M:<piece>` | 常駐端這邊已知缺的塊 |
| viewer→常駐端 | `K` | 套用成功 |
| viewer→常駐端 | `N:<piece>` | 套用失敗 |

  - `public static List<String> StateChunks.encode(byte[] document)`：每段 base64 最多 24000 字元。
  - `public final class StateChunks.Reassembler { public Reassembler(int maxBytes); public Optional<byte[]> accept(String message) }`：index 必須連續從 0 開始、total 每段相同、total ≤ 200、解碼總長 ≤ maxBytes，違反就丟 `IllegalArgumentException`。最後一段到才回傳完整內容。
  - `public final class JoinGate`：
    - `public String nonce()`、`public void pong(String nonce, long rttNanos)`
    - `public void hostMissing(Set<String> pieces)`、`public void viewerAck()`、`public void viewerFailed(String piece)`
    - `public boolean mayGrant()`：已 ACK、`rttNanos ≤ 1_500_000_000`、hostMissing 為空、沒有 viewerFailed。
    - `public String blocker()`：`mayGrant()` 為 true 時回 `null`，否則回第一個原因，順序是 viewerFailed 的 piece、hostMissing 的第一個、`"latency"`（沒量到或超過）、`"pending"`（還沒 ACK）。
  - piece 名稱（viewer 畫面會顯示對應中文）：`version`、`saoblessing`、`replay-budget`、`latency`、`snapshot`、`pending`。

- [ ] **Step 1: 寫失敗的測試**

```java
@Test void chunksRoundTripTwoMegabytes()        // 2 MiB 隨機內容 encode 後逐段 accept，最後一段回傳相同 bytes，段數 == ceil(base64長度/24000)
@Test void reassemblerRejectsGapsAndOverflow()  // 跳過 index 1、total 不一致、total 201、超過 maxBytes 都丟 IllegalArgumentException
@Test void gateNeedsAckRttAndNoMissing()        // 只有 ACK → blocker "latency"；加 rtt 1.0s → mayGrant；rtt 1.6s → "latency"
@Test void hostMissingBlocksEvenAfterAck()      // hostMissing {"saoblessing"} + ACK + rtt → blocker "saoblessing"
@Test void viewerFailureWins()                  // viewerFailed("version") → blocker "version"
@Test void wrongNonceIgnored()                  // pong("other", 1ms) 不算量到
@Test void takeBeforeAckIsRefusedWithBlocker()  // 只有 rtt → mayGrant false、blocker "pending"
```

- [ ] **Step 2: 跑測試確認失敗**

Run: `.\gradlew.bat :relay-core:test --tests "*StateChunksTest" --tests "*JoinGateTest" --no-daemon --console=plain`
Expected: 編譯失敗。

- [ ] **Step 3: 實作三個類別**，nonce 用 `SecureRandom` 產生 16 bytes 的 hex。

- [ ] **Step 4: 跑測試確認通過**，再跑 `:relay-core:test` 全部通過。

---

### Task 3: 時間值快照（武器冷卻、護盾鎖定）

**Files:**
- Modify: `sao-state/src/main/java/tw/handoff/sao/SaoSchema.java`、`SaoSnapshot.java`
- Create: `sao-state/src/test/java/fr/sao/saoui/skills/WeaponCooldownTracker.java`、`sao-state/src/test/java/fr/sao/saoui/hud/ShieldBar.java`（假類別，欄位名照 2.51.14）
- Test: `sao-state/src/test/java/tw/handoff/sao/TimerSnapshotTest.java`，`ArtifactContractTest` 更新常數後要通過

**Interfaces:**
- Produces：快照 JSON 的 `schema` 改成 `2`，頂層多一個 `timers`：
  ```json
  {"weapon":{"<key>":{"startAgoNanos":123,"remainingNanos":456}},"shield":{"lockRemainingMillis":789}}
  ```
  `SaoSnapshot.capture`、`prepare`、`RestorePlan.apply`、`summary` 的簽名不變。
- 真實欄位（2.51.14）：`fr.sao.saoui.skills.WeaponCooldownTracker` 的 `private static final Map<String,Long> cooldownEndNanos`、`cooldownStartNanos`。`fr.sao.saoui.hud.ShieldBar` 的 `private static long lockMillis`、`lockReceivedNanos`。
- 擷取：`remainingNanos = end - now`，`≤ 0` 的 key 不收。`startAgoNanos = now - start`，沒有 start 就不收那個 key。`lockRemainingMillis = max(0, lockMillis - (now - lockReceivedNanos)/1_000_000)`。
- 還原（prepare 扣 `elapsedNanos`，apply 再扣本機等待，兩次合計記為 `aged`）：`end = now + max(0, remainingNanos - aged)`、`start = now - startAgoNanos - aged`，剩餘歸零的 key 不寫。`lockMillis = max(0, lockRemainingMillis - aged/1_000_000)`、`lockReceivedNanos = now`。兩張 map 整份換掉，rollback 照現有 `Change` 機制。
- 兩個類別加進 `CONTRACT_CLASSES`，`CONTRACT_FINGERPRINT` 重算。

- [ ] **Step 1: 寫失敗的測試**

```java
@Test void weaponCooldownAgesByElapsedOnly()   // remaining 5s、elapsed 2s、本機等待 0 → end - now 介於 2.9s 與 3.0s
@Test void expiredWeaponKeyDropped()           // remaining 1s、elapsed 2s → map 不含該 key
@Test void shieldLockAges()                    // lockRemainingMillis 800、elapsed 300ms → lockMillis 500，lockReceivedNanos == apply 時的 now
@Test void schemaOneDocumentRefused()          // schema 1 的文件 prepare → IllegalArgumentException("Snapshot contract mismatch")
@Test void timersRollBackOnFailure()           // 讓後面的 Change 失敗 → 兩張 map 與 lockMillis 回到原值
```

- [ ] **Step 2: 跑測試確認失敗**

Run: `.\gradlew.bat :sao-state:test --tests "*TimerSnapshotTest" --no-daemon --console=plain`

- [ ] **Step 3: 實作**，再重算指紋：設 `HANDOFF_SAO_JAR` 指向 `SAOUI-2.51.14.jar` 跑 `:sao-state:test --tests "*ArtifactContractTest"`，從輸出 `CONTRACT_FINGERPRINT SAOUI-2.51.14.jar <值>` 取值更新常數。2.51.13 的 JAR 若還找得到就再跑一次，值不同就在交接檔記錄 2.51.13 不再相容。

- [ ] **Step 4: 跑 `:sao-state:test` 全部通過**（有設 `HANDOFF_SAO_JAR`，`ArtifactContractTest` 不能 skip）。

---

### Task 4: 通用轉送正式模式

**Files:**
- Modify: `relay-bridge/src/client/java/tw/handoff/relay/bridge/PacketLab.java`、`HostBridge.java`（只改 `start` 裡建立 lab 的判斷與 `write` 過濾）
- Modify: `viewer-mod/src/client/java/tw/handoff/viewer/ViewerClient.java`（只改 `LAB` 旗標）
- Test: `relay-bridge/src/test/java/tw/handoff/relay/bridge/GenericModeTest.java`

**Interfaces:**
- Produces：
  - `enum PacketLab.Mode { FIXTURE, PRODUCTION }`
  - `static Mode PacketLab.mode(Minecraft mc)`：`-Dhandoff.generic=true` 回 `PRODUCTION`（不需要 fixture 標記）。`-Dhandoff.packetLab=true` 照舊要 fixture 標記並回 `FIXTURE`。兩個都設丟 `IllegalStateException`。都沒設回 `null`。
  - `void replay(NativePeer peer)` 在 `PRODUCTION` 什麼都不送。`PRODUCTION` 不建 `PacketJournal`。
  - `ViewerClient.LAB` 在 `handoff.generic=true` 時也是 true（viewer 端同一套包裝）。
- `forward` 的過濾不變：本機封包、非 gameplay 通道、聊天都不轉。

- [ ] **Step 1: 寫失敗的測試**

```java
@Test void productionNeedsNoFixtureMarker()      // 只設 handoff.generic → PRODUCTION
@Test void fixtureStillNeedsMarker()             // 只設 handoff.packetLab、沒有標記 → IllegalStateException
@Test void bothFlagsRefused()
@Test void productionNeverReplaysHistory()       // PRODUCTION 的 replay 對假 peer 送出 0 個 frame
@Test void chatAndLauncherStillBlocked()         // forward 的過濾：chat、launcher:* 通道不寫入 channel
```

- [ ] **Step 2: 跑測試確認失敗**，Run: `.\gradlew.bat :relay-bridge:test --tests "*GenericModeTest" --no-daemon --console=plain`
- [ ] **Step 3: 實作**
- [ ] **Step 4: 跑 `:relay-bridge:test`、`:viewer-mod:build` 全部通過**

---

### Task 5: 常駐端加入流程

**Files:**
- Modify: `relay-bridge/src/client/java/tw/handoff/relay/bridge/HostBridge.java`、`RemoteControl.java`、`NativePeer.java`
- Modify: `relay-bridge/build.gradle`（`implementation project(':sao-state')`，jar 一起打包，照 relay-core 的寫法）
- Create: `relay-bridge/src/client/java/tw/handoff/relay/bridge/JoinState.java`
- Test: `relay-bridge/src/test/java/tw/handoff/relay/bridge/JoinStateTest.java`

**Interfaces:**
- Consumes：Task 1 `SaoReplayTable`、Task 2 `ControlWire`／`StateChunks`／`JoinGate`、Task 3 `SaoSnapshot.capture`、Task 4 `PacketLab.Mode`。
- Produces：
  - `final class JoinState`：把下面的順序寫成可以不開遊戲就測的類別，輸入是函式介面（送 frame、送訊息、擷取快照、取 nanoTime），輸出是送出的序列。
  - `NativePeer` 多一個欄位 `final JoinGate gate=new JoinGate()`。

**流程（`HostBridge.snapshot` 在遊戲執行緒、上游暫停讀取時）：**
1. `SnapshotPackets.send`（不變）。
2. 送 `minecraft:register`：raw frame `OpaqueFrames.payload(<play custom payload id>, "minecraft:register", <名稱以 \0 分隔>)`，名稱來自 `ClientPlayNetworking.getSendable()`。packet id 照 `PacketLab.install` 的做法取。
3. 送 `table.replay().frames()`，`gate.hostMissing(replay.missing())`，每個 missing 送 `M:<piece>`。
4. `SaoSnapshot.capture(mc.getClass().getClassLoader(), capturedAt)`。失敗時 `gate.hostMissing({"version"})` 並送 `M:version`，不送 S。
5. 包成 `{"ageNanos":<送出前 nanoTime - capturedAt>,"snapshot":<快照>}`，用 `StateChunks.encode` 送出全部 `S:`。
6. 送 `Q:<gate.nonce()>`，記下送出時間。
7. `peer.live=true`（live 轉送從這裡開始，與 1 到 6 在同一個暫停讀取區段內）。

- `table` 在 `HostBridge.start` 建立。擷取點的 `channelRead` 對每個 `ClientboundCustomPayloadPacket`（非 `DiscardedPayload`）呼叫 `table.observe`，用 `codec.freeze` 取 bytes，不論有沒有 peer。`maxBytes = 4 MiB`。
- `RemoteControl.ready` 只送 `R:0`，不再 `grant`。
- `RemoteControl.dispatch` 新增 `q:`（`gate.pong`）、`K`（`gate.viewerAck()`，`mayGrant()` 就 `grant`，否則送 `E:` 帶 blocker）、`N:`（`gate.viewerFailed`）。`TAKE` 在 `!gate.mayGrant()` 時送 `E:` 帶 blocker，不 `grant`。

- [ ] **Step 1: 寫失敗的測試（JoinStateTest，假的函式介面）**

```java
@Test void orderIsWorldRegisterReplaySnapshotProbeLive()  // 送出序列的種類依序是 world, register, replay..., S..., Q，最後 live=true
@Test void captureFailureSendsMissingVersionAndNoChunks()
@Test void ageIsMeasuredOnHostClockOnly()                 // 假 clock：captured 100、送出前 350 → ageNanos == 250
@Test void tableAndFieldsCapturedInOneGameTask()         // 假 executor 記錄呼叫，table.replay() 與 capture 在同一次 run() 內，重播序列在 S 之前送出
@Test void failedNewViewerKeepsOldOwner()                 // 舊 peer 持有 lease，新 peer 送 N:version → lease.owner 仍是舊 peer
```

- [ ] **Step 2: 跑測試確認失敗**，Run: `.\gradlew.bat :relay-bridge:test --tests "*JoinStateTest" --no-daemon --console=plain`
- [ ] **Step 3: 實作 `JoinState` 並接進 `HostBridge`、`RemoteControl`、`NativePeer`**
- [ ] **Step 4: 跑 `:relay-bridge:test`、`:relay-bridge:build` 全部通過**，用 `jar tf` 確認 jar 內有 `tw/handoff/sao/SaoSnapshot.class` 與 `tw/handoff/relay/core/SaoReplayTable.class`。

---

### Task 6: viewer 套用與 ACK

**Files:**
- Create: `viewer-mod/src/client/java/tw/handoff/viewer/ViewerJoinState.java`
- Modify: `viewer-mod/src/client/java/tw/handoff/viewer/ViewerClient.java`（`receive` 轉給 `ViewerJoinState`）、`viewer-mod/build.gradle`（加 sao-state，打包方式同 relay-core）
- Test: `viewer-mod/src/test/java/tw/handoff/viewer/ViewerJoinStateTest.java`

**Interfaces:**
- Consumes：Task 2 的訊息格式與 `StateChunks.Reassembler(2*1024*1024 + 64*1024)`、Task 3 的 `SaoSnapshot.prepare`／`RestorePlan.apply`。
- Produces：`final class ViewerJoinState { boolean receive(String message, long nowNanos); }`，回傳 true 表示訊息已處理。

**行為：**
- `Q:<n>` 立刻回 `q:<n>`。
- `M:<piece>` 顯示缺塊。
- `S:` 交給 Reassembler，最後一段到時記 `receivedAt`，在遊戲執行緒上 `SaoSnapshot.prepare(loader, snapshot, ageNanos + (now - receivedAt))` 再 `apply()`，成功送 `K`。`prepare` 丟 `IllegalArgumentException` 且訊息含 `contract` 送 `N:version`，其他例外送 `N:snapshot`。Reassembler 例外送 `N:snapshot`。
- 只在 `ClientPlayConnectionEvents.JOIN` 之後處理 `S:`。JOIN 之前收到就送 `N:snapshot`（照協定不會發生，發生代表順序壞了）。
- overlay 文字（`mc.gui.setOverlayMessage`）：缺塊時顯示「觀察中 · 缺：<中文名>」，對照 `version`→「SAO 版本不符」、`saoblessing`→「saoblessing 紀錄超過上限」、`replay-budget`→「重播紀錄超過上限」、`latency`→「延遲過大」、`snapshot`→「狀態快照失敗」、`pending`→「等待狀態同步」。`E:` 帶的 blocker 也用同一張對照表。

- [ ] **Step 1: 寫失敗的測試（假的送出函式與假的 prepare）**

```java
@Test void echoesProbe()                         // receive("HANDOFF2:Q:ab") → 送出 "HANDOFF2:q:ab"
@Test void appliesAfterLastChunkAndAcks()        // 3 段依序 → prepare 被呼叫一次，elapsed == ageNanos + 本機等待，送出 "HANDOFF2:K"
@Test void contractMismatchSendsVersion()        // 假 prepare 丟 "Snapshot contract mismatch" → "HANDOFF2:N:version"
@Test void chunkBeforeJoinFails()                // 還沒 JOIN 收到 S → "HANDOFF2:N:snapshot"，prepare 沒被呼叫
@Test void blockerLabelsAreChinese()             // label("latency") == "延遲過大" 等 6 個
```

- [ ] **Step 2: 跑測試確認失敗**，Run: `.\gradlew.bat :viewer-mod:test --tests "*ViewerJoinStateTest" --no-daemon --console=plain`（viewer-mod 若沒有 test 設定，照 relay-bridge 的 build.gradle 補 JUnit 5）
- [ ] **Step 3: 實作**
- [ ] **Step 4: 跑 `:viewer-mod:test`、`:viewer-mod:build` 通過**

---

### Task 7: 本機 fixture 整合驗收

**Files:**
- Modify: `packet-lab/src/main/java/tw/handoff/lab/LabMod.java`、`LabPayload.java`（假的 SAO 推送與請求回應）
- Modify: `integration-probe/src/client/java/tw/handoff/probe/client/ClientProbe.java`（讀 viewer 端狀態）
- Create: `handoff-evidence/join-state/README.md`（記錄執行指令與結果，這個目錄被 gitignore）

**Interfaces:**
- Consumes：Task 5、Task 6 的 jar，`docs/GENERIC-PACKET-LAB.md` 的私有 Fabric 後端與 fixture 流程。
- 假資料用 `saocore:mux` 子通道 `saogui:main` 的 JSON 操作碼與 `saoblessing:main`，內容是自造的，不放真實 SAO 資料。fixture 不載入真 SAOUI，所以這一步只驗 relay 與 viewer 的流程，不驗 `SaoSnapshot` 的 SAO 欄位（那部分由 Task 3 的單元測試與明天的 Mac 實測負責）。

**驗收（每一項寫進 README，附 log 行）：**
1. 後端在 viewer 加入前推 `INIT`、`MANA_UPDATE`、兩筆 `CHARGE_UPDATE`（一筆只有 value、一筆只有 max）。viewer 加入後 probe 讀到的最後值與常駐端相同。
2. 後端推 `quest_hud` REMOVE `*` 後 viewer 加入，viewer 沒有任務 HUD。
3. viewer 按 F6 在 ACK 之前，標題仍是 SPECTATOR，overlay 有缺塊文字。ACK 之後按 F6 取得控制權。
4. 取得控制權後 viewer 送一個請求，後端回應同時到常駐端與 viewer。
5. 後端在 viewer 加入前推 300 筆 blessing frame，viewer 停在觀看，overlay 顯示「saoblessing 紀錄超過上限」。
6. 後端仍只有 1 個玩家連線（RCON `list`）。

- [ ] **Step 1: 依 `GENERIC-PACKET-LAB.md` 啟動私有後端與常駐 fixture**（在 herdr 旁邊的 pane 跑）
- [ ] **Step 2: 逐項跑 1 到 6，記錄結果**
- [ ] **Step 3: 全部符合才算這份通過**

---

## 驗證

1. 每份照上表的 reviewer 模型開 herdr reviewer 審，通過才算完成。全部通過後主對話跑完整檢查：
   ```powershell
   .\gradlew.bat :relay-core:test :relay-bridge:test :relay-bridge:build :viewer-mod:test :viewer-mod:build :sao-state:build :viewer-bootstrap:test :handoff-service:test :integration-probe:build --no-daemon --console=plain
   ```
2. 整份計畫做完、review 通過而且沒有其他建議，照通用規則開一次 Fable 最後檢查。
3. Mac 常駐加 Windows 操作的實服實測（使用者通知後）。清單照設計文件「驗收」最後一項，碰實服前先用選單問。

## 實作對照（實服實測清單）

| 實測項目 | 實作位置 |
|---|---|
| 技能列 | `SaoReplayTable` 的 `skillbar` key（Task 1）、`SkillStatusClient.allSkills` 欄位快照（既有 `SaoSnapshot`） |
| 魔力條 | `SaoReplayTable` 的 `mana` key（Task 1） |
| 冷卻剩餘 | `SaoSnapshot` 既有 `cooldowns` 與 `CooldownAge`，`ageNanos` 由 `JoinState`（Task 5） |
| 武器冷卻 | `SaoSnapshot` 的 `timers.weapon`（Task 3） |
| 任務 HUD | `SaoReplayTable` 的 `quest:` key（Task 1），套用在 JOIN 之後（Task 6） |
| 裝備、saojob 技能側欄 | 控制權交出後經 `PacketLab.Mode.PRODUCTION` 轉送（Task 4）、`minecraft:register`（Task 5） |
| saoblessing 介面 | `SaoReplayTable` 的 blessing session 紀錄（Task 1） |
| 拒絕交出控制權的畫面 | `JoinGate.blocker`（Task 2）、`RemoteControl` 的 `E:`（Task 5）、`ViewerJoinState` overlay（Task 6） |
