# Minecraft Handoff 原型

Mac 保留由原本 Triggers 啟動器開啟、已通過驗證的 Minecraft。Windows 使用自己的遊戲引擎、模組和顯示卡繪製畫面，透過私人接力入口操作 Mac 那名玩家。Windows 不會再次登入 SAO；關閉 Windows 操作端也不應使 Mac 的原連線離線。

**這個版本還在測試。** Windows 加入時會先拿到 Mac 目前的 SAO 技能列、等級、魔力或體力、冷卻、任務 HUD 與祝福欄位，對得上才交出控制權，對不上就停在觀察並標出缺的那一塊。裝備、隊伍、技能側欄這類介面，要在 Windows 拿到控制權後打開，由遊戲自己向伺服器要資料。以上只在本機測試伺服器驗過，還沒在 SAO 伺服器和 Mac 實機驗過，不能把此包視為已完成的 SAO 無縫接力成品。

## 安裝與更新

第一次安裝只要做一次，Mac 與 Windows 各自做。

1. 開啟 <https://github.com/0BlueYan0/minecraft-handoff/releases/latest>，下載 `minecraft-handoff.zip`。
2. 解壓縮到自己的資料夾。
3. Mac 照「Mac 常駐端怎麼開」，Windows 照「Windows 操作端怎麼開」。

之後每次啟動，腳本會先檢查有沒有新版，有就自動下載、檢查檔案完整、換成新版，再用同樣的設定重新啟動。配對檔與遊戲設定不會被動到。網路連不上或下載失敗時，會顯示一行說明，接著用現有的版本繼續啟動。

- 更新時 `lib`、`mac`、`windows` 三個資料夾會整個換掉，不要把自己的檔案放進去。
- 不想自動更新時，啟動前設定 `HANDOFF_NO_UPDATE=1`。Mac 在 Terminal 執行 `export HANDOFF_NO_UPDATE=1`，Windows 在命令提示字元執行 `set HANDOFF_NO_UPDATE=1`。
- 更新後 Mac 常駐端要先在舊視窗按 Ctrl+C，再重新執行 `Start-Host.command`，新版才會生效。
- 第一次升級到支援免重開遊戲更新的版本時，Mac 的遊戲要重開一次。

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

`pairing.json` 只有第一次需要傳到 Windows，之後重開 Mac 常駐端會沿用同一份。Mac 的 IP 也只有第一次要輸入，之後直接按 Enter 就會沿用。要換一份新的配對時，用下面這行執行，換完要重新傳一次 `pairing.json`：

```bash
HANDOFF_NEW_PAIRING=1 ./Start-Host.command
```

第一次執行可以不賦予 `.command` 執行權限，直接用上述 `bash` 指令。腳本使用 `caffeinate -i` 避免系統閒置睡眠。原本遊戲仍必須持續運作；先保持視窗沒有最小化，也不要鎖定／登出 Mac。

若遊戲目錄不同，可在執行前指定，例如：

```bash
HANDOFF_GAME_DIR="$HOME/Library/Application Support/Triggers Client/instances/Sword Art Online" \
HANDOFF_JAVA="/你的Java21JDK/Contents/Home/bin/java" \
bash mac/Start-Host.command
```

`HANDOFF_INSTALL`、`HANDOFF_STATE`、`HANDOFF_PID`、`HANDOFF_HOST`、`HANDOFF_PORT` 也可自行設定。不要傳入舊啟動器 token 或複製原遊戲啟動參數。

拿到控制權之後的操作（例如裝備、技能側欄）預設會送出。要關掉時，在執行前加上 `HANDOFF_GENERIC=0`，Windows 操作端也要同時加 `-NoGeneric`。

Mac 常駐端沒人操作時，每 5 分鐘會自己在遊戲裡前、右、後、左各走 2 秒，不會送出任何鍵盤或滑鼠事件。Mac 視窗最近 5 分鐘有鍵盤或滑鼠操作、Windows 操作端持有控制權、角色死亡或畫面還在載入時，這一輪不走。要關掉時，在執行前加上 `HANDOFF_IDLE_WALK=0`。

## Windows 操作端怎麼開

1. 解壓縮相同版本的包，保留資料夾結構；將 Mac 的 `pairing.json` 放在自己方便找到的位置。
2. **不必用 Triggers 再開一個登入 SAO 的遊戲。** 雙擊 `windows\Start-Viewer.cmd`，它會依序問兩件事：
   - Triggers 資料夾（裡面有 `versions`、`runtime`、`instances`）。直接按 Enter 就用預設的 `%APPDATA%\.Triggers Client`。
   - 配對檔完整路徑。

   Java 21 依序找 Triggers 資料夾裡的 `runtime`、`%LOCALAPPDATA%\MinecraftHandoff\jdk21`、`JAVA_HOME`、`PATH`。都找不到時，會用 Windows 內建的 curl 下載 Eclipse Temurin JDK 21.0.13（約 205 MB），比對 SHA-256 之後解壓到 `%LOCALAPPDATA%\MinecraftHandoff\jdk21`。下載失敗就改問 `java.exe` 的完整路徑。這個檔案不需要 PowerShell，也不需要管理員權限。

   也可以在 cmd 一次帶好參數：

   ```bat
   windows\Start-Viewer.cmd -Pairing "C:\你的資料夾\pairing.json" -Install "D:\Triggers Client"
   ```
   > Mac 掛著的時候，Windows 不要打開 Triggers Client 啟動遊戲。兩次在 Windows 用同一個帳號從 Triggers Client 開遊戲之後，Mac 在 10 分鐘內被判定「啟動器工作階段已過期或無效」而斷線。操作端只讀 Triggers 資料夾裡已經下載好的檔案，不需要開 Triggers Client。
3. 程式先檢查 Mac 憑證與配對權限，再啟動獨立的 Minecraft。視窗會顯示目前在做什麼：
   - 第一次會複製本機模組、資源包與光影包，顯示 `Copying game files: 已複製/總數 (MB)`，約 1 GB。
   - 之後只檢查有沒有變，顯示 `Checking game files: 數量`，再顯示 `Game files up to date`。
   - Minecraft 載入期間每 10 秒顯示一次 `Minecraft loading... 秒數`，大約要 2 到 4 分鐘。
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

操作端資料預設在 `%LOCALAPPDATA%\MinecraftHandoff\viewer`。它使用獨立 options、config、logs，光影設定第一次沿用原整合包選的光影包；不複製原本帳戶資料、cookie、遊戲存檔或舊 launcher token。原整合包不會被改寫，額外模組也不放進啟動器管理的 mods。`-Vanilla` 僅供本機原版測試世界，不能拿來連模組 SAO 服。

## 接力與停止

- A 操作中，B 啟動自己的操作端並載入世界；B 完成後接管，A 轉為觀察。
- 關閉操作端會釋放控制。短暫沒有輸入時會鬆開按鍵；逾時會撤銷控制權。
- Windows 被 Mac 斷線時，斷線畫面的「重新連線到 Mac」會直接連回去，不用關掉 cmd 重開。斷線畫面上的原因文字請記下來，回報問題時用得到。
- Mac 遊戲視窗按 **F8**，可強制取回本機控制。遠端操作時 Mac 本機鍵鼠會暫時被隔離，避免同時操控。
- Mac Terminal 按 **Ctrl+C** 只關掉加密入口，不會關閉遊戲或原啟動器。已附加的 loopback 橋接仍隨原遊戲存在。
- 重開 Mac 常駐端會沿用原本的 `pairing.json`。用 `HANDOFF_NEW_PAIRING=1` 換新之後，把新的 `pairing.json` 傳給要使用的電腦，舊檔立即無法通過驗證。
- 關閉／重開原遊戲後 PID 會變，必須重新啟動常駐服務。更新 bridge 程式也需要原遊戲下次正常啟動，不能把重複附加當成熱更新。
- 啟動腳本結束後若操作端遊戲仍開著，先關閉該視窗再重新啟動。資料夾鎖會防止兩個遊戲同時寫入同一個操作端實例。

## 故障與限制

- `Invalid token`／啟動器驗證失敗：不要重用舊參數重開遊戲；請用原啟動器正常登入後再附加。接力程式不會偽造或修改 SAO 驗證。
- `HANDOFF_FAILED`：先確認 JDK 21、正確 PID、進服狀態、相同使用者與遊戲路徑。Java 禁用動態附加時，這個方法無法載入。
- 配對失敗：核對 Mac 服務仍在、IP／TCP 25575 可達、配對檔是最新一份。憑證不符會直接拒絕連線。
- `SPECTATOR` 無法接管：先關閉 Mac 的特殊畫面或容器、清空游標拿著的物品，再在 Windows 按 F6。Mac 遊戲視窗不在前景而自己開出暫停選單時，不會擋住接管。
- Windows 啟動失敗：查看操作端的 `launch-console.log` 和 `logs/latest.log`。更新整合包後兩端模組須相同。
- 這是完整 Minecraft 引擎常駐方案，資源需求主要來自原整合包。M1 8GB 是否足夠要實機量測；本包沒有完成 Mac 長時間測試，也不能推論台灣 1C1G VPS 能跑。
- Windows 拿到控制權後，它的遊戲操作會原樣轉給伺服器，Mac 那段時間不送操作。Windows 送出的簽章聊天伺服器收不收、SAO 伺服器會不會送出本機沒測到的資料格式，都要在 SAO 伺服器上實測才知道。
- Mac 斷線、遊戲崩潰、伺服器重啟或閒置踢出仍會中斷連線；本版只有上面那個定時走動，沒有其他防踢或自動練功。伺服器是否允許這種走動要自行確認。
