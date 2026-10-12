package com.dogepopcat.cpgui.completion;

import com.dogepopcat.cpgui.shell.ShellKind;

import java.util.List;
import java.util.Locale;
import java.util.StringJoiner;

/**
 * The signature hint shown while the caret sits in a command's arguments.
 *
 * @param command     the command being invoked
 * @param description what the command does
 * @param parameters  the switches or parameters it accepts
 */
public record ParameterHint(String command, String description, List<String> parameters) {

    /** @return the parameters joined for display, or a placeholder when none. */
    public String parameterLine() {
        if (parameters.isEmpty()) {
            return "";
        }
        StringJoiner joiner = new StringJoiner("  ");
        for (String parameter : parameters) {
            joiner.add(parameter);
        }
        return joiner.toString();
    }

    /**
     * Works out whether a hint should be shown for the caret.
     *
     * <p>A hint is offered once a known command has been typed and the caret has
     * moved past it into the argument area.</p>
     *
     * @param kind  interpreter whose language applies
     * @param text  the whole document
     * @param caret caret offset into {@code text}
     * @return the hint, or {@code null} when the caret is not in a known
     *         command's arguments
     */
    public static ParameterHint forCaret(ShellKind kind, String text, int caret) {
        if (text == null || text.isEmpty()) {
            return null;
        }
        int position = Math.max(0, Math.min(caret, text.length()));
        int lineStart = position;
        while (lineStart > 0 && text.charAt(lineStart - 1) != '\n'
                && text.charAt(lineStart - 1) != '\r') {
            lineStart = lineStart - 1;
        }
        String line = text.substring(lineStart, position);

        int statementStart = 0;
        for (int i = line.length() - 1; i >= 0; i--) {
            char c = line.charAt(i);
            if (c == '|' || c == '&' || c == ';') {
                statementStart = i + 1;
                break;
            }
        }
        String statement = line.substring(statementStart).stripLeading();
        int nameEnd = 0;
        while (nameEnd < statement.length() && !Character.isWhitespace(statement.charAt(nameEnd))) {
            nameEnd++;
        }
        if (nameEnd == 0 || nameEnd >= statement.length()) {
            // Either no command yet, or the caret is still inside its name.
            return null;
        }
        String command = statement.substring(0, nameEnd);

        List<CompletionItem> entries = CommandCatalog.commands(kind);
        String description = null;
        for (CompletionItem item : entries) {
            if (item.label().equalsIgnoreCase(command)) {
                description = item.detail();
                break;
            }
        }
        if (description == null) {
            return null;
        }

        List<String> parameters = new java.util.ArrayList<>();
        for (CompletionItem item : CommandCatalog.switchesFor(kind, command)) {
            parameters.add(item.label());
        }
        if (kind == ShellKind.POWERSHELL) {
            for (CompletionItem item : CommandCatalog.commonParameters(kind)) {
                parameters.add(item.label());
            }
        }
        return new ParameterHint(command, description, List.copyOf(parameters));
    }

    /** @return the command name in lower case, used for comparisons in tests. */
    public String normalisedCommand() {
        return command.toLowerCase(Locale.ROOT);
    }
}
