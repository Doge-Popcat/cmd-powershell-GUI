package dev;

import com.dogepopcat.cpgui.i18n.Language;
import com.dogepopcat.cpgui.i18n.Messages;
import com.dogepopcat.cpgui.ui.EditorTab;
import com.dogepopcat.cpgui.ui.MainFrame;

import javax.swing.SwingUtilities;

/**
 * Developer check for single line mode.
 *
 * <pre>
 *   java -cp "build/classes;build/tools" dev.SingleLineProbe
 * </pre>
 */
public final class SingleLineProbe {

    private static int failures;

    private SingleLineProbe() {
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
        EditorTab tab = frame.editorTabs().current();

        String script = String.join("\r\n",
                "echo line-one",
                "REM a comment line",
                "echo line-three",
                "exyzxo-typo",
                "echo line-five");
        SwingUtilities.invokeAndWait(() -> tab.setText(script));
        Thread.sleep(500);

        report(!frame.isSingleLineMode(), "single-line mode starts off", "");

        SwingUtilities.invokeAndWait(() -> frame.setSingleLineMode(true));
        Thread.sleep(300);
        report(frame.isSingleLineMode(), "single line mode can be turned on", "");

        // Run from the top: each press should run one line and advance.
        SwingUtilities.invokeAndWait(() -> tab.moveCaretToLine(0));
        Thread.sleep(200);

        runOnce(frame);
        Thread.sleep(2500);
        report(tab.lineResult(0) != null && tab.lineResult(0),
                "line 0 ran and was marked successful",
                String.valueOf(tab.lineResult(0)));
        report(tab.caretLine() == 1, "the caret advanced to line 1",
                "line " + tab.caretLine());

        // Line 1 is a comment: it must be skipped without being marked.
        runOnce(frame);
        Thread.sleep(800);
        report(tab.lineResult(1) == null, "a comment line is skipped",
                String.valueOf(tab.lineResult(1)));
        report(tab.caretLine() == 2, "the caret advanced past the comment",
                "line " + tab.caretLine());

        runOnce(frame);
        Thread.sleep(2500);
        report(tab.lineResult(2) != null && tab.lineResult(2),
                "line 2 ran and was marked successful",
                String.valueOf(tab.lineResult(2)));

        // A failing command must be marked as a failure, not a success.
        runOnce(frame);
        Thread.sleep(3000);
        report(Boolean.FALSE.equals(tab.lineResult(3)),
                "a failing line is marked as a failure",
                String.valueOf(tab.lineResult(3)));
        report(tab.caretLine() == 4, "the caret advanced past the failure",
                "line " + tab.caretLine());

        SwingUtilities.invokeAndWait(() -> frame.setSingleLineMode(false));
        Thread.sleep(400);
        report(tab.lineResult(0) == null, "turning the mode off clears the marks",
                String.valueOf(tab.lineResult(0)));
    }

    private static void runOnce(MainFrame frame) throws Exception {
        SwingUtilities.invokeAndWait(frame::runActiveScript);
    }

    private static String quote(String value) {
        return "\"" + value.replace("\r", "\\r").replace("\n", "\\n") + "\"";
    }

    private static String tail(MainFrame frame, int lines) {
        String[] all = frame.console().getText().split("\\R");
        StringBuilder text = new StringBuilder();
        for (int i = Math.max(0, all.length - lines); i < all.length; i++) {
            text.append("      |").append(all[i]).append('\n');
        }
        return text.toString();
    }

    private static void report(boolean ok, String label, String extra) {
        System.out.printf("  %-7s %-46s %s%n", ok ? "ok" : "FAIL", label, extra);
        if (!ok) {
            failures++;
        }
    }
}
