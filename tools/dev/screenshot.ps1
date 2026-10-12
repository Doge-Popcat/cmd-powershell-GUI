<#
.SYNOPSIS
    Developer helper: launch the app, bring its window forward and screenshot it.

.DESCRIPTION
    Used while working on the UI, to confirm that what is drawn matches what was
    intended. Not part of the shipped application.

.EXAMPLE
    .\screenshot.ps1 -Out build\shot.png
    .\screenshot.ps1 -MainClass dev.GuiDemo -ClassPath "build\classes;build\tools" -ProgramArgs danger
#>
param(
    [string]$Jar  = "",
    [string]$Out  = "shot.png",
    [int]$WaitSeconds = 7,
    [string]$Java = "",
    [string]$MainClass = "",
    [string]$ClassPath = "",
    [string]$ProgramArgs = "",
    [switch]$FullScreen
)

$root = Split-Path -Parent (Split-Path -Parent $PSScriptRoot)
if ($Jar -eq "")  { $Jar  = Join-Path $root 'build\cpgui.jar' }
if ($Java -eq "") {
    # Verify each candidate by running it; directory junctions can make a path
    # look present when the executable behind it is not usable.
    $candidates = New-Object System.Collections.Generic.List[string]
    if ($env:JAVA_HOME) { [void]$candidates.Add((Join-Path $env:JAVA_HOME 'bin\java.exe')) }
    foreach ($base in @('D:\Java', 'C:\Program Files\Java', 'C:\Program Files\Eclipse Adoptium')) {
        if (-not (Test-Path -LiteralPath $base)) { continue }
        foreach ($child in Get-ChildItem -LiteralPath $base -Directory -ErrorAction SilentlyContinue) {
            [void]$candidates.Add((Join-Path $child.FullName 'bin\java.exe'))
            foreach ($grand in Get-ChildItem -LiteralPath $child.FullName -Directory -ErrorAction SilentlyContinue) {
                [void]$candidates.Add((Join-Path $grand.FullName 'bin\java.exe'))
            }
        }
    }
    foreach ($candidate in $candidates) {
        if (-not (Test-Path -LiteralPath $candidate)) { continue }
        & $candidate -version *> $null
        if ($LASTEXITCODE -eq 0) { $Java = $candidate; break }
    }
}
if ($Java -eq "") { throw "No usable JDK found. Set JAVA_HOME or pass -Java." }

Add-Type -AssemblyName System.Drawing

Add-Type @"
using System;
using System.Runtime.InteropServices;
public class Win32Shot {
    [DllImport("user32.dll")] public static extern bool SetProcessDPIAware();
    [DllImport("user32.dll")] public static extern bool SetForegroundWindow(IntPtr hWnd);
    [DllImport("user32.dll")] public static extern bool ShowWindow(IntPtr hWnd, int nCmdShow);
    [DllImport("user32.dll")] public static extern bool GetWindowRect(IntPtr hWnd, out RECT lpRect);
    [DllImport("dwmapi.dll")] public static extern int DwmGetWindowAttribute(IntPtr hWnd, int attr, out RECT val, int size);
    [DllImport("user32.dll")] private static extern bool EnumWindows(EnumProc cb, IntPtr param);
    [DllImport("user32.dll")] private static extern uint GetWindowThreadProcessId(IntPtr hWnd, out uint pid);
    [DllImport("user32.dll")] private static extern bool IsWindowVisible(IntPtr hWnd);
    [DllImport("user32.dll")] private static extern bool SetWindowPos(IntPtr hWnd, IntPtr after, int x, int y, int cx, int cy, uint flags);
    [StructLayout(LayoutKind.Sequential)] public struct RECT { public int Left, Top, Right, Bottom; }
    private delegate bool EnumProc(IntPtr hWnd, IntPtr param);
    private static readonly IntPtr TOPMOST = new IntPtr(-1);
    private const uint NOMOVE_NOSIZE_SHOW = 0x0001 | 0x0002 | 0x0040;

    /// Raises every visible window of a process, dialogs included, so a
    /// screenshot cannot end up showing whatever happened to be in front.
    /// EnumWindows walks from the top of the Z order downwards, so the windows
    /// are re-applied in reverse: that keeps popups above the frame they belong
    /// to instead of burying them.
    public static int BringToFront(int pid) {
        System.Collections.Generic.List<IntPtr> found = new System.Collections.Generic.List<IntPtr>();
        EnumWindows((h, p) => {
            uint owner;
            GetWindowThreadProcessId(h, out owner);
            if (owner == (uint)p && IsWindowVisible(h)) {
                found.Add(h);
            }
            return true;
        }, new IntPtr(pid));
        for (int i = found.Count - 1; i >= 0; i--) {
            SetWindowPos(found[i], TOPMOST, 0, 0, 0, 0, NOMOVE_NOSIZE_SHOW);
        }
        if (found.Count > 0) {
            SetForegroundWindow(found[0]);
        }
        return found.Count;
    }
}
"@

[void][Win32Shot]::SetProcessDPIAware()

if ($MainClass -ne "") {
    $argString = '-cp "' + $ClassPath + '" ' + $MainClass
    if ($ProgramArgs -ne "") { $argString = $argString + ' ' + $ProgramArgs }
} else {
    $argString = '-jar "' + $Jar + '"'
}
Write-Output "launching: $Java $argString"
$proc = Start-Process -FilePath $Java -ArgumentList $argString -PassThru
Start-Sleep -Seconds $WaitSeconds

if ($proc.HasExited) { Write-Output "APP EXITED EARLY code=$($proc.ExitCode)"; exit 1 }

$proc.Refresh()
$h = $proc.MainWindowHandle
for ($i = 0; $i -lt 20 -and $h -eq [IntPtr]::Zero; $i++) {
    Start-Sleep -Milliseconds 500
    $proc.Refresh()
    $h = $proc.MainWindowHandle
}
if ($h -eq [IntPtr]::Zero) { Write-Output "NO WINDOW HANDLE"; $proc.Kill(); exit 1 }

[void][Win32Shot]::ShowWindow($h, 5)
[void][Win32Shot]::SetForegroundWindow($h)
# A modal dialog is its own window; raise the whole process, not just the frame.
$raised = [Win32Shot]::BringToFront($proc.Id)
Write-Output "raised $raised window(s)"
Start-Sleep -Milliseconds 900

$r = New-Object Win32Shot+RECT
if ($FullScreen) {
    # Modal dialogs are separate windows, so the whole screen is captured.
    Add-Type -AssemblyName System.Windows.Forms
    $bounds = [System.Windows.Forms.Screen]::PrimaryScreen.Bounds
    $r.Left = $bounds.Left; $r.Top = $bounds.Top
    $r.Right = $bounds.Right; $r.Bottom = $bounds.Bottom
} else {
    # Prefer the DWM frame bounds: GetWindowRect includes the invisible border.
    $ok = [Win32Shot]::DwmGetWindowAttribute($h, 9, [ref]$r, 16)
    if ($ok -ne 0) { [void][Win32Shot]::GetWindowRect($h, [ref]$r) }
}
$w = $r.Right - $r.Left
$hh = $r.Bottom - $r.Top
Write-Output "window rect = $($r.Left),$($r.Top) ${w}x${hh}"

$bmp = New-Object System.Drawing.Bitmap $w, $hh
$g = [System.Drawing.Graphics]::FromImage($bmp)
$g.CopyFromScreen($r.Left, $r.Top, 0, 0, (New-Object System.Drawing.Size($w, $hh)))
$bmp.Save($Out, [System.Drawing.Imaging.ImageFormat]::Png)
$g.Dispose(); $bmp.Dispose()

Write-Output "saved $Out"
Start-Sleep -Milliseconds 300
$proc.Kill()
$proc.WaitForExit()
Write-Output "app closed"
