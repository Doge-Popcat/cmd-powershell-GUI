package com.dogepopcat.cpgui.io;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Decides which directory the application may treat as its workspace.
 *
 * <p>When Windows starts a program from a shortcut or the Start menu it often
 * sets the working directory to {@code C:\Windows\System32}. Using that as the
 * workspace would put the console, the file chooser and any relative path right
 * next to the operating system, so it is rejected in favour of somewhere the
 * user actually owns.</p>
 */
public final class WorkspacePolicy {

    /** Directory names directly under the Windows directory that are off limits. */
    private static final List<String> FORBIDDEN_NAMES = List.of(
            "system32", "syswow64", "winsxs", "windows", "program files",
            "program files (x86)", "programdata", "system volume information",
            "$recycle.bin", "recovery", "perflogs");

    private WorkspacePolicy() {
    }

    /**
     * @return the directory the application should start in: the process working
     *         directory when it is somewhere sensible, otherwise the user's home
     */
    public static File defaultWorkspace() {
        List<File> candidates = new ArrayList<>();
        addCandidate(candidates, System.getProperty("user.dir"));
        addCandidate(candidates, System.getProperty("user.home"));
        addCandidate(candidates, documentsDirectory());
        addCandidate(candidates, System.getProperty("java.io.tmpdir"));
        for (File candidate : candidates) {
            if (isUsable(candidate)) {
                return candidate;
            }
        }
        return new File(System.getProperty("user.home", "."));
    }

    private static void addCandidate(List<File> candidates, String path) {
        if (path != null && !path.isBlank()) {
            candidates.add(new File(path));
        }
    }

    private static String documentsDirectory() {
        String home = System.getProperty("user.home");
        return home == null ? null : home + File.separator + "Documents";
    }

    /**
     * @param directory the directory to test
     * @return {@code true} when it exists, is writable and is not part of Windows
     */
    public static boolean isUsable(File directory) {
        if (directory == null || !directory.isDirectory() || !directory.canWrite()) {
            return false;
        }
        return !isSystemDirectory(directory);
    }

    /**
     * @param directory the directory to test
     * @return {@code true} when the directory belongs to Windows or to a program
     *         installation rather than to the user
     */
    public static boolean isSystemDirectory(File directory) {
        if (directory == null) {
            return false;
        }
        String path = directory.getAbsolutePath().toLowerCase(Locale.ROOT);
        while (path.endsWith(File.separator)) {
            path = path.substring(0, path.length() - 1);
        }
        // A bare drive root such as "c:" is never a good workspace.
        if (path.length() <= 2 && path.endsWith(":")) {
            return true;
        }
        File parent = directory.getParentFile();
        if (parent == null) {
            return true;
        }
        String name = directory.getName().toLowerCase(Locale.ROOT);
        if (FORBIDDEN_NAMES.contains(name)) {
            return true;
        }
        String windows = System.getenv("SystemRoot");
        if (windows != null && path.equals(windows.toLowerCase(Locale.ROOT))) {
            return true;
        }
        // Anything directly inside a Program Files folder is an installation.
        String parentName = parent.getName() == null
                ? "" : parent.getName().toLowerCase(Locale.ROOT);
        return parentName.equals("program files") || parentName.equals("program files (x86)");
    }

    /**
     * Describes why a directory is unsuitable, for a status message.
     *
     * @param directory the directory that was rejected
     * @return a short explanation
     */
    public static String describeRejection(File directory) {
        if (directory == null || !directory.isDirectory()) {
            return "not a directory";
        }
        if (!directory.canWrite()) {
            return "not writable";
        }
        if (isSystemDirectory(directory)) {
            return "a Windows system folder";
        }
        return "unavailable";
    }
}
