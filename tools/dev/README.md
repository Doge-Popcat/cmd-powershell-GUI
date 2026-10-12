# Developer tools

Helpers used while working on the application. They are **not** part of the
shipped program — nothing under `src/` depends on them.

## Building them

```powershell
# from the repository root, after .\build.ps1
$javac = "$env:JAVA_HOME\bin\javac.exe"
New-Item -ItemType Directory -Force build\tools | Out-Null
& $javac -encoding UTF-8 -cp build\classes -d build\tools `
    (Get-ChildItem -Recurse tools\dev -Filter *.java | Select-Object -ExpandProperty FullName)
```

Then run any of the following with
`java -cp "build\classes;build\tools" <class>`.

## The tools

| Tool | What it does |
| --- | --- |
| `dev.SessionProbe` | Drives `ShellSession` against the real interpreters and prints what came back. This is what confirmed the code page, the prompt marker and the exit-code protocol. |
| `dev.ParserProbe` | Prints how `ScriptParser` splits example cmd and PowerShell scripts. |
| `dev.DangerProbe` | Exercises the dangerous-command gate by substituting the confirmation handler, then answers it both ways. |
| `dev.GuiDemo` | Opens the real window with a sample script (`cmd`, `ps`, `danger` or `confirm`), for looking at and screenshotting the UI. |
| `screenshot.ps1` | Launches the app, raises its windows (modal dialogs included) and captures a PNG. |

## Safety

`dev.DangerProbe` and `screenshot.ps1 -ProgramArgs danger` are written to be
harmless: dangerous-looking commands point at a folder that does not exist, and
`dev.GuiDemo` never runs its samples on its own. If you change those scripts,
keep it that way — an accepted prompt really does execute the command.
