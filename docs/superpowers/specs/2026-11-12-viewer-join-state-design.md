# Viewer 中途加入時的 SAO 狀態同步：設計

日期：2026-11-12。依據是 `CLAUDE_HANDOFF.md`「read-only probe」一節的實服擷取與重送來源調查、`docs/GENERIC-PACKET-LAB.md` 的中途加入實驗，以及 2026-11-12 重新反編譯的 SAOUI 2.51.14 與 saoblessing 1.1.0（反編譯檔放 session scratchpad，不提交）。

## 目的

使用者在 Mac 上常駐一個 SAO 客戶端（唯一的真正伺服器連線），在 Windows 上用自己的原生客戶端接手操作同一個角色。Windows 加入時，畫面與 SAO 介面要跟常駐端一致，才能安全地放技能、看任務、換裝備。

現在的轉送只處理「加入之後」的封包。加入前就送到的 SAO 狀態，viewer 沒有。2026-11-12 實測，客戶端再送一次 `HELLO`，伺服器 4 秒內沒有重送 `INIT`。

使用者的電腦同時跑不動兩個 SAO，所以實際交付與實測一定是跨電腦：Mac 常駐端、Windows 操作端。

## 目標

1. Windows viewer 加入後，以下狀態與常駐端一致：Minecraft 世界（已有 `SnapshotPackets`）、SAOUI 技能列與等級、魔力或體力條、冷卻、武器冷卻、護盾、任務 HUD、saoblessing 的祝福欄位與選擇介面。A 類狀態（裝備、隊伍、saojob 技能目錄等）不在交接檢查內，viewer 拿到控制權後開介面時由 SAO 自己向伺服器要，常駐端本身也是開過介面才載入。（使用者 2026-11-12 選的「控制權交出後才同步」）
2. 任何一塊對不上就不交出控制權，viewer 只能觀看，畫面標出缺的那一塊。
3. Mac 與 Windows 在同一個區網可以直連。之後經 VPS 中繼時，不用改狀態同步的部分。

## 不做的事

- 不逐項重寫技能、挖掘、聊天的動作轉接。使用者已否決這種白名單架構。
- 不重播完整封包歷史。實驗證明歷史重播會讓過期效果重新計時，也會重播別人的回應（`GENERIC-PACKET-LAB.md`「中途加入」）。
- 不碰啟動器驗證，不複製或偽造 token，viewer 不登入 SAO。
- 這一份不做 VPS 中繼本身，只留介面。

## 失敗的定義（技術上能跑也算失敗）

- viewer 拿到控制權時，狀態有一塊是舊的或缺的，而畫面沒有標出來。
- 冷卻、武器冷卻或護盾鎖定在 viewer 端重新從頭計時，或比常駐端短。
- 舊 viewer 在新 viewer 準備好之前就被撤銷，導致兩邊都不能操作。
- 對 SAO 伺服器送出常駐端自己不會送的封包。viewer 透過通用轉送送出的請求，是 viewer 原生 mod 本來就會送的，不算在內。

## 狀態分類與做法

### A. 客戶端可以要求重送的狀態：拿到控制權後才同步

| 狀態 | 誰送請求 | 何時 |
|---|---|---|
| saojob 技能目錄 | `SkillsData.requestRefresh` → `SKILLS_DATA_REQUEST` | 開技能側欄 |
| companionsmod | `DATA_REQUEST` | 開介面、`refreshSilent` |
| altarmod | `DATA_REQUEST` | 祭壇介面輪詢 |
| SAOUI 裝備、隊伍 | `OPEN`、`PRESETS`、`LIST` | 開介面 |
| saocharacters 表情目錄 | `CATALOG_REQUEST` | 世界內目錄空的就每 100 tick 自動送 |
| SAOUI 怪物血條 | `MOB_HB_RESYNC` | `SaoUiMessaging.requestResync` |

viewer 跑的是原生 SAO mod，請求經通用轉送到伺服器，回應經通用 S2C 轉送回來。這是設計者的判斷，前提是下面的「前置條件」成立。怪物血條是 `MOB_HB_ADD`、`MOB_HB_TICK` 這類增量，不適合重播，所以歸在這一類。

### B. 只有伺服器推送的狀態：最後一筆重播加欄位快照（使用者選的）

1. **最後一筆重播。** 常駐端在 S2C 擷取點，對下表每一個 key 只保留最新一筆原始 frame。viewer 加入後依原本收到的順序重播。表外的推送訊息不重播。這張表跟 SAO 版本綁在一起，跟契約指紋一起維護。下表是設計者整理的，依據是 2.51.14 與 saoblessing 1.1.0 的客戶端程式。

| 通道 | 操作 | key | 刪除規則 |
|---|---|---|---|
| `saocore:mux` | `:hello` 公告 | 單一 | 無 |
| `saogui:main` | `INIT`、`USE_SKILL_OK` | 「技能列」共用一個 key | 無。兩者都會整份換掉 `allSkills` |
| `saogui:main` | `INIT`、`MANA_UPDATE` | 「魔力條」共用一個 key | 無。兩者都帶目前值與上限 |
| `saogui:main` | `LEVEL_UP` | 併入「技能列」之後的等級，只留最後一筆 | 無 |
| `saogui:main` | `SHIELD_UPDATE` | 單一 | 無。`lock` 是時間值，見第 2 點 |
| `saogui:main` | `SKILL_VARIANT`、`SETTINGS`、`UI_STYLE`、`PROJECTION_LIST` | 各自單一 | 無 |
| `capuodev:quest_hud` | `packetType=0` | `hudId` | `packetType=1` 加 `hudId="*"` 清掉全部，加單一 `hudId` 刪那一筆 |
| `saoblessing:main` | 全部操作 | 見下 | 見下 |

`saogui:main` 不重播的操作：`ITEM_SKILL_COOLDOWN`（只有時間值，走第 2 點）、`DISCOVERY_REGION`（一次性的發現提示）、`ASK`（伺服器的提問，重播會讓 viewer 再回答一次）、`SERVER_HELLO`（重播會讓 viewer 再送簽章 HELLO）、`MOB_HB_*`（A 類）。

saoblessing 的 `CHARGE_UPDATE`、`LEVEL_UPDATE` 是部分更新（欄位有才改），只留最後一筆會掉欄位，`SESSION_START`、`SESSION_END` 會清掉其他狀態。所以這個通道不用最後一筆，改成保留最近一次 `SESSION_START` 或 `SESSION_END` 之後的全部 frame，依序重播，上限 256 筆，超過就把 saoblessing 標成補不到。這個通道的 payload 沒有時間欄位，依序重播不會重新計時。`OFFER_OPEN` 之後若有 `OFFER_PICK_OK` 或 `OFFER_PICK_ERROR`，依序重播的結果也是關閉。`SERVER_HELLO` 不重播。（設計者的判斷）

**key 在線上的位置。** `saocore:mux` 的 payload 是 1 byte 子通道名長度、子通道名（UTF-8）、再接 JSON，操作碼在 JSON 的 `op`，`:hello` 的子通道名就是 `:hello`。`saoblessing:main` 是直接的 custom payload，內容是 UTF-8 JSON，操作碼在 `op`。`capuodev:quest_hud` 的二進位格式開頭是 1 byte `packetType`、接著 varint 長度的 UTF-8 `hudId`。它有兩種線上形式，伺服器在 `:hello` 有列這個名字時，SAOUI 會撤掉舊的直接通道（`LegacyChannels`），改經 `saocore:mux` 的同名子通道送來，所以擷取點兩種都要認。

2. **欄位快照（現有 `sao-state`）用在時間值與技能列欄位。** 冷卻這類「剩幾秒」的值，重播會重新從頭計時，所以由常駐端在送出前一刻擷取，扣掉擷取到套用的經過時間後寫回 viewer。版本靠 `SaoSchema.CONTRACT_FINGERPRINT` 檢查，對不上就算補不到。
   - 已在 schema：`SkillStatusClient` 的 `cooldowns`、`durations`、`slot`、`skillName`、`level`、`allSkills`。
   - 要新增（設計者的判斷，延續使用者選的「兩種一起用」）：`WeaponCooldownTracker` 的剩餘時間（來自 `ITEM_SKILL_COOLDOWN` 的 `remainingMs`、`durationMs`），`ShieldBar` 的鎖定剩餘時間（`SHIELD_UPDATE` 的 `lock`，客戶端用收到時的 `nanoTime` 倒數）。兩個類別都要加進 `CONTRACT_CLASSES`。
   - 任務 HUD 的 payload 沒有倒數欄位，`distanceText` 是文字，動畫的 `durationTicks` 只是出現動畫，重播會重跑一次，只影響畫面。`trackerJson` 的內容沒查，未確認。
3. **技能列以欄位快照為準。** 三次實服擷取，常駐端自己的 `allSkills` 都是 0，原因是 `SpellOverlay` 在冷卻結束 5 秒加淡出後會清空它。目標是「跟常駐端一致」，所以 viewer 的技能列寫成常駐端當下的欄位值，不要求一定要有 `INIT` frame。重播「技能列」key 只是為了讓 `INIT` 帶的魔力與等級也到位，之後的欄位快照會蓋過 `allSkills`、`slot`、`skillName`、`level`。下一次任一端放技能，伺服器回的 `USE_SKILL_OK` 會整份換掉兩邊的 `allSkills`。（設計者的判斷。「伺服器的 `USE_SKILL_OK` 一定帶 `allSkills`」是讀客戶端程式的推論，Mac 實測時確認）
4. **套用順序**：viewer 的 `ClientPlayConnectionEvents.JOIN` 完成之後才重播與套用快照。SAO 在 JOIN 時會清空 `Mux` 的 `SERVED`、任務 HUD 與 `ServerClock`，早於 JOIN 套用會被清掉。先重播，再寫欄位快照，因為 `INIT` 會把不在清單裡的冷卻刪掉並補 0。

## 前置條件

1. **通用轉送升成正式路徑。** 現在 viewer 送來的封包只有帶控制前綴的 brand payload 會被處理（`RemoteControl.input`），通用 C2S 轉送只在 `PacketLab`（`handoff.packetLab=true` 加 fixture 標記）裡，S2C 正式路徑則是用 host 的 codec 重新編碼。A 類要能運作，`PacketLab` 的不透明 frame 轉送要先升成正式模式，並把只允許私有 fixture 的檢查換成正式的安全檢查。
2. **viewer 要知道伺服器可收的頻道。** `NativePeer` 在 configuration 階段只送 registry、tags 與 feature flags，沒有送 `minecraft:register`，所以 viewer 端 `ClientPlayNetworking.canSend` 對 SAO 的通道都是 false，請求在 viewer 端就被丟掉。頻道清單在 viewer 連上時從常駐端當下的 Fabric 可送清單（`ClientPlayNetworking.getSendable()`）產生，是現況不是歷史，所以 relay 在常駐端進服後才附加也拿得到。（設計者的判斷，實作時確認 Fabric API 0.119.4 的這個方法）
3. **`:hello` 要重播。** SAOUI 的 `Mux.isOpen` 要求 `SERVED` 含有子通道，`SERVED` 只在收到 `:hello` 時設定，JOIN 會清空，所以 `:hello` 在 B 類表裡。

`ServerClock` 不需要 relay 代答。viewer 重播 `:hello` 之後會想送一次 `CLOCK`，觀看中這個封包會被丟掉。5 秒沒有回應，viewer 會自己對 SAO 網站送一次 HTTP HEAD 取時間，這是 SAO 本來的行為，不經過 relay。拿到控制權後，viewer 每 10 分鐘重問一次時，`CLOCK` 會經通用轉送送出，回應同時到常駐端，但 `ServerClock.received` 只接受 `t0` 等於自己 `askedAt` 的回應，常駐端會忽略。`FrameTap` 在 SAOUI 裡只有 `ServerClock` 這一個 listener。

## 跨電腦

- 傳輸沿用 `handoff-service` 的配對與憑證 pinning TLS。區網直連是第一個目標。VPS 中繼之後只換傳輸層，狀態同步的訊息格式不變。
- 兩台的 `System.nanoTime` 不能互減。快照只帶「常駐端擷取到送出經過多久」。viewer 收到時再加上傳輸時間的上限，這個上限用 TLS 連線上的往返時間量，取一半再加安全邊際。現有的 `CooldownAge` 與 `RestorePlan` 已經處理本機等待的部分。（設計者的判斷）
- 傳輸時間上限量不到，或超過設定值，就當成補不到。

## 控制權順序

1. viewer 完成 Minecraft 世界快照後是觀看狀態。
2. 等 viewer 的 JOIN 完成、重播完、欄位快照套用成功，viewer 送 ACK 給常駐端。
3. 常駐端收到 ACK 才把控制權交給新 viewer。在那之前，原本有控制權的一端（常駐端或舊 viewer）照舊可以操作，不撤銷。現在的 `HostBridge.ready` 在世界快照送完就直接 `grant`，要改成等 ACK。
4. 任何一步失敗，viewer 停在觀看狀態，畫面標出缺哪一塊，例如「SAO 版本不符」「saoblessing 紀錄超過上限」「時間差超過上限」。

（3 是設計者的判斷，延續 `CLAUDE_HANDOFF.md` 建議順序第 4 點）

觀看中的 viewer 不對伺服器送出任何封包，照現在 `RemoteControl` 只處理控制前綴的做法。A 類的請求在拿到控制權之後才經通用轉送送出。

## 已知要實測的風險

- 常駐端收到不是自己要的回應（例如 viewer 開技能側欄引起的 `SKILLS_DATA_RESPONSE`）。saojob 的處理是整份取代，推論無害，其他 mod 要實服確認。
- `INIT` 重播後，`SpellOverlay` 在冷卻全為 0 時，5 秒加淡出之後會清空 `allSkills`。這是 SAO 本來的行為，常駐端也一樣，不算失敗。
- viewer 拿到控制權後，簽章訊息的 `ts` 用 viewer 自己的 `ServerTime`。如果 viewer 只拿到網站時間（誤差約 500 ms），伺服器收不收沒驗證。
- 伺服器的 `USE_SKILL_OK` 是否一定帶 `allSkills`，只讀過客戶端程式。

## 留給實作者決定

- 最後一筆表的資料結構、上限與記憶體預算（參考 journal 的 8192 frames／8MiB）。
- ACK 與「缺哪一塊」的訊息格式。
- 畫面怎麼標示缺的那一塊。
- 往返時間要量幾次、安全邊際取多少。

## 驗收

- 本機單元測試：最後一筆表的取代與刪除規則（含 `quest_hud` 兩種線上形式、saoblessing 的 session 紀錄與上限）、重播順序、時間值扣除（含武器冷卻與護盾鎖定）、ACK 前不交出控制權。
- Windows 本機：只能跑一個 SAO，所以用私有 Fabric 後端加測試 mod 模擬「推送整份取代」「部分更新」「請求回應」三類訊息。
- Mac 常駐加 Windows 操作的實服實測（使用者 2026-11-12 說明天可以測）：技能列、魔力條、冷卻剩餘、武器冷卻、任務 HUD、裝備、saojob 技能側欄、saoblessing 介面、拒絕交出控制權的畫面。附加或送封包前照 `CLAUDE.md` 先問。
