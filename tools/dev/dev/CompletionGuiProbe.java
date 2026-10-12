package dev;

import com.dogepopcat.cpgui.shell.ShellKind;
import com.dogepopcat.cpgui.ui.EditorTab;
import com.dogepopcat.cpgui.ui.MainFrame;

import javax.swing.Action;
import javax.swing.SwingUtilities;

/**
 * Developer check for the IntelliSense popup driven through the real UI.
 *
 * <pre>
 *   java -cp "build/classes;build/tools" dev.CompletionGuiProbe
 * </pre>
 */
public final class CompletionGuiProbe {

    private static int failures;

    private CompletionGuiProbe() {
    }

    public static void main(String[] args) {
        try {
            run();
        } catch (Throwable failure) {
            // A Swing window keeps the JVM alive, so always exit explicitly.
            failure.printStackTrace();
            System.out.println("RESULT: FAIL (probe threw)");
            System.exit(1);
        }
    }

    private static void run() throws Exception {
        MainFrame[] holder = new MainFrame[1];
        SwingUtilities.invokeAndWait(() -> {
            holder[0] = new MainFrame();
            holder[0].setVisible(true);
        });
        MainFrame frame = holder[0];
        EditorTab tab = frame.editorTabs().current();

        check(tab, "list for a partial command name", "di", "dir ");
        check(tab, "ghost text for a unique match", "roboc", "robocopy ");
        check(tab, "switch list after dir", "dir /", "dir /a");
        quietCheck(tab, "plain argument stays quiet", "somefile");

        SwingUtilities.invokeAndWait(() ->
                frame.setActiveShellKind(ShellKind.POWERSHELL));
        check(tab, "popup opens for Get-Ch", "Get-Ch", "Get-ChildItem ");
        check(tab, "cmdlet after interpreter switch", "Write-H", "Write-Host ");
        check(tab, "parameter list after a cmdlet", "Remove-Item -W",
                "Remove-Item -WarningAction");
        quietCheck(tab, "plain PowerShell argument stays quiet", "somefile");

        // Enter must never be swallowed by a suggestion: it always breaks the line.
        SwingUtilities.invokeAndWait(() -> {
            tab.setText("Get-Ch");
            tab.textPane().setCaretPosition(6);
            tab.completion().trigger(false);
        });
        SwingUtilities.invokeAndWait(() -> {
            Action enter = tab.textPane().getActionMap()
                    .get(javax.swing.text.DefaultEditorKit.insertBreakAction);
            enter.actionPerformed(new java.awt.event.ActionEvent(tab.textPane(),
                    java.awt.event.ActionEvent.ACTION_PERFORMED, ""));
        });
        String afterEnter = tab.getText();
        boolean newline = afterEnter.startsWith("Get-Ch");
        System.out.printf("  %-7s %-32s text=%s%n", newline ? "ok" : "FAIL",
                "Enter inserts a newline", quote(afterEnter));
        if (!newline) {
            failures++;
        }

        System.out.println(failures == 0 ? "RESULT: PASS" : "RESULT: FAIL (" + failures + ")");
        System.exit(failures == 0 ? 0 : 1);
    }

    /** Types a fragment, opens the popup for it and accepts the first item. */
    private static void check(EditorTab tab, String label, String typed, String wanted)
            throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            tab.setText(typed);
            tab.textPane().setCaretPosition(typed.length());
        });
        // Let the editor lay out so suggestions can be placed at the caret.
        SwingUtilities.invokeAndWait(() -> { });
        SwingUtilities.invokeAndWait(() -> tab.completion().trigger(false));

        boolean popup = tab.completion().isPopupVisible();
        boolean ghost = tab.completion().ghostItem() != null;
        int offered = tab.completion().items().size();
        SwingUtilities.invokeAndWait(() -> tab.completion().acceptSuggestion());
        String actual = tab.getText();

        boolean ok = (popup || ghost) && actual.equals(wanted);
        System.out.printf("  %-7s %-32s popup=%-5s ghost=%-5s offered=%-4d text=%s%n",
                ok ? "ok" : "FAIL", label, popup, ghost, offered, quote(actual));
        if (!ok) {
            failures++;
            System.out.println("        wanted: " + quote(wanted));
        }
    }

    /** Typing an ordinary argument must not raise anything on its own. */
    private static void quietCheck(EditorTab tab, String label, String typed) throws Exception {
        SwingUtilities.invokeAndWait(() -> tab.completion().close());
        SwingUtilities.invokeAndWait(() -> {
            tab.setText("mkdir ");
            tab.textPane().setCaretPosition(6);
            try {
                tab.document().insertString(6, typed, null);
            } catch (javax.swing.text.BadLocationException e) {
                throw new IllegalStateException(e);
            }
            tab.textPane().setCaretPosition(tab.getText().length());
        });
        Thread.sleep(500);
        boolean quiet = !tab.completion().isPopupVisible() && tab.completion().ghostItem() == null;
        System.out.printf("  %-7s %-32s quiet=%s%n", quiet ? "ok" : "FAIL", label, quiet);
        if (!quiet) {
            failures++;
        }
    }

    private static String quote(String value) {
        return "\"" + value.replace("\r", "\\r").replace("\n", "\\n") + "\"";
    }
}
