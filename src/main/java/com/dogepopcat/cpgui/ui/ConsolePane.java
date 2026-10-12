package com.dogepopcat.cpgui.ui;

import javax.swing.BorderFactory;
import javax.swing.JPanel;
import javax.swing.JScrollBar;
import javax.swing.JScrollPane;
import javax.swing.JTextPane;
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

        flushTimer = new Timer(FLUSH_DELAY_MS, e -> flush());
        flushTimer.setRepeats(false);
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
