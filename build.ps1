param([switch]$Offline,[switch]$SkipApplet)
$ErrorActionPreference='Stop'
if (!$env:JAVA_HOME -or !(Test-Path "$env:JAVA_HOME/bin/javac.exe")) { $env:JAVA_HOME='C:/Program Files/Java/jdk-21.0.11' }
Push-Location $PSScriptRoot
try {
    if ($Offline) { & python scripts/offline_verify.py } else { & mvn.cmd -B verify }
    if ($LASTEXITCODE -ne 0) { throw 'Card bridge build failed' }
    if (!(Test-Path node_modules)) {
        if (Test-Path package-lock.json) { & npm.cmd ci } else { & npm.cmd install }
        if ($LASTEXITCODE -ne 0) { throw 'Cannot install web dependencies. Check npm connectivity and retry.' }
    }
    & npm.cmd test
    if ($LASTEXITCODE -ne 0) { throw 'Web API checks failed' }
    & npm.cmd run build
    if ($LASTEXITCODE -ne 0) { throw 'React build failed' }
    if (!$SkipApplet) { & "$PSScriptRoot/card-applet/build.ps1"; if ($LASTEXITCODE -ne 0) { throw 'Applet build failed' } }
} finally { Pop-Location }
