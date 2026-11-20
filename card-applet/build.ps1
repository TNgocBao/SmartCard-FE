$ErrorActionPreference = 'Stop'
if (!$env:JAVA_HOME -or !(Test-Path "$env:JAVA_HOME\bin\javac.exe")) { $env:JAVA_HOME = 'C:\Program Files\Java\jdk-21.0.11' }
if (!$env:JC_HOME_TOOLS) { $env:JC_HOME_TOOLS = 'D:\SmartCard\tools' }
if (!$env:JC_HOME_SIMULATOR) { $env:JC_HOME_SIMULATOR = 'D:\SmartCard\simulator' }
$tools = $env:JC_HOME_TOOLS
$sim = $env:JC_HOME_SIMULATOR
New-Item -ItemType Directory -Force -Path "$PSScriptRoot\build\classes", "$PSScriptRoot\build\cap", "$PSScriptRoot\build\tools" | Out-Null
& "$env:JAVA_HOME\bin\javac.exe" -g -source 10 -target 10 -Xlint:-options -cp "$tools\lib\api_classic-3.2.0.jar" -d "$PSScriptRoot\build\classes" "$PSScriptRoot\src\kma\idp\card\IdentityApplet.java"
if ($LASTEXITCODE -ne 0) { throw 'Applet compilation failed' }
& "$env:JAVA_HOME\bin\java.exe" "-Djc.home=$tools" -cp "$tools\lib\*" com.sun.javacard.converter.Main -i -classdir "$PSScriptRoot\build\classes" -applet '0xF0:0x4B:0x4D:0x41:0x49:0x44:0x50:0x01' kma.idp.card.IdentityApplet -out CAP EXP JCA -d "$PSScriptRoot\build\cap" -target 3.2.0 kma.idp.card '0xF0:0x4B:0x4D:0x41:0x49:0x44:0x50' 1.0
if ($LASTEXITCODE -ne 0) { throw 'CAP conversion failed' }
& "$env:JAVA_HOME\bin\javac.exe" -p "$sim\client\AMService\amservice.jar;$sim\client\COMService\socketprovider.jar" --add-modules ALL-MODULE-PATH -d "$PSScriptRoot\build\tools" "$PSScriptRoot\tools\InstallApplet.java"
if ($LASTEXITCODE -ne 0) { throw 'Installer compilation failed' }
Write-Host 'Applet CAP and installer built.'
