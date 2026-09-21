param([string[]]$Tasks = @('testDebugUnitTest','lintDebug','assembleDebug'))
$ErrorActionPreference = 'Stop'
$projectRoot = $PSScriptRoot
$env:JAVA_HOME = (Get-ChildItem -LiteralPath "$projectRoot\.tools\jdk" -Directory | Select-Object -First 1).FullName
$env:ANDROID_HOME = "$projectRoot\.tools\sdk"
$env:ANDROID_USER_HOME = "$projectRoot\.tools\android-user"
$env:GRADLE_USER_HOME = "$projectRoot\.tools\gradle-user"
Push-Location $projectRoot
try { & "$projectRoot\.tools\gradle-8.13\bin\gradle.bat" --no-daemon @Tasks; exit $LASTEXITCODE } finally { Pop-Location }
