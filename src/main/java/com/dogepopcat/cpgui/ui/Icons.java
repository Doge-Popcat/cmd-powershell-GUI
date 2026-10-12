package com.dogepopcat.cpgui.ui;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;

/**
 * Programmatically drawn window and toolbar icons.
 *
 * <p>Keeping icons in code avoids shipping binary assets and lets the artwork
 * follow the selected theme. Every method returns a fresh image so callers may
 * cache or mutate the result freely.</p>
 */
public final class Icons {

    private static final Color TERMINAL_BG = new Color(0x1E, 0x22, 0x2B);
    private static final Color PROMPT = new Color(0x4E, 0xC9, 0xB0);
    private static final Color CARET = new Color(0xE6, 0xE6, 0xE6);

    private Icons() {
    }

    /**
     * Draws the application icon: a dark terminal window with a green prompt
     * chevron and a caret.
     *
     * @param size edge length of the square icon in pixels
     */
    public static BufferedImage appIcon(int size) {
        BufferedImage image = new BufferedImage(size, size, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = prepare(image);
        int pad = Math.max(1, size / 16);
        g.setColor(TERMINAL_BG);
        g.fillRoundRect(pad, pad, size - 2 * pad, size - 2 * pad, size / 5, size / 5);

        g.setColor(PROMPT);
        g.setStroke(new BasicStroke(Math.max(1.5f, size / 9f),
                BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        int x0 = (int) (size * 0.28);
        int y0 = (int) (size * 0.36);
        int x1 = (int) (size * 0.46);
        int y1 = (int) (size * 0.52);
        int x2 = (int) (size * 0.28);
        int y2 = (int) (size * 0.68);
        g.drawPolyline(new int[]{x0, x1, x2}, new int[]{y0, y1, y2}, 3);

        g.setColor(CARET);
        int caretW = Math.max(2, size / 8);
        g.fillRect((int) (size * 0.56), (int) (size * 0.62), caretW, Math.max(2, size / 9));
        g.dispose();
        return image;
    }

    /**
     * Renders an icon of the requested edge length using the supplied painter.
     *
     * @param size   edge length in pixels
     * @param onIcon a scalable icon rendered at {@code size * 0.62}
     */
    public static BufferedImage appIcon(int size, boolean onIcon) {
        BufferedImage image = appIcon(size);
        if (!onIcon) {
            return image;
        }
        Graphics2D g = prepare(image);
        g.setColor(new Color(0x3D, 0xD5, 0x6A));
        int d = Math.max(3, size / 3);
        g.fillOval(size - d - 2, 2, d, d);
        g.dispose();
        return image;
    }

    private static Graphics2D prepare(BufferedImage image) {
        Graphics2D g = image.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL, RenderingHints.VALUE_STROKE_PURE);
        return g;
    }
}
