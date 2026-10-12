package com.dogepopcat.cpgui.analysis;

import com.dogepopcat.cpgui.completion.CommandCatalog;
import com.dogepopcat.cpgui.completion.CompletionItem;
import com.dogepopcat.cpgui.shell.ShellKind;

import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Decides whether the first word of a command is something the machine can
 * actually run.
 *
 * <p>A typo such as {@code exyzxo} fails the moment it is submitted, so it is
 * worth pointing out while it is still being written. The check is deliberately
 * conservative: anything containing a path separator, a wildcard, a variable, an
 * extension or a colon is left alone, because those either resolve at run time
 * or are not worth guessing about. Only a plain word that is neither a known
 * command nor a program on {@code PATH} is reported.</p>
 */
public final class CommandResolver {

    /** Extensions Windows appends when resolving a bare name. */
    private static final String DEFAULT_PATHEXT = ".COM;.EXE;.BAT;.CMD";

    /** PowerShell aliases that are common enough to be worth knowing about. */
    private static final Set<String> POWERSHELL_ALIASES = Set.of(
            "?", "%", "ac", "cat", "cd", "chdir", "clc", "clear", "clhy", "cli", "clp", "cls",
            "clv", "compare", "copy", "cp", "cpi", "cpp", "cvpa", "dbp", "del", "diff", "dir",
            "ebp", "echo", "epal", "epcsv", "epsn", "erase", "etsn", "fc", "fl", "ft", "fw",
            "gc", "gci", "gcm", "gcs", "gdr", "ghy", "gi", "gjb", "gl", "gm", "gmo", "gp",
            "gps", "gpv", "group", "gsn", "gsnp", "gsv", "gu", "gv", "gwmi", "h", "history",
            "icm", "iex", "ihy", "ii", "ipal", "ipcsv", "ipmo", "ipsn", "iwmi", "kill", "lp",
            "ls", "man", "md", "measure", "mi", "mount", "move", "mp", "mv", "nal", "ndr",
            "ni", "nmo", "npssc", "nsn", "nv", "ogv", "oh", "popd", "ps", "pushd", "pwd",
            "r", "rbp", "rcjb", "rcsn", "rd", "rdr", "ren", "ri", "rjb", "rm", "rmdir", "rmo",
            "rni", "rnp", "rp", "rsn", "rsnp", "rujb", "rv", "rvpa", "rwmi", "sajb", "sal",
            "sasv", "sbp", "sc", "select", "set", "shcm", "si", "sl", "sleep", "sls", "sort",
            "sp", "spjb", "spps", "spsv", "start", "sujb", "sv", "swmi", "tee", "trcm", "type",
            "where", "wjb", "write");

    /** Known names, and whether they are runnable, cached across analyses. */
    private static final Map<String, Boolean> RESOLVED = new HashMap<>();

    private static List<String> pathDirectories;
    private static List<String> pathExtensions;

    private CommandResolver() {
    }

    /**
     * @param kind  interpreter whose vocabulary applies
     * @param token the first word of a command
     * @return {@code true} when the word looks runnable
     */
    public static boolean isKnown(ShellKind kind, String token) {
        if (token == null || token.isBlank()) {
            return true;
        }
        String name = token.strip();
        if (!isPlainWord(name)) {
            // Paths, variables, wildcards and the like are resolved by the shell.
            return true;
        }
        String key = kind.name() + '\u0000' + name.toLowerCase(Locale.ROOT);
        Boolean cached = RESOLVED.get(key);
        if (cached != null) {
            return cached;
        }
        boolean known = computeKnown(kind, name);
        synchronized (RESOLVED) {
            RESOLVED.put(key, known);
        }
        return known;
    }

    private static boolean computeKnown(ShellKind kind, String name) {
        String lower = name.toLowerCase(Locale.ROOT);
        // Call and goto targets are labels, not commands.
        if (lower.equals("call") || lower.equals("goto")) {
            return true;
        }
        for (CompletionItem item : CommandCatalog.commands(kind)) {
            if (item.label().equalsIgnoreCase(name)) {
                return true;
            }
        }
        if (kind == ShellKind.POWERSHELL && POWERSHELL_ALIASES.contains(lower)) {
            return true;
        }
        return existsOnPath(name);
    }

    /** Searches {@code PATH} for a program with one of the {@code PATHEXT} suffixes. */
    private static boolean existsOnPath(String name) {
        for (String directory : pathDirectories()) {
            File base = new File(directory);
            if (!base.isDirectory()) {
                continue;
            }
            for (String extension : pathExtensions()) {
                if (new File(base, name + extension).isFile()) {
                    return true;
                }
            }
        }
        return false;
    }

    private static List<String> pathDirectories() {
        List<String> cached = pathDirectories;
        if (cached == null) {
            cached = split(System.getenv("PATH"), File.pathSeparator);
            pathDirectories = cached;
        }
        return cached;
    }

    private static List<String> pathExtensions() {
        List<String> cached = pathExtensions;
        if (cached == null) {
            String value = System.getenv("PATHEXT");
            List<String> extensions = split(value == null ? DEFAULT_PATHEXT : value, ";");
            if (extensions.isEmpty()) {
                extensions = split(DEFAULT_PATHEXT, ";");
            }
            // The bare name is tried too, for files that already carry one.
            List<String> withEmpty = new ArrayList<>(extensions);
            withEmpty.add("");
            cached = withEmpty;
            pathExtensions = cached;
        }
        return cached;
    }

    private static List<String> split(String value, String separator) {
        List<String> parts = new ArrayList<>();
        if (value == null || value.isBlank()) {
            return parts;
        }
        for (String part : value.split(java.util.regex.Pattern.quote(separator))) {
            String trimmed = part.strip().replace("\"", "");
            if (!trimmed.isEmpty()) {
                parts.add(trimmed);
            }
        }
        return parts;
    }

    /**
     * @param name the word to test
     * @return {@code true} when it is a bare command name rather than something
     *         the shell resolves itself
     */
    private static boolean isPlainWord(String name) {
        if (name.length() < 2 || name.length() > 64) {
            return false;
        }
        for (int i = 0; i < name.length(); i++) {
            char c = name.charAt(i);
            if (!Character.isLetterOrDigit(c) && c != '-' && c != '_' && c != '.') {
                return false;
            }
        }
        // A dot means a file name or an extension; leave those to the shell.
        return name.indexOf('.') < 0;
    }

    /** Forgets the cache, needed when {@code PATH} changes under a running app. */
    public static synchronized void clearCache() {
        RESOLVED.clear();
        pathDirectories = null;
        pathExtensions = null;
    }
}
