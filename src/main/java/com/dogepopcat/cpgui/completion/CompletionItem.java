package com.dogepopcat.cpgui.completion;

/**
 * One entry offered by the completion popup.
 *
 * @param label      text shown in the popup
 * @param insertText text inserted into the document when accepted
 * @param detail     short description shown next to the label and, for some
 *                   kinds, as a hint in the status bar
 * @param kind       category, which decides the icon colour and whether a
 *                   trailing space is added
 */
public record CompletionItem(String label, String insertText, String detail, Kind kind) {

    /** Category of a completion, used for colouring and spacing. */
    public enum Kind {
        /** An executable or built-in command. */
        COMMAND,
        /** A language keyword. */
        KEYWORD,
        /** A switch or parameter. */
        PARAMETER,
        /** A variable reference. */
        VARIABLE,
        /** A file system path fragment. */
        PATH
    }

    /** Creates a command style entry that gets a trailing space when accepted. */
    public static CompletionItem command(String name, String description) {
        return new CompletionItem(name, name + " ", description, Kind.COMMAND);
    }

    /** Creates a keyword style entry that gets a trailing space when accepted. */
    public static CompletionItem keyword(String name, String description) {
        return new CompletionItem(name, name + " ", description, Kind.KEYWORD);
    }

    /** Creates a parameter entry, inserted without a trailing space. */
    public static CompletionItem parameter(String name, String description) {
        return new CompletionItem(name, name, description, Kind.PARAMETER);
    }

    /** Creates a variable entry, inserted without a trailing space. */
    public static CompletionItem variable(String name, String description) {
        return new CompletionItem(name, name, description, Kind.VARIABLE);
    }

    /** Creates a path entry; the caller decides whether to append a separator. */
    public static CompletionItem path(String label, String insertText, String description) {
        return new CompletionItem(label, insertText, description, Kind.PATH);
    }

    /** @return {@code true} when accepting this item should leave a trailing space. */
    public boolean appendsSpace() {
        return kind == Kind.COMMAND || kind == Kind.KEYWORD;
    }
}
