# 小小筆記

「小小筆記」是 Android 筆記與桌面小工具，支援多篇筆記、彩色與柔光文字、圖片縮放、免費表情貼圖，以及可直接點選完成的 Checklist。筆記預設保存在裝置上，也可選擇使用 Google Drive 加密備份：Free 可手動備份，Pro 可自動備份。

## 建置

```powershell
.\gradlew.bat testPlayDebug lintPlayDebug assemblePlayDebug
```

本版本只使用 `app/src/main` 的共用程式與 `app/src/play` 的公開素材。Twemoji 圖像的來源與 CC BY 4.0 授權資訊見 `docs/ASSET_SOURCES_PLAY.md` 與 `docs/third_party/TWEMOJI_LICENSE_GRAPHICS.txt`。

雲端備份需由使用者授權 Google Drive，備份內容在上傳前會以使用者設定的密碼加密；App 只要求管理由本 App 建立的備份檔案。OAuth 目前為 Google 外部測試模式，測試帳戶須列在 OAuth 測試使用者清單中。此 GitHub 測試版尚未接入 Pro 付款／訂閱；正式 Google Play 上架前需完成付款整合與隱私權／Data safety 更新。
