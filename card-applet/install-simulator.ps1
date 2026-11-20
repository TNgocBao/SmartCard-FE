$ErrorActionPreference = 'Stop'
if (!$env:JAVA_HOME -or !(Test-Path "$env:JAVA_HOME\bin\java.exe")) { $env:JAVA_HOME='C:\Program Files\Java\jdk-21.0.11' }
if (!$env:JC_HOME_SIMULATOR) { $env:JC_HOME_SIMULATOR='D:\SmartCard\simulator' }
$sim=$env:JC_HOME_SIMULATOR
$cap="$PSScriptRoot\build\cap\kma\idp\card\javacard\card.cap"
if (!(Test-Path $cap)) { throw 'Run build.ps1 first' }
& "$env:JAVA_HOME\bin\java.exe" -p "$sim\client\AMService\amservice.jar;$sim\client\COMService\socketprovider.jar" --add-modules ALL-MODULE-PATH -cp "$PSScriptRoot\build\tools" InstallApplet $cap "$sim\samples\client.config.properties"
if ($LASTEXITCODE -ne 0) { throw 'Simulator installation failed; inspect simulator and secure-channel configuration' }
