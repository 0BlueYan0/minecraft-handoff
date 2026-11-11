# Mac 啟動相容性測試包 0.1.1

這個輔助程式讓原 Triggers Client 先完成正常啟動，再使用同一個遊戲程序的 Java 與參數，於第一次遊戲關閉後重新啟動，加入位於實例外的 P0 診斷 JAR。

**這是待實測的相容性測試，不是已完成的遊戲接力。** 原啟動器取得的驗證是否在第一次遊戲結束後仍有效，尚未確認。Java 參數只保留在本機記憶體中，不輸出、不另存，也不讀取啟動器帳號資料庫。

## 使用方式

1. 解壓縮整個測試包到實例以外的資料夾，例如「下載項目」或「文件」。保留 `Start-Handoff.command`、`handoff-launcher-helper-0.1.1.jar`、`minecraft-handoff-0.1.0.jar` 在同一層。不要把它們放進原實例。
2. 用原 Triggers Client 按「開始遊戲」。第一次遊戲停在主選單，先不要進伺服器；只保留一個 SAO 遊戲程序。
3. 開啟 Mac「終端機」，輸入 `bash `（後面留一格空白），把解壓後的 `Start-Handoff.command` 拖入終端機，再按 Enter。
4. 輔助程式會尋找同一個使用者、同一個 SAO 實例的遊戲，最多等待五分鐘。看到 `Original game detected` 後，在兩分鐘內正常關閉第一次遊戲。
5. 保留終端機視窗，等待第二次遊戲自動啟動。這次是原 Java、原參數加上外部診斷 JAR；不要再按原啟動器的 Play。
6. 第二次遊戲到主選單後，`logs/latest.log` 應出現 `[minecraft-handoff]` 及 `INITIALIZED`。再正常進入 SAO、等待約 70 秒、退出伺服器。
7. 只提供 `latest.log` 中含 `[minecraft-handoff]` 的行。如果進服驗證失敗，提供錯誤訊息文字即可，不需要任何 token 或完整啟動命令。

Mac 預設實例：

```text
~/Library/Application Support/Triggers Client/instances/Sword Art Online
```

第二次遊戲沿用這個實例，因此照常讀寫它的設定與正常遊戲日誌；輔助程式本身不修改啟動器、原模組或設定。直接用原啟動器啟動遊戲即可恢復原啟動方式；這個測試包沒有常駐服務、登入項目或全域環境設定。

## 失敗時的資訊

- `Java 21 could not be found`：提供原啟動器 runtime 資料夾的目錄結構或 Java 路徑即可，不需要登入資料。腳本逐一確認啟動器 runtime 是 Java 21 以上，再嘗試 JAVA_HOME 與 macOS 已註冊的 JDK 21。
- `does not expose process argument arrays`：目前 Java／系統沒有提供可靠參數陣列；程式會停止，不會嘗試用字串拆分來猜測參數。
- `No uniquely identifiable SAO game process`：確認只有一個 SAO 遊戲，且第一次遊戲仍開著並使用預設實例路徑。
- `Helper stopped`：提供錯誤類別與最後一行一般提示。輔助程式不輸出例外訊息或堆疊，以免其中包含啟動參數。
- 第二次遊戲未啟動或驗證失敗：保留原啟動方式，提供一般錯誤及有無 `[minecraft-handoff]`；不要提供或修改驗證值。

本程式不會終止原遊戲；等待超過兩分鐘就停止。它也不會新增登入或更新 token，只重用這一次原啟動流程取得的參數；若原驗證在退出時失效，這條路徑便不成立。

## 已驗證與未驗證

- Windows 建置與測試：34 項通過；3 項需要原生參數陣列的程序辨識測試，因本機 Windows Java 不提供該資訊而明確跳過。
- 實際 Java 子程序測試確認帶空白的參數能完整傳給新程序，且能設定外部模組 property；測試程序不是真實 Minecraft，也沒有使用實際驗證資料。
- 4 個腳本行為測試通過（Windows Git Bash 的受控 macOS 命令 fixture），涵蓋 Java 17／21 混存、參數環境清理、空白路徑與 JAVA_HOME 後備。
- 原 P0 JAR 的 25 項測試與 29 個不合規包裝樣本檢查仍通過。
- 尚未在 Mac 上實際執行此輔助程式，也尚未驗證真實 SAO 驗證生命週期。不得把建置成功視為 Mac 測試成功。

## 技術依據

- Fabric 0.20.1 原始碼確實讀取 `fabric.addMods` 系統 property，支援指定額外 JAR：<https://github.com/FabricMC/fabric-loader/blob/0.20.1/src/main/java/net/fabricmc/loader/impl/discovery/ArgumentModCandidateFinder.java>
- OpenJDK 21 的 macOS 實作透過 `KERN_PROCARGS2` 取得參數邊界；實際讀取仍取決於本機 runtime 與權限：<https://github.com/openjdk/jdk21u/blob/master/src/java.base/macosx/native/libjava/ProcessHandleImpl_macosx.c>

程式使用 Java 的 ProcessHandle API，不解析 `ps` 輸出的命令字串，不從文字日誌還原憑證，不修改原啟動器的完整性檢查。
