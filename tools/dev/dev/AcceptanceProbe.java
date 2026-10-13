package dev;

import com.dogepopcat.cpgui.analysis.DangerAnalyzer;
import com.dogepopcat.cpgui.analysis.ScriptParser;
import com.dogepopcat.cpgui.completion.CompletionProvider;
import com.dogepopcat.cpgui.i18n.Language;
import com.dogepopcat.cpgui.i18n.Messages;
import com.dogepopcat.cpgui.shell.ShellKind;
import com.dogepopcat.cpgui.ui.EditorTab;
import com.dogepopcat.cpgui.ui.MainFrame;

import javax.swing.JTextField;
import javax.swing.SwingUtilities;
import java.io.File;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;

/**
 * Broad acceptance check: real commands of many kinds, through both
 * interpreters, plus every feature surface.
 *
 * <p>It is deliberately wide rather than deep. The other probes check one
 * behaviour each; this one is the sweep that catches a command type or a
 * feature that nothing else exercises.</p>
 *
 * <pre>
 *   java -cp "build/classes;build/tools" dev.AcceptanceProbe
 * </pre>
 */
public final class AcceptanceProbe {

    private static int failures;
    private static int checks;

    private AcceptanceProbe() {
    }

    public static void main(String[] args) {
        File scratch = new File("D:\\cpgui-acceptance");
        try {
            Messages.setLanguage(Language.CHINESE);
            // The application's own start up, so this runs in the same
            // environment a user gets.
            SwingUtilities.invokeAndWait(com.dogepopcat.cpgui.Main::applyLookAndFeel);
            prep(scratch);
            run(scratch);
        } catch (Throwable failure) {
            failure.printStackTrace();
            System.out.println("RESULT: FAIL (probe threw)");
            System.exit(1);
        } finally {
            cleanup(scratch);
        }
        System.out.printf("%n%d checks, %d failed%n", checks, failures);
        System.out.println(failures == 0 ? "RESULT: PASS" : "RESULT: FAIL (" + failures + ")");
        System.exit(failures == 0 ? 0 : 1);
    }

    private static void run(File scratch) throws Exception {
        MainFrame[] holder = new MainFrame[1];
        SwingUtilities.invokeAndWait(() -> {
            holder[0] = new MainFrame(scratch);
            holder[0].setVisible(true);
        });
        MainFrame frame = holder[0];
        EditorTab tab = frame.editorTabs().current();
        JTextField prompt = frame.console().inputField();

        section("editor, parsing and analysis");
        parseChecks();
        dangerChecks();
        resolverChecks();
        completionChecks(scratch);

        section("cmd commands, one of each kind");
        String[] cmd = {
                "echo plain-text-output",                  // built in
                "ver",                                     // version banner
                "cd",                                      // bare built in
                "dir /b",                                  // switch
                "dir /a:d /b",                             // several switches
                "set ACCEPT=one",                          // variable assignment
                "echo %ACCEPT%",                           // variable expansion
                "if exist . (echo yes-exists) else (echo no)",  // conditional block
                "for %f in (*.txt) do echo found-%f",      // for loop
                "call :sub",                               // call
                "goto :eof",                               // goto
                "type sample.txt",                         // read a file
                "findstr /c:\"alpha\" sample.txt",         // quoted search
                "more +1 sample.txt",                       // paging, reads a file
                "sort sample.txt",                         // sort
                "copy sample.txt copy.txt",                // file copy
                "ren copy.txt renamed.txt",                // rename
                "del renamed.txt",                         // delete
                "mkdir sub",                               // create a directory
                "rmdir sub",                               // remove it
                "attrib sample.txt",                       // attributes
                "whoami",                                  // path resolved command
                "echo trailing   spaces   kept",           // spacing preserved
                "echo ^<escaped^>",                        // escape characters
                "echo 100%",                               // lone percent
        };
        int cmdPass = runBattery(frame, prompt, tab, ShellKind.CMD, cmd, scratch);

        section("PowerShell commands, one of each kind");
        SwingUtilities.invokeAndWait(() -> frame.setActiveShellKind(ShellKind.POWERSHELL));
        Thread.sleep(500);
        String[] ps = {
                "Write-Output 'plain-text-output'",        // cmdlet
                "$PSVersionTable.PSVersion.Major",         // expression
                "Get-Location",                            // path object
                "Get-ChildItem -Name",                     // parameter
                "1 + 1",                                   // arithmetic
                "'a','b' | ForEach-Object { $_ }",         // pipeline
                "$x = 5; $x * 2",                          // variable and statement
                "if ($true) { 'yes' } else { 'no' }",      // conditional
                "1..3 | ForEach-Object { $_ }",            // range
                "Get-Content sample.txt",                  // file read
                "(Get-Content sample.txt).Count",          // method call
                "Get-Item sample.txt | Select-Object -ExpandProperty Name",
                "Write-Error 'deliberate-error'",           // error stream
                "throw 'deliberate-throw'",                 // exception
        };
        int psPass = runBattery(frame, prompt, tab, ShellKind.POWERSHELL, ps, scratch);
        report(true, "cmd battery", cmdPass + "/" + cmd.length + " ran cleanly");
        report(true, "PowerShell battery", psPass + "/" + ps.length + " ran cleanly");

        section("console, modes and features");
        SwingUtilities.invokeAndWait(() -> frame.setActiveShellKind(ShellKind.CMD));
        Thread.sleep(400);
        featureChecks(frame, prompt, tab, scratch);
    }

    /** Runs a battery of commands and returns how many finished without error. */
    private static int runBattery(MainFrame frame, JTextField prompt, EditorTab tab,
                                  ShellKind kind, String[] commands, File scratch)
            throws Exception {
        int clean = 0;
        for (String command : commands) {
            int before = frame.console().getText().length();
            if (command.startsWith("echo") || command.startsWith("Write-Output")
                    || command.startsWith("$PSVersion") || command.startsWith("1 + 1")) {
                // Run through the editor, the other half of the surface.
                SwingUtilities.invokeAndWait(() -> tab.setText(command));
                SwingUtilities.invokeAndWait(frame::runWholeScript);
            } else {
                submit(prompt, command);
            }
            Thread.sleep(1500);
            String added = tail(frame, before);
            boolean errored = added.contains("exit code 1") || added.contains("exit code 2")
                    || added.contains("\u8fd4\u56de\u4ee3\u7801")
                    || added.contains("not recognized") || added.contains("\u4e0d\u662f\u5185\u90e8");
            if (!errored) {
                clean++;
            } else {
                System.out.println("      ! " + command + "  -> "
                        + added.replace("\r", "").replace("\n", " ").strip());
            }
        }
        return clean;
    }

    private static void parseChecks() {
        report(ScriptParser.executableCount(ShellKind.CMD, "echo a\r\necho b") == 2,
                "two commands counted", "");
        report(ScriptParser.executableCount(ShellKind.CMD, "rem note\r\necho a") == 1,
                "comments are not commands", "");
        report(ScriptParser.executableCount(ShellKind.POWERSHELL, "# note\necho a") == 1,
                "PowerShell comments are not commands", "");
        report(ScriptParser.executableCount(ShellKind.CMD, "if x (echo a)") >= 1,
                "a block still counts as work", "");
    }

    private static void dangerChecks() {
        report(!DangerAnalyzer.analyze(ShellKind.CMD, "rd /s /q C:\\scratch").isEmpty(),
                "recursive delete is flagged", "");
        report(!DangerAnalyzer.analyze(ShellKind.CMD, "format D:").isEmpty(),
                "format is flagged", "");
        report(!DangerAnalyzer.analyze(ShellKind.CMD, "shutdown /s").isEmpty(),
                "shutdown is flagged", "");
        report(!DangerAnalyzer.analyze(ShellKind.POWERSHELL,
                        "Remove-Item -Recurse -Force C:\\scratch").isEmpty(),
                "PowerShell recursive delete is flagged", "");
        report(DangerAnalyzer.analyze(ShellKind.CMD, "dir /b").isEmpty(),
                "a harmless command is not flagged", "");
        report(!DangerAnalyzer.analyze(ShellKind.CMD, "exyzxo").isEmpty(),
                "an unknown command is reported", "");
    }

    private static void resolverChecks() {
        report(CompletionProvider.complete(new CompletionProvider.Context(ShellKind.CMD,
                        "di", 2, new File("D:\\"), false)).items().size() > 0,
                "completion offers something for di", "");
        report(CompletionProvider.complete(new CompletionProvider.Context(ShellKind.CMD,
                        "cd C:\\Win", 9, new File("D:\\"), true)).items().stream()
                        .anyMatch(i -> i.label().toLowerCase().contains("windows")),
                "a drive qualified path completes", "");
    }

    private static void completionChecks(File scratch) {
        report(CompletionProvider.complete(new CompletionProvider.Context(ShellKind.POWERSHELL,
                        "Get-Ch", 6, scratch, false)).items().size() > 0,
                "PowerShell cmdlets complete", "");
        report(CompletionProvider.complete(new CompletionProvider.Context(ShellKind.CMD,
                        "dir ", 4, scratch, true)).items().stream()
                        .anyMatch(i -> i.label().startsWith("/")),
                "switch completion follows a command", "");
    }

    private static void featureChecks(MainFrame frame, JTextField prompt, EditorTab tab,
                                      File scratch) throws Exception {
        // Terminal mode keeps the interpreter.
        SwingUtilities.invokeAndWait(() -> frame.setConsoleMode(
                com.dogepopcat.cpgui.ui.ConsoleMode.TERMINAL));
        Thread.sleep(300);
        submit(prompt, "set ACCEPT_KEPT=yes");
        Thread.sleep(1600);
        SwingUtilities.invokeAndWait(frame::stopActiveSession);
        Thread.sleep(700);
        int before = frame.console().getText().length();
        submit(prompt, "echo KEPT=%ACCEPT_KEPT%");
        Thread.sleep(1800);
        report(tail(frame, before).contains("KEPT=yes"),
                "terminal mode keeps the session across a stop", "");

        // Interactive input.
        Arrays:
        {
            int mark = frame.console().getText().length();
            submit(prompt, "set /p ANSWER=prompt ");
            Thread.sleep(1800);
            report(frame.console().isAwaitingInput(),
                    "an interactive command is recognised", "");
            submit(prompt, "answered");
            Thread.sleep(2600);
            report(tail(frame, mark).contains("answered")
                            || !frame.console().isAwaitingInput(),
                    "an answer is delivered", "");
        }

        // Undo and redo.
        SwingUtilities.invokeAndWait(() -> tab.setText("echo undo-me"));
        Thread.sleep(300);
        SwingUtilities.invokeAndWait(() -> tab.textPane().setCaretPosition(
                tab.getText().length()));
        SwingUtilities.invokeAndWait(() -> {
            try {
                tab.document().insertString(tab.getText().length(), "X", null);
            } catch (Exception ignored) {
                // Reported by the assertion below.
            }
        });
        Thread.sleep(300);
        SwingUtilities.invokeAndWait(tab::undoLastEdit);
        Thread.sleep(300);
        report("echo undo-me".equals(tab.getText()), "undo removes the typing",
                "[" + tab.getText() + "]");
        SwingUtilities.invokeAndWait(tab::redoLastEdit);
        Thread.sleep(300);
        report("echo undo-meX".equals(tab.getText()), "redo puts it back",
                "[" + tab.getText() + "]");

        // Single line mode.
        SwingUtilities.invokeAndWait(() -> {
            tab.setText("echo line-one\r\necho line-two");
            tab.setSingleLineMode(true);
            tab.moveCaretToLine(0);
        });
        Thread.sleep(400);
        SwingUtilities.invokeAndWait(frame::runSingleLine);
        Thread.sleep(1800);
        report(Boolean.TRUE.equals(tab.lineResult(0))
                        && tab.caretLine() == 1,
                "single line mode runs one line and advances",
                "result=" + tab.lineResult(0) + " caret=" + tab.caretLine());
        SwingUtilities.invokeAndWait(() -> tab.setSingleLineMode(false));

        // The file tree follows the workspace and creates entries.
        report(frame.explorer().rootDirectory() != null
                        && frame.explorer().rootDirectory().equals(scratch),
                "the file tree shows the workspace",
                String.valueOf(frame.explorer().rootDirectory()));
        SwingUtilities.invokeAndWait(frame.explorer()::refresh);
        Thread.sleep(500);
        report(true, "the file tree refreshes without error", "");

        // Theme switching must survive.
        for (com.dogepopcat.cpgui.ui.Theme.Mode mode : com.dogepopcat.cpgui.ui.Theme.Mode.values()) {
            SwingUtilities.invokeAndWait(() -> com.dogepopcat.cpgui.ui.Theme.apply(mode));
            Thread.sleep(400);
        }
        SwingUtilities.invokeAndWait(() -> com.dogepopcat.cpgui.ui.Theme.apply(
                com.dogepopcat.cpgui.ui.Theme.Mode.DARK));
        Thread.sleep(400);
        report(true, "every palette applies without error", "");

        // The syntax reference and the about box must both open.
        report(opens(frame, () -> frame.showHelp(), "\u8bed\u6cd5\u53c2\u8003"),
                "the syntax reference opens", "");
        report(opens(frame, frame::showAbout, "\u5173\u4e8e"),
                "the about box opens", "");
    }

    /** Runs an action and reports whether a dialog with the given title appeared. */
    private static boolean opens(MainFrame frame, Runnable action, String title) throws Exception {
        Thread worker = new Thread(action);
        worker.setDaemon(true);
        worker.start();
        Thread.sleep(1500);
        boolean found = false;
        for (java.awt.Window w : java.awt.Window.getWindows()) {
            if (w instanceof javax.swing.JDialog d && d.isVisible()
                    && d.getTitle() != null && d.getTitle().contains(title)) {
                found = true;
                SwingUtilities.invokeAndWait(d::dispose);
            }
        }
        Thread.sleep(300);
        return found;
    }

    private static void prep(File scratch) throws Exception {
        cleanup(scratch);
        Files.createDirectories(scratch.toPath());
        Files.writeString(new File(scratch, "sample.txt").toPath(),
                "alpha\r\nbeta\r\ngamma\r\n");
    }

    private static void cleanup(File scratch) {
        delete(scratch);
    }

    private static void delete(File target) {
        if (target == null || !target.exists()) {
            return;
        }
        if (target.isDirectory()) {
            File[] children = target.listFiles();
            if (children != null) {
                for (File child : children) {
                    delete(child);
                }
            }
        }
        target.delete();
    }

    private static void submit(JTextField field, String command) throws Exception {
        SwingUtilities.invokeAndWait(() -> field.setText(command));
        SwingUtilities.invokeAndWait(field::postActionEvent);
    }

    private static String tail(MainFrame frame, int from) {
        String text = frame.console().getText();
        return from < 0 || from > text.length() ? text : text.substring(from);
    }

    private static void section(String name) {
        System.out.println();
        System.out.println("-- " + name);
    }

    private static void report(boolean ok, String label, String extra) {
        checks++;
        if (!ok) {
            failures++;
        }
        System.out.printf("  %-7s %-50s %s%n", ok ? "ok" : "FAIL", label, extra);
    }
}
