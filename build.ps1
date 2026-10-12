<#
.SYNOPSIS
    Builds the Cmd/PowerShell GUI desktop application.

.DESCRIPTION
    Compiles every Java source under src/main/java with javac, copies the
    contents of src/main/resources into the class output directory and packages
    a runnable jar at build/cpgui.jar.

    The script is dependency free: it only needs a JDK on this machine. It looks
    for javac in JAVA_HOME first and then scans the usual installation roots.

.PARAMETER Clean
    Delete the build directory before compiling.

.PARAMETER Run
    Launch the application after a successful build.

.EXAMPLE
    .\build.ps1 -Clean -Run
#>
[CmdletBinding()]
param(
    [switch]$Clean,
    [switch]$Run
)

$ErrorActionPreference = 'Stop'
Set-StrictMode -Version Latest

$root       = $PSScriptRoot
$srcDir     = Join-Path $root 'src\main\java'
$resDir     = Join-Path $root 'src\main\resources'
$buildDir   = Join-Path $root 'build'
$classesDir = Join-Path $buildDir 'classes'
$jarFile    = Join-Path $buildDir 'cpgui.jar'

function Find-Jdk {
    $candidates = New-Object System.Collections.Generic.List[string]

    if ($env:JAVA_HOME) { [void]$candidates.Add($env:JAVA_HOME) }

    $roots = @('D:\Java', 'C:\Program Files\Java', 'C:\Program Files\Eclipse Adoptium',
               'C:\Program Files\Microsoft\jdk', "$env:LOCALAPPDATA\Programs\Java")
    foreach ($base in $roots) {
        if (-not (Test-Path -LiteralPath $base)) { continue }
        foreach ($child in Get-ChildItem -LiteralPath $base -Directory -ErrorAction SilentlyContinue) {
            [void]$candidates.Add($child.FullName)
            foreach ($grand in Get-ChildItem -LiteralPath $child.FullName -Directory -ErrorAction SilentlyContinue) {
                [void]$candidates.Add($grand.FullName)
            }
        }
    }

    foreach ($candidate in $candidates) {
        if (Test-Path -LiteralPath (Join-Path $candidate 'bin\javac.exe')) { return $candidate }
    }
    return $null
}

$jdk = Find-Jdk
if (-not $jdk) {
    throw "No JDK found. Install a JDK (17 or newer) or set JAVA_HOME, then re-run this script."
}

$javac = Join-Path $jdk 'bin\javac.exe'
$jar   = Join-Path $jdk 'bin\jar.exe'
$java  = Join-Path $jdk 'bin\java.exe'

Write-Host "JDK      : $jdk"
& $javac -version

if ($Clean -and (Test-Path -LiteralPath $buildDir)) {
    Write-Host "Cleaning : $buildDir"
    Remove-Item -LiteralPath $buildDir -Recurse -Force
}

New-Item -ItemType Directory -Force -Path $classesDir | Out-Null

# --- collect sources -------------------------------------------------------
$sources = @(Get-ChildItem -LiteralPath $srcDir -Recurse -Filter '*.java' -File |
             Select-Object -ExpandProperty FullName)
if ($sources.Count -eq 0) { throw "No Java sources found under $srcDir" }
Write-Host ("Compiling: {0} source file(s)" -f $sources.Count)

$argFile = Join-Path $buildDir 'javac.args'
# javac reads an argument file literally, so it must not start with a BOM.
$utf8NoBom = New-Object System.Text.UTF8Encoding($false)
[System.IO.File]::WriteAllLines($argFile, [string[]]$sources, $utf8NoBom)
# Force English diagnostics so compiler output is readable in any console codepage.
# Force English diagnostics so compiler output is readable in any console codepage.
& $javac "-J-Duser.language=en" "-J-Duser.country=US" -encoding UTF-8 -d $classesDir "@$argFile"
if ($LASTEXITCODE -ne 0) { throw "Compilation failed with exit code $LASTEXITCODE." }

# --- copy resources --------------------------------------------------------
if (Test-Path -LiteralPath $resDir) {
    Write-Host "Resources: copying"
    Copy-Item -Path (Join-Path $resDir '*') -Destination $classesDir -Recurse -Force
}

# --- package ---------------------------------------------------------------
$manifest = Join-Path $buildDir 'MANIFEST.MF'
@(
    'Manifest-Version: 1.0'
    'Main-Class: com.dogepopcat.cpgui.Main'
    'Implementation-Title: Cmd/PowerShell GUI'
    'Implementation-Version: 0.1.0'
    ''
) | Set-Content -LiteralPath $manifest -Encoding ASCII

& $jar --create --file $jarFile --manifest $manifest -C $classesDir .
if ($LASTEXITCODE -ne 0) { throw "jar packaging failed with exit code $LASTEXITCODE." }

Write-Host ""
Write-Host "Build OK -> $jarFile" -ForegroundColor Green
Write-Host ("Run with : `"{0}`" -jar `"{1}`"" -f $java, $jarFile)

if ($Run) {
    Write-Host "Starting application ..." -ForegroundColor Cyan
    Start-Process -FilePath $java -ArgumentList '-jar', $jarFile
}
