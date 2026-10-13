package com.dogepopcat.cpgui.ui;

import com.dogepopcat.cpgui.i18n.Messages;
import com.dogepopcat.cpgui.i18n.Msg;

import javax.swing.AbstractAction;
import javax.swing.BorderFactory;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollBar;
import javax.swing.JScrollPane;
import javax.swing.JTextField;
import javax.swing.JTextPane;
import javax.swing.KeyStroke;
import javax.swing.SwingUtilities;
import javax.swing.Timer;
import javax.swing.text.BadLocationException;
import javax.swing.text.SimpleAttributeSet;
import javax.swing.text.StyleConstants;
import javax.swing.text.StyledDocument;
import java.awt.BorderLayout;
import java.awt.Color;
import java.util.ArrayList;
import java.util.List;

/**
 * Read-only console that shows everything the shell sessions produce.
 *
 * <p>Output can arrive from background reader threads at a very high rate, so
 * text is queued and flushed to the document on the event dispatch thread in
 * batches. This keeps a {@code dir /s} from flooding the UI with one repaint
 * per line.</p>
 */
public class ConsolePane extends JPanel {

    private static final long serialVersionUID = 1L;

    /** Maximum number of characters kept; older output is trimmed away. */
    private static final int MAX_CHARS = 800_000;
    /** How often queued output is pushed into the document, in milliseconds. */
    private static final int FLUSH_DELAY_MS = 60;

    private final JTextPane text = new JTextPane();
    private final List<Segment> queue = new ArrayList<>();
    private final Timer flushTimer;
    private final JScrollPane scroll;
    private final JScrollBar verticalBar;

    private final JTextField input = new JTextField();
    private final JLabel prompt = new JLabel("CMD>");
    private final JPanel row = new JPanel(new BorderLayout(6, 0));
    /** Foreground set by a color command, or the theme colour by default. */
    private Color currentForeground;
    private final List<String> history = new ArrayList<>();

    private java.util.function.Consumer<String> inputHandler = command -> { };
    private int historyIndex;
    /** The text being typed when the user started looking through the history. */
    private String draft = "";
    /** True while the history is writing the field, so listeners stand aside. */
    private boolean recalling;
    /** True while the interpreter is waiting for an answer to a question. */
    private boolean awaitingInput;
    /** Whether a rule is drawn between runs. Off in terminal mode. */
    private boolean runSeparator = true;

    private record Segment(String text, Color color) {
    }

    public ConsolePane() {
        super(new BorderLayout());
        text.setEditable(false);
        text.setFont(Theme.mono(13));
        text.setBackground(Theme.CONSOLE_BACKGROUND);
        text.setForeground(Theme.CONSOLE_FOREGROUND);
        text.setCaretColor(Theme.CONSOLE_FOREGROUND);
        text.setBorder(BorderFactory.createEmptyBorder(6, 8, 6, 8));

        JScrollPane scroll = new JScrollPane(text);
        scroll.setBorder(BorderFactory.createEmptyBorder());
        scroll.getViewport().setBackground(Theme.CONSOLE_BACKGROUND);
        scroll.getVerticalScrollBar().setUnitIncrement(16);
        add(scroll, BorderLayout.CENTER);

        this.scroll = scroll;
        this.verticalBar = scroll.getVerticalScrollBar();

        add(createInputRow(), BorderLayout.SOUTH);

        flushTimer = new Timer(FLUSH_DELAY_MS, e -> flush());
        flushTimer.setRepeats(false);
    }

    /**
     * Builds the single line prompt at the bottom of the console.
     *
     * <p>Being able to type a one-off command here saves reaching for the mouse
     * and the Run button every time, which is how a console is normally used.</p>
     */
    private JPanel createInputRow() {

        row.setBackground(Theme.CONSOLE_BACKGROUND);
        row.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createMatteBorder(1, 0, 0, 0, Theme.tint(Theme.GUTTER_BACKGROUND, 40)),
                BorderFactory.createEmptyBorder(4, 8, 4, 8)));

        prompt.setFont(Theme.mono(13, java.awt.Font.BOLD));
        prompt.setForeground(Theme.CONSOLE_COMMAND);
        row.add(prompt, BorderLayout.WEST);

        input.setFont(Theme.mono(13));
        input.setBackground(Theme.CONSOLE_BACKGROUND);
        input.setForeground(Theme.CONSOLE_FOREGROUND);
        input.setCaretColor(Theme.CONSOLE_FOREGROUND);
        input.setBorder(BorderFactory.createEmptyBorder(2, 4, 2, 4));
        input.addActionListener(e -> submitInput());
        installHistoryKeys();
        row.add(input, BorderLayout.CENTER);
        Messages.addListener(language -> refreshText());
        Theme.addListener(this::applyTheme);
        // The palette must be applied now, not only on a later change: the look
        // and feel defaults are light whatever the chosen theme is.
        applyTheme();
        refreshText();
        return row;
    }

    /**
     * Binds Up and Down to the command history.
     *
     * <p>Reinstalled after a theme change, because restyling the component tree
     * replaces the input and action maps and would otherwise drop the keys.</p>
     */
    private void installHistoryKeys() {
        input.getInputMap(JComponent.WHEN_FOCUSED).put(
                KeyStroke.getKeyStroke(java.awt.event.KeyEvent.VK_UP, 0), "cpgui.console.prev");
        input.getInputMap(JComponent.WHEN_FOCUSED).put(
                KeyStroke.getKeyStroke(java.awt.event.KeyEvent.VK_DOWN, 0), "cpgui.console.next");
        input.getActionMap().put("cpgui.console.prev", new AbstractAction() {
            private static final long serialVersionUID = 1L;

            @Override
            public void actionPerformed(java.awt.event.ActionEvent e) {
                recall(-1);
            }
        });
        input.getActionMap().put("cpgui.console.next", new AbstractAction() {
            private static final long serialVersionUID = 1L;

            @Override
            public void actionPerformed(java.awt.event.ActionEvent e) {
                recall(1);
            }
        });
    }

    /** Re-applies the palette after a theme change. */
    void applyTheme() {
        installHistoryKeys();
        setPalette(Theme.CONSOLE_BACKGROUND, Theme.CONSOLE_FOREGROUND);
        setBackground(Theme.CONSOLE_BACKGROUND);
        repaint();
    }

    /** Re-applies the localized text after a language change. */
    public void refreshText() {
        input.setToolTipText(Msg.CONSOLE_INPUT_TIP.text());
    }

    private void submitInput() {
        String command = input.getText();
        if (command == null || command.isBlank()) {
            return;
        }
        history.add(command);
        historyIndex = history.size();
        draft = "";
        input.setText("");
        boolean answer = awaitingInput;
        awaitingInput = false;
        if (!answer) {
            startBlock();
        }
        inputHandler.accept(command);
        if (answer) {
            setAwaitingInput(false);
        }
    }

    /**
     * Moves through the entered commands, newest first.
     *
     * <p>Stepping past the newest entry restores whatever was being typed, so a
     * half-written command is not lost by looking something up. Nothing else may
     * reset the position, otherwise the second Up press would start from the
     * newest command again and only the last one would ever be reachable.</p>
     */
    private void recall(int delta) {
        if (history.isEmpty()) {
            return;
        }
        if (recalling) {
            return;
        }
        if (historyIndex >= history.size()) {
            // Remember the draft the first time the user walks away from it.
            draft = input.getText();
        }
        recalling = true;
        try {
            historyIndex = Math.max(0, Math.min(history.size(), historyIndex + delta));
            String recalled = historyIndex >= history.size()
                    ? (draft == null ? "" : draft)
                    : history.get(historyIndex);
            input.setText(recalled);
            input.setCaretPosition(input.getText().length());
        } finally {
            recalling = false;
        }
    }

    /**
     * Registers the receiver for commands typed into the console.
     *
     * @param handler called with the command text
     */
    public void setInputHandler(java.util.function.Consumer<String> handler) {
        this.inputHandler = handler == null ? command -> { } : handler;
    }

    /**
     * Marks the console as waiting for an answer to an interactive command.
     *
     * @param awaiting {@code true} while the interpreter is waiting
     */
    public void setAwaitingInput(boolean awaiting) {
        SwingUtilities.invokeLater(() -> {
            awaitingInput = awaiting;
            prompt.setForeground(awaiting ? Theme.CONSOLE_SYSTEM : Theme.CONSOLE_FOREGROUND);
            input.setToolTipText(awaiting
                    ? Msg.CONSOLE_AWAITING_INPUT_TIP.text()
                    : Msg.CONSOLE_INPUT_TIP.text());
            repaint();
        });
    }

    /** @return {@code true} while the interpreter is waiting for an answer. */
    public boolean isAwaitingInput() {
        return awaitingInput;
    }

    /** Sets the prompt shown before the input field. */
    public void setPrompt(String value) {
        prompt.setText(value == null || value.isBlank() ? ">" : value);
    }

    /** @return the prompt currently shown before the input field. */
    public String promptText() {
        return prompt.getText();
    }

    /**
     * Changes the console font, applying it to the output and the input line.
     *
     * @param font the font to use
     */
    public void setConsoleFont(java.awt.Font font) {
        if (font == null) {
            return;
        }
        text.setFont(font);
        input.setFont(font);
        prompt.setFont(font.deriveFont(java.awt.Font.BOLD));
    }

    /** @return the font the console output is using. */
    public java.awt.Font consoleFont() {
        return text.getFont();
    }

    /** Moves focus to the console input line. */
    public void focusInput() {
        input.requestFocusInWindow();
    }

    /**
     * Turns the rule between runs on or off.
     *
     * @param runSeparator {@code false} in terminal mode, where output flows on
     */
    public void setRunSeparator(boolean runSeparator) {
        this.runSeparator = runSeparator;
    }

    /** @return how many commands the history holds, exposed for tests. */
    public int historySize() {
        return history.size();
    }

    /** @return the console input field, exposed for tests. */
    public JTextField inputField() {
        return input;
    }

    /**
     * Recolours the console, so {@code color} typed at the prompt has a visible
     * effect even though the interpreter cannot change a captured console.
     *
     * @param background the new background
     * @param foreground the new default text colour
     */
    public void setPalette(Color background, Color foreground) {
        SwingUtilities.invokeLater(() -> {
            text.setBackground(background);
            text.setForeground(foreground);
            text.setCaretColor(foreground);
            scroll.getViewport().setBackground(background);
            input.setBackground(background);
            input.setForeground(foreground);
            input.setCaretColor(foreground);
            prompt.setForeground(foreground);
            row.setBackground(background);
            this.currentForeground = foreground;
            repaint();
        });
    }

    /**
     * @param error whether the text is an error
     * @return the colour normal output should be drawn in, which follows a
     *         {@code color} command so the whole console changes and not just
     *         the prompt
     */
    public Color outputColor(boolean error) {
        return error ? Theme.CONSOLE_STDERR
                : currentForeground == null ? Theme.CONSOLE_FOREGROUND : currentForeground;
    }

    /** @return the colour an echoed command is drawn in. */
    public Color commandColor() {
        return Theme.CONSOLE_COMMAND;
    }

    /** @return the colour console output is currently drawn in. */
    public Color paletteForeground() {
        return text.getForeground();
    }

    /** @return the colour the console is currently drawn on. */
    public Color paletteBackground() {
        return text.getBackground();
    }

    /**
     * Draws a dim rule so each run's command and output stand apart from the
     * previous block.
     */
    public void startBlock() {
        if (!runSeparator) {
            // Terminal mode runs on continuously; the interpreter's own prompt
            // is the only thing separating one command from the next.
            return;
        }
        String existing = getText();
        if (existing.isBlank()) {
            return;
        }
        append("\u2500".repeat(72) + System.lineSeparator(), Theme.tint(Theme.CONSOLE_BACKGROUND, 40));
    }

    /** Appends text in the default output colour. Safe to call off the EDT. */
    public void append(String value, Color color) {
        if (value == null || value.isEmpty()) {
            return;
        }
        synchronized (queue) {
            queue.add(new Segment(value, color == null ? Theme.CONSOLE_FOREGROUND : color));
        }
        if (!flushTimer.isRunning()) {
            SwingUtilities.invokeLater(flushTimer::restart);
        }
    }

    /** Appends plain output. */
    public void appendOutput(String value) {
        append(value, Theme.CONSOLE_FOREGROUND);
    }

    /** Appends a line captured from the interpreter's error stream. */
    public void appendError(String value) {
        append(value, Theme.CONSOLE_STDERR);
    }

    /** Appends the echo of a command that is about to run. */
    public void appendCommand(String value) {
        append(value, Theme.CONSOLE_COMMAND);
    }

    /** Appends a hint or notice produced by the application itself. */
    public void appendInfo(String value) {
        append(value, Theme.CONSOLE_INFO);
    }

    /** Appends a system level message such as a session start or exit. */
    public void appendSystem(String value) {
        append(value, Theme.CONSOLE_SYSTEM);
    }

    /** Appends a line and a trailing newline. */
    public void appendLine(String value, Color color) {
        append(value + System.lineSeparator(), color);
    }

    private void flush() {
        List<Segment> batch;
        synchronized (queue) {
            if (queue.isEmpty()) {
                return;
            }
            batch = new ArrayList<>(queue);
            queue.clear();
        }
        StyledDocument doc = text.getStyledDocument();
        boolean atBottom = isScrolledToBottom();
        for (Segment segment : batch) {
            SimpleAttributeSet attrs = new SimpleAttributeSet();
            StyleConstants.setForeground(attrs, segment.color());
            try {
                doc.insertString(doc.getLength(), segment.text(), attrs);
            } catch (BadLocationException ignored) {
                // Inserting at the end of the document cannot be out of range.
            }
        }
        trim(doc);
        if (atBottom) {
            text.setCaretPosition(doc.getLength());
        }
    }

    /** Drops the oldest output once the buffer exceeds {@link #MAX_CHARS}. */
    private void trim(StyledDocument doc) {
        int length = doc.getLength();
        if (length <= MAX_CHARS) {
            return;
        }
        int excess = length - MAX_CHARS;
        try {
            doc.remove(0, excess);
        } catch (BadLocationException ignored) {
            // A concurrent edit shrank the document; nothing to trim.
        }
    }

    private boolean isScrolledToBottom() {
        return verticalBar.getValue() + verticalBar.getVisibleAmount() >= verticalBar.getMaximum() - 8;
    }

    /** Removes all output. */
    public void clear() {
        synchronized (queue) {
            queue.clear();
        }
        SwingUtilities.invokeLater(() -> {
            try {
                text.getStyledDocument().remove(0, text.getStyledDocument().getLength());
            } catch (BadLocationException ignored) {
                // Empty document.
            }
        });
    }

    /** @return the console contents as plain text, mainly for tests. */
    public String getText() {
        try {
            return text.getDocument().getText(0, text.getDocument().getLength());
        } catch (BadLocationException e) {
            return "";
        }
    }

    /** @return the component that owns focus for scrolling and selection. */
    public JTextPane textPane() {
        return text;
    }
}
