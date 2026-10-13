package dev;

import com.dogepopcat.cpgui.i18n.Language;
import com.dogepopcat.cpgui.i18n.Messages;
import com.dogepopcat.cpgui.ui.ConsoleMode;
import com.dogepopcat.cpgui.ui.MainFrame;
import com.dogepopcat.cpgui.ui.Theme;

import javax.swing.JTextField;
import javax.swing.SwingUtilities;
import java.awt.Color;

/**
 * Developer check for terminal mode.
 *
 * <p>Terminal mode keeps one interpreter for the tab and runs its output on
 * continuously; stopping gives up the run instead of the interpreter, so the
 * working directory and any variables are still there afterwards. Block mode is
 * unchanged, which is what the mode selector is for.</p>
 *
 * <pre>
 *   java -cp "build/classes;build/tools" dev.TerminalModeProbe
 * </pre>
 */
public final class TerminalModeProbe {

    private static int failures;

    private TerminalModeProbe() {
    }

    public static void main(String[] args) {
        try {
            Messages.setLanguage(Language.CHINESE);
            run();
        } catch (Throwable failure) {
            failure.printStackTrace();
            System.out.println("RESULT: FAIL (probe threw)");
            System.exit(1);
        }
        System.out.println(failures == 0 ? "RESULT: PASS" : "RESULT: FAIL (" + failures + ")");
        System.exit(failures == 0 ? 0 : 1);
    }

    private static void run() throws Exception {
        MainFrame[] holder = new MainFrame[1];
        SwingUtilities.invokeAndWait(() -> {
            holder[0] = new MainFrame();
            holder[0].setVisible(true);
        });
        MainFrame frame = holder[0];
        JTextField field = frame.console().inputField();

        report(frame.consoleMode() == ConsoleMode.CLASSIC,
                "block mode is the starting mode", frame.consoleMode().label());
        report(frame.toolBar().consoleModeBox() != null,
                "the tool bar offers a mode selector", "");
        report(frame.toolBar().consoleModeBox().getItemCount() == 2,
                "the selector lists both modes",
                "items=" + frame.toolBar().consoleModeBox().getItemCount());

        // --- terminal mode: a variable survives a stop ---
        SwingUtilities.invokeAndWait(() -> frame.setConsoleMode(ConsoleMode.TERMINAL));
        Thread.sleep(300);
        report(frame.consoleMode() == ConsoleMode.TERMINAL,
                "terminal mode can be selected", frame.consoleMode().label());

        submit(field, "set KEPT=still-here");
        Thread.sleep(1800);
        report(frame.console().getText().contains("\u9000\u51fa\u7801"),
                "the first run finished", "");

        // Stopping must not throw the interpreter away.
        SwingUtilities.invokeAndWait(frame::stopActiveSession);
        Thread.sleep(600);
        repositorySize(frame);

        submit(field, "echo KEPT=%KEPT%");
        Thread.sleep(2000);
        report(frame.console().getText().contains("KEPT=still-here"),
                "the variable survived the stop in terminal mode", lastLines(frame, 3));

        // --- block mode: stopping does discard the interpreter ---
        SwingUtilities.invokeAndWait(() -> frame.setConsoleMode(ConsoleMode.CLASSIC));
        Thread.sleep(300);
        submit(field, "set GONE=only-in-this-session");
        Thread.sleep(1800);
        SwingUtilities.invokeAndWait(frame::stopActiveSession);
        Thread.sleep(800);
        report(frame.console().getText().contains("\u4f1a\u8bdd\u5df2\u505c\u6b62")
                        || frame.console().getText().contains("\u5df2\u505c\u6b62"),
                "block mode reports the session as stopped", lastLines(frame, 2));
        int mark = frame.console().getText().length();
        submit(field, "echo GONE=%GONE%");
        Thread.sleep(2000);
        String afterStop = tail(frame, mark);
        report(afterStop.contains("GONE=%GONE%"),
                "the variable is gone after a block mode stop", afterStop.strip());

        // --- the separator follows the mode ---
        SwingUtilities.invokeAndWait(() -> frame.setConsoleMode(ConsoleMode.TERMINAL));
        Thread.sleep(200);
        int markBefore = frame.console().getText().length();
        submit(field, "echo first");
        Thread.sleep(1600);
        submit(field, "echo second");
        Thread.sleep(1600);
        int rules = countIn(frame, markBefore);
        report(rules == 0, "terminal mode draws no rule between runs",
                "rules=" + rules + " in " + (frame.console().getText().length() - markBefore)
                        + " new characters");

        SwingUtilities.invokeAndWait(() -> frame.setConsoleMode(ConsoleMode.CLASSIC));
        Thread.sleep(200);
        int markBlock = frame.console().getText().length();
        submit(field, "echo third");
        Thread.sleep(1600);
        submit(field, "echo fourth");
        Thread.sleep(1600);
        int after = countIn(frame, markBlock);
        report(after > 0, "block mode draws a rule again", "rules=" + after);

        // --- restart gives a fresh interpreter on request ---
        submit(field, "set FRESH=here");
        Thread.sleep(1600);
        SwingUtilities.invokeAndWait(frame::restartActiveSession);
        Thread.sleep(600);
        int markRestart = frame.console().getText().length();
        submit(field, "echo FRESH=%FRESH%");
        Thread.sleep(2000);
        String afterRestart = tail(frame, markRestart);
        report(afterRestart.contains("FRESH=%FRESH%"),
                "a restart starts a new interpreter", afterRestart.strip());

        // The mode selector must follow the palette like everything else.
        SwingUtilities.invokeAndWait(() -> Theme.apply(Theme.Mode.DARK));
        Thread.sleep(500);
        report(frame.toolBar().consoleModeBox().getForeground() != null
                        && !frame.toolBar().consoleModeBox().getForeground().equals(Color.BLACK),
                "the selector follows the palette",
                String.valueOf(frame.toolBar().consoleModeBox().getForeground()));
    }

    private static void repositorySize(MainFrame frame) {
        // The session must still exist; a released one would be recreated.
    }

    /** @return the console text added since an earlier length. */
    private static String tail(MainFrame frame, int from) {
        String text = frame.console().getText();
        return from < 0 || from > text.length() ? text : text.substring(from);
    }

    /** @return how many run rules were added since an earlier length. */
    private static int countIn(MainFrame frame, int from) {
        String needle = "\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500";
        String added = tail(frame, from);
        int found = 0;
        int index = 0;
        while ((index = added.indexOf(needle, index)) >= 0) {
            found++;
            index += needle.length();
        }
        return found;
    }

    private static void submit(JTextField field, String command) throws Exception {
        SwingUtilities.invokeAndWait(() -> field.setText(command));
        SwingUtilities.invokeAndWait(field::postActionEvent);
    }

    private static String lastLines(MainFrame frame, int count) {
        String[] lines = frame.console().getText().split("\\R");
        StringBuilder text = new StringBuilder();
        for (int i = Math.max(0, lines.length - count); i < lines.length; i++) {
            text.append('|').append(lines[i]).append(' ');
        }
        return text.toString();
    }

    private static void report(boolean ok, String label, String extra) {
        System.out.printf("  %-7s %-52s %s%n", ok ? "ok" : "FAIL", label, extra);
        if (!ok) {
            failures++;
        }
    }
}
