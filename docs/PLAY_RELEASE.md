# Google Play release checklist

目前 `LittleNotes-Play` 的原始碼、Twemoji 授權文件與 Android 建置已整理完成。正式上傳前仍必須使用只由你保管的 release upload keystore 簽署 AAB；請勿把 keystore、密碼或 Play service account 金鑰提交到 GitHub。

## 建立 upload keystore（在你自己的安全環境執行）

```powershell
keytool -genkeypair -v `
  -keystore little-notes-upload.jks `
  -alias little-notes-upload `
  -keyalg RSA -keysize 2048 -validity 10000
```

請將檔案放在 Git 工作區之外，並備份到安全位置。密碼不要貼到聊天、原始碼或 CI 設定檔中。

## Gradle signing 設定

在本機 `gradle.properties` 或 CI secret 中提供 keystore 路徑、store password、key alias 與 key password，再於 `app/build.gradle.kts` 建立 release `signingConfig`。完成後執行：

```powershell
./gradlew bundleRelease
jarsigner -verify -verbose -certs path\to\app-release.aab
```

驗證輸出必須顯示簽章有效，才能交給 Play Console。現有的 `app-release.aab` 是建置驗證用的 unsigned bundle，不能直接上架。
