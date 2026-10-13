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
 * Fills a range with a translucent wash, used to mark the lines that single line
 * mode has already run.
 *
 * <p>It is a layered painter so the wash follows each text view and therefore
 * covers exactly the line, including when the editor is scrolled.</p>
 */
public final class LineStatusPainter extends LayeredHighlighter.LayerPainter {

    private final Color colour;

    public LineStatusPainter(Color colour) {
        this.colour = colour;
    }

    @Override
    public void paint(Graphics g, int p0, int p1, Shape bounds, JTextComponent component) {
        // This is the fallback the highlighter uses when it cannot paint per
        // view. The bounds it passes cover the whole component, so the rectangle
        // is derived from the offsets instead; filling the bounds directly is
        // what marked every line in the document.
        Rectangle area = rectangleFor(p0, p1, component);
        fill(g, area == null ? bounds.getBounds() : area);
    }

    /** @return the rectangle covering an offset range, or {@code null}. */
    private static Rectangle rectangleFor(int p0, int p1, JTextComponent component) {
        try {
            Shape start = component.modelToView2D(p0);
            Shape end = component.modelToView2D(p1);
            if (start == null || end == null) {
                return null;
            }
            Rectangle a = start.getBounds();
            Rectangle b = end.getBounds();
            int top = Math.min(a.y, b.y);
            int height = Math.max(a.height, b.height);
            return new Rectangle(a.x, top, Math.max(1, b.x - a.x), height);
        } catch (BadLocationException e) {
            return null;
        }
    }

    @Override
    @SuppressWarnings("deprecation")
    public Shape paintLayer(Graphics g, int p0, int p1, Shape viewBounds,
                            JTextComponent component, View view) {
        Rectangle area;
        try {
            Shape shape = view.modelToView(p0, Position.Bias.Forward,
                    p1, Position.Bias.Backward, viewBounds);
            area = shape instanceof Rectangle rectangle ? rectangle : shape.getBounds();
        } catch (BadLocationException | IllegalArgumentException e) {
            return null;
        }
        if (area == null || area.height <= 0) {
            return null;
        }
        // Extend to the full width of the component, so the whole row is washed
        // rather than just the characters on it.
        int fullWidth = component.getWidth() - area.x;
        if (fullWidth > area.width) {
            area = new Rectangle(area.x, area.y, fullWidth, area.height);
        }
        fill(g, area);
        return area;
    }

    private void fill(Graphics g, Rectangle area) {
        if (area.width <= 0 || area.height <= 0) {
            return;
        }
        Color previous = g.getColor();
        g.setColor(colour);
        g.fillRect(area.x, area.y, area.width, area.height);
        g.setColor(previous);
    }

    /** @return the colour this painter washes with. */
    public Color colour() {
        return colour;
    }
}
