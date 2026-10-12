@echo off
rem Build the project (if needed) and start the GUI.
setlocal
powershell -NoProfile -ExecutionPolicy Bypass -File "%~dp0build.ps1" -Run
endlocal
