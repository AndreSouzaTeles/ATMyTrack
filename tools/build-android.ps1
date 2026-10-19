param([switch]$DeviceTest)
$ErrorActionPreference='Stop'
$projectRoot=Split-Path $PSScriptRoot -Parent
$runtimeRoot=Join-Path $env:USERPROFILE '.cache\atmytrack-android'
if(-not $env:JAVA_HOME) {
    $jdk=Get-ChildItem (Join-Path $runtimeRoot 'jdk') -Directory -ErrorAction SilentlyContinue | Select-Object -First 1
    if($jdk) { $env:JAVA_HOME=$jdk.FullName }
}
if(-not $env:ANDROID_HOME) { $env:ANDROID_HOME=Join-Path $runtimeRoot 'sdk' }
if(-not (Test-Path (Join-Path $env:JAVA_HOME 'bin\java.exe'))) { throw 'Configure JAVA_HOME com JDK 17.' }
if(-not (Test-Path (Join-Path $env:ANDROID_HOME 'platforms\android-36\android.jar'))) { throw 'Configure ANDROID_HOME com SDK 36, build-tools 36.0.0, NDK 28.2.13676358 e CMake 3.22.1.' }
Push-Location $projectRoot
try {
    & .\gradlew.bat testDebugUnitTest lintDebug :app:exportDebugApk assembleDebugAndroidTest --console=plain
    if($LASTEXITCODE) { throw "Build/testes falharam: $LASTEXITCODE" }
    if($DeviceTest) {
        & .\gradlew.bat connectedDebugAndroidTest '-Pandroid.testInstrumentationRunnerArguments.class=com.atmytrack.app.BrandAssetsTest,com.atmytrack.app.Sections09Test,com.atmytrack.app.PadLocks13Test' --console=plain
        if($LASTEXITCODE) { throw "Teste Android falhou: $LASTEXITCODE" }
    }
    Get-FileHash dist\ATMyTrack-debug.apk -Algorithm SHA256
} finally { Pop-Location }
