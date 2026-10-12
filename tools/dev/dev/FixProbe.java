package dev;

import com.dogepopcat.cpgui.analysis.DangerAnalyzer;
import com.dogepopcat.cpgui.analysis.DangerAnalyzer.Finding;
import com.dogepopcat.cpgui.i18n.Language;
import com.dogepopcat.cpgui.i18n.Messages;
import com.dogepopcat.cpgui.shell.ShellKind;
import com.dogepopcat.cpgui.ui.MainFrame;

import javax.swing.Action;
import javax.swing.SwingUtilities;
import java.awt.Color;
import java.awt.Window;
import java.util.List;

/**
 * Developer check for the five reported defects.
 *
 * <pre>
 *   java -cp "build/classes;build/tools" dev.FixProbe
 * </pre>
 */
public final class FixProbe {

    private static int failures;

    private FixProbe() {
    }

    public static void main(String[] args) {
        try {
            Messages.setLanguage(Language.CHINESE);
            punctuationChecks();

            MainFrame[] holder = new MainFrame[1];
            SwingUtilities.invokeAndWait(() -> {
                holder[0] = new MainFrame();
                holder[0].setVisible(true);
            });
            MainFrame frame = holder[0];

            colourChecks(frame);
            clearChecks(frame);
            interpreterChecks(frame);
            aboutChecks(frame);
        } catch (Throwable failure) {
            failure.printStackTrace();
            System.out.println("RESULT: FAIL (probe threw)");
            System.exit(1);
        }
        System.out.println(failures == 0 ? "RESULT: PASS" : "RESULT: FAIL (" + failures + ")");
        System.exit(failures == 0 ? 0 : 1);
    }

    /** Trailing punctuation must not hide an unknown command. */
    private static void punctuationChecks() {
        System.out.println("unknown command detection");
        for (String script : new String[]{"exyzxo", "exyzxo[]", "exyzxo[]()", "exyzxo&",
                "exyzxo()", "exyzxo|more", "@exyzxo", "exyzxo>out.txt"}) {
            List<Finding> findings = DangerAnalyzer.analyze(ShellKind.CMD, script);
            boolean reported = findings.stream()
                    .anyMatch(f -> f.ruleId().equals("unknown.command"));
            report(reported, "reports " + script, findings.isEmpty() ? "(none)"
                    : findings.get(0).message());
        }
        for (String script : new String[]{"dir[]", "echo (a)", "cd ..", "ver&", "whoami/x"}) {
            List<Finding> findings = DangerAnalyzer.analyze(ShellKind.CMD, script);
            boolean clean = findings.stream()
                    .noneMatch(f -> f.ruleId().equals("unknown.command"));
            report(clean, "accepts " + script, clean ? "" : findings.get(0).message());
        }
    }

    /** `color a` must change the console palette instead of failing. */
    private static void colourChecks(MainFrame frame) throws Exception {
        System.out.println("color command");
        Color before = frame.console().paletteForeground();
        submit(frame, "color a");
        Thread.sleep(800);
        Color after = frame.console().paletteForeground();
        report(!before.equals(after), "color a changes the palette",
                String.valueOf(after));
        report(!frame.console().getText().contains("exit code 1"),
                "color a reports no failure", "");

        submit(frame, "color 0e");
        Thread.sleep(800);
        report(frame.console().paletteForeground().equals(new Color(0xF9, 0xF1, 0xA5)),
                "color 0e applies the yellow foreground",
                String.valueOf(frame.console().paletteForeground()));

        submit(frame, "color zz");
        Thread.sleep(500);
        report(frame.console().paletteForeground().equals(new Color(0xF9, 0xF1, 0xA5)),
                "an invalid colour is rejected without changing anything", "");

        submit(frame, "color");
        Thread.sleep(800);
        report(!frame.console().paletteForeground().equals(new Color(0xF9, 0xF1, 0xA5)),
                "color with no argument restores the defaults",
                String.valueOf(frame.console().paletteForeground()));
    }

    /** `cls` must clear our console rather than doing nothing. */
    private static void clearChecks(MainFrame frame) throws Exception {
        System.out.println("cls command");
        submit(frame, "echo something to clear");
        Thread.sleep(1500);
        boolean hadText = frame.console().getText().contains("something to clear");
        submit(frame, "cls");
        Thread.sleep(900);
        boolean cleared = !frame.console().getText().contains("something to clear");
        report(hadText && cleared, "cls clears the console",
                "had=" + hadText + " cleared=" + cleared);
    }

    /** Switching the interpreter must be visible on the console prompt. */
    private static void interpreterChecks(MainFrame frame) throws Exception {
        System.out.println("interpreter switching");
        SwingUtilities.invokeAndWait(() -> frame.toolBar().interpreterBox()
                .setSelectedItem(ShellKind.CMD));
        Thread.sleep(400);
        report("CMD>".equals(frame.console().promptText()), "prompt starts as CMD>",
                frame.console().promptText());

        SwingUtilities.invokeAndWait(() -> frame.toolBar().interpreterBox()
                .setSelectedItem(ShellKind.POWERSHELL));
        Thread.sleep(400);
        report("PS>".equals(frame.console().promptText()),
                "prompt follows an interpreter switch", frame.console().promptText());

        // A PowerShell-only command must actually run in PowerShell.
        submit(frame, "Write-Output ('switched-' + $PSVersionTable.PSVersion.Major)");
        Thread.sleep(3000);
        report(frame.console().getText().contains("switched-"),
                "the switched interpreter runs the command", "");
        report(frame.console().getText().contains("PowerShell"),
                "the run is attributed to PowerShell", "");

        SwingUtilities.invokeAndWait(() -> frame.toolBar().interpreterBox()
                .setSelectedItem(ShellKind.CMD));
        Thread.sleep(400);
        report("CMD>".equals(frame.console().promptText()), "prompt switches back",
                frame.console().promptText());
    }

    /** The About item must actually open a dialog. */
    private static void aboutChecks(MainFrame frame) throws Exception {
        System.out.println("about dialog");
        Action about = null;
        for (java.awt.Component component : frame.getJMenuBar().getComponents()) {
            if (component instanceof javax.swing.JMenu menu) {
                for (javax.swing.JMenuItem item : menuItems(menu)) {
                    String name = item.getText();
                    if (name != null && name.startsWith(Messages.text(
                            com.dogepopcat.cpgui.i18n.Msg.ACTION_ABOUT))) {
                        about = item.getAction();
                    }
                }
            }
        }
        report(about != null, "the About menu item exists", String.valueOf(about));
        if (about == null) {
            return;
        }

        int before = countDialogs(frame);
        final Action aboutAction = about;
        Thread thread = new Thread(() -> aboutAction.actionPerformed(
                new java.awt.event.ActionEvent(frame, 0, "about")));
        thread.setDaemon(true);
        thread.start();
        Thread.sleep(1500);
        int after = countDialogs(frame);
        report(after > before, "the About item opens a dialog",
                "dialogs " + before + " -> " + after);
        SwingUtilities.invokeAndWait(() -> {
            for (Window window : Window.getWindows()) {
                if (window instanceof javax.swing.JDialog dialog && dialog.isVisible()) {
                    dialog.dispose();
                }
            }
        });
        Thread.sleep(300);
    }

    private static java.util.List<javax.swing.JMenuItem> menuItems(javax.swing.JMenu menu) {
        java.util.List<javax.swing.JMenuItem> items = new java.util.ArrayList<>();
        for (int i = 0; i < menu.getItemCount(); i++) {
            javax.swing.JMenuItem item = menu.getItem(i);
            if (item != null) {
                items.add(item);
            }
        }
        return items;
    }

    private static int countDialogs(MainFrame frame) {
        int count = 0;
        for (Window window : Window.getWindows()) {
            if (window instanceof javax.swing.JDialog && window.isVisible()) {
                count++;
            }
        }
        return count;
    }

    private static void submit(MainFrame frame, String command) throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            frame.selectBottomTab(MainFrame.BottomTab.OUTPUT);
            frame.console().inputField().setText(command);
            frame.console().inputField().postActionEvent();
        });
    }

    private static void report(boolean ok, String label, String extra) {
        System.out.printf("  %-7s %-44s %s%n", ok ? "ok" : "FAIL", label, extra);
        if (!ok) {
            failures++;
        }
    }
}
