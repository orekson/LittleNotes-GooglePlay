param(
    [ValidateSet("personal", "play")]
    [string]$Flavor = "personal"
)

$ErrorActionPreference = "Stop"
$projectRoot = $PSScriptRoot
$env:JAVA_HOME = (Get-ChildItem -LiteralPath (Join-Path $projectRoot ".tools/jdk") -Directory | Select-Object -First 1).FullName
$env:ANDROID_HOME = Join-Path $projectRoot ".tools/sdk"
$env:ANDROID_SDK_ROOT = $env:ANDROID_HOME
$env:ANDROID_USER_HOME = Join-Path $projectRoot ".tools/android-user"
$env:ANDROID_AVD_HOME = Join-Path $env:ANDROID_USER_HOME "avd"
$env:GRADLE_USER_HOME = Join-Path $projectRoot ".tools/gradle-user"

$adb = Join-Path $env:ANDROID_HOME "platform-tools/adb.exe"
$emulator = Join-Path $env:ANDROID_HOME "emulator/emulator.exe"
$gradle = Join-Path $projectRoot ".tools/gradle-8.13/bin/gradle.bat"
$avdName = "LittleNotesTest"

$deviceLine = & $adb devices | Select-String "^emulator-\d+\s+(device|offline)$" | Select-Object -First 1
if (-not $deviceLine) {
    Start-Process -FilePath $emulator -ArgumentList @("-avd", $avdName, "-no-snapshot", "-no-audio") -WindowStyle Normal | Out-Null
    $deadline = (Get-Date).AddMinutes(3)
    do {
        Start-Sleep -Seconds 3
        $deviceLine = & $adb devices | Select-String "^emulator-\d+\s+(device|offline)$" | Select-Object -First 1
    } while (-not $deviceLine -and (Get-Date) -lt $deadline)
}
if (-not $deviceLine) { throw "Android 模擬器沒有啟動，請確認虛擬化功能已開啟。" }

$serial = ($deviceLine.ToString() -split "\s+")[0]
$deadline = (Get-Date).AddMinutes(3)
do {
    $bootOutput = & $adb -s $serial shell getprop sys.boot_completed 2>$null
    $boot = ([string]$bootOutput).Trim()
    if ($boot -eq "1") { break }
    Start-Sleep -Seconds 3
} while ((Get-Date) -lt $deadline)
if ($boot -ne "1") { throw "Android 模擬器開機逾時。" }

$task = if ($Flavor -eq "play") { ":app:assemblePlayDebug" } else { ":app:assemblePersonalDebug" }
& $gradle --no-daemon $task
if ($LASTEXITCODE -ne 0) { throw "Gradle 建置失敗，結束碼 $LASTEXITCODE" }

$applicationId = if ($Flavor -eq "play") { "tw.local.memonote.play" } else { "tw.local.memonote" }
$apk = Join-Path $projectRoot "app/build/outputs/apk/$Flavor/debug/app-$Flavor-debug.apk"
& $adb -s $serial install -r $apk
if ($LASTEXITCODE -ne 0) { throw "APK 安裝失敗，結束碼 $LASTEXITCODE" }
& $adb -s $serial shell monkey -p $applicationId 1
if ($LASTEXITCODE -ne 0) { throw "App 啟動失敗，結束碼 $LASTEXITCODE" }

Write-Host "已在桌面 Android 模擬器開啟 $Flavor 版本。關閉模擬器視窗即可結束。"