# 小小筆記

「小小筆記」是離線 Android 筆記與桌面小工具，支援多篇筆記、彩色與柔光文字、圖片縮放、免費表情貼圖，以及可直接點選完成的 Checklist。

## 建置

```powershell
.\gradlew.bat testPlayDebug lintPlayDebug assemblePlayDebug
```

本版本只使用 `app/src/main` 的共用程式與 `app/src/play` 的公開素材。Twemoji 圖像的來源與 CC BY 4.0 授權資訊見 `docs/ASSET_SOURCES_PLAY.md` 與 `docs/third_party/TWEMOJI_LICENSE_GRAPHICS.txt`。

所有筆記與圖片都保留在使用者裝置上；本工具不需要帳戶、不提供雲端同步，也不會將筆記內容傳送到網路。
