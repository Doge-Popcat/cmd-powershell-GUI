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

    private GuiDemo() {
    }

    public static void main(String[] args) throws Exception {
        String mode = args.length > 0 ? args[0].toLowerCase(java.util.Locale.ROOT) : "cmd";
        boolean powershell = mode.equals("ps");
        boolean danger = mode.equals("danger");
        boolean confirm = mode.equals("confirm");
        boolean complete = mode.equals("complete");
        boolean path = mode.equals("path");
        boolean hint = mode.equals("hint");
        String script = powershell ? PS_SCRIPT : (danger || confirm) ? DANGER_SCRIPT : CMD_SCRIPT;

        String demoText = complete ? "Get-Ch"
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
            if (danger) {
                tab.diagnostics().refresh();
                frame.selectBottomTab("Problems");
            }
            frame.statusBar().setMessage("Demo \u2014 " + tab.shellKind().displayName());
            if (confirm) {
                // Opens the real modal warning so it can be inspected.
                frame.runActiveScript();
            }
            if (complete || path) {
                // Wait for layout so the popup can be placed at the caret.
                SwingUtilities.invokeLater(() -> tab.completion().trigger(true));
            }
            if (hint) {
                // Let the layout settle, then show the signature hint alone.
                javax.swing.Timer timer = new javax.swing.Timer(800, e -> {
                    tab.completion().close();
                    com.dogepopcat.cpgui.completion.ParameterHint parameterHint =
                            com.dogepopcat.cpgui.completion.ParameterHint.forCaret(
                                    tab.shellKind(), tab.getText(),
                                    tab.textPane().getCaretPosition());
                    if (parameterHint != null) {
                        tab.completion().hintWindow().show(tab.textPane(),
                                tab.textPane().getCaretPosition(), parameterHint);
                    }
                });
                timer.setRepeats(false);
                timer.start();
            }
        });
        Thread.sleep(Long.MAX_VALUE);
    }
}
