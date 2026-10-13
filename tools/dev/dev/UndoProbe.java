package dev;

import com.dogepopcat.cpgui.i18n.Language;
import com.dogepopcat.cpgui.i18n.Messages;
import com.dogepopcat.cpgui.ui.EditorTab;
import com.dogepopcat.cpgui.ui.MainFrame;

import javax.swing.JComponent;
import javax.swing.KeyStroke;
import javax.swing.SwingUtilities;
import java.awt.event.ActionEvent;
import java.awt.event.InputEvent;
import java.awt.event.KeyEvent;

/**
 * Developer check for undo and redo.
 *
 * <p>The syntax highlighter recolours the document as it is typed, and each
 * recolour used to land on the undo stack. Ctrl+Z then undid a colour change
 * rather than the typing, so it looked as though undo did nothing at all.</p>
 *
 * <pre>
 *   java -cp "build/classes;build/tools" dev.UndoProbe
 * </pre>
 */
public final class UndoProbe {

    private static int failures;

    private UndoProbe() {
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

        // Type through the document so the highlighter runs as it does for a user.
        SwingUtilities.invokeAndWait(() -> typeText(tab, "dir /b"));
        Thread.sleep(600);
        report("dir /b".equals(tab.getText()), "the text was typed", "[" + tab.getText() + "]");
        report(tab.canUndo(), "there is something to undo", "");

        // Ctrl+Z must remove the typing. Before the highlighter's colour edits
        // were filtered out it undid a colour change instead, so the text never
        // appeared to change at all.
        SwingUtilities.invokeAndWait(() -> press(tab, KeyEvent.VK_Z, InputEvent.CTRL_DOWN_MASK));
        Thread.sleep(400);
        report("dir /".equals(tab.getText()),
                "Ctrl+Z removes typed text", "[" + tab.getText() + "]");

        report(tab.canRedo(), "there is something to redo", "");
        SwingUtilities.invokeAndWait(() -> press(tab, KeyEvent.VK_Y, InputEvent.CTRL_DOWN_MASK));
        Thread.sleep(400);
        report("dir /b".equals(tab.getText()), "Ctrl+Y puts it back", "[" + tab.getText() + "]");

        // Consecutive undos must keep walking back rather than sticking.
        int steps = 0;
        for (int i = 0; i < 20 && tab.canUndo(); i++) {
            SwingUtilities.invokeAndWait(tab::undoLastEdit);
            steps++;
            Thread.sleep(60);
        }
        report(steps >= 5 && tab.getText().isBlank(),
                "undo walks back through every edit", "steps=" + steps
                        + " left=[" + tab.getText() + "]");

        // The tool bar must offer the same two commands.
        boolean undoButton = false;
        boolean redoButton = false;
        for (java.awt.Component component : frame.toolBar().getComponents()) {
            if (component instanceof javax.swing.JButton button && button.getText() != null) {
                if (button.getText().contains("\u21B6")) {
                    undoButton = true;
                }
                if (button.getText().contains("\u21B7")) {
                    redoButton = true;
                }
            }
        }
        report(undoButton && redoButton, "the tool bar has undo and redo buttons",
                "undo=" + undoButton + " redo=" + redoButton);
    }

    /** Types character by character, the way a user does. */
    private static void typeText(EditorTab tab, String text) {
        try {
            for (int i = 0; i < text.length(); i++) {
                tab.document().insertString(tab.document().getLength(),
                        String.valueOf(text.charAt(i)), null);
            }
        } catch (javax.swing.text.BadLocationException e) {
            throw new IllegalStateException(e);
        }
    }

    /** Sends a key through the editor's own binding. */
    private static void press(EditorTab tab, int keyCode, int modifiers) {
        JComponent pane = tab.textPane();
        Object key = pane.getInputMap(JComponent.WHEN_FOCUSED)
                .get(KeyStroke.getKeyStroke(keyCode, modifiers));
        javax.swing.Action action = key == null ? null : pane.getActionMap().get(key);
        if (action == null) {
            throw new IllegalStateException("No binding for key " + keyCode);
        }
        action.actionPerformed(new ActionEvent(pane, ActionEvent.ACTION_PERFORMED, "key"));
    }

    private static void report(boolean ok, String label, String extra) {
        System.out.printf("  %-7s %-48s %s%n", ok ? "ok" : "FAIL", label, extra);
        if (!ok) {
            failures++;
        }
    }
}
