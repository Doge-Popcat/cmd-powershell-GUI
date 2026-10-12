package dev;

import com.dogepopcat.cpgui.shell.ShellKind;
import com.dogepopcat.cpgui.ui.EditorTab;
import com.dogepopcat.cpgui.ui.MainFrame;

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

    public static void main(String[] args) throws Exception {
        MainFrame[] holder = new MainFrame[1];
        SwingUtilities.invokeAndWait(() -> {
            holder[0] = new MainFrame();
            holder[0].setVisible(true);
        });
        MainFrame frame = holder[0];
        EditorTab tab = frame.editorTabs().current();

        check(tab, "popup opens for di (cmd)", "di", "dir ");
        check(tab, "switch completion", "dir /s", "dir /s");

        SwingUtilities.invokeAndWait(() ->
                frame.setActiveShellKind(ShellKind.POWERSHELL));
        check(tab, "popup opens for Get-Ch", "Get-Ch", "Get-ChildItem ");
        check(tab, "cmdlet after interpreter switch", "Write-H", "Write-Host ");

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
        // Let the editor lay out so the popup can be placed at the caret.
        SwingUtilities.invokeAndWait(() -> { });
        SwingUtilities.invokeAndWait(() -> tab.completion().trigger(true));

        boolean visible = tab.completion().isPopupVisible();
        int offered = tab.completion().items().size();
        SwingUtilities.invokeAndWait(() -> tab.completion().accept());
        String actual = tab.getText();

        boolean ok = visible && actual.equals(wanted);
        System.out.printf("  %-7s %-30s visible=%-5s offered=%-4d text=%s%n",
                ok ? "ok" : "FAIL", label, visible, offered, quote(actual));
        if (!ok) {
            failures++;
            System.out.println("        wanted: " + quote(wanted));
        }
    }

    private static String quote(String value) {
        return "\"" + value.replace("\r", "\\r").replace("\n", "\\n") + "\"";
    }
}
