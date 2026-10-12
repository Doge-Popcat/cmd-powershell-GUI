@echo off
rem Convenience wrapper so the project can be built from cmd.exe without
rem touching the PowerShell execution policy.
setlocal
powershell -NoProfile -ExecutionPolicy Bypass -File "%~dp0build.ps1" %*
if errorlevel 1 (
  echo.
  echo BUILD FAILED
  exit /b 1
)
endlocal
