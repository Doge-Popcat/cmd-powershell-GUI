package com.dogepopcat.cpgui.ui;

import javax.swing.JTextPane;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.geom.Rectangle2D;

/**
 * A text pane that can draw a greyed inline suggestion after the caret.
 *
 * <p>This is the "ghost text" style of hint: the remainder of the suggested
 * word is painted directly in the editor rather than in a floating window, so it
 * never covers the input method candidate list and never floats over other
 * applications. Accepting it is a matter of pressing Tab, exactly as in the
 * JetBrains IDEs.</p>
 */
public final class GhostTextPane extends JTextPane {

    private static final long serialVersionUID = 1L;

    private String ghostText = "";

    /**
     * Sets the greyed text drawn at the caret.
     *
     * @param ghostText the suggestion remainder, or {@code null} to clear it
     */
    public void setGhostText(String ghostText) {
        String next = ghostText == null ? "" : ghostText;
        if (!next.equals(this.ghostText)) {
            this.ghostText = next;
            repaint();
        }
    }

    /** @return the greyed text currently drawn at the caret. */
    public String getGhostText() {
        return ghostText;
    }

    @Override
    public void paintComponent(Graphics g) {
        super.paintComponent(g);
        if (ghostText.isEmpty() || !isEditable() || !isShowing()) {
            return;
        }
        try {
            Rectangle2D caret = modelToView2D(getCaretPosition());
            if (caret == null) {
                return;
            }
            Graphics2D g2 = (Graphics2D) g.create();
            try {
                g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING,
                        RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
                g2.setFont(getFont());
                g2.setColor(Theme.GHOST_TEXT);
                int baseline = (int) (caret.getY() + getFontMetrics(getFont()).getAscent());
                g2.drawString(ghostText, (float) caret.getX(), baseline);
            } finally {
                g2.dispose();
            }
        } catch (Exception ignored) {
            // The pane was mid-relayout; the next paint will succeed.
        }
    }
}
