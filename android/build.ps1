param(
 [string]$ToolRoot = 'D:\CodexTools\android',
 [string]$JavaBin = 'C:\Program Files\bin',
 [string]$SigningDir = 'D:\CodexSigning\kotoba-android'
)
$ErrorActionPreference = 'Stop'
$env:PATHEXT = '.COM;.EXE;.BAT;.CMD'
$ProgressPreference = 'SilentlyContinue'
$root = $PSScriptRoot
$bt = Join-Path $ToolRoot 'tools\android-15'
$androidJar = Join-Path $ToolRoot 'platform\android-35\android.jar'
function Native([string]$exe, [string[]]$argv) {
 & $exe @argv | Out-Host
 if ($LASTEXITCODE -ne 0) { throw "Build step failed: $exe ($LASTEXITCODE)" }
}
foreach ($dir in @('build','build\classes','build\generated','build\dex','build\package','release')) {
 New-Item -ItemType Directory -Path (Join-Path $root $dir) -Force | Out-Null
}
Native (Join-Path $bt 'aapt2.exe') @('compile','--dir',"$root\res",'-o',"$root\build\resources.zip")
Native (Join-Path $bt 'aapt2.exe') @('link','-o',"$root\build\base.apk",'-I',$androidJar,'--manifest',"$root\AndroidManifest.xml",'--java',"$root\build\generated","$root\build\resources.zip")
$sources = @(Get-ChildItem -LiteralPath "$root\src" -Recurse -Filter '*.java' | ForEach-Object {$_.FullName})
$sources += "$root\build\generated\com\kotoba\window\R.java"
$classpath = "$androidJar;$ToolRoot\sherpa\classes.jar;$ToolRoot\kotlin-stdlib.jar"
Native (Join-Path $JavaBin 'javac.exe') (@('-J-Duser.language=en','-encoding','UTF-8','-source','8','-target','8','-classpath',$classpath,'-d',"$root\build\classes") + $sources)
Native (Join-Path $JavaBin 'jar.exe') @('cf',"$root\build\classes.jar",'-C',"$root\build\classes",'.')
Native (Join-Path $JavaBin 'java.exe') @('-cp',"$ToolRoot\r8.jar",'com.android.tools.r8.D8','--lib',$androidJar,'--min-api','26','--output',"$root\build\dex","$root\build\classes.jar","$ToolRoot\sherpa\classes.jar","$ToolRoot\kotlin-stdlib.jar")
Copy-Item -LiteralPath "$root\build\dex\classes.dex" -Destination "$root\build\package\classes.dex" -Force
foreach ($abi in @('arm64-v8a','x86_64')) {
 New-Item -ItemType Directory -Path "$root\build\package\lib\$abi" -Force | Out-Null
 Get-ChildItem -LiteralPath "$ToolRoot\sherpa\jni\$abi" -Filter '*.so' | Copy-Item -Destination "$root\build\package\lib\$abi" -Force
}
# jar uses portable '/' ZIP paths. aapt2 35 on Windows emits backslashes for nested -A assets.
Native (Join-Path $JavaBin 'jar.exe') @('uf',"$root\build\base.apk",'-C',$root,'assets','-C',"$root\build\package",'.')
Native (Join-Path $bt 'zipalign.exe') @('-P','16','-f','4',"$root\build\base.apk","$root\build\aligned.apk")
$apk = "$root\release\KotobaWindow-Android-v1.0.0.apk"
Native (Join-Path $JavaBin 'java.exe') @('-jar',"$bt\lib\apksigner.jar",'sign','--ks',"$SigningDir\release.jks",'--ks-pass',"file:$SigningDir\password.txt",'--out',$apk,"$root\build\aligned.apk")
Native (Join-Path $JavaBin 'java.exe') @('-jar',"$bt\lib\apksigner.jar",'verify','--verbose',$apk)
Get-FileHash -LiteralPath $apk -Algorithm SHA256
