package dev;

import com.dogepopcat.cpgui.io.WorkspacePolicy;
import com.dogepopcat.cpgui.ui.EditorTab;
import com.dogepopcat.cpgui.ui.MainFrame;

import javax.swing.SwingUtilities;
import java.io.File;

/**
 * Developer check for the workspace policy and the live location display.
 *
 * <pre>
 *   java -cp "build/classes;build/tools" dev.WorkspaceProbe
 * </pre>
 */
public final class WorkspaceProbe {

    private static int failures;

    private WorkspaceProbe() {
    }

    public static void main(String[] args) throws Exception {
        policyChecks();
        liveChecks();

        System.out.println(failures == 0 ? "RESULT: PASS" : "RESULT: FAIL (" + failures + ")");
        System.exit(failures == 0 ? 0 : 1);
    }

    private static void policyChecks() {
        System.out.println("workspace policy (running from "
                + System.getProperty("user.dir") + ")");
        expectSystem("C:\\Windows\\System32", true);
        expectSystem("C:\\Windows", true);
        expectSystem("C:\\Windows\\SysWOW64", true);
        expectSystem("C:\\Program Files", true);
        expectSystem("C:\\Program Files\\Some App", true);
        expectSystem("C:\\", true);
        expectSystem(System.getProperty("user.home"), false);
        expectSystem(System.getProperty("user.dir"), false);

        File chosen = WorkspacePolicy.defaultWorkspace();
        report(!WorkspacePolicy.isSystemDirectory(chosen),
                "default workspace is not a system folder", chosen.getAbsolutePath());
        report(chosen.isDirectory() && chosen.canWrite(),
                "default workspace is writable", chosen.getAbsolutePath());
    }

    /** Moves a session with cd and checks the location bar follows it. */
    private static void liveChecks() throws Exception {
        System.out.println("live directory tracking");
        MainFrame[] holder = new MainFrame[1];
        SwingUtilities.invokeAndWait(() -> {
            holder[0] = new MainFrame();
            holder[0].setVisible(true);
        });
        MainFrame frame = holder[0];
        EditorTab tab = frame.editorTabs().current();

        File start = frame.workspace();
        report(start != null && !WorkspacePolicy.isSystemDirectory(start),
                "frame starts outside system folders", String.valueOf(start));

        File target = new File(start, "src").getAbsoluteFile();
        report(target.isDirectory(), "test target exists", target.getAbsolutePath());
        if (!target.isDirectory()) {
            return;
        }

        SwingUtilities.invokeAndWait(() -> {
            tab.setText("cd /d \"" + target.getAbsolutePath() + "\"");
            frame.runActiveScript();
        });
        Thread.sleep(2500);
        SwingUtilities.invokeAndWait(() -> frame.refreshLiveDirectory(tab));
        Thread.sleep(1500);

        File shown = frame.locationBar().directory();
        report(shown != null && shown.getAbsolutePath().equalsIgnoreCase(target.getAbsolutePath()),
                "location bar follows the session", String.valueOf(shown));
    }

    private static void expectSystem(String path, boolean expected) {
        boolean actual = WorkspacePolicy.isSystemDirectory(new File(path));
        report(actual == expected, (expected ? "rejects " : "accepts ") + path,
                expected == actual ? "" : "got " + actual);
    }

    private static void report(boolean ok, String label, String extra) {
        System.out.printf("  %-7s %-38s %s%n", ok ? "ok" : "FAIL", label, extra);
        if (!ok) {
            failures++;
        }
    }
}
