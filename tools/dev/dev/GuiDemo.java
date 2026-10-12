package dev;

import com.dogepopcat.cpgui.shell.ShellKind;
import com.dogepopcat.cpgui.ui.EditorTab;
import com.dogepopcat.cpgui.ui.MainFrame;

import javax.swing.SwingUtilities;

/**
 * Developer demo: opens the real window with a sample script so the UI can be
 * inspected, screenshotted and clicked through by hand.
 *
 * <p>None of the sample scripts is executed automatically. The "danger" example
 * deliberately uses scratch paths, because it exists only so the diagnostics
 * view has something to report.</p>
 *
 * <pre>
 *   java -cp "build/classes;build/tools" dev.GuiDemo [cmd|ps|danger]
 * </pre>
 */
public final class GuiDemo {

    static final String CMD_SCRIPT = String.join("\r\n",
            "REM === cmd syntax demo ===",
            "set TARGET=C:\\Windows\\Temp",
            "if exist \"%TARGET%\" (",
            "    dir /b /a \"%TARGET%\"",
            ") else (",
            "    echo \"not found\" 2>&1",
            ")",
            "",
            ":cleanup",
            "for %%f in (*.log) do del /q \"%%f\"",
            "echo done ^& rem trailing note",
            "ver");

    static final String PS_SCRIPT = String.join("\r\n",
            "# === PowerShell syntax demo ===",
            "param([string]$Root = 'D:\\cmd-powershellGUI')",
            "$count = 0",
            "foreach ($file in Get-ChildItem -Path $Root -Recurse) {",
            "    if ($file.Length -gt 1MB -and $file.Name -like '*.java') {",
            "        Write-Output \"large: $($file.FullName)\"",
            "        $count = $count + 1",
            "    }",
            "}",
            "Write-Host \"total = $count\" -ForegroundColor Green");

    /** Illustrative only: every path here points at a scratch folder. */
    static final String DANGER_SCRIPT = String.join("\r\n",
            "REM === dangerous command demo ===",
            "echo inspecting the scratch folder",
            "dir /b \"D:\\scratch\\demo\"",
            "",
            "REM this deletes an entire folder tree permanently",
            "rd /s /q \"D:\\scratch\\demo\\output\"",
            "del /f /s /q \"D:\\scratch\\demo\\output\\*\"",
            "takeown /f \"D:\\scratch\\demo\\output\" /r",
            "reg delete HKLM\\Software\\Contoso\\Demo /f",
            "shutdown /a",
            "echo still fine");

    /** Illustrative only: a mistyped command among correct ones. */
    static final String TYPO_SCRIPT = String.join("\r\n",
            "REM === unknown command demo ===",
            "echo starting",
            "dir /b",
            "exyzxo /b",
            "Get-NonexistentThing",
            "mkdir \"D:\\scratch\\demo\\output\"",
            "echo finished");

    private GuiDemo() {
    }

    public static void main(String[] args) throws Exception {
        String mode = args.length > 0 ? args[0].toLowerCase(java.util.Locale.ROOT) : "cmd";
        boolean powershell = mode.equals("ps");
        boolean danger = mode.equals("danger");
        boolean confirm = mode.equals("confirm");
        boolean complete = mode.equals("complete");
        boolean list = mode.equals("list");
        boolean path = mode.equals("path");
        boolean hint = mode.equals("hint");
        boolean typo = mode.equals("typo");
        boolean consoleMode = mode.equals("console");
        String script = powershell ? PS_SCRIPT
                : typo ? TYPO_SCRIPT
                : (danger || confirm) ? DANGER_SCRIPT : CMD_SCRIPT;

        String demoText = complete ? "Get-Ch"
                : list ? "di"
                : path ? "dir build\\"
                : hint ? "dir /b /s"
                : script;

        SwingUtilities.invokeLater(() -> {
            MainFrame frame = new MainFrame();
            frame.setVisible(true);
            if (powershell || complete) {
                frame.setActiveShellKind(ShellKind.POWERSHELL);
            }
            EditorTab tab = frame.editorTabs().current();
            tab.setText(demoText);
            tab.textPane().setCaretPosition(tab.getText().length());
            if (danger || typo) {
                tab.diagnostics().refresh();
                frame.selectBottomTab("Problems");
            }
            frame.statusBar().setMessage("Demo \u2014 " + tab.shellKind().displayName());
            if (confirm) {
                // Opens the real modal warning so it can be inspected.
                frame.runActiveScript();
            }
            if (complete || path || list) {
                // Wait for layout so suggestions can be placed at the caret.
                SwingUtilities.invokeLater(() -> tab.completion().trigger(false));
            }
            if (consoleMode) {
                // Type at the console prompt so the screenshot shows a real run.
                javax.swing.Timer timer = new javax.swing.Timer(700, e -> {
                    frame.selectBottomTab("Output");
                    frame.console().inputField().setText("echo console prompt works");
                    frame.console().inputField().postActionEvent();
                });
                timer.setRepeats(false);
                timer.start();
            }
            if (hint) {
                // The signature now lives in the status bar; just leave the caret
                // in the argument area so it appears.
                tab.textPane().setCaretPosition(tab.getText().length());
                tab.completion().trigger(false);
            }
        });
        Thread.sleep(Long.MAX_VALUE);
    }
}
