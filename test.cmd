@echo off
setlocal
cd /d "%~dp0"
set "FW=%WINDIR%\Microsoft.NET\Framework64\v4.0.30319"
if not exist "%FW%\csc.exe" set "FW=%WINDIR%\Microsoft.NET\Framework\v4.0.30319"
if not exist build mkdir build
"%FW%\csc.exe" /nologo /out:build\CoreTests.exe /r:"%FW%\System.Web.Extensions.dll" src\Core.cs tests\CoreTests.cs
if errorlevel 1 exit /b 1
build\CoreTests.exe
if errorlevel 1 exit /b 1
"%FW%\csc.exe" /nologo /main:UiSmokeTests /out:build\UiSmokeTests.exe /r:"%FW%\System.dll" /r:"%FW%\System.Core.dll" /r:"%FW%\System.Xml.dll" /r:"%FW%\System.Xaml.dll" /r:"%FW%\System.Web.Extensions.dll" /r:"%FW%\System.Drawing.dll" /r:"%FW%\System.Windows.Forms.dll" /r:"%FW%\WPF\PresentationCore.dll" /r:"%FW%\WPF\PresentationFramework.dll" /r:"%FW%\WPF\WindowsBase.dll" /r:"%FW%\WPF\System.Speech.dll" /resource:data\vocab.json,vocab.json src\Core.cs src\Main.cs src\VoiceService.cs tests\UiSmokeTests.cs
if errorlevel 1 exit /b 1
build\UiSmokeTests.exe
exit /b %errorlevel%
