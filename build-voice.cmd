@echo off
setlocal
cd /d "%~dp0"
set "FW=%WINDIR%\Microsoft.NET\Framework64\v4.0.30319"
if not exist voice-assets\voice mkdir voice-assets\voice
"%FW%\csc.exe" /nologo /target:exe /platform:x64 /optimize+ /out:voice-assets\voice\VoiceHost.exe src\VoiceHost.cs
if errorlevel 1 exit /b 1
echo VoiceHost build succeeded. Keep the supplied sherpa-onnx DLL and model files.
