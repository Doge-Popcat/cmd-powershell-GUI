package com.dogepopcat.cpgui.ui;

import javax.swing.text.BadLocationException;
import javax.swing.text.JTextComponent;
import javax.swing.text.LayeredHighlighter;
import javax.swing.text.Position;
import javax.swing.text.View;
import java.awt.Color;
import java.awt.Graphics;
import java.awt.Rectangle;
import java.awt.Shape;

/**
 * Draws the wavy underline used to mark a diagnostic range.
 *
 * <p>Implementing {@link LayeredHighlighter.LayerPainter} means the underline is
 * produced per text view, so it follows the exact glyphs of the range instead of
 * boxing the whole highlight.</p>
 */
public final class SquigglePainter extends LayeredHighlighter.LayerPainter {

    private static final int WAVE_PERIOD = 4;
    private static final int WAVE_HEIGHT = 2;

    private final java.util.function.Supplier<Color> colour;

    public SquigglePainter(Color colour) {
        this(() -> colour);
    }

    /**
     * @param colour asked for the colour every time the underline is drawn, so a
     *               diagnostic already on screen follows a palette change instead
     *               of keeping the colour it was created with
     */
    public SquigglePainter(java.util.function.Supplier<Color> colour) {
        this.colour = colour;
    }

    @Override
    public void paint(Graphics g, int p0, int p1, Shape bounds, JTextComponent component) {
        // Layered highlighting calls paintLayer instead; this is only a fallback.
        Rectangle area = bounds.getBounds();
        drawWave(g, area.x, area.y + area.height - 1, area.width);
    }

    @Override
    @SuppressWarnings("deprecation")
    public Shape paintLayer(Graphics g, int p0, int p1, Shape viewBounds,
                            JTextComponent component, View view) {
        Rectangle area;
        try {
            // The 2D variant of this call has no multi-position overload, so the
            // long standing modelToView form is the right one here.
            Shape shape = view.modelToView(p0, Position.Bias.Forward,
                    p1, Position.Bias.Backward, viewBounds);
            area = shape instanceof Rectangle rectangle ? rectangle : shape.getBounds();
        } catch (BadLocationException | IllegalArgumentException e) {
            return null;
        }
        if (area.width <= 0) {
            return area;
        }
        drawWave(g, area.x, area.y + area.height - 1, area.width);
        return area;
    }

    private void drawWave(Graphics g, int x, int baseline, int width) {
        Color previous = g.getColor();
        g.setColor(colour.get());
        for (int i = 0; i < width; i++) {
            int y = baseline - ((i / (WAVE_PERIOD / 2)) % 2 == 0 ? 0 : WAVE_HEIGHT);
            g.drawLine(x + i, y, x + i, y);
        }
        g.setColor(previous);
    }

    /** @return the colour this painter draws with. */
    public Color colour() {
        return colour.get();
    }
}
