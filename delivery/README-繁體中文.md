# Minecraft Handoff 0.2.0 — 原型

Mac 保留由原本 Triggers 啟動器開啟、已通過驗證的 Minecraft。Windows 使用自己的遊戲引擎、模組和顯示卡繪製畫面，透過私人接力入口操作 Mac 那名玩家。Windows 不會再次登入 SAO；關閉 Windows 操作端也不應使 Mac 的原連線離線。

**這個版本是 P1 原版遊戲狀態接力原型。完整 SAO 任務、技能、特殊裝備及自訂 UI 的狀態轉接仍屬 P2，尚未驗收。Mac 實機驗收也尚未完成。請先以測試用途使用，不能把此包視為已完成的 SAO 無縫接力成品。**

## 適用環境

- Minecraft 1.21.4、Fabric Loader 0.20.1、Fabric API 0.119.4+1.21.4；兩台整合包須一致。
- Mac M1 使用 ARM64 Java 21 **JDK**。它需要 Attach API 和 keytool；僅有一般 JRE 可能不足。
- Windows 已由 Triggers 安裝過完整遊戲資源；預設安裝根目錄 `%APPDATA%\.Triggers Client`。
- 兩台能互通 Mac 的 TCP 25575。可用區域網路，或自己已建立的私人 VPN。此程式不會自動設定 VPN、路由器或防火牆。
- 壓縮包只含專案程式，沒有附帶 Minecraft、第三方模組、登入憑證或其他人的遊戲資源。

## Mac 常駐端怎麼開

1. 解壓縮至自己的資料夾，保留 `mac`、`windows`、`lib` 的相對位置。
2. 用**原本 Triggers 啟動器**開啟 Sword Art Online，正常進入伺服器。保留啟動器與遊戲，先關閉遊戲內選單／容器。
3. 在 Terminal 執行：

   ```bash
   cd "/你解壓縮的位置"
   bash mac/Start-Host.command
   ```

4. 腳本會列出 Minecraft PID；輸入目前已進服那個程序的 PID，再輸入 Windows 能連到的 Mac 區網或 VPN IP。
5. 看到 `HOST_READY` 才表示私人加密入口啟動。它會顯示配對檔位置，預設為：

   ```text
   ~/Library/Application Support/Minecraft Handoff/host/pairing.json
   ```

6. 把這個 `pairing.json` 私下傳到自己的 Windows。它含**控制你遊戲的權限**，請勿公開；它不含 Triggers／Microsoft 登入 token。不要傳 `host.p12` 或 `keystore.password`。

第一次執行可以不賦予 `.command` 執行權限，直接用上述 `bash` 指令。腳本使用 `caffeinate -i` 避免系統閒置睡眠。原本遊戲仍必須持續運作；先保持視窗沒有最小化，也不要鎖定／登出 Mac。

若遊戲目錄不同，可在執行前指定，例如：

```bash
HANDOFF_GAME_DIR="$HOME/Library/Application Support/Triggers Client/instances/Sword Art Online" \
HANDOFF_JAVA="/你的Java21JDK/Contents/Home/bin/java" \
bash mac/Start-Host.command
```

`HANDOFF_INSTALL`、`HANDOFF_STATE`、`HANDOFF_PID`、`HANDOFF_HOST`、`HANDOFF_PORT` 也可自行設定。不要傳入舊啟動器 token 或複製原遊戲啟動參數。

## Windows 操作端怎麼開

1. 解壓縮相同版本的包，保留資料夾結構；將 Mac 的 `pairing.json` 放在自己方便找到的位置。
2. **不必用 Triggers 再開一個登入 SAO 的遊戲。** 雙擊 `windows\Start-Viewer.cmd`，依提示輸入配對檔完整路徑。
3. 程式先檢查 Mac 憑證與配對權限，再啟動獨立的 Minecraft。第一次會複製本機模組與資源包，等候時間較長。
4. 世界載入完成後會自動嘗試接管。視窗標題 `CONTROLLER` 表示能操作；`SPECTATOR` 表示觀察。按 **F6** 接管、**F7** 釋放。筆電可能需要搭配 Fn。

也可在 PowerShell 執行：

```powershell
& .\windows\Start-Viewer.ps1 -Pairing 'C:\你的資料夾\pairing.json'
```

若安裝位置不同：

```powershell
& .\windows\Start-Viewer.ps1 -Pairing 'C:\你的資料夾\pairing.json' `
  -Install 'D:\Triggers Client' -ViewerDir 'D:\MinecraftHandoff\viewer' `
  -Java 'C:\你的Java21\bin\java.exe'
```

操作端資料預設在 `%LOCALAPPDATA%\MinecraftHandoff\viewer`。它使用獨立 options、config、logs；不複製原本帳戶資料、cookie、遊戲存檔或舊 launcher token。原整合包不會被改寫，額外模組也不放進啟動器管理的 mods。`-Vanilla` 僅供本機原版測試世界，不能拿來連模組 SAO 服。

## 接力與停止

- A 操作中，B 啟動自己的操作端並載入世界；B 完成後接管，A 轉為觀察。
- 關閉操作端會釋放控制。短暫沒有輸入時會鬆開按鍵；逾時會撤銷控制權。
- Mac 遊戲視窗按 **F8**，可強制取回本機控制。遠端操作時 Mac 本機鍵鼠會暫時被隔離，避免同時操控。
- Mac Terminal 按 **Ctrl+C** 只關掉加密入口，不會關閉遊戲或原啟動器。已附加的 loopback 橋接仍隨原遊戲存在。
- 再次啟動常駐服務會產生新的配對 token；把新的 `pairing.json` 傳給要使用的電腦，舊檔立即無法通過新服務驗證。
- 關閉／重開原遊戲後 PID 會變，必須重新啟動常駐服務。更新 bridge 程式也需要原遊戲下次正常啟動，不能把重複附加當成熱更新。
- 啟動腳本結束後若操作端遊戲仍開著，先關閉該視窗再重新啟動。資料夾鎖會防止兩個遊戲同時寫入同一個操作端實例。

## 故障與限制

- `Invalid token`／啟動器驗證失敗：不要重用舊參數重開遊戲；請用原啟動器正常登入後再附加。接力程式不會偽造或修改 SAO 驗證。
- `HANDOFF_FAILED`：先確認 JDK 21、正確 PID、進服狀態、相同使用者與遊戲路徑。Java 禁用動態附加時，這個方法無法載入。
- 配對失敗：核對 Mac 服務仍在、IP／TCP 25575 可達、配對檔是最新一份。憑證不符會直接拒絕連線。
- `SPECTATOR` 無法接管：先關閉 Mac 的特殊畫面或容器、清空游標拿著的物品，再在 Windows 按 F6。
- Windows 啟動失敗：查看操作端的 `launch-console.log` 和 `logs/latest.log`。更新整合包後兩端模組須相同。
- 這是完整 Minecraft 引擎常駐方案，資源需求主要來自原整合包。M1 8GB 是否足夠要實機量測；本包沒有完成 Mac 長時間測試，也不能推論台灣 1C1G VPS 能跑。
- 本版尚未涵蓋 SAO 專屬狀態、所有模組操作、配方書／創造模式物品操作、所有載具、自訂資源包推送等情況。未知的操作封包不會任意透傳到真實伺服器。
- Mac 斷線、遊戲崩潰、伺服器重啟或閒置踢出仍會中斷連線；本版不提供防踢或自動練功。

實際驗證記錄與未完成項目見隨包 `VALIDATION.md`，不要用「能啟動」代替「SAO 接力已驗收」。
