# 原程序接入測試包 0.1.2

這版保留原啟動器正式啟動、已通過伺服器驗證的 Minecraft 程序，透過 Java Attach 加入診斷代理。它不重新啟動遊戲、不沿用或複製登入參數、不讀取 token property，也不修改啟動器或驗證模組。

Windows 本機原 SAO 實例已測到：原啟動器驗證成功後，在同一個 PID 內附加代理，持續收到在線診斷，沒有因附加而重新登入或出現 Invalid token。Mac 的 Attach 與整合包共存仍待實機驗證。

目前交付的是接入與診斷功能，尚未包含兩台客戶端之間的操作權交接、世界或 SAO UI 狀態同步。

這份流程針對唯一連到真實 SAO 的常駐端。未來的 A／B 操作端應只連自有接力入口，並在連到真實 SAO 之前進入操作端模式；0.1.2 尚未提供這個入口或模式。不能讓兩台用同帳號直連 SAO 來測試接管。

## Mac 使用方式

1. 將整包解壓到實例以外的資料夾，保留 `Attach-Handoff.command` 與 `handoff-runtime-agent-0.1.2.jar` 在同一層。不要放入原本的 mods 目錄。
2. 用原 Triggers Client 正常啟動 SAO，正常進入伺服器。原啟動器與原遊戲都保持開啟。
3. 在終端機輸入 `bash `，把 `Attach-Handoff.command` 拖入終端機並按 Enter。
4. 出現 `AGENT_READY` 代表代理已在原遊戲程序中安裝完成，訊息會顯示原 PID；不用退出遊戲。
5. 原實例的 `logs/latest.log` 會出現 `[minecraft-handoff-agent]`、`ATTACHED_IN_GAME`，以及約每分鐘一次的 heap／事件計數。

重複執行應回覆 `AGENT_ALREADY_READY`，不會重複安裝。代理不是 Fabric mod，不會新增 Mod Menu 項目；以診斷標記判斷是否載入。

腳本使用的實例位置：

```text
~/Library/Application Support/Triggers Client/instances/Sword Art Online
```

腳本先確認 Java 21 以上且包含 `jdk.attach`，依序檢查啟動器 runtime、JAVA_HOME 及 macOS 已註冊的 JDK 21。環境變數調整只作用於輔助程式，不改變原遊戲或全域設定。

## Windows 使用

本機測試已由 Codex 完成附加，該次原遊戲保持在線。如果之後需要手動重做，先用原啟動器正常進服，再於解壓目錄的 PowerShell 執行：

```powershell
$handoffJava = Join-Path $env:APPDATA '.Triggers Client\runtime\jre-21.0.7-windows-x64\bin\java.exe'
$handoffGame = Join-Path $env:APPDATA '.Triggers Client\instances\Sword Art Online'
$handoffGamePid = Read-Host '輸入工作管理員中 SAO javaw.exe 的 PID'
& $handoffJava --add-modules jdk.attach -jar .\handoff-runtime-agent-0.1.2.jar --game-dir $handoffGame --pid $handoffGamePid
```

Windows 原生 ProcessHandle 不提供可靠參數陣列，因此 Windows 手動模式明確指定 PID。代理仍會核對遊戲實際目錄，選到其他實例會拒絕安裝。

## 診斷與失敗

- `ATTACHED_IN_GAME`：附加當下有遊戲網路處理器。這個欄位本身不宣稱伺服器授權成功；需要搭配原啟動器的驗證結果與後續持續連線。
- `joins`／`disconnects`：附加之後的 Fabric 事件數；附加前已經進服時 joins 為 0 是正常的。
- `JAVA_WITH_ATTACH_NOT_FOUND`：需要含 Attach 模組的 Java 21+；程式不安裝 JDK 或修改系統設定。
- `NO_UNIQUE_GAME_PROCESS`：檢查實例路徑及目前是否有多個 SAO 程序。
- `FAILED:WRONG_GAME_DIRECTORY`：指定程序不是所要求的實例，未安裝事件回呼。
- `ATTACH_FAILED`：只輸出例外類別，不輸出原參數或例外內容。若 JVM 不允許動態代理，程式停止，不會修改 JVM 的安全旗標。
- `OBSERVER_DISABLED`：診斷遇到可恢復錯誤，診斷回呼會停用，避免把錯誤傳回遊戲迴圈。
- `AGENT_PENDING`：遊戲執行緒可能尚在載入；先查看原日誌，不要連續重複附加。

暫存狀態檔只傳遞固定狀態碼，讀取後清理。沒有收集聊天、封包內容或其他玩家資料，沒有另建完整遊戲日誌，沒有網路監聽、登入項目或常駐外部服務。

診斷回呼會保留到這個遊戲程序結束；之後再由原啟動器正常啟動，就不會自動載入代理。輔助程式沒有停止或重新啟動遊戲的功能。

## 驗證範圍

已做 Java 真實子程序 Attach、獨立類別載入器、客戶端執行緒切換、重複附加、錯誤實例拒絕、停用動態代理時保留目標程序、診斷錯誤隔離及排程失敗狀態回報測試；也在真正 Fabric 客戶端與原 SAO 實例觀察到接入。

這些結果不等於已完成跨電腦遊戲接力，也不保證所有 JVM 致命錯誤或長時間運作都能恢復。
