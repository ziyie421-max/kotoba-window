@echo off
setlocal
cd /d "%~dp0"
set "FW=%WINDIR%\Microsoft.NET\Framework64\v4.0.30319"
if not exist "%FW%\csc.exe" set "FW=%WINDIR%\Microsoft.NET\Framework\v4.0.30319"
if not exist build mkdir build
"%FW%\csc.exe" /nologo /target:winexe /platform:anycpu /optimize+ /out:build\KotobaWindow.exe /r:"%FW%\System.dll" /r:"%FW%\System.Core.dll" /r:"%FW%\System.Xml.dll" /r:"%FW%\System.Xaml.dll" /r:"%FW%\System.Web.Extensions.dll" /r:"%FW%\System.Drawing.dll" /r:"%FW%\System.Windows.Forms.dll" /r:"%FW%\WPF\PresentationCore.dll" /r:"%FW%\WPF\PresentationFramework.dll" /r:"%FW%\WPF\WindowsBase.dll" /r:"%FW%\WPF\System.Speech.dll" /win32manifest:src\app.manifest /win32icon:src\app.ico /resource:data\vocab.json,vocab.json src\Core.cs src\Main.cs src\VoiceService.cs src\AssemblyInfo.cs
if errorlevel 1 exit /b 1
copy /Y src\KotobaWindow.exe.config build\KotobaWindow.exe.config >nul
echo Build succeeded: build\KotobaWindow.exe
