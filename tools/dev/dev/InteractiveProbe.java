package dev;

import com.dogepopcat.cpgui.i18n.Language;
import com.dogepopcat.cpgui.i18n.Messages;
import com.dogepopcat.cpgui.ui.MainFrame;

import javax.swing.JTextField;
import javax.swing.SwingUtilities;

/**
 * Developer check for interactive commands.
 *
 * <p>{@code set /p} asks a question and reads one line. The end-of-run marker
 * used to be sent with the command, so the interpreter read the marker as the
 * answer and the reply was never seen; the session now recognises commands that
 * ask a question, withholds the marker and passes the typed line straight
 * through.</p>
 *
 * <pre>
 *   java -cp "build/classes;build/tools" dev.InteractiveProbe
 * </pre>
 */
public final class InteractiveProbe {

    private static int failures;

    private InteractiveProbe() {
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

        // Ask a question, and remember the reply in a variable we can read back.
        submit(field, "set /p REPLY=type-something ");
        Thread.sleep(1600);
        report(frame.console().isAwaitingInput(),
                "a question is recognised as waiting for input",
                "console awaiting=" + frame.console().isAwaitingInput());
        report(frame.console().getText().contains("type-something"),
                "the question itself is shown",
                lastLines(frame, 2));

        // The answer must reach the command, not be swallowed as a command.
        submit(field, "answered-by-the-user");
        // The answer is written straight to the interpreter, and the run is
        // closed once it goes quiet; that takes a moment, so wait for it.
        boolean finished = false;
        for (int i = 0; i < 40 && !finished; i++) {
            Thread.sleep(250);
            finished = !frame.console().isAwaitingInput()
                    && frame.console().getText().contains("\u9000\u51fa\u7801");
        }
        report(finished, "the run finishes once the answer is given", lastLines(frame, 2));

        // Read the variable back in a second run to prove the value arrived.
        submit(field, "echo REPLY=%REPLY%");
        Thread.sleep(2200);
        report(frame.console().getText().contains("REPLY=answered-by-the-user"),
                "the interpreter received the answer",
                lastLines(frame, 3));
        report(!frame.console().isAwaitingInput(),
                "the console leaves the waiting state", "");

        // An ordinary command must still behave exactly as before.
        submit(field, "echo ordinary-still-works");
        Thread.sleep(2200);
        report(frame.console().getText().contains("ordinary-still-works"),
                "an ordinary command still runs", lastLines(frame, 2));
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
