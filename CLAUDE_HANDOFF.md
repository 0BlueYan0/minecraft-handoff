# Minecraft 無縫接力：Claude 交接

交接日期：2026-11-11（Asia/Taipei）。使用者明確要求把專案放到 `C:\myspace`，由 Claude 接手。

## 目前狀態（2026-11-12 更新）

這一段比下面各段新，下面寫的「未提交」「待伺服器恢復」「正常預設路徑仍是 thin」都已過時。

- 分支 `feature/p0-diagnostics`，`b8c4fb5`..`996eb5e` 全部已 commit，**沒有 push**（使用者選「測完再 push」）。
- 實服可以連，使用者經常在玩。碰實服前照 `CLAUDE.md` 先用選單問。
- 已做完、只在本機 fixture（真 SAOUI、私有後端）驗過的：

  | 項目 | commit | 證據 |
  |---|---|---|
  | viewer 中途加入：常駐端重播 SAO 推送狀態（`SaoReplayTable`）與 `sao-state` 欄位快照，viewer 在 JOIN 後套用、回 ACK，`JoinGate` 通過才交出控制權 | `a4a5fa7`..`3b42254` | `handoff-evidence/join-state/README.md`，規劃 `docs/superpowers/plans/2026-11-12-viewer-join-state.md` |
  | 通用轉送（`PacketLab.Mode.PRODUCTION`）由啟動腳本的 `--generic` 打開，Mac `HANDOFF_GENERIC=0`、Windows `-NoGeneric` 關掉 | `c8f704b` | `README-t8.md` |
  | 常駐端沒人操作時每 5 分鐘自動前右後左各走 2 秒（`IdleWalkPlan`、`IdleWalker`），`--idle-walk`，Mac `HANDOFF_IDLE_WALK=0` 關掉 | `996eb5e` | `README-walk.md`，規劃 `docs/superpowers/plans/2026-11-12-host-idle-walk.md` |

- 還沒驗的：Mac 實機、SAO 實服接管、VPS 中繼（設計時排除）。實服要看的項目列在 `.herdr-work/handoff.md`。
- 已知限制：SAOUI 用自己的暫停選單 `fr.sao.triggersmod.screen.pausemenu.GameMenuScreen`（活動中是 `fr.sao.backrooms.BackroomsPauseScreen`），不是原版 `PauseScreen`。2026-11-12 起 `RemoteControl.grant` 用完整類別名（`PauseScreens`）放行這兩個，generic 模式不關畫面，thin 模式照舊關掉。只在本機 fixture 驗過（`README-pause.md`），Mac 實機與 SAO 實服沒驗。SAOUI 改類別名時會退回「Close the host screen」，要重新比對。
- 每一輪的 worker、reviewer session id 與結果在 `.herdr-work/ledger.md`。

## 先讀這一段（2026-11-11 原交接）

- **新的工作位置：`C:\myspace\minecraft-handoff`。** 舊副本保留在 `C:\Users\user\Documents\Codex\2026-11-11\new-chat\outputs\minecraft-handoff`，後續請以新位置繼續，避免兩份分叉。
- 分支 `feature/p0-diagnostics`；當時最後提交 `aad42f7`（generic packet feasibility lab）。當時 SAO snapshot 還沒提交，現在已提交（見上一段）。
- 使用者當時告知：「現在伺服器也進不去暫時測不了」。之後實服已能連線，見上一段。
- 使用者已多次要求持續解決問題，不要反覆請求例行批准。範圍已包含實作、自製模組／代理及獨立測試；需要使用者時，提出具體且最少的操作步驟。
- 截止交接，僅關閉了我們的私有測試遊戲 PID72524、私有測試伺服器。**沒有關閉使用者原本的遊戲**。最後曾觀察到原遊戲 PID61084，但這是歷史資料，之後一律重新確認，不能照 PID 盲目操作。

## 使用者的真正目標

每台電腦執行自己的原生 Minecraft 客戶端、本機渲染與模組；在不同電腦接管同一名玩家。真正 SAO 伺服器從頭到尾只看到同一條玩家連線，不能第二台再正式登入相同帳號。

Mac M1 / 8GB 可作常駐端；Windows 是操作端。另有香港6C12G、台灣1C1G VPS及GCP抵免，但目前架構保留完整原生 Minecraft，沒有證據支援1C1G。

最終交付必須包含 Mac 常駐端及 Windows 操作端如何啟動。目前 `delivery/` 只是未完成版本的啟動腳本草稿，不能當已驗收成品交付。

## 環境與建置

- Minecraft1.21.4、Fabric Loader0.20.1、Fabric API0.119.4+1.21.4。
- SAOUI：2026-11-11 22:24 啟動器自動從2.51.13更新成2.51.14（`4e31858d…`）。sao-state不再比對整包hash，改比對`SaoSchema.CONTRACT_CLASSES`（本模組讀寫／建構的23個類別）的bytecode指紋`CONTRACT_FINGERPRINT`；2.51.13與2.51.14算出相同值。SAO更新只要這些類別沒變就不用改程式；有變會擋下，要重新比對後更新常數。
- Windows 原始實例：`C:\Users\user\AppData\Roaming\.Triggers Client\instances\Sword Art Online`。
- 原啟動器：`C:\Users\user\AppData\Local\Triggers Client\triggers-client.exe`。
- Mac 歷史實例路徑：`~/Library/Application Support/Triggers Client/instances/Sword Art Online`。Mac 先前版本2.51.10，必須重新確認；state 模組接受的是契約類別指紋相同的版本（見上一條）。
- 可用完整 JDK21（目錄雖叫 jre）：`C:\Users\user\AppData\Roaming\.Triggers Client\runtime\jre-21.0.7-windows-x64`。有 javac/jar/keytool/jdk.attach。
- PATH 預設 Java/Gradle 可能錯；使用專案 Gradle Wrapper8.12、Loom1.10.5。

```powershell
Set-Location -LiteralPath 'C:\myspace\minecraft-handoff'
$env:JAVA_HOME = 'C:\Users\user\AppData\Roaming\.Triggers Client\runtime\jre-21.0.7-windows-x64'
$env:HANDOFF_SAO_JAR = 'C:\Users\user\AppData\Roaming\.Triggers Client\instances\Sword Art Online\mods\SAOUI-2.51.13.jar'
.\gradlew.bat :sao-state:build :viewer-bootstrap:test :integration-probe:build --no-daemon --console=plain
```

`HANDOFF_SAO_JAR` 只供離線 bytecode contract 測試。未設定時該測試會 skip；交接前有設定，實際通過。

## 已排除的錯誤方向：不要再重試

原啟動器會在啟動時移除管理目錄內的額外 JAR。舊0.1.1方案把原 JVM參數複製後重開遊戲，雖載入附加模組，實服回應 `Launcher validation failed: Invalid token`。原遊戲結束時，啟動器會釋放 launch slot；新程序沒有同樣的啟動器登記。

因此保留原啟動器與原本已驗證的 JVM，以 Attach agent 加入自己的程式。**不要複製／重播 launcher token、偽造驗證成功、關掉原遊戲改用舊參數重開，也不要直接修改官方驗證模組。**

P0 `runtime-agent0.1.2` 曾在真實 SAO 遊戲 PID68948附加，原 PID與已通過的驗證維持，觀察約20分鐘、disconnects0。那是歷史驗證，該程序已不存在；它只證明 observer attach，不是接力成功。

不要完整列印 JVM啟動參數、launcher驗證值或原登入日誌。Viewer 只能連自己的 loopback入口，藉獨立配對 TLS連常駐端；正常 viewer使用公開角色name/UUID，accessToken為本機占位0，從不第二次登入 SAO。

## 架構演進與目前結論

第一版 native frontend 已能從常駐遊戲擷取世界快照，建立私人 Minecraft入口，讓新客戶端中途載入。起初輸入轉送尚未完成，所以使用者觀察到：Host聊天有效、Client聊天無效、Client挖掘只是本機假象、Host改方塊可傳到Client。

之後做了「thin viewer＋Host代做動作」實驗：取消操作端物理／挖掘、用按鍵frame讓Host執行，再逐項映射聊天／背包。使用者指出沒有揮手動畫，也擔心未知 SAO 行為。**使用者已明確否決這種逐項動作白名單作為最終架構。**

已驗證替代方向：正常原生操作端保留 Minecraft/模組行為，通用轉送其遊戲封包，原Host只保有唯一真正上游傳輸與必要連線／驗證責任。連線管理、確認序號、簽章聊天等仍需協調，不能盲目轉送所有封包。

請讀 `docs/GENERIC-PACKET-LAB.md`。實驗成果：

| 項目 | 證據 |
|---|---|
| 未寫專用handler的自訂 payload | Host沒有測試mod/codec，仍讓`0001ff007f802a`原樣往返Fabric後端與操作端 |
| 原生攻擊／移動／挖掘 | swinging=true，移動到後端；挖掘將後端stone變air，RCON驗證通過 |
| A→B接管 | A變觀察、B可送出，後端仍1玩家 |
| 完整值訊息重播 | 新A/B成功取得加入前STATE37；修正後新程序另恢復STATE88 |
| 全歷史重播的反例 | 已過期10秒效果重新計時；A純本機值5在B仍0；B還重播了A舊echo |

結論：**通用live轉送可行；journal不是任意mod的完整現在狀態快照。** 後續應補加入時的現在狀態與重同步，不再逐個重寫技能操作。

實驗 code `PacketLab` 仍需要 `handoff.packetLab=true`＋owned fixture marker；**尚未提升為實服 production generic mode**。正常預設路徑仍保留舊thin實驗，不能忘記這個差異。

## 模組地圖

| 模組 | 責任／狀態 |
|---|---|
| 根目錄src | 最早P0 Fabric診斷、控制模型 |
| runtime-agent | 0.1.2 observer、JDK Attach，實服歷史驗證通過 |
| launcher-helper | 舊重啟方法的歷史程式，實服Invalid token，不再採用 |
| relay-core | lifecycle、epoch/sequence控制權、容量限制、網路目的地限制、輸入佇列、opaque frame與journal |
| relay-bridge | remapped native agent、Minecraft登入/config/快照、增量轉送；含未完成thin控制與guarded generic lab |
| viewer-bootstrap | 從合法安裝metadata組乾淨啟動參數，隔離可寫資料、只複製mod/資源，不複製帳號／cookie／token |
| viewer-mod | loopback目的地guard、控制權UI、舊thin mixin、generic lab envelope |
| handoff-service | 配對及憑證pinning TLS sidecar，停止sidecar不殺原遊戲 |
| packet-lab | 獨立測試mod，正式runtime不能帶入 |
| integration-probe | 只對marked私有fixture執行應用API的測試agent，不能交付為正式操作工具 |
| sao-state | SAO目前資料快照／本機還原API（含武器冷卻與護盾鎖定時間），以及獨立read-only Attach probe |

## 最新工作：SAO state snapshot

主檔：`sao-state/src/main/java/tw/handoff/sao/{SaoSchema,SaoSnapshot,StateValues,CooldownAge}.java`。

`SaoSchema` 明確限制2.51.13的欄位與資料record，避免遍歷任意物件圖：

- `fr.sao.net.Mux`：SERVED、sendable能力資訊。
- `SkillStatusClient`：slot、skillName、level、allSkills、cooldowns、durations。
- `EquipmentClient`：state、list/listSlot/lists、presets、listBatch、cursorOps。
- `PartyClient`：state record。
- `QuestHudStore`：從當前HUD取語意欄位，重建21-component `QuestHudPayload`／20-argument `QuestHudState`，不複製動畫、cached Component等物件。

`StateValues` 只處理白名單record/enum、有限集合及primitive；深度16、節點20000、字串131072、集合4096。snapshot整體2MiB。直接讀record欄位，避開Equipment.State.grade/playerClass會翻譯文字的accessor。

還原先完整驗證／解碼，再回傳一次性RestorePlan；apply有資料rollback。null只允許根欄位Mux.sendable、equipment.state/presets，以及Slot.item等已知可空位置。會扣掉來源快照elapsed、驗證／準備時間及plan等候apply時間；不拿來源VM的absolute lastTickNanos直接塞到另一個VM。

**未完成：** Mac↔Windows monotonic clock校準、快照網路傳送、Peer的SAO bootstrap ACK／grant前屏障、live增量與快照一致性、其他SAO系統（朋友、公會、通行證、副本、mana等）、party HUD衍生快取。不要只因這4組欄位round-trip就宣稱完整角色狀態可搬移。

### 真正SAO類別的本機測試

原生載入原整合包全部mod，使用獨立本機Vanilla世界；只有可選外觀資源包被略過，沒有複製或偽造正式token。

- fixture：舊scratch `work/sao-state-re/fullmods-fixture`，測試PID72524，已關閉。
- 正常初始化共載入1856個SAOUI classes、0 failed。能進本機世界。
- read-only capture初值：mux0、skills0、cooldowns0、questHUD0、equipmentReady=false。
- **僅在這個marked本機fixture**，`SaoFixtureRoundtrip`寫入自製假資料後，真正vendor record/constructor還原成功：skills1、cooldownAged=true、equipment=true、partyMembers1、questHUD1。
- 後續read-only capture確認裝備／隊伍／任務仍在、cooldown歸零。技能allSkills稍後變0。
- 已查明`SpellOverlay.onHudRender`會在冷卻結束＋等待5秒＋淡出後清空allSkills；這張map是短暫顯示列表，**不能把它當完整角色技能目錄**。不要誤診為搬移必然失敗，也不要假裝已恢復完整解鎖技能。實服HELLO/INIT與其他狀態來源還要查。
- UI動畫／介面呈現沒有完整驗收；測的是實際類別資料與constructor，並非SAO實服。

### read-only probe

JAR：`sao-state/build/libs/sao-state-0.3.0.jar`。入口`tw.handoff.sao.probe.StateProbeMain`。

```powershell
& "$env:JAVA_HOME\bin\java.exe" --add-modules jdk.attach `
  -jar '.\sao-state\build\libs\sao-state-0.3.0.jar' `
  --pid <重新確認的原遊戲PID> `
  --game-dir 'C:\Users\user\AppData\Roaming\.Triggers Client\instances\Sword Art Online' `
  --out 'C:\myspace\minecraft-handoff\work\live-state-新的檔名.json'
```

先建立`work`目錄；輸出必須是實例之外的新檔。Console只印聚合counts，檔案包含遊戲狀態，留本機，不要提交或公開。該入口只capture：不開relay、不呼叫還原、不控制角色、不送伺服器封包。

NativeCapture會檢查same-user/PID、實際gameDirectory、player/level非null、精確SAO artifact hash。從原MC Executor排程。最新agent參數為4個base64欄位：status、gameDir、output、該次immutable probe JAR；每次使用當次image建立child loader，避免之後更新工具卻一直跑首次JAR。

**2026-11-11/12 實服只讀擷取已完成（Claude）。** 遊戲PID79996，每次擷取前後都正常回應。檔案在ignored `work/live-state-20261111-*.json`，含角色資料，不提交。

| 時機 | 結果 |
|---|---|
| 大廳排隊（baseline2） | mux30、其他0、equipment未載入 |
| 世界內、開過裝備／技能／任務畫面（world5） | mux100、allSkills0、cooldowns2、questHUD2（羅盤＋文字）、equipment已載入（12格、19項stats、6組lists、presets）、party無隊員 |

- 第一次世界內擷取失敗：`quests.targetAngleDegrees`是`Float.NaN`（SAO預設值，代表無目標方向）。`StateValues`改成非有限float/double以字串`"NaN"`/`"Infinity"`/`"-Infinity"`保存，只有浮點欄位接受。冷卻／持續時間若非有限，擷取時就以`skills.cooldowns: non-finite timer`失敗（還原時`CooldownAge`本來就拒絕）。
- 遊戲JVM會保留第一次附加時的`StateProbeAgent`類別（推論，與現象一致），舊agentmain只寫例外類別名。所以`NativeCapture.capture`自己寫失敗訊息並正常返回；訊息帶欄位路徑，經`StateProbeAgent.detail`清洗（只留`[A-Za-z0-9_.$#:, -]`、300字）。
- `mux.sendable`在世界內仍是null：`Mux.hello`只在HELLO帶`outgoing`陣列時設定，實服HELLO沒帶；SERVED有100個，HELLO確實收到。
- SAO有客戶端要求重送狀態的封包（只查了97個選定classes）：Equipment `OPEN`/`PRESETS`/`LIST`、Party `OPEN`、`SaoUiMessaging.requestResync`送簽章的`MOB_HB_RESYNC`。技能與任務HUD在這97個classes裡沒找到重送封包，不代表整個jar沒有。這支持「viewer加入時請伺服器重送」的方向，但簽章訊息viewer能否送出未查。
- **2026-11-12 全jar查重送來源（Vineflower反編譯整個2.51.14，1057個top-level檔，放session scratchpad，不提交）。** 只看客戶端，伺服器怎麼回應看不到，以下「會重送」都是推論。
  - 技能：`saogui:main`的`INIT`帶level、mana或stamina、`skills`（slot→技能名，最多64，略過slot7），`handleInitPacket`整個換掉`allSkills`並補cooldown鍵。客戶端只在收到伺服器`SERVER_HELLO`時回簽章的`HELLO`（caps `MOB_HB_TICKS,ASK`），客戶端沒有主動要`INIT`的操作。HELLO後伺服器是否回INIT沒驗證。簽章是jar內常數HMAC加玩家UUID與`ServerTime`，同一個jar的viewer算得出相同格式，伺服器收不收沒驗證。
  - 任務HUD：只有伺服器→客戶端的`capuodev:quest_hud`。客戶端唯一的任務C2S是`capuodev:quest_menu_action`的START，**沒有重送要求**。`QuestTrackerClient`在`ClientPlayConnectionEvents.JOIN`與`DISCONNECT`都呼叫`QuestHudStore.clear()`，所以viewer的快照還原一定要在JOIN之後套用，否則會被清掉。
  - 2026-11-12 00:35 同一個PID79996再做一次只讀擷取（`work/live-state-20261112-003550.json`），擷取後遊戲有回應：level4、slot2、`allSkills`0、cooldowns3（剩餘時間都是0）、durations0、questHUD2、equipment已載入。INIT會對每個技能`putIfAbsent(name,0.0)`，並刪掉不在清單裡且已歸零的cooldown，所以這3個cooldown鍵**推論**是最近一次INIT的技能加上用過的技能，只能當作技能清單的下限，不是完整清單。要拿完整清單就得讓伺服器重送INIT，或在INIT剛到時先擷取。
  - **2026-11-12 00:52 對PID79996送一次HELLO（使用者要求「送 HELLO 測一次」）。** 一次性agent放在session scratchpad不進repo，呼叫SAO自己的`SaoUiMessaging.sendHello()`，送出前與送出後0.25/0.5/1/2/4秒各讀一次`SkillStatusClient`。Sonnet審PASS（`.herdr-work/hello/review-1.md`），4條建議修完由主對話檢查。結果（`work/hello-init-20261112-005242.txt`）是6次取樣都相同：level5、slot2、skillName非空、allSkills0、cooldown鍵3。**觀察：4秒內伺服器沒有回INIT。** 遊戲log 00:51到00:54沒有斷線、被踢或錯誤，遊戲有回應。推論（未驗證）：伺服器只在加入或特定事件時送INIT，或同一條連線的第二次HELLO被忽略。所以viewer中途加入時拿不到完整技能清單，要靠常駐端在INIT到達時擷取並保存，或另外找伺服器會回INIT的事件。同一個JVM已設`tw.handoff.hello.sent`，這支agent不會再送。
  - **完整技能目錄有可重送的來源，但不在SAOUI，在`saojob-1.1.3.jar`。** 遊戲log（`[TriggersShopClient] Packet sortant/entrant`，SAO自己把封包印到STDOUT）顯示00:06與00:58各送一次`SKILLS_DATA_REQUEST`，1秒內收到`SKILLS_DATA_RESPONSE`，欄位有`hasClass`、`className`、`classLevel`、`availablePoints`、`resetPoints`、`resetRefund`、`competences`、`slots`、`stats`等。反編譯（session scratchpad，不提交）：通道`saojob:main`，`MapSenders.sendSkillsDataRequest()`簽章送出，呼叫者只有`SkillsData.requestRefresh()`，由`SkillsSidebarScreen`開啟時觸發。回應由`SkillsHandler.handleSkillsData`交給`SkillsData.applySnapshot`，整份換掉`skills`與`competenceById`。所以viewer開技能側欄就會向伺服器重新要一次完整資料，這是遊戲本來的操作，不用自己組封包。另外有`SKILL_TREE_REQUEST`（技能樹）。
  - 這帶出兩件事。第一，`sao-state`的schema沒有包含`saojob`的`SkillsData`，目前的快照漏了這一塊。第二，SAOUI的`SkillStatusClient.allSkills`（技能列配置）仍然只有INIT會送，伺服器什麼時候送INIT，log裡看不到，因為SAOUI收INIT時不寫log。
  - **其他SAO mod的重送來源（2026-11-12，Python掃類別檔字串＋反編譯4個mod，放scratchpad不提交）。** 「會重送」都是讀客戶端碼的推論，伺服器端行為只有saojob在log裡看到。

    | mod | 通道 | 客戶端可要求重送 | 觸發時機 |
    |---|---|---|---|
    | saojob | `saojob:main` | `SKILLS_DATA_REQUEST`（log已看到回應） | 開技能側欄 |
    | companionsmod | `companions:main` | `DATA_REQUEST` | 開介面`requestData`與`refreshSilent` |
    | altarmod | `altar:main` | `DATA_REQUEST`（帶altarId） | 祭壇介面輪詢 |
    | sao-dungeons-companion | `saodungeons:region` | 有`REQUEST_STATE`常數，呼叫者沒查 | JOIN時送HELLO，區域編輯用 |
    | saoblessing | `saoblessing`的MessagingManager | 沒有，只有回應`SERVER_HELLO`的HELLO，`OFFER_OPEN`是伺服器推送 | 同SAOUI的INIT |

    | fracturedwar | `war:main` | `MAIN_MENU_REQUEST`等約20種`*_REQUEST` | 開選單時`requestMainMenu`，HELLO只回應`SERVER_HELLO` |
    | z-plugin-companion（saocharacters） | 多個saocharacters通道 | 表情`CATALOG_REQUEST`：世界內每100 tick檢查一次，目錄空的就自動要，JOIN與DISCONNECT會清空。角色選擇畫面每50 tick送`ROSTER_REQUEST`。選單`OPEN` | viewer加入後自動補，不用額外處理 |
    | zap-ui-companion | `zapui:open`、`zapui:update`、`zapui:action` | 沒有，open與update是伺服器推送，action是玩家操作 | 伺服器推送的介面 |
    | sao-ui-lib | 無自己的通道 | 不適用，是altarmod與companionsmod共用的`JsonChannel`程式庫 | |
    | zap-dialogue-client | 沒查到 | Vineflower沒輸出原始碼，只從字串看到`DIALOGUE_OPEN`、`NOTIFICATION_OPEN`、`OPTION_OPEN`，推論是伺服器推送 | |

    relmod、saolabyrinth-companion、sao-post-companion、meg、trueinvisibility沒有找到請求字串。
  - Mux `:hello`（伺服器送）會跑`HELLO_LISTENERS`，cinematic、kobold、memorylaby、ServerClock等在這時各自送HELLO。daily、mail、redeem、guild、shop、bugreport也有HELLO。大約50個`Mux.open/ops/openRaw`通道，很多系統有`OPEN`/`LIST`/`STATE`操作（auctions、mounts、mail、echos、daily、dungeon、cosmetics等），是不是「要求重送」要逐個看。
- 審查：Sonnet兩輪皆PASS（`.herdr-work/v14/review-1.md`、`review-2.md`），建議已修。2026-11-12 補了QuestHud NaN的`SaoSnapshot`端到端還原測試（test下照2.51.13反編譯加了QuestHudPayload/State/Kind/Anchor/Style假類別，Style只取2個component），使用者選不開reviewer，主對話自查。

## 測試與審查

最新`work/sao-state-re/state-final-check.log`：`BUILD SUCCESSFUL in24s`。

| 範圍 | 最新結果 |
|---|---|
| sao-state | 33 tests，0 failure/error/skip（2026-11-12 加QuestHud NaN端到端還原測試；同日加NaN編碼、失敗訊息欄位路徑；2026-11-11 改成契約類別指紋，新增`VersionContractTest`5項）；`ArtifactContractTest`用2.51.13與2.51.14兩個真JAR各跑一次，指紋都等於常數；Sonnet reviewer判PASS（`.herdr-work/v14/review-1.md`），建議已修 |
| viewer-bootstrap | 10 tests全過；增加僅供host-fixture的`--fixture-no-resourcepacks`，避免模型測試複製大量外觀小檔 |
| relay-core | 先前22 tests全過，這輪未修改 |
| relay-bridge | 先前6 tests全過，這輪未修改 |
| handoff-service | 先前3 tests全過 |

2026-11-11 Claude 接手後開 Opus reviewer 整輪審查（報告 `handoff-evidence/sao-state/review-1.md`）判 FAIL，兩項確認問題都是上限與未知JAR拒絕路徑沒有測試；已補 `StateLimitsTest`（6）與 `UnknownArtifactTest`（1），sao-state 23 tests全過，使用者選擇不再複查。reviewer 另提未確認項：Equipment/Party version 在 prepare 時+1，使用者選擇等實服再看，不改。

Generic lab獨立審查已通過。sao-state第一輪審查發現rollback非final Set備份型別、必填集合可為null，已補RED→GREEN測試並修正。最後新增的probe bootstrap四欄位、fixture略過packs、timer準備時間修正，**尚未做整輪最終複查**；接手後建議先複查。

## 保留的分析與證據在哪裡

`handoff-evidence/`放選定測試log與generic實驗摘要，2026-11-11 由 Claude 從舊 scratch 補建，索引見其中 README.md。`handoff-artifacts/`從未建立；舊0.1.x JAR/ZIP仍在 `C:\Users\user\Documents\Codex\2026-11-11\new-chat\outputs`。兩者被gitignore，並非正式runtime release。以重新建置的source為準。

舊scratch仍在這台機器，未搬入新repo：

```text
C:\Users\user\Documents\Codex\2026-11-11\new-chat\work
```

選定反編譯資料：`sao-state-re/state-decompiled`（97個選定classes）、`sao-state-re/decompiled`（早期43個）、`sao-state-re/skill-decompiled`（技能callers）。這些是第三方artifact的本機分析資料，**不提交、不打包，不列印驗證secret常數**。

REA `open_binary`已確認原Jar的ZIP identity與SHA。這台REA沒有可用Java decompiler provider，所以用已安裝Vineflower1.11.1離線分析；不要說成REA完成Java反編譯或Ghidra全程式分析。Decompiler位置：

```text
C:\Users\user\.gradle\caches\modules-2\files-2.1\org.vineflower\vineflower\1.11.1\77767cdbc76d116c6d5e1565ec387e531085b9f4\vineflower-1.11.1.jar
```

舊scratch有本機測試RCON secret、pairing資料及遊戲狀態JSON；**不要整包複製／上傳**。本交接沒有把它們搬到新repo。

舊文件中的`../../work`只適用舊工作位置。在`C:\myspace\minecraft-handoff`請改用本repo的ignored `work/`，不要直接照抄那些相對路徑。

## Claude接手後的建議順序

1. 讀本檔、`docs/GENERIC-PACKET-LAB.md`、`docs/P1-PROGRESS.md`及最新sao-state source，先檢查working tree。
2. 用上面的JDK21指令重跑sao-state／bootstrap tests，複查最後修改；不需再大範圍掃盤或重做已證明的generic實驗。
3. 確認SAO目前狀態模型覆蓋度：尤其skills顯示列表與真正技能／手持物／INIT的關係、mana與其他必要HUD。現在只有部分client狀態快照，不能假定完整。
4. 設計peer bootstrap：世界JOIN清理後套用現在狀態，驗證成功ACK後才grant；準備新B不能先撤銷舊A。新舊增量須有一致邊界，不能讓較舊snapshot蓋掉較新live更新。
5. 跨Mac/Windows不可直接相減兩個VM的System.nanoTime。需有clock alignment/RTT誤差界線，或等價的相對deadline傳輸；目前只有elapsed參數和本地aging，網路尚未做。
6. 原始launcher-authenticated transport仍唯一。Viewer需恢復Fabric通道可送能力及Mux HELLO資訊；只塞SERVED不代表`ClientPlayNetworking.canSend`會變true。先檢查這個前置條件。
7. 實服恢復後先做只讀baseline，依需要請使用者開特定介面；再逐步驗收真正接管。
8. 最後完成Mac M1實測、資源量測、Windows viewer啟動，重新整理`delivery/`及可交付包。不要提前稱「專案完成」。

本交接不要求merge或push；保留分支讓使用者與Claude繼續審查即可。
