# P0 原程序接入驗證

日期：2026-11-11。版本：0.1.2 runtime agent。

## 決策與範圍

0.1.1 重新啟動、重播參數的方案已因實服 Invalid token 撤回。新方案保留原啟動器啟動的原 Minecraft JVM，使用標準 Java Attach／Instrumentation agentmain 加入觀察性 Fabric 回呼。

Agent manifest 不授予 class redefine 或 retransform 能力。程式不使用 transformer，不修改驗證模組，不讀取驗證 property，不重建啟動命令，也不啟動第二個遊戲。所有操作均限於同一位系統使用者；自動選擇需是唯一的 KnotClient／相同實例，代理本身還會再次核對 Minecraft 的實際遊戲目錄。

代理在已載入的 Minecraft class 中取得原客戶端及其類別載入器，將安裝工作放到客戶端 Executor。它只註冊公開的 Fabric JOIN、DISCONNECT、END_CLIENT_TICK 回呼。JOIN／DISCONNECT 即使由 Netty 執行緒觸發，也會交回客戶端執行緒處理。

## 真正 Fabric 客戶端測試

使用獨立 `run-agent-smoke` 開發實例、Minecraft 1.21.4、Fabric Loader 0.20.1、Fabric API 0.119.4+1.21.4，原 P0 mod 被明確停用。

- 測試 JVM PID 65288。
- 13:24:24–25，CLI 回覆 AGENT_READY，原日誌記錄 ATTACHED_IN_MENU。
- 其後同一 PID 持續記錄每分鐘樣本。
- 13:28:40 透過測試遊戲的 Quit Game 正常退出；沒有使用此測試進入真實伺服器。

這項測試證明反射名稱、實際 Fabric 事件型別、類別載入器及主執行緒接線可在真正客戶端執行，不只依賴模擬 fixture。

## 原 SAO 實例實服測試

使用者確認本機也有相同實例後，透過原 Triggers Client 的開始遊戲操作正常啟動。沒有把自訂 JAR 加入受管理 mods，也沒有重播參數。

| 時間 | 證據 |
|---|---|
| 13:31:28 | 原遊戲程序 PID 68948 啟動，啟動器回報遊戲執行中 |
| 13:38:49 | 原 latest.log 記錄 Launcher validated successfully |
| 13:41:13 | 對既有 PID 68948 附加，CLI 回覆 AGENT_READY |
| 13:41:13 | 原日誌記錄 ATTACHED_IN_GAME，joins=0、disconnects=0 |
| 13:42:13–14:01:14 | 每分鐘仍為 ATTACHED_IN_GAME，原 PID 未變，disconnects=0；附加後觀察逾 20 分鐘 |

附加前後沒有因代理出現新的 Invalid token。joins=0 代表附加時原遊戲已進服，不是假造第二次登入。原遊戲仍保留給使用者，沒有為了測試而終止它。

實服觀察使用 0.1.2 開發建置。獨立審查後增加的回呼錯誤隔離與排程失敗狀態修補，以原生 JVM fixture／回歸測試驗證；沒有為了重新測試這兩個錯誤路徑而中斷或重啟已在線的原遊戲。最終成品包含這些修補。

## 自動化驗證

Runtime agent 測試涵蓋：

- 真實 JVM、隔離的遊戲類別載入器及標準 Attach API，目標 PID 保持存活。
- 真正 helper CLI 安裝與回覆，重複附加只註冊一次。
- 錯誤目錄即使在已安裝後仍被拒絕。
- JVM 關閉動態代理時，helper 失敗而原程序繼續運作。
- 客戶端排程拒絕時持續回報失敗，不會把未排程工作錯報成永久 pending。
- 可恢復的診斷錯誤與警告輸出錯誤被隔離，後續正常遊戲工作仍執行。
- 60 秒取樣、既有連線快照與事件計數。

Mac 腳本以受控 Git Bash fixture 驗證舊 Java 跳過、Attach 模組檢查、空白路徑、環境清理與 JAVA_HOME 後備。首次在原整合包大量載入時出現 fixture 的 15 秒測試 deadline 超時；它不是效能斷言，因此改為 60 秒測試預算，保留所有版本／參數／環境斷言，之後四項通過。

## 審查與取捨

獨立唯讀審查指出回呼執行期間也需錯誤隔離，已先重現失敗再加入 ObserverGuard。審查另指出 Executor 拒絕排程後的 shared status 應保存 failure；考量後續重試會被誤導為 pending，將此項列為需修正，原生回歸測試也先失敗後通過。

保留以下邊界：Mac 的實際 Attach 權限與模組相容性仍未實測；同一使用者惡意替換檔案／偽造程序資料不屬於本次合作式測試邊界；沒有保證從所有 JVM 致命錯誤恢復；實服短期觀察不能取代長期穩定性測試。

## 下一階段

已解決目前「加入自訂診斷功能卻失去原啟動器驗證」的接入障礙，至少在本機原實例的觀察範圍內成立。接下來的接力核心仍需實作唯一操作權、世界狀態同步、SAO 自訂狀態重建及遠端連線；這些尚未包含在 0.1.2。

## 最終交付檢查

完整 Gradle test/build/verifyModArtifact 通過，Java 測試 46 項中 43 項通過、3 項舊 helper 的平台能力測試明確跳過，0 失敗。新 runtime agent 的 9 項測試全部通過；Mac 啟動腳本另外 4 項行為測試通過。原 P0 成品的 29 個不合規包裝樣本仍全部被拒絕。

最終 agent JAR 為 17,688 bytes，SHA-256 `55d90111085b00efd7b9b1ea731b57778bd51afd17d52137120b121feb91f1f7`。JAR 僅含本專案 7 個 agent class 與 manifest，沒有 Minecraft／Fabric fixture、日誌、驗證資料或舊重啟 helper。

使用者誤按 Escape 後，桌面控制停止；之後沒有再送桌面輸入。原實例的附加與持續連線檢查由已確認的原 PID 及 CLI／日誌完成。
