package dev;

import com.dogepopcat.cpgui.i18n.Language;
import com.dogepopcat.cpgui.i18n.Messages;
import com.dogepopcat.cpgui.ui.EditorTab;
import com.dogepopcat.cpgui.ui.MainFrame;

import javax.swing.SwingUtilities;

/**
 * Developer check for the inline suggestion and Tab.
 *
 * <p>It pins the two faults that were reported: typing in a different case
 * showed no suggestion at all, and Tab inserted something other than the
 * suggestion that was on screen.</p>
 *
 * <pre>
 *   java -cp "build/classes;build/tools" dev.GhostProbe
 * </pre>
 */
public final class GhostProbe {

    private static int failures;

    private GhostProbe() {
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

        // --- lower case: mkdir and mklink both fit ---
        type(tab, "mk");
        Thread.sleep(500);
        System.out.println("typed mkdir?");
        report(tab.completion().ghostItem() != null, "a suggestion is offered for mk",
                describe(tab));
        String shownGhost = ghostOf(tab);
        report("dir".equalsIgnoreCase(shownGhost.strip()),
                "the inline suggestion is the rest of mkdir", "[" + shownGhost + "]");

        // Tab must insert exactly what the ghost promised.
        if (tab.completion().isPopupVisible() && tab.completion().popup().selected() != null) {
            String promised = tab.completion().popup().selected().insertText();
            SwingUtilities.invokeAndWait(tab.completion()::acceptSuggestion);
            Thread.sleep(400);
            String line = firstLine(tab);
            report(line.equalsIgnoreCase(promised.strip()),
                    "Tab inserts the suggestion that was highlighted",
                    "promised=" + promised.strip() + " got=" + line);
        } else {
            report(false, "the list is open for mk", "ghost=" + shownGhost);
        }

        // --- upper case must behave the same ---
        type(tab, "MK");
        Thread.sleep(500);
        System.out.println("typed MK?");
        report(tab.completion().ghostItem() != null
                        || tab.completion().isPopupVisible(),
                "a suggestion is offered for MK too", describe(tab));
        String upperGhost = ghostOf(tab);
        report(upperGhost != null && !upperGhost.isBlank(),
                "the inline suggestion appears whatever the case", String.valueOf(upperGhost));

        if (tab.completion().isPopupVisible() && tab.completion().popup().selected() != null) {
            String promised = tab.completion().popup().selected().insertText();
            SwingUtilities.invokeAndWait(tab.completion()::acceptSuggestion);
            Thread.sleep(400);
            String line = firstLine(tab);
            report(line.equalsIgnoreCase(promised.strip()),
                    "Tab inserts the highlighted suggestion for MK",
                    "promised=" + promised.strip() + " got=" + line);
        } else if (tab.completion().ghostItem() != null) {
            String promised = tab.completion().ghostItem().insertText();
            SwingUtilities.invokeAndWait(tab.completion()::acceptSuggestion);
            Thread.sleep(400);
            report(firstLine(tab).equalsIgnoreCase(promised.strip()),
                    "Tab inserts the inline suggestion for MK",
                    "promised=" + promised.strip() + " got=" + firstLine(tab));
        }

        // --- the ghost must follow the highlighted row ---
        type(tab, "mkl");
        Thread.sleep(500);
        // Only mklink fits, so the suggestion is inline and the list stays shut.
        report("ink".equalsIgnoreCase(ghostOf(tab).strip()),
                "a single match is suggested inline for mkl", "[" + ghostOf(tab) + "]");
        SwingUtilities.invokeAndWait(tab.completion()::acceptSuggestion);
        Thread.sleep(400);
        report(firstLine(tab).equalsIgnoreCase("mklink"),
                "Tab completes mkl to mklink", firstLine(tab));

        // Every candidate is reachable, and the inline text follows the choice.
        type(tab, "mk");
        Thread.sleep(500);
        if (tab.completion().isPopupVisible()) {
            String before = ghostOf(tab);
            // Press Down through the editor binding, as the user would.
            SwingUtilities.invokeAndWait(() -> pressDown(tab));
            Thread.sleep(250);
            String after = ghostOf(tab);
            report(before != null && after != null && !before.equals(after),
                    "moving the selection changes the inline suggestion",
                    before + " -> " + after);
            String promised = tab.completion().popup().selected().insertText().strip();
            SwingUtilities.invokeAndWait(tab.completion()::acceptSuggestion);
            Thread.sleep(400);
            report(firstLine(tab).equalsIgnoreCase(promised),
                    "Tab inserts the newly highlighted suggestion",
                    "promised=" + promised + " got=" + firstLine(tab));
        } else {
            report(false, "the list is open for mk", describe(tab));
        }
    }

    /** Replaces the document with a prefix and puts the caret after it. */
    private static void type(EditorTab tab, String text) throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            tab.completion().close();
            tab.setText(text);
            tab.textPane().setCaretPosition(text.length());
        });
        Thread.sleep(250);
        SwingUtilities.invokeAndWait(() -> tab.completion().trigger(false));
    }

    /** Sends the Down key through the editor's own binding. */
    private static void pressDown(EditorTab tab) {
        javax.swing.JComponent pane = tab.textPane();
        Object key = pane.getInputMap(javax.swing.JComponent.WHEN_FOCUSED)
                .get(javax.swing.KeyStroke.getKeyStroke(java.awt.event.KeyEvent.VK_DOWN, 0));
        javax.swing.Action action = key == null ? null : pane.getActionMap().get(key);
        if (action != null) {
            action.actionPerformed(new java.awt.event.ActionEvent(pane,
                    java.awt.event.ActionEvent.ACTION_PERFORMED, "down"));
        }
    }

    private static String ghostOf(EditorTab tab) {
        return ((com.dogepopcat.cpgui.ui.GhostTextPane) tab.textPane()).getGhostText();
    }

    private static String firstLine(EditorTab tab) {
        String text = tab.getText();
        int end = text.indexOf('\n');
        return (end < 0 ? text : text.substring(0, end)).strip();
    }

    private static String describe(EditorTab tab) {
        return "popup=" + tab.completion().isPopupVisible()
                + " items=" + tab.completion().items().size()
                + " ghost=" + (tab.completion().ghostItem() == null
                        ? "none" : tab.completion().ghostItem().label());
    }

    private static void report(boolean ok, String label, String extra) {
        System.out.printf("  %-7s %-48s %s%n", ok ? "ok" : "FAIL", label, extra);
        if (!ok) {
            failures++;
        }
    }
}
