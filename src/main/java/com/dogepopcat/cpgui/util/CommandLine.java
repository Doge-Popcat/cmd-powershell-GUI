package com.dogepopcat.cpgui.util;

import java.io.File;
import java.util.List;
import java.util.Optional;

/**
 * Helpers for working out how the current process was started.
 *
 * <p>Restarting elevated means re-running exactly the command that started this
 * JVM, whether it was {@code java -jar app.jar} or {@code java -cp ... Main},
 * so the original command line is read back from the operating system.</p>
 */
public final class CommandLine {

    private CommandLine() {
    }

    /**
     * @return how this program was started, as the executable followed by a
     *         single argument string, or an empty list when it cannot be
     *         determined
     */
    public static List<String> currentProcess() {
        Optional<String> commandLine = ProcessHandle.current().info().commandLine();
        if (commandLine.isPresent()) {
            List<String> parsed = split(commandLine.get());
            if (parsed.size() >= 2) {
                return parsed;
            }
        }
        return fallback();
    }

    /**
     * Rebuilds a command from the running JVM when the operating system hides
     * the command line, which Windows does for the current process often enough
     * to matter.
     */
    private static List<String> fallback() {
        String javaHome = System.getProperty("java.home");
        String classPath = System.getProperty("java.class.path", "");
        if (javaHome == null || classPath.isEmpty()) {
            return List.of();
        }
        String java = javaHome + File.separator + "bin" + File.separator + "java.exe";

        // "sun.java.command" is either the main class with its arguments, or the
        // path of the jar when the program was started with -jar.
        String sunCommand = System.getProperty("sun.java.command", "").strip();
        if (sunCommand.endsWith(".jar")) {
            return List.of(java, "-jar " + quoteIfNeeded(sunCommand));
        }
        if (!sunCommand.isEmpty()) {
            return List.of(java, "-cp " + quoteIfNeeded(classPath) + " " + sunCommand);
        }
        return List.of(java, "-cp " + quoteIfNeeded(classPath)
                + " com.dogepopcat.cpgui.Main");
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
