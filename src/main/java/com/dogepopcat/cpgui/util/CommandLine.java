package com.dogepopcat.cpgui.util;

import java.io.File;
import java.io.IOException;
import java.util.List;
import java.util.Locale;

/**
 * Helpers for working out how the current process was started.
 *
 * <p>Restarting elevated means starting exactly this program again, so the
 * command line is read back from the operating system whenever it is available.
 * Two kinds of start need care:</p>
 *
 * <ul>
 *   <li>a JVM started as {@code java -jar app.jar} or {@code java -cp ... Main}
 *       must be started again through the same executable, because the program
 *       itself is only the jar;</li>
 *   <li>the packaged application image is a launcher executable that carries its
 *       own class path and JVM options, and whose bundled runtime deliberately
 *       has no {@code java.exe} at all. It must therefore be started again as
 *       itself, never through a Java executable that is not there.</li>
 * </ul>
 */
public final class CommandLine {

    private CommandLine() {
    }

    /**
     * @return how to start this program again, as the executable followed by a
     *         single argument string, which is empty for a launcher that needs no
     *         arguments, or an empty list when that cannot be determined
     */
    public static List<String> currentProcess() {
        ProcessHandle handle = ProcessHandle.current();
        return relaunchCommand(
                handle.info().commandLine().orElse(null),
                handle.info().command().orElse(null),
                javaExecutable(),
                System.getProperty("java.class.path", ""),
                System.getProperty("sun.java.command", ""));
    }

    /**
     * Decides how to start this program again from facts about the running
     * process.
     *
     * <p>This is the decision {@link #currentProcess()} makes; it is public so
     * that a check can verify every case without starting a second process.</p>
     *
     * @param commandLine the raw command line, or {@code null} when Windows hides
     *                    it
     * @param image       the path of the running executable, or {@code null}
     * @param java        a {@code java.exe} shipped with this runtime, or
     *                    {@code null} when there is none
     * @param classPath   the current class path
     * @param sunCommand  {@code sun.java.command}: the main class with its
     *                    arguments, or the jar path when started with {@code -jar}
     * @return the executable followed by its argument string, or an empty list
     *         when no way back into the program could be found
     */
    public static List<String> relaunchCommand(String commandLine, String image,
            String java, String classPath, String sunCommand) {
        List<String> parsed = split(commandLine);
        if (!parsed.isEmpty()) {
            String executable = resolve(parsed.get(0), image);
            if (executable != null) {
                if (parsed.size() >= 2) {
                    return List.of(executable, parsed.get(1));
                }
                // No arguments of its own: only a self-contained launcher can be
                // started that way. A bare java.exe would just print its usage.
                if (!isJavaLauncher(executable)) {
                    return List.of(executable, "");
                }
            }
        }

        String javaExecutable = resolve(java, image);
        if (javaExecutable != null) {
            List<String> rebuilt = rebuild(javaExecutable, classPath, sunCommand);
            if (rebuilt != null) {
                return rebuilt;
            }
        }

        // Nothing else is left. The bundled runtime of a packaged application
        // image has no java.exe, so starting the launcher again is the only way
        // back into the program.
        String self = resolve(image, image);
        if (self != null && !isJavaLauncher(self)) {
            return List.of(self, "");
        }
        return List.of();
    }

    /**
     * Rebuilds the command from the running JVM when the operating system hides
     * the command line, which Windows does for the current process often enough
     * to matter.
     *
     * @param javaExecutable an existing {@code java.exe}
     * @param classPath      the current class path
     * @param sunCommand     {@code sun.java.command}
     * @return the executable and its argument string, or {@code null} when
     *         nothing usable can be built
     */
    private static List<String> rebuild(String javaExecutable, String classPath,
            String sunCommand) {
        String command = sunCommand == null ? "" : sunCommand.strip();
        if (command.endsWith(".jar")) {
            return List.of(javaExecutable, "-jar " + quoteIfNeeded(command));
        }
        if (!command.isEmpty()) {
            return List.of(javaExecutable, "-cp " + quoteIfNeeded(classPath) + " " + command);
        }
        if (!classPath.isEmpty()) {
            return List.of(javaExecutable, "-cp " + quoteIfNeeded(classPath)
                    + " com.dogepopcat.cpgui.Main");
        }
        return null;
    }

    /**
     * @return the {@code java.exe} of this runtime, or {@code null} when the
     *         runtime does not ship one, as a packaged application image does
     */
    private static String javaExecutable() {
        String javaHome = System.getProperty("java.home");
        if (javaHome == null) {
            return null;
        }
        for (String name : new String[]{"java.exe", "java"}) {
            File candidate = new File(javaHome, "bin" + File.separator + name);
            if (candidate.isFile()) {
                return candidate.getAbsolutePath();
            }
        }
        return null;
    }

    /**
     * Turns a name found in a command line into the absolute path of a file that
     * really exists.
     *
     * <p>A command line often names its executable the way the user typed it, so
     * a bare name is looked for on {@code PATH}, and a relative name is also
     * matched against the running executable, which is where a launcher usually
     * lives.</p>
     *
     * @param name  the executable as it was written, possibly {@code null}
     * @param image the path of the running executable, possibly {@code null}
     * @return the absolute path, or {@code null} when no such file exists
     */
    private static String resolve(String name, String image) {
        if (name == null || name.isBlank()) {
            return null;
        }
        File direct = new File(name);
        if (direct.isFile()) {
            return canonical(direct);
        }
        if (direct.isAbsolute()) {
            return null;
        }
        if (image != null && new File(image).getName().equalsIgnoreCase(direct.getName())) {
            File running = new File(image);
            if (running.isFile()) {
                return canonical(running);
            }
        }
        if (name.indexOf(File.separatorChar) < 0 && name.indexOf('/') < 0) {
            String path = System.getenv("PATH");
            if (path != null) {
                for (String entry : path.split(File.pathSeparator)) {
                    if (entry.isBlank()) {
                        continue;
                    }
                    File candidate = new File(entry, name);
                    if (candidate.isFile()) {
                        return canonical(candidate);
                    }
                }
            }
        }
        return null;
    }

    private static String canonical(File file) {
        try {
            return file.getCanonicalPath();
        } catch (IOException e) {
            return file.getAbsolutePath();
        }
    }

    /** @return {@code true} for the executables that launch a JVM */
    private static boolean isJavaLauncher(String executable) {
        String name = new File(executable).getName().toLowerCase(Locale.ROOT);
        return name.equals("java.exe") || name.equals("javaw.exe")
                || name.equals("java") || name.equals("javaw");
    }

    /**
     * Splits a Windows command line into the executable and the remaining
     * argument string.
     *
     * <p>The arguments are deliberately kept as one string: they are handed
     * straight back to Windows when the process is restarted, so re-quoting them
     * could only introduce mistakes.</p>
     *
     * @param commandLine the raw command line, possibly {@code null}
     * @return one or two elements, or an empty list when nothing could be parsed
     */
    public static List<String> split(String commandLine) {
        if (commandLine == null || commandLine.isBlank()) {
            return List.of();
        }
        String text = commandLine.strip();
        String executable;
        int rest;
        if (text.startsWith("\"")) {
            int closing = text.indexOf('"', 1);
            if (closing < 0) {
                return List.of();
            }
            executable = text.substring(1, closing);
            rest = closing + 1;
        } else {
            int space = text.indexOf(' ');
            if (space < 0) {
                return List.of(text);
            }
            executable = text.substring(0, space);
            rest = space;
        }
        String arguments = text.substring(rest).strip();
        return arguments.isEmpty()
                ? List.of(executable)
                : List.of(executable, arguments);
    }

    private static String quoteIfNeeded(String value) {
        return value.indexOf(' ') >= 0 ? "\"" + value + "\"" : value;
    }
}
