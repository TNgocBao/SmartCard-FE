param([string]$ApiBaseUrl='http://127.0.0.1:8080',[switch]$Dev,[switch]$DemoCard)
$ErrorActionPreference='Stop'
if (!$Dev -and !(Test-Path "$PSScriptRoot/dist/index.html")) { throw 'React is not built. Run npm install and npm run build, or use run.ps1 -Dev with npm run dev.' }
$savedApiBaseUrl=$env:IDP_API_BASE_URL
$savedDemoCard=$env:IDP_DEMO_CARD
if ($DemoCard) { $env:IDP_DEMO_CARD='true' }
$env:IDP_API_BASE_URL=$ApiBaseUrl
if (!$env:JAVA_HOME -or !(Test-Path "$env:JAVA_HOME/bin/java.exe")) { $env:JAVA_HOME='C:/Program Files/Java/jdk-21.0.11' }
if (!$env:JC_HOME_SIMULATOR) { $env:JC_HOME_SIMULATOR='D:/SmartCard/simulator' }
$classpath="$PSScriptRoot/target/classes;$PSScriptRoot/target/dependency/*"
$provider="$env:JC_HOME_SIMULATOR/client/COMService/socketprovider.jar"
if (!(Test-Path "$PSScriptRoot/target/classes/vn/kma/desktop/WebApplication.class")) { throw 'Run build.ps1 -SkipApplet first.' }
Push-Location $PSScriptRoot
try {
    $arguments=@('-Djava.awt.headless=true')
    if (Test-Path $provider) { $arguments+=@('--module-path',$provider,'--add-modules','ALL-MODULE-PATH') }
    Write-Host 'Open http://127.0.0.1:8787 after building React, or start npm run dev in a second terminal.'
    & "$env:JAVA_HOME/bin/java.exe" @arguments -cp $classpath vn.kma.desktop.WebApplication
    if ($LASTEXITCODE -ne 0) { throw 'Web service did not start; check port 8787 and backend configuration.' }
} finally { Pop-Location; $env:IDP_API_BASE_URL=$savedApiBaseUrl; $env:IDP_DEMO_CARD=$savedDemoCard }
