package dev;

import com.dogepopcat.cpgui.i18n.Language;
import com.dogepopcat.cpgui.i18n.Messages;
import com.dogepopcat.cpgui.ui.MainFrame;

import javax.swing.Action;
import javax.swing.JComponent;
import javax.swing.JTextField;
import javax.swing.KeyStroke;
import javax.swing.SwingUtilities;
import java.awt.event.ActionEvent;
import java.awt.event.KeyEvent;

/**
 * Developer check for the console command history.
 *
 * <p>The report was that Up only ever reached the most recent command. The
 * position has to survive each recall, and a half-written command must come back
 * when the user walks past the newest entry.</p>
 *
 * <pre>
 *   java -cp "build/classes;build/tools" dev.HistoryProbe
 * </pre>
 */
public final class HistoryProbe {

    private static int failures;

    private HistoryProbe() {
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
        com.dogepopcat.cpgui.ui.EditorTab tab = frame.editorTabs().current();

        // A command is remembered only once it has been run.
        int rememberable = 0;
        for (String command : new String[]{"rem probe one", "rem probe two", "rem probe three"}) {
            if (commandHandlerRuns(frame, field, command)) {
                rememberable++;
            }
        }
        report(rememberable == 3, "three commands were run and remembered",
                "remembered=" + rememberable);

        // Walk back through all of them.
        String[] expected = {"rem probe three", "rem probe two", "rem probe one"};
        for (int i = 0; i < expected.length; i++) {
            SwingUtilities.invokeAndWait(() -> press(field, KeyEvent.VK_UP));
            Thread.sleep(200);
            report(expected[i].equals(field.getText()),
                    "Up #" + (i + 1) + " recalls " + expected[i],
                    "[" + field.getText() + "]");
        }
        // The oldest entry is the end of the history.
        SwingUtilities.invokeAndWait(() -> press(field, KeyEvent.VK_UP));
        Thread.sleep(200);
        report("rem probe one".equals(field.getText()),
                "Up stops at the oldest command", "[" + field.getText() + "]");

        // Walking forward returns to an empty line.
        for (int i = 0; i < 3; i++) {
            SwingUtilities.invokeAndWait(() -> press(field, KeyEvent.VK_DOWN));
            Thread.sleep(150);
        }
        report(field.getText().isEmpty(), "Down returns to an empty line",
                "[" + field.getText() + "]");

        // A half-written command survives a look through the history.
        SwingUtilities.invokeAndWait(() -> field.setText("rem half written"));
        Thread.sleep(150);
        SwingUtilities.invokeAndWait(() -> press(field, KeyEvent.VK_UP));
        Thread.sleep(200);
        report("rem probe three".equals(field.getText()),
                "Up replaces the draft with the newest command", "[" + field.getText() + "]");
        SwingUtilities.invokeAndWait(() -> press(field, KeyEvent.VK_DOWN));
        Thread.sleep(200);
        report("rem half written".equals(field.getText()),
                "Down brings the draft back", "[" + field.getText() + "]");

        // A command run from the editor must be in the same history, otherwise
        // Up appears to do nothing for a user who runs everything from there.
        int before = frame.console().historySize();
        SwingUtilities.invokeAndWait(() -> tab.setText("rem run-from-the-editor"));
        SwingUtilities.invokeAndWait(frame::runWholeScript);
        Thread.sleep(2000);
        report(frame.console().historySize() > before,
                "a command run from the editor is remembered",
                "history " + before + " -> " + frame.console().historySize());

        SwingUtilities.invokeAndWait(() -> field.setText(""));
        Thread.sleep(150);
        SwingUtilities.invokeAndWait(() -> press(field, KeyEvent.VK_UP));
        Thread.sleep(200);
        report("rem run-from-the-editor".equals(field.getText()),
                "Up recalls the command run from the editor", "[" + field.getText() + "]");

        // The output area is part of the console, so the arrow keys work there.
        JComponent output = frame.console().outputComponent();
        SwingUtilities.invokeAndWait(() -> output.requestFocusInWindow());
        Thread.sleep(200);
        SwingUtilities.invokeAndWait(() -> {
            field.setText("");
            press(output, KeyEvent.VK_UP);
        });
        Thread.sleep(300);
        report(!field.getText().isEmpty(),
                "Up in the output area recalls a command", "[" + field.getText() + "]");
    }

    /** Runs a command through the console, and reports whether it was kept. */
    private static boolean commandHandlerRuns(MainFrame frame, JTextField field, String command)
            throws Exception {
        SwingUtilities.invokeAndWait(() -> field.setText(command));
        SwingUtilities.invokeAndWait(field::postActionEvent);
        Thread.sleep(1400);
        return frame.console().historySize() > 0;
    }

    /** Sends an arrow key through the field's own binding. */
    private static void press(JComponent field, int keyCode) {
        Object key = field.getInputMap(JComponent.WHEN_FOCUSED)
                .get(KeyStroke.getKeyStroke(keyCode, 0));
        Action action = key == null ? null : field.getActionMap().get(key);
        if (action == null) {
            throw new IllegalStateException("No binding for key " + keyCode);
        }
        action.actionPerformed(new ActionEvent(field, ActionEvent.ACTION_PERFORMED, "key"));
    }

    private static void report(boolean ok, String label, String extra) {
        System.out.printf("  %-7s %-48s %s%n", ok ? "ok" : "FAIL", label, extra);
        if (!ok) {
            failures++;
        }
    }
}
