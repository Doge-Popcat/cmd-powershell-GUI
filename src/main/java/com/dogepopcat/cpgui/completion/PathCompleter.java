package com.dogepopcat.cpgui.completion;

import com.dogepopcat.cpgui.shell.ShellKind;

import java.io.File;
import java.text.DateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.Date;
import java.util.List;
import java.util.Locale;

/**
 * Completes file system paths.
 *
 * <p>Only words that already look like a path are completed, so ordinary
 * arguments such as {@code hello} are left to the command catalogue. A leading
 * quote is preserved, and a suggestion is quoted when the resulting path
 * contains a space, which is what a user would have had to type anyway.</p>
 */
public final class PathCompleter {

    /** Upper bound on the number of path suggestions. */
    private static final int MAX_SUGGESTIONS = 60;

    private PathCompleter() {
    }

    /** The directory listing a path word should be completed against. */
    private record Split(String quote, String directoryPart, String leaf) {
    }

    /**
     * @param word    the partial word under the caret
     * @param baseDir directory relative paths are resolved against
     * @param kind    interpreter, which decides the path separator used
     * @return suggestions, empty when the word is not path like
     */
    public static List<CompletionItem> complete(String word, File baseDir, ShellKind kind) {
        if (word == null || word.isEmpty() || baseDir == null) {
            return List.of();
        }
        Split split = split(word);
        if (split == null) {
            return List.of();
        }

        File directory = resolve(baseDir, split.directoryPart());
        if (directory == null || !directory.isDirectory()) {
            return List.of();
        }
        File[] children = directory.listFiles();
        if (children == null || children.length == 0) {
            return List.of();
        }
        Arrays.sort(children, Comparator
                .comparing(File::isDirectory).reversed()
                .thenComparing(file -> file.getName().toLowerCase(Locale.ROOT)));

        String leafLower = split.leaf().toLowerCase(Locale.ROOT);
        List<CompletionItem> items = new ArrayList<>();
        for (File child : children) {
            if (child.isHidden() && !split.leaf().startsWith(".")) {
                continue;
            }
            String name = child.getName();
            if (!leafLower.isEmpty() && !name.toLowerCase(Locale.ROOT).startsWith(leafLower)) {
                continue;
            }
            items.add(toItem(split, child, name, kind));
            if (items.size() >= MAX_SUGGESTIONS) {
                break;
            }
        }
        return items;
    }

    private static CompletionItem toItem(Split split, File child, String name, ShellKind kind) {
        boolean directory = child.isDirectory();
        String separator = kind == ShellKind.CMD ? "\\" : "\\";
        String bare = split.directoryPart() + name + (directory ? separator : "");
        String inserted = quoteIfNeeded(split.quote(), bare);
        return CompletionItem.path(bare, inserted, describe(child, directory));
    }

    private static String quoteIfNeeded(String quote, String value) {
        if (!quote.isEmpty() || value.indexOf(' ') >= 0) {
            return (quote.isEmpty() ? "\"" : quote) + value;
        }
        return value;
    }

    private static String describe(File file, boolean directory) {
        if (directory) {
            return "Folder";
        }
        long length = file.length();
        String size = length < 1024
                ? length + " B"
                : length < 1024 * 1024
                        ? (length / 1024) + " KB"
                        : String.format(Locale.ROOT, "%.1f MB", length / 1048576.0);
        String modified = DateFormat.getDateInstance(DateFormat.SHORT)
                .format(new Date(file.lastModified()));
        return size + " \u00b7 " + modified;
    }

    /**
     * Decides whether a word is path like and separates it into the part that
     * names a directory and the part being typed.
     *
     * @return the split, or {@code null} when the word is not a path
     */
    private static Split split(String word) {
        String quote = "";
        String body = word;
        if (body.startsWith("\"")) {
            quote = "\"";
            body = body.substring(1);
        }
        int backSlash = body.lastIndexOf('\\');
        int forwardSlash = body.lastIndexOf('/');
        int lastSeparator = Math.max(backSlash, forwardSlash);

        // A leading "/" is a cmd switch such as "/s", not a rooted path, so a
        // separator only counts when something precedes it or it is a backslash.
        boolean pathLike = lastSeparator > 0 || (backSlash >= 0 && !body.isEmpty());
        if (pathLike) {
            return new Split(quote, body.substring(0, lastSeparator + 1),
                    body.substring(lastSeparator + 1));
        }
        // "C:" on its own, or a dot relative path such as ".\src" or "..".
        if (body.length() == 2 && body.charAt(1) == ':') {
            return new Split(quote, body + "\\", "");
        }
        if (body.startsWith(".")) {
            return new Split(quote, "", body);
        }
        return null;
    }

    /** Turns the directory part of a word into an absolute directory. */
    private static File resolve(File baseDir, String directoryPart) {
        if (directoryPart.isEmpty()) {
            return baseDir;
        }
        File candidate = new File(directoryPart);
        if (candidate.isAbsolute()) {
            return candidate;
        }
        return new File(baseDir, directoryPart);
    }
}
