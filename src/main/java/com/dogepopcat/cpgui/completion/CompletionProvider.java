package com.dogepopcat.cpgui.completion;

import com.dogepopcat.cpgui.completion.CompletionItem;
import com.dogepopcat.cpgui.shell.ShellKind;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;

/**
 * Works out what should be offered at a caret position.
 *
 * <p>The provider only needs the document text and the caret offset, which keeps
 * it independent of Swing and easy to reason about.</p>
 */
public final class CompletionProvider {

    /** Upper bound on the number of suggestions returned. */
    private static final int MAX_ITEMS = 300;

    private static final List<CompletionItem> VARIABLE_SUFFIXES = List.of(
            CompletionItem.variable("%*", "All arguments passed to the script"),
            CompletionItem.variable("%0", "Name of the script being run"),
            CompletionItem.variable("%1", "First argument passed to the script"),
            CompletionItem.variable("%~dp0", "Drive and path of the script"));

    private static final List<CompletionItem> POWERSHELL_AUTOMATIC = List.of(
            CompletionItem.variable("$_", "The current object in the pipeline"),
            CompletionItem.variable("$args", "Arguments not bound to a parameter"),
            CompletionItem.variable("$env:", "Environment variables, for example $env:PATH"),
            CompletionItem.variable("$Error", "The most recent errors"),
            CompletionItem.variable("$false", "The boolean false value"),
            CompletionItem.variable("$HOME", "The user's home directory"),
            CompletionItem.variable("$Host", "Information about the current host"),
            CompletionItem.variable("$input", "Input passed to a function or script block"),
            CompletionItem.variable("$LASTEXITCODE", "Exit code of the last native program"),
            CompletionItem.variable("$null", "The null value"),
            CompletionItem.variable("$PID", "Process id of the current session"),
            CompletionItem.variable("$PROFILE", "Path of the profile script"),
            CompletionItem.variable("$PSVersionTable", "Version information for PowerShell"),
            CompletionItem.variable("$PWD", "The current working directory"),
            CompletionItem.variable("$true", "The boolean true value"));

    private CompletionProvider() {
    }

    /**
     * Everything the provider needs to make a suggestion.
     *
     * @param kind    interpreter whose language applies
     * @param text    the whole document
     * @param caret   caret offset into {@code text}
     * @param baseDir directory relative paths are resolved against, or
     *                {@code null} to skip path completion
     */
    public record Context(ShellKind kind, String text, int caret, java.io.File baseDir) {

        /** Convenience constructor for callers that do not complete paths. */
        public Context(ShellKind kind, String text, int caret) {
            this(kind, text, caret, null);
        }
    }

    /**
     * The suggestions for one caret position.
     *
     * @param replaceStart offset where the partial word begins
     * @param replaceEnd   offset where it ends, which is the caret
     * @param items        suggestions in the order they should be shown
     */
    public record Result(int replaceStart, int replaceEnd, List<CompletionItem> items) {

        /** @return {@code true} when there is nothing worth showing. */
        public boolean isEmpty() {
            return items.isEmpty();
        }
    }

    /**
     * Produces suggestions for a caret position.
     *
     * @param context the caret context
     * @return the suggestions, never {@code null}
     */
    public static Result complete(Context context) {
        String text = context.text() == null ? "" : context.text();
        int caret = Math.max(0, Math.min(context.caret(), text.length()));
        ShellKind kind = context.kind();

        int wordStart = scanWordStart(kind, text, caret);
        String word = text.substring(wordStart, caret);
        String linePrefix = lineStart(text, wordStart);
        String command = currentCommand(linePrefix);
        String prefix = word.toLowerCase(Locale.ROOT);

        // A word that already looks like a path is completed from the disk.
        List<CompletionItem> paths = PathCompleter.complete(word, context.baseDir(), kind);
        if (!paths.isEmpty()) {
            return new Result(wordStart, caret, paths);
        }

        List<CompletionItem> candidates = candidatesFor(kind, word, command, linePrefix);
        return new Result(wordStart, caret, filter(candidates, prefix));
    }

    private static List<CompletionItem> candidatesFor(ShellKind kind, String word,
                                                      String command, String linePrefix) {
        if (kind == ShellKind.CMD) {
            if (word.startsWith("%")) {
                return cmdVariables();
            }
            if (word.startsWith("/") || word.startsWith("-")) {
                List<CompletionItem> switches = CommandCatalog.switchesFor(kind, command);
                return switches.isEmpty() ? CommandCatalog.commands(kind) : switches;
            }
            if (word.isEmpty() && !command.isEmpty() && !linePrefix.isBlank()) {
                List<CompletionItem> switches = CommandCatalog.switchesFor(kind, command);
                if (!switches.isEmpty()) {
                    List<CompletionItem> combined = new ArrayList<>(switches);
                    combined.addAll(CommandCatalog.commands(kind));
                    return combined;
                }
            }
            return CommandCatalog.commands(kind);
        }

        // PowerShell
        if (word.startsWith("$")) {
            return powerShellVariables();
        }
        if (word.startsWith("-")) {
            return CommandCatalog.commonParameters(kind);
        }
        if (word.isEmpty() && !command.isEmpty() && !linePrefix.isBlank()) {
            List<CompletionItem> combined = new ArrayList<>(CommandCatalog.commonParameters(kind));
            combined.addAll(CommandCatalog.commands(kind));
            return combined;
        }
        return CommandCatalog.commands(kind);
    }

    private static List<CompletionItem> cmdVariables() {
        List<CompletionItem> items = new ArrayList<>(VARIABLE_SUFFIXES);
        for (Map.Entry<String, String> entry : environmentVariables().entrySet()) {
            items.add(CompletionItem.variable("%" + entry.getKey() + "%", entry.getValue()));
        }
        return items;
    }

    private static List<CompletionItem> powerShellVariables() {
        List<CompletionItem> items = new ArrayList<>(POWERSHELL_AUTOMATIC);
        for (Map.Entry<String, String> entry : environmentVariables().entrySet()) {
            items.add(CompletionItem.variable("$env:" + entry.getKey(), entry.getValue()));
        }
        return items;
    }

    /** @return the process environment, sorted so the popup order is stable. */
    private static Map<String, String> environmentVariables() {
        Map<String, String> sorted = new TreeMap<>(String.CASE_INSENSITIVE_ORDER);
        sorted.putAll(System.getenv());
        return sorted;
    }

    private static List<CompletionItem> filter(List<CompletionItem> candidates, String prefix) {
        if (prefix.isEmpty()) {
            return candidates.size() <= MAX_ITEMS ? candidates : candidates.subList(0, MAX_ITEMS);
        }
        List<CompletionItem> starts = new ArrayList<>();
        for (CompletionItem item : candidates) {
            if (item.label().toLowerCase(Locale.ROOT).startsWith(prefix)) {
                starts.add(item);
                if (starts.size() >= MAX_ITEMS) {
                    return starts;
                }
            }
        }
        if (!starts.isEmpty()) {
            return starts;
        }
        // Fall back to a substring match so "Item" still finds Remove-Item.
        List<CompletionItem> contains = new ArrayList<>();
        for (CompletionItem item : candidates) {
            if (item.label().toLowerCase(Locale.ROOT).contains(prefix)) {
                contains.add(item);
                if (contains.size() >= MAX_ITEMS) {
                    break;
                }
            }
        }
        return contains;
    }

    /** Walks back over the characters that make up one word. */
    private static int scanWordStart(ShellKind kind, String text, int caret) {
        int i = caret;
        while (i > 0 && isWordChar(kind, text.charAt(i - 1))) {
            i--;
        }
        return i;
    }

    private static boolean isWordChar(ShellKind kind, char c) {
        if (Character.isLetterOrDigit(c) || c == '_' || c == '.' || c == '\\' || c == '/'
                || c == '~' || c == '@' || c == '-') {
            return true;
        }
        if (kind == ShellKind.CMD) {
            return c == '%' || c == '!';
        }
        return c == '$' || c == ':';
    }

    /** @return the text of the line that contains {@code offset}, up to it. */
    private static String lineStart(String text, int offset) {
        int i = offset;
        while (i > 0 && text.charAt(i - 1) != '\n' && text.charAt(i - 1) != '\r') {
            i--;
        }
        return text.substring(i, offset);
    }

    /**
     * @return the command the caret is an argument of, meaning the first word
     *         after the last command separator
     */
    private static String currentCommand(String linePrefix) {
        int start = 0;
        for (int i = linePrefix.length() - 1; i >= 0; i--) {
            char c = linePrefix.charAt(i);
            if (c == '|' || c == '&' || c == ';') {
                start = i + 1;
                break;
            }
        }
        String tail = linePrefix.substring(start).stripLeading();
        int end = 0;
        while (end < tail.length() && !Character.isWhitespace(tail.charAt(end))) {
            end++;
        }
        return tail.substring(0, end);
    }
}
