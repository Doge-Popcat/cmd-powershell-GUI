<#
.SYNOPSIS
    Packages the application as a self-contained Windows application image.

.DESCRIPTION
    Uses the JDK's own jpackage tool to produce dist\CmdPowerShellGUI, containing
    a launcher executable and a trimmed Java runtime. The result runs on a machine
    that has no JDK installed, and needs no installer, no registry entries and no
    files outside the dist folder.

    The application icon is rendered from the Java2D artwork at build time, so the
    repository stays free of binary assets.

.PARAMETER Clean
    Delete the dist directory before packaging.

.PARAMETER SkipIcon
    Package with jpackage's default icon.

.EXAMPLE
    .\package.ps1 -Clean
#>
[CmdletBinding()]
param(
    [switch]$Clean,
    [switch]$SkipIcon
)

$ErrorActionPreference = 'Stop'
Set-StrictMode -Version Latest

$root       = $PSScriptRoot
$buildDir   = Join-Path $root 'build'
$classesDir = Join-Path $buildDir 'classes'
$toolsDir   = Join-Path $buildDir 'tools'
$distDir    = Join-Path $root 'dist'
$iconFile   = Join-Path $buildDir 'cpgui.ico'
$jarFile    = Join-Path $buildDir 'cpgui.jar'

function Find-Jdk {
    $candidates = New-Object System.Collections.Generic.List[string]
    if ($env:JAVA_HOME) { [void]$candidates.Add($env:JAVA_HOME) }
    foreach ($base in @('D:\Java', 'C:\Program Files\Java', 'C:\Program Files\Eclipse Adoptium',
                        'C:\Program Files\Microsoft\jdk')) {
        if (-not (Test-Path -LiteralPath $base)) { continue }
        foreach ($child in Get-ChildItem -LiteralPath $base -Directory -ErrorAction SilentlyContinue) {
            [void]$candidates.Add($child.FullName)
            foreach ($grand in Get-ChildItem -LiteralPath $child.FullName -Directory -ErrorAction SilentlyContinue) {
                [void]$candidates.Add($grand.FullName)
            }
        }
    }
    foreach ($candidate in $candidates) {
        if (Test-Path -LiteralPath (Join-Path $candidate 'bin\jpackage.exe')) { return $candidate }
    }
    return $null
}

$jdk = Find-Jdk
if (-not $jdk) {
    throw "No JDK with jpackage found. Install a JDK (17 or newer) or set JAVA_HOME."
}
$jpackage = Join-Path $jdk 'bin\jpackage.exe'
$java     = Join-Path $jdk 'bin\java.exe'
$javac    = Join-Path $jdk 'bin\javac.exe'
Write-Host "JDK      : $jdk"

# --- build the jar ---------------------------------------------------------
Write-Host "Building : running build.ps1"
& (Join-Path $root 'build.ps1') | Out-Null
if (-not (Test-Path -LiteralPath $jarFile)) { throw "build.ps1 did not produce $jarFile" }

# --- application icon ------------------------------------------------------
$iconArgs = @()
if (-not $SkipIcon) {
    Write-Host "Icon     : rendering from the Java2D artwork"
    New-Item -ItemType Directory -Force -Path $toolsDir | Out-Null
    $toolSources = @(Get-ChildItem -LiteralPath (Join-Path $root 'tools\dev') -Recurse -Filter '*.java' -File |
                     Select-Object -ExpandProperty FullName)
    $argFile = Join-Path $buildDir 'tools.args'
    $utf8NoBom = New-Object System.Text.UTF8Encoding($false)
    [System.IO.File]::WriteAllLines($argFile, [string]$toolSources, $utf8NoBom)
    & $javac "-J-Duser.language=en" -encoding UTF-8 -cp $classesDir -d $toolsDir "@$argFile"
    if ($LASTEXITCODE -ne 0) { throw "Could not compile the developer tools needed for the icon." }

    & $java -cp "$classesDir;$toolsDir" dev.IconExport $iconFile
    if ($LASTEXITCODE -ne 0 -or -not (Test-Path -LiteralPath $iconFile)) {
        Write-Host "Icon     : rendering failed, falling back to the default" -ForegroundColor Yellow
    } else {
        $iconArgs = @('--icon', $iconFile)
    }
}

# --- package ---------------------------------------------------------------
if ($Clean -and (Test-Path -LiteralPath $distDir)) {
    Write-Host "Cleaning : $distDir"
    Remove-Item -LiteralPath $distDir -Recurse -Force
}
New-Item -ItemType Directory -Force -Path $distDir | Out-Null

Write-Host "Packing  : jpackage app-image"
$jpackageArgs = @(
    '--type', 'app-image',
    '--name', 'CmdPowerShellGUI',
    '--input', $buildDir,
    '--main-jar', 'cpgui.jar',
    '--main-class', 'com.dogepopcat.cpgui.Main',
    '--dest', $distDir,
    '--app-version', '0.1.0',
    '--vendor', 'Doge-Popcat',
    '--description', 'Graphical front end for cmd.exe and powershell.exe',
    '--copyright', 'MIT licensed',
    # Only the modules this program actually uses, so the bundled runtime stays
    # small. jlink resolves their transitive dependencies automatically.
    '--add-modules', 'java.base,java.desktop,java.logging,java.prefs,java.xml,jdk.unsupported'
) + $iconArgs

& $jpackage @jpackageArgs
if ($LASTEXITCODE -ne 0) { throw "jpackage failed with exit code $LASTEXITCODE." }

$launcher = Join-Path $distDir 'CmdPowerShellGUI\CmdPowerShellGUI.exe'
Write-Host ""
if (Test-Path -LiteralPath $launcher) {
    $size = (Get-ChildItem -LiteralPath (Join-Path $distDir 'CmdPowerShellGUI') -Recurse -File |
             Measure-Object -Property Length -Sum).Sum / 1MB
    Write-Host ("Package OK -> {0} ({1:N1} MB)" -f $launcher, $size) -ForegroundColor Green
    Write-Host "Run it with:" -ForegroundColor Cyan
    Write-Host "    $launcher"
} else {
    Write-Host "jpackage finished but the launcher was not found." -ForegroundColor Yellow
}
