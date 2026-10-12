package com.dogepopcat.cpgui.analysis;

import com.dogepopcat.cpgui.shell.ShellKind;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * A small, allocation friendly tokeniser for cmd and PowerShell scripts.
 *
 * <p>It is deliberately forgiving: it never fails and never throws, because it
 * runs on every keystroke. Its output drives syntax highlighting, and is reused
 * by the diagnostics and completion features.</p>
 */
public final class ShellLexer {

    /** Kinds of token the editor knows how to colour. */
    public enum TokenType {
        COMMAND,
        KEYWORD,
        PARAMETER,
        SWITCH,
        VARIABLE,
        STRING,
        NUMBER,
        OPERATOR,
        COMMENT,
        LABEL
    }

    /**
     * A classified range of the document.
     *
     * @param type  what the range represents
     * @param start absolute offset of the first character
     * @param end   absolute offset just past the last character
     * @param text  the matched source text
     */
    public record Token(TokenType type, int start, int end, String text) {

        /** @return the number of characters covered. */
        public int length() {
            return end - start;
        }
    }

    private static final Set<String> CMD_COMMANDS = Set.of(
            "assoc", "attrib", "break", "bcdedit", "cacls", "call", "cd", "chcp", "chdir",
            "chkdsk", "chkntfs", "cls", "cmd", "color", "comp", "compact", "convert", "copy",
            "date", "del", "dir", "diskpart", "doskey", "driverquery", "echo", "endlocal",
            "erase", "exit", "fc", "find", "findstr", "fltmc", "forfiles", "format", "fsutil",
            "ftype", "getmac", "goto", "gpresult", "gpupdate", "icacls", "ipconfig", "label",
            "md", "mkdir", "mklink", "mode", "more", "move", "msiexec", "net", "netsh",
            "openfiles", "path", "pause", "ping", "popd", "powercfg", "print", "prompt",
            "pushd", "rd", "recover", "reg", "regedit", "ren", "rename", "replace", "rmdir",
            "robocopy", "route", "runas", "sc", "schtasks", "set", "setlocal", "sfc", "shift",
            "shutdown", "sort", "start", "subst", "systeminfo", "takeown", "taskkill",
            "tasklist", "time", "title", "tracert", "tree", "type", "typeperf", "ver", "verify",
            "vol", "where", "whoami", "wmic", "xcopy", "rem");

    private static final Set<String> CMD_KEYWORDS = Set.of(
            "if", "else", "exist", "defined", "errorlevel", "not", "for", "in", "do", "goto",
            "call", "exit", "setlocal", "endlocal", "shift", "choice", "cmdextversion");

    private static final Set<String> POWERSHELL_KEYWORDS = Set.of(
            "if", "else", "elseif", "switch", "foreach", "foreach-object", "for", "while", "do",
            "until", "function", "filter", "param", "return", "break", "continue", "try",
            "catch", "finally", "throw", "begin", "process", "end", "class", "enum", "using",
            "in", "trap", "exit", "default", "workflow", "parallel", "sequence", "dynamicparam",
            "data", "hidden", "static", "configuration");

    private static final Set<String> POWERSHELL_OPERATORS = Set.of(
            "eq", "ne", "gt", "ge", "lt", "le", "like", "notlike", "match", "notmatch",
            "contains", "notcontains", "in", "notin", "replace", "split", "join", "is", "isnot",
            "as", "and", "or", "not", "xor", "band", "bor", "bxor", "bnot", "shl", "shr", "f");

    /** Words that commonly follow a cmdlet and are worth colouring. */
    private static final Set<String> POWERSHELL_COMMON_VERBS = Set.of(
            "get", "set", "new", "remove", "add", "clear", "copy", "move", "rename", "start",
            "stop", "restart", "enable", "disable", "invoke", "test", "select", "where",
            "foreach", "sort", "group", "measure", "compare", "convert", "convertto",
            "convertfrom", "export", "import", "out", "write", "read", "push", "pop", "join",
            "split", "format", "show", "hide", "register", "unregister", "mount", "dismount",
            "resolve", "save", "send", "receive", "enter", "exit", "wait", "watch", "debug",
            "update", "install", "uninstall", "optimize", "repair", "reset", "restore",
            "suspend", "resume", "block", "unblock", "grant", "revoke", "connect",
            "disconnect", "open", "close", "find", "search", "merge", "compress", "expand");

    private ShellLexer() {
    }

    /**
     * Tokenises a whole document or a fragment.
     *
     * @param kind       interpreter whose syntax applies
     * @param text       the source text
     * @param baseOffset absolute offset of {@code text} inside the document, so
     *                   that the returned ranges can be applied directly
     * @return the tokens in source order
     */
    public static List<Token> tokenize(ShellKind kind, String text, int baseOffset) {
        List<Token> tokens = new ArrayList<>();
        if (text == null || text.isEmpty()) {
            return tokens;
        }
        int length = text.length();
        int i = 0;
        boolean commandPosition = true;

        while (i < length) {
            char c = text.charAt(i);

            if (c == '\n' || c == '\r') {
                i++;
                commandPosition = true;
                continue;
            }
            if (c == ' ' || c == '\t') {
                i++;
                continue;
            }

            int start = i;

            // --- comments ---------------------------------------------------
            if (kind == ShellKind.POWERSHELL && c == '#') {
                i = lineEnd(text, i);
                tokens.add(token(TokenType.COMMENT, text, start, i, baseOffset));
                continue;
            }
            if (kind == ShellKind.CMD && commandPosition) {
                if (text.startsWith("::", i)) {
                    i = lineEnd(text, i);
                    tokens.add(token(TokenType.COMMENT, text, start, i, baseOffset));
                    continue;
                }
                if (startsWithWord(text, i, "rem")) {
                    i = lineEnd(text, i);
                    tokens.add(token(TokenType.COMMENT, text, start, i, baseOffset));
                    continue;
                }
            }

            // --- strings ----------------------------------------------------
            if (c == '"') {
                i = scanQuoted(text, i, '"', kind == ShellKind.POWERSHELL ? '`' : '\0');
                tokens.add(token(TokenType.STRING, text, start, i, baseOffset));
                commandPosition = false;
                continue;
            }
            if (kind == ShellKind.POWERSHELL && c == '\'') {
                i = scanQuoted(text, i, '\'', '\0');
                tokens.add(token(TokenType.STRING, text, start, i, baseOffset));
                commandPosition = false;
                continue;
            }

            // --- variables --------------------------------------------------
            if (kind == ShellKind.CMD && (c == '%' || c == '!')) {
                i = scanCmdVariable(text, i, c);
                tokens.add(token(TokenType.VARIABLE, text, start, i, baseOffset));
                continue;
            }
            if (kind == ShellKind.POWERSHELL && c == '$') {
                i = scanPowerShellVariable(text, i);
                tokens.add(token(TokenType.VARIABLE, text, start, i, baseOffset));
                continue;
            }

            // --- labels -----------------------------------------------------
            if (kind == ShellKind.CMD && c == ':' && commandPosition && start + 1 < length
                    && isWordChar(text.charAt(start + 1))) {
                i = scanWhile(text, start + 1, ch -> isWordChar(ch) || ch == '-');                tokens.add(token(TokenType.LABEL, text, start, i, baseOffset));
                commandPosition = false;
                continue;
            }

            // --- operators --------------------------------------------------
            if (isOperatorStart(kind, c)) {
                i = scanOperator(kind, text, i);
                String operator = text.substring(start, i);
                tokens.add(token(TokenType.OPERATOR, text, start, i, baseOffset));
                commandPosition = isCommandSeparator(kind, operator);
                continue;
            }

            // --- numbers ----------------------------------------------------
            if (Character.isDigit(c)) {
                i = scanWhile(text, i, ShellLexer::isNumberChar);
                tokens.add(token(TokenType.NUMBER, text, start, i, baseOffset));
                commandPosition = false;
                continue;
            }

            // --- parameters and words ---------------------------------------
            if (kind == ShellKind.POWERSHELL && c == '-'
                    && i + 1 < length && isWordStart(text.charAt(i + 1))) {
                i = scanWhile(text, i + 1, ShellLexer::isParameterChar);
                String name = text.substring(start + 1, i).toLowerCase(Locale.ROOT);
                TokenType type = POWERSHELL_OPERATORS.contains(name)
                        ? TokenType.OPERATOR : TokenType.PARAMETER;
                tokens.add(token(type, text, start, i, baseOffset));
                commandPosition = false;
                continue;
            }

            if (isWordStart(c)) {
                i = scanWhile(text, i, ShellLexer::isWordChar);
                String word = text.substring(start, i);
                TokenType type = classify(kind, word, commandPosition);
                if (type != null) {
                    tokens.add(token(type, text, start, i, baseOffset));
                }
                commandPosition = false;
                continue;
            }

            // Anything else is punctuation we do not colour.
            i++;
        }
        return tokens;
    }

    private static Token token(TokenType type, String text, int start, int end, int baseOffset) {
        return new Token(type, baseOffset + start, baseOffset + end, text.substring(start, end));
    }

    /**
     * Chooses the token type for a bare word.
     *
     * @return the type to colour, or {@code null} when the word is an ordinary
     *         argument and should keep the default foreground
     */
    private static TokenType classify(ShellKind kind, String word, boolean commandPosition) {
        String lower = word.toLowerCase(Locale.ROOT);
        if (kind == ShellKind.CMD) {
            if (word.startsWith("/") || word.startsWith("-")) {
                return word.length() > 1 ? TokenType.SWITCH : null;
            }
            if (CMD_KEYWORDS.contains(lower)) {
                return TokenType.KEYWORD;
            }
            if (CMD_COMMANDS.contains(lower)) {
                return TokenType.COMMAND;
            }
            return commandPosition ? TokenType.COMMAND : null;
        }
        if (POWERSHELL_KEYWORDS.contains(lower)) {
            return TokenType.KEYWORD;
        }
        if (looksLikeCmdlet(word)) {
            return TokenType.COMMAND;
        }
        return commandPosition ? TokenType.COMMAND : null;
    }

    /** {@code Get-Item} and friends follow the Verb-Noun convention. */
    private static boolean looksLikeCmdlet(String word) {
        int dash = word.indexOf('-');
        if (dash <= 0 || dash == word.length() - 1) {
            return false;
        }
        if (!Character.isLetter(word.charAt(0))) {
            return false;
        }
        for (int i = 0; i < word.length(); i++) {
            char c = word.charAt(i);
            if (c == '-') {
                continue;
            }
            if (!Character.isLetterOrDigit(c)) {
                return false;
            }
        }
        return POWERSHELL_COMMON_VERBS.contains(word.substring(0, dash).toLowerCase(Locale.ROOT));
    }

    private static boolean isCommandSeparator(ShellKind kind, String operator) {
        if (kind == ShellKind.CMD) {
            return operator.equals("|") || operator.equals("&") || operator.equals("&&")
                    || operator.equals("||");
        }
        return operator.equals("|") || operator.equals(";") || operator.equals("&")
                || operator.equals("&&") || operator.equals("||") || operator.equals("{");
    }

    private static boolean isOperatorStart(ShellKind kind, char c) {
        if (kind == ShellKind.CMD) {
            return c == '|' || c == '&' || c == '<' || c == '>' || c == '=' || c == '(' || c == ')';
        }
        return c == '|' || c == '&' || c == '=' || c == '+' || c == '*' || c == '/'
                || c == '<' || c == '>' || c == '!' || c == ';' || c == '{' || c == '}'
                || c == '(' || c == ')' || c == ',' || c == '[';
    }

    private static int scanOperator(ShellKind kind, String text, int start) {
        int length = text.length();
        int i = start;
        char first = text.charAt(i);
        if (kind == ShellKind.CMD && first == '2' && i + 3 < length
                && text.startsWith("2>&1", i)) {
            return i + 4;
        }
        while (i < length) {
            char c = text.charAt(i);
            if (c == '&' || c == '|' || c == '<' || c == '>') {
                i++;
                continue;
            }
            break;
        }
        if (i > start) {
            return i;
        }
        // "=", "+", "-" style operators.
        while (i < length && isOperatorStart(kind, text.charAt(i))) {
            i++;
        }
        return Math.max(i, start + 1);
    }

    private static int lineEnd(String text, int start) {
        int i = start;
        while (i < text.length() && text.charAt(i) != '\n' && text.charAt(i) != '\r') {
            i++;
        }
        return i;
    }

    private static int scanQuoted(String text, int start, char quote, char escape) {
        int i = start + 1;
        while (i < text.length()) {
            char c = text.charAt(i);
            if (escape != '\0' && c == escape) {
                i += 2;
                continue;
            }
            if (c == quote) {
                // A doubled quote is an escaped quote in both interpreters.
                if (i + 1 < text.length() && text.charAt(i + 1) == quote) {
                    i += 2;
                    continue;
                }
                return i + 1;
            }
            if (c == '\n') {
                return i;
            }
            i++;
        }
        return text.length();
    }

    private static int scanCmdVariable(String text, int start, char delimiter) {
        int i = start + 1;
        while (i < text.length()) {
            char c = text.charAt(i);
            if (c == delimiter) {
                return i + 1;
            }
            if (Character.isWhitespace(c)) {
                break;
            }
            i++;
        }
        // An unpaired % such as "%~dp0" still gets a sensible span.
        return Math.max(i, start + 1);
    }

    private static int scanPowerShellVariable(String text, int start) {
        int i = start + 1;
        if (i >= text.length()) {
            return i;
        }
        char c = text.charAt(i);
        if (c == '{') {
            int close = text.indexOf('}', i);
            return close < 0 ? text.length() : close + 1;
        }
        if (c == '(') {
            int depth = 1;
            int j = i + 1;
            while (j < text.length() && depth > 0) {
                char d = text.charAt(j);
                if (d == '(') {
                    depth++;
                } else if (d == ')') {
                    depth--;
                }
                j++;
            }
            return j;
        }
        return scanWhile(text, i, ch -> isWordChar(ch) || ch == ':' || ch == '?');
    }

    private static boolean startsWithWord(String text, int offset, String word) {
        int end = offset + word.length();
        if (end > text.length()) {
            return false;
        }
        if (!text.regionMatches(true, offset, word, 0, word.length())) {
            return false;
        }
        return end >= text.length() || !isWordChar(text.charAt(end));
    }

    private static int scanWhile(String text, int start, java.util.function.IntPredicate accept) {
        int i = start;
        while (i < text.length() && accept.test(text.charAt(i))) {
            i++;
        }
        return i;
    }

    private static boolean isWordStart(char c) {
        return Character.isLetter(c) || c == '_' || c == '.' || c == '\\' || c == '/'
                || c == ':' || c == '@' || c == '~';
    }

    private static boolean isWordChar(int codePoint) {
        char c = (char) codePoint;
        return Character.isLetterOrDigit(c) || c == '_' || c == '-' || c == '.' || c == '\\'
                || c == '/' || c == ':' || c == '@' || c == '~' || c == '%' || c == '+'
                || c == '#' || c == '!' || c == '?' || c == '*';
    }

    private static boolean isParameterChar(int codePoint) {
        char c = (char) codePoint;
        return Character.isLetterOrDigit(c) || c == '_' || c == '-';
    }

    private static boolean isNumberChar(int codePoint) {
        char c = (char) codePoint;
        return Character.isDigit(c) || c == '.' || c == 'x' || c == 'X'
                || (c >= 'a' && c <= 'f') || (c >= 'A' && c <= 'F');
    }
}
