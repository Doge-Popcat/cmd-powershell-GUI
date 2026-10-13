@echo off
rem Build with the JDK below and start the GUI, printing exactly what was built.
rem This bypasses JDK discovery so the same build is always launched.
setlocal
set "JDK=D:\Java\jdk-27"
set "ROOT=%~dp0"

echo ============================================================
echo JDK      : %JDK%
if not exist "%JDK%\bin\javac.exe" (
    echo ERROR: no JDK at %JDK%
    pause
    exit /b 1
)

echo Building ...
powershell -NoProfile -ExecutionPolicy Bypass -File "%ROOT%build.ps1"
if errorlevel 1 (
    echo ERROR: the build failed, so the old jar would have been started.
    pause
    exit /b 1
)

echo.
echo ============================================================
echo Jar      : %ROOT%build\cpgui.jar
for %%F in ("%ROOT%build\cpgui.jar") do echo Built at : %%~tF   size %%~zF bytes
for /f "skip=1 tokens=* delims=" %%H in ('certutil -hashfile "%ROOT%build\cpgui.jar" SHA256 ^| findstr /r /v ":"') do (
    echo SHA256   : %%H
    goto :hashingDone
)
:hashingDone

echo Commit   :
pushd "%ROOT%"
git log --oneline -1
popd

echo ============================================================
echo Starting Cmd/PowerShell GUI ...
echo.
start "" "%JDK%\bin\javaw.exe" -jar "%ROOT%build\cpgui.jar"
endlocal
