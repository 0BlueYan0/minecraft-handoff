# P0 驗證紀錄

日期：2026-11-11。適用：Minecraft Handoff 0.1.0。

## 已執行的驗證

在 Windows 11 上，使用原啟動器隨附的 JDK 21.0.7 編譯、JRE 21.0.11 執行 JUnit。所有 class 以 Java 21、非 preview bytecode 產生。

```text
gradlew.bat clean test build verifyModArtifact --console=plain --warning-mode all
BUILD SUCCESSFUL
```

| 檢查 | 結果 |
|---|---|
| ControlSessionTest | 15／15 通過 |
| DiagnosticRecorderTest | 5／5 通過 |
| DiagnosticSnapshotTest | 5／5 通過 |
| 合法 JAR 測試樣本 | 1／1 接受 |
| 損壞／不合規 JAR 測試樣本 | 29／29 拒絕 |
| 真正 remapped 成品檢查 | 通過 |
| 固定版本 Wrapper JAR 與發佈 checksum 比對 | 通過 |
| 完整建置的 Gradle deprecation 輸出 | 最後一輪沒有警告 |

測試先驗證缺少功能時會失敗，再實作功能：控制權行為 15 項失敗後轉為通過；診斷測試先出現 9 項失敗，再全部通過；JAR 檢查器的空實作會錯誤接受缺少 metadata 的測試樣本，實作後能拒絕全部 29 個不合規樣本。

測試時鐘可控制，不使用等待一分鐘的測試。測試也確認診斷 map 修改不會改變既有快照、JSON 會正確跳脫字串，以及非允許清單中的欄位不會輸出。

## 成品

```text
minecraft-handoff-0.1.0.jar
13,585 bytes
SHA-256: 71d2fa1db53c6271b17eeb501f78f29933062f926c36c444f0269fa6183fe105
```

JAR 僅含 Fabric metadata、manifest 與本專案 8 個 class；沒有第三方模組、Minecraft 遊戲資產、日誌或憑證。包裝檢查不能取代實際載入。

## 尚未驗證

- 原啟動器是否會保留這個附加 JAR，以及它與完整 SAO 整合包的實際共存。
- 在使用者 M1／8 GB Mac mini 上的實際載入、進服和診斷事件。
- 原啟動器／SAO 驗證在多電腦登入時的生命週期。
- Mac 上的程序總記憶體、CPU、記憶體壓力與 swap；目前模組只報 JVM heap。
- 所有實際接力功能：遊戲入口、世界與模組狀態重建、操作轉交、上游不斷線。

沒有將測試模組安裝進原始遊戲資料夾，也沒有透過原啟動器啟動或連線。Mac 的原始整合包可正常進服，是使用者先前確認的環境條件，不能當作本次附加模組已通過 Mac 驗證。

## 實作判斷

- 在新建的獨立專案分支實作，保留設計文件的起始 commit；未額外建立第二個 worktree。若出現其他並行編輯，需要再隔離工作。
- 固定 Loom 1.10.5／Gradle 8.12，避免依賴移動中的 snapshot；後續升級遊戲版本時須重新驗證組合。
- 額外抽出純 Java DiagnosticRecorder，讓事件計數與 60 秒取樣有可重現測試；Fabric 的實際事件接線仍留待啟動器驗收。
- 首次 Loom 準備依賴期間先撰寫兩個任務的測試，兩者通過後才提交；保留了各自的失敗與成功證據。

## 下一個實際驗收

依 README 將 JAR 加入 Mac 的原實例，使用原啟動器啟動、進入 SAO、等待約 70 秒，再退出伺服器。核對 `[minecraft-handoff]` 診斷行與附加 JAR 是否被保留。這一步成立後再開始 P1 遊戲接力的垂直切片。

## 獨立程式碼審查

以獨立審查代理唯讀檢查 `8a2ccea..edc8b4c` 的程式碼、測試、建置與文件。結論：P0 可交付，沒有 Critical 或 Important 問題。審查代理沒有執行建置或改動檔案。

一項 Minor 留待 P1 前處理：ControlSession 的 Ticket 使用物件身分比較，呼叫端須保留 `prepare` 傳回的原物件。實作已有此行為，但介面文件與測試尚未明確涵蓋「重建同值 ticket」及「其他 session 的 ticket」。目前沒有接力網路 adapter，不影響 P0 的診斷功能；P1 不可把反序列化的新物件直接當作原票據。

對審查範圍之外項目的判斷：

- 原啟動器／M1 載入仍需由真實設備驗證；否則可能有尚未發現的相容問題。
- 遊戲同步、上游連線保持、封包清理與遠端驗證由 P1／P2 實作；P0 通過不能用來宣稱已接管遊戲。
- ControlSession 目前要求單一協調執行緒；未來 adapter 必須隔離連線 ID 並過濾過期斷線回呼，否則可能撤銷新連線的控制權。
- class 檢查只驗證檔案存在、標頭及 bytecode 版本；它不取代 JVM 的完整驗證器，刻意偽造的 class 仍可能在載入時被 JVM 拒絕。
