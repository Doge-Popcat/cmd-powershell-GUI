package com.dogepopcat.cpgui.analysis;

import com.dogepopcat.cpgui.shell.ShellKind;

import java.util.ArrayList;
import java.util.List;

/**
 * Splits a script into the individual commands an interpreter would execute.
 *
 * <p>The editor lets a user write several commands at once, so the application
 * needs to know where one command ends and the next begins. Both interpreters
 * allow a command to span several physical lines with a continuation character,
 * and both have their own comment syntax, so the split is interpreter
 * specific.</p>
 */
public final class ScriptParser {

    /** Command continuation character at the end of a line. */
    private static final char CMD_CONTINUATION = '^';
    private static final char POWERSHELL_CONTINUATION = '`';

    private ScriptParser() {
    }

    /**
     * One command, or one comment, found in a script.
     *
     * @param lineIndex   zero based index of the first source line
     * @param text        the command exactly as written, continuations included
     * @param comment     {@code true} when the line is a comment
     * @param startOffset document offset where the command starts
     * @param endOffset   document offset just past the command
     */
    public record Command(int lineIndex, String text, boolean comment,
                          int startOffset, int endOffset) {

        /** @return {@code true} when this entry should be handed to the shell. */
        public boolean executable() {
            return !comment && !text.isBlank();
        }
    }

    /**
     * Parses a script.
     *
     * @param kind   interpreter whose syntax should be used
     * @param script the whole editor contents
     * @return every command and comment in source order
     */
    public static List<Command> parse(ShellKind kind, String script) {
        List<Command> result = new ArrayList<>();
        if (script == null || script.isEmpty()) {
            return result;
        }
        List<String> lines = new ArrayList<>();
        List<Integer> starts = new ArrayList<>();
        splitLines(script, lines, starts);

        int index = 0;
        while (index < lines.size()) {
            String line = lines.get(index);
            if (line.isBlank()) {
                index++;
                continue;
            }
            int lineStart = starts.get(index);
            if (isCommentLine(kind, line)) {
                result.add(new Command(index, line, true, lineStart, lineStart + line.length()));
                index++;
                continue;
            }

            StringBuilder text = new StringBuilder(line);
            int lastLine = index;
            while (hasContinuation(kind, text.toString()) && lastLine + 1 < lines.size()) {
                lastLine++;
                text.append('\n').append(lines.get(lastLine));
            }
            int end = starts.get(lastLine) + lines.get(lastLine).length();
            result.add(new Command(index, text.toString(), false, lineStart, end));
            index = lastLine + 1;
        }
        return result;
    }

    /**
     * Splits text on any of the three line terminators, recording where each
     * line starts so that offsets stay exact for {@code \r\n} sources.
     */
    private static void splitLines(String script, List<String> lines, List<Integer> starts) {
        int length = script.length();
        int lineStart = 0;
        int i = 0;
        while (i < length) {
            char c = script.charAt(i);
            if (c == '\r' || c == '\n') {
                lines.add(script.substring(lineStart, i));
                starts.add(lineStart);
                if (c == '\r' && i + 1 < length && script.charAt(i + 1) == '\n') {
                    i++;
                }
                i++;
                lineStart = i;
            } else {
                i++;
            }
        }
        lines.add(script.substring(lineStart));
        starts.add(lineStart);
    }

    /**
     * @return {@code true} when the line only contains a comment for this
     *         interpreter
     */
    public static boolean isCommentLine(ShellKind kind, String line) {
        String trimmed = line.strip();
        if (trimmed.isEmpty()) {
            return false;
        }
        if (kind == ShellKind.POWERSHELL) {
            return trimmed.startsWith("#");
        }
        String upper = trimmed.toUpperCase(java.util.Locale.ROOT);
        return upper.equals("REM")
                || upper.startsWith("REM ")
                || upper.startsWith("REM\t")
                || upper.startsWith("::");
    }

    /** @return {@code true} when the line ends with the continuation character. */
    private static boolean hasContinuation(ShellKind kind, String text) {
        String trimmed = text.stripTrailing();
        if (trimmed.isEmpty()) {
            return false;
        }
        char last = trimmed.charAt(trimmed.length() - 1);
        char expected = kind == ShellKind.CMD ? CMD_CONTINUATION : POWERSHELL_CONTINUATION;
        if (last != expected) {
            return false;
        }
        // An escaped continuation character is literal, not a continuation.
        int back = 0;
        for (int i = trimmed.length() - 2; i >= 0 && trimmed.charAt(i) == expected; i--) {
            back++;
        }
        return back % 2 == 0;
    }

    /**
     * @return the executable source text of a script, with comment-only lines
     *         removed
     */
    public static String executableSource(ShellKind kind, String script) {
        List<Command> commands = parse(kind, script);
        StringBuilder text = new StringBuilder();
        for (Command command : commands) {
            if (command.executable()) {
                text.append(command.text()).append('\n');
            }
        }
        return text.toString();
    }

    /** @return how many executable commands a script contains. */
    public static int executableCount(ShellKind kind, String script) {
        return (int) parse(kind, script).stream().filter(Command::executable).count();
    }
}
