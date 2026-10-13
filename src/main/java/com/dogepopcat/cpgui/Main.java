package com.dogepopcat.cpgui;

import com.dogepopcat.cpgui.ui.MainFrame;

import javax.swing.SwingUtilities;
import javax.swing.UIManager;
import java.awt.Font;

/**
 * Application entry point.
 *
 * <p>The whole UI is plain Swing so the program has no third-party runtime
 * dependencies and runs on any desktop JDK out of the box.</p>
 */
public final class Main {

    /** Command line switch carrying the directory the new window should use. */
    private static final String WORKSPACE_OPTION = "--workspace";

    /** Command line switch writing a diagnostic report about this start. */
    private static final String ELEVATION_REPORT_OPTION = "--elevation-report";

    private Main() {
    }

    public static void main(String[] args) {
        // Crisper text rendering on Windows high-DPI displays.
        System.setProperty("awt.useSystemAAFontSettings", "on");
        System.setProperty("swing.aatext", "true");

        java.io.File workspace = parseOption(args, WORKSPACE_OPTION);
        java.io.File report = parseOption(args, ELEVATION_REPORT_OPTION);
        if (report != null) {
            writeElevationReport(report, workspace);
            return;
        }

        SwingUtilities.invokeLater(() -> {
            applyLookAndFeel();
            new MainFrame(workspace).setVisible(true);
        });
    }

    /**
     * Reads {@code --option <value>} or {@code --option=<value>} from the command
     * line.
     *
     * <p>{@code --workspace} is used when an elevated copy is started, so that
     * window opens in the same directory as the one that launched it.</p>
     *
     * @param args   the raw program arguments, possibly {@code null}
     * @param option the option to look for
     * @return the value that was given, or {@code null} when it was not
     */
    private static java.io.File parseOption(String[] args, String option) {
        if (args == null) {
            return null;
        }
        for (int i = 0; i < args.length; i++) {
            String argument = args[i];
            if (argument.startsWith(option + "=")) {
                return new java.io.File(argument.substring(option.length() + 1));
            }
            if (argument.equals(option) && i + 1 < args.length) {
                return new java.io.File(args[i + 1]);
            }
        }
        return null;
    }

    /**
     * Writes down how this program was started and how it would start itself
     * again, so that administrator mode can be diagnosed on a machine that
     * cannot be inspected directly.
     *
     * <p>It is also the only view from inside the packaged application image:
     * that launcher has no console and its bundled runtime ships no
     * {@code java.exe}, so no other program can be run in that environment.</p>
     *
     * @param file      where the report is written
     * @param workspace the workspace this start was asked for, possibly
     *                  {@code null}; it shows whether a quoted path survived the
     *                  trip through the command line as one argument
     */
    private static void writeElevationReport(java.io.File file, java.io.File workspace) {
        ProcessHandle handle = ProcessHandle.current();
        java.util.List<String> relaunch = com.dogepopcat.cpgui.util.CommandLine.currentProcess();
        String executable = relaunch.isEmpty() ? "" : relaunch.get(0);

        StringBuilder text = new StringBuilder();
        append(text, "user.dir", System.getProperty("user.dir", ""));
        append(text, "workspace", workspace == null ? "(none)" : workspace.getPath());
        append(text, "java.version", System.getProperty("java.version", ""));
        append(text, "java.home", System.getProperty("java.home", ""));
        append(text, "java.home/java.exe", String.valueOf(new java.io.File(
                System.getProperty("java.home", ""), "bin" + java.io.File.separator + "java.exe")
                .isFile()));
        append(text, "process image", handle.info().command().orElse("(hidden)"));
        append(text, "process command line", handle.info().commandLine().orElse("(hidden)"));
        append(text, "java.class.path", System.getProperty("java.class.path", ""));
        append(text, "sun.java.command", System.getProperty("sun.java.command", ""));
        append(text, "relaunch executable", executable);
        append(text, "relaunch arguments", relaunch.size() >= 2 ? relaunch.get(1) : "(none)");
        append(text, "relaunch executable exists",
                String.valueOf(new java.io.File(executable).isFile()));

        try {
            java.nio.file.Files.writeString(file.toPath(), text.toString());
            System.out.print(text);
            System.out.println("report written to " + file.getAbsolutePath());
        } catch (java.io.IOException e) {
            System.err.println("Could not write the elevation report to "
                    + file.getAbsolutePath() + ": " + e.getMessage());
            System.exit(1);
        }
    }

    private static void append(StringBuilder text, String name, String value) {
        text.append(String.format("%-24s %s%n", name, value));
    }

    /** Applies the platform look and feel; public so tests start as the app does. */
    public static void applyLookAndFeel() {
        try {
            UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
        } catch (Exception ignored) {
            // The cross-platform look and feel is a perfectly fine fallback.
        }
        UIManager.put("Tree.paintLines", Boolean.TRUE);
        UIManager.put("Table.showGrid", Boolean.TRUE);
        UIManager.put("Table.intercellSpacing", new java.awt.Dimension(0, 0));
        installMonospacedUIFont();
    }

    /**
     * Editor widgets must use a monospaced font even when the platform look and
     * feel would pick a proportional one.
     */
    private static void installMonospacedUIFont() {
        Font mono = new Font(Font.MONOSPACED, Font.PLAIN, 13);
        for (String key : new String[]{
                "TextArea.font", "TextPane.font", "EditorPane.font",
                "TextField.font", "List.font", "Table.font"}) {
            Object current = UIManager.get(key);
            if (current instanceof Font) {
                UIManager.put(key, mono);
            }
        }
    }
}
