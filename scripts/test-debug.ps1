$ErrorActionPreference = "Stop"

$projectRoot = Split-Path $PSScriptRoot -Parent
$tooling = Join-Path $projectRoot ".tooling"
$jdk = Get-ChildItem (Join-Path $tooling "jdk17") -Directory |
    Where-Object { Test-Path (Join-Path $_.FullName "bin\java.exe") } |
    Select-Object -First 1
$gradle = Join-Path $tooling "gradle-8.9\gradle-8.9\bin\gradle.bat"

if ($null -eq $jdk) {
    throw "Project-local JDK was not found under .tooling\jdk17."
}
if (-not (Test-Path $gradle)) {
    throw "Project-local Gradle was not found under .tooling\gradle-8.9."
}

$localUserHome = Join-Path $tooling "android-user-home"
$localTemp = Join-Path $tooling "tmp"
New-Item -ItemType Directory -Force -Path $localUserHome, $localTemp | Out-Null

$env:JAVA_HOME = $jdk.FullName
$env:GRADLE_USER_HOME = Join-Path $tooling "gradle-user-home"
$env:GRADLE_OPTS = "-Djava.io.tmpdir=$localTemp"

& $gradle "-Duser.home=$localUserHome" `
    testDebugUnitTest `
    lintDebug `
    assembleDebug `
    --no-daemon
exit $LASTEXITCODE

