package com.dogepopcat.cpgui.ui;

import javax.swing.JComponent;
import javax.swing.SwingUtilities;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import javax.swing.text.JTextComponent;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Insets;
import java.awt.Rectangle;

/**
 * Line number gutter for a text component.
 *
 * <p>Installed as the row header view of a {@link javax.swing.JScrollPane} so it
 * scrolls vertically in lock step with the editor. The component assumes the
 * editor does not wrap lines, which lets it compute the y offset of a line from
 * the font height alone.</p>
 */
final class LineNumberView extends JComponent implements DocumentListener {

    private static final long serialVersionUID = 1L;
    private static final int LEFT_PADDING = 8;
    private static final int RIGHT_PADDING = 8;

    private final JTextComponent text;
    private Font font;
    private int digits = 3;

    LineNumberView(JTextComponent text) {
        this.text = text;
        this.font = text.getFont();
        setFont(font);
        setBackground(Theme.GUTTER_BACKGROUND);
        setForeground(Theme.GUTTER_FOREGROUND);
        setOpaque(true);
        text.getDocument().addDocumentListener(this);
    }

    /** Changes the gutter font so it keeps matching the editor. */
    void setGutterFont(Font font) {
        this.font = font;
        setFont(font);
        revalidate();
        repaint();
    }

    private int lineHeight() {
        FontMetrics fm = getFontMetrics(font);
        return Math.max(1, fm.getHeight());
    }

    private int lineCount() {
        return Math.max(1, text.getDocument().getDefaultRootElement().getElementCount());
    }

    /** Index of the line holding the caret, zero based. */
    private int caretLine() {
        int caret = text.getCaretPosition();
        return text.getDocument().getDefaultRootElement().getElementIndex(caret);
    }

    @Override
    public Dimension getPreferredSize() {
        int count = lineCount();
        digits = Math.max(2, Integer.toString(count).length());
        FontMetrics fm = getFontMetrics(font);
        int width = LEFT_PADDING + digits * fm.charWidth('0') + RIGHT_PADDING;
        return new Dimension(width, Math.max(text.getHeight(), count * lineHeight()));
    }

    @Override
    protected void paintComponent(Graphics g) {
        Rectangle clip = g.getClipBounds();
        g.setColor(getBackground());
        g.fillRect(clip.x, clip.y, clip.width, clip.height);

        FontMetrics fm = getFontMetrics(font);
        int lh = fm.getHeight();
        Insets insets = text.getInsets();
        int top = insets.top;
        int ascent = fm.getAscent();

        int first = Math.max(0, (clip.y - top) / lh);
        int last = Math.min(lineCount() - 1, (clip.y + clip.height - top) / lh + 1);
        int active = caretLine();
        int x = getWidth() - RIGHT_PADDING - digits * fm.charWidth('0');

        for (int line = first; line <= last; line++) {
            g.setColor(line == active ? Theme.GUTTER_CURRENT : Theme.GUTTER_FOREGROUND);
            String label = Integer.toString(line + 1);
            int y = top + line * lh + ascent;
            g.drawString(label, x + (digits - label.length()) * fm.charWidth('0'), y);
        }

        g.setColor(Theme.tint(Theme.GUTTER_BACKGROUND, 16));
        g.drawLine(getWidth() - 1, clip.y, getWidth() - 1, clip.y + clip.height);
    }

    private void refresh() {
        SwingUtilities.invokeLater(() -> {
            revalidate();
            repaint();
        });
    }

    @Override
    public void insertUpdate(DocumentEvent e) {
        refresh();
    }

    @Override
    public void removeUpdate(DocumentEvent e) {
        refresh();
    }

    @Override
    public void changedUpdate(DocumentEvent e) {
        refresh();
    }

    /** Repaints the gutter so the active line highlight follows the caret. */
    void caretMoved() {
        repaint();
    }
}
