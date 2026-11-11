# Minecraft 接力專案

請先完整閱讀根目錄 `CLAUDE_HANDOFF.md`，再決定下一步。那份交接檔比舊 README 的階段描述新。

目前正式交接位置是 `C:\myspace\minecraft-handoff`。保留目前未提交的 SAO snapshot 修改與 Git 歷史。

使用者要求：每台電腦用自己的 Minecraft 客戶端；真正伺服器只看到常駐端同一條、同一個帳號的連線。最終要交付 Mac M1 常駐端與 Windows 操作端的啟動方法。

原始啟動器驗證不可被重播或偽造；不要重跑已撤回的 launcher-helper0.1.1 遊戲重啟方案。不要擅自停止使用者原本的 SAO JVM。

後續方向是原生客戶端＋通用封包轉送＋目前狀態同步。使用者已否決逐項補技能／挖掘動作白名單作為最終架構。

實服目前進不去，使用者已告知暫時不能測試；繼續可獨立完成的工作，需要實服時再列具體測試步驟。不要把本機假資料測試說成 SAO 實服接管成功。

使用者提供的 AGENTS 規則要求結構探索使用已安裝 codebase-memory skill。舊圖對新模組顯示 not_tracked；可按該 skill 的 coverage 規則讀實際來源補證，不能以舊索引宣稱不存在。
