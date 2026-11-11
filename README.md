# Minecraft Handoff

跨電腦接力的開發專案，目標是由常駐 Mac mini 維持 SAO 玩家連線，各電腦在本機渲染並輪流接管。

**目前版本：0.1.0，P0 診斷模組。尚未具備遊戲接管或保持其他客戶端連線的功能。**

**Mac 實測更新：原啟動流程會移除額外 JAR。0.1.1 輔助程式的替代啟動已在 M1 載入診斷模組，但進服收到 `Launcher validation failed: Invalid token`，因此這條路徑未通過實服驗證，不再作為目前部署方案。詳見 [驗證失敗紀錄](docs/P0-LAUNCHER-COMPATIBILITY.md)。**

## 本次交付

- 可安裝的附加 Fabric 客戶端模組；使用原本啟動器啟動。
- 初始化、加入／離開遊戲連線與每 60 秒一次的 JVM heap 診斷。
- 獨立的控制權模型與測試：交接失敗保留原控制者、準備票據失效、舊世代操作拒絕、斷線撤銷。
- 固定版本建置、JUnit 測試與最終 JAR 內容檢查。

診斷只寫入 Minecraft 現有日誌，標記為 `[minecraft-handoff]`。不讀取啟動器驗證值、不收集聊天／玩家資料、不攔截封包、不開啟網路監聽，也不修改第三方模組。控制權模型目前沒有接到遊戲流程。

## 適用版本

| 項目 | 版本 |
|---|---|
| Minecraft | 1.21.4 |
| Fabric Loader | 0.20.1 或更新 |
| Java 執行環境 | 21 或更新 |
| Fabric API | 需要安裝；本次以 0.119.4+1.21.4 建置 |
| 模組 | minecraft_handoff 0.1.0 |
| Gradle Wrapper | 8.12，下載時檢查固定 SHA-256 |
| Fabric Loom | 1.10.5，固定 release |

## 安裝與 Mac 驗收

下列步驟保留作為一般 Fabric 安裝方式；目前這版 Triggers 啟動流程會清除附加 JAR，請改用上方的啟動輔助程式說明。

1. 關閉正在執行的遊戲。
2. 透過原啟動器開啟 **Sword Art Online 實例資料夾**。
3. 將本次提供的 `minecraft-handoff-0.1.0.jar` 複製到該實例的 `mods`。只加入這個 JAR，保留原本模組。
4. 用原啟動器啟動遊戲，確認附加 JAR 在啟動後仍存在。
5. 在該實例的 `logs/latest.log` 搜尋 `[minecraft-handoff]`。主選單階段應出現 `"lifecycle":"INITIALIZED"`。
6. 正常進入 SAO 伺服器，確認原本遊戲功能正常，並看到 `"lifecycle":"JOINED"`、`"joinCount":1`。
7. 留在遊戲約 70 秒，應出現額外一筆同狀態診斷。最小化或暫停可能影響 tick 時機，這是隨遊戲 tick 觸發的取樣，不是獨立背景計時器。
8. 退出伺服器回主選單，應看到 `"lifecycle":"DISCONNECTED"`、`"disconnectCount":1`。此步是原本的退出行為；P0 尚未保持該連線。

重新啟動遊戲後計數從零開始。回復原狀只需關閉遊戲、移除 `minecraft-handoff-0.1.0.jar` 再啟動。

若啟動器移除附加 JAR，記錄此現象即可；若載入器顯示版本不符，記錄 Minecraft／Fabric 版本及錯誤。這些都是 P0 要驗證的相容性結果。

**Windows 的已知實例路徑：**

```text
C:\Users\user\AppData\Roaming\.Triggers Client\instances\Sword Art Online
```

Mac 的實例路徑以啟動器實際開啟的資料夾為準。本次尚未在 M1 上實際載入附加模組，也尚未在原啟動器中驗證它是否保留附加 JAR。

只需提供日誌中含 `[minecraft-handoff]` 的行，即可核對 P0；這個專案不需要完整遊戲日誌或啟動器憑證。

## 診斷欄位

| 欄位 | 意義 |
|---|---|
| schemaVersion | 本版為 1 |
| applicationVersion、minecraftVersion、fabricLoaderVersion | 本模組與執行環境版本 |
| modVersions | 僅 fabricloader、fabric-api、saoui、mcef、minecraft_handoff 的已載入版本 |
| heapUsedBytes、heapMaxBytes | JVM heap 已用／上限，單位 bytes |
| lifecycle | INITIALIZED、JOINED、DISCONNECTED |
| joinCount、disconnectCount | 本次遊戲程序內的連線事件數 |

JVM heap **不是整個遊戲的總記憶體**，也不包含 Chromium 等其他程序。在 Mac 上判斷容量時仍需搭配「活動監視器」中的記憶體壓力、swap 與相關程序用量。

## 從原始碼建置

需要 JDK 21（包含 javac），初次建置需要網路。成品位於 `build/libs/minecraft-handoff-0.1.0.jar`；不要安裝 `-sources.jar`。

Windows PowerShell：

```powershell
# JAVA_HOME 指向已安裝的 JDK 21；本機啟動器隨附目錄經確認包含 javac。
$env:JAVA_HOME = 'C:\Users\user\AppData\Roaming\.Triggers Client\runtime\jre-21.0.7-windows-x64'
.\gradlew.bat clean test build verifyModArtifact
```

Mac（已安裝並註冊 JDK 21 時）：

```bash
export JAVA_HOME="$(/usr/libexec/java_home -v 21)"
chmod +x gradlew
./gradlew clean test build verifyModArtifact
```

`verifyModArtifact` 會檢查真正的 remapped JAR，確認 metadata、8 個必要 class、Java 21 非 preview bytecode，以及沒有多餘檔案；同時用刻意損壞的 JAR 測試檢查器。它是包裝與設定檢查，不等於已完成實際啟動測試。

## 開發文件

- [第一版設計](docs/superpowers/specs/2026-11-11-minecraft-handoff-design.md)
- [P0 實作計畫](docs/superpowers/plans/2026-11-11-p0-loader-diagnostics.md)
- [驗證結果與限制](docs/P0-VALIDATION.md)

後續 P1 需要實作實際遊戲入口、世界狀態重建、操作轉交與上游連線協調；P2 才逐項驗證 SAO 任務、技能、隊伍與自訂介面。所有階段的範圍與成功標準以設計文件為準。

Gradle Wrapper 取自官方 8.12 發佈來源；原始授權文字保留於 [Gradle 授權文件](licenses/gradle-8.12-LICENSE.txt)。
