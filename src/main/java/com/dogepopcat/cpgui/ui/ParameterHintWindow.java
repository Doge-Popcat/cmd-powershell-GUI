package com.dogepopcat.cpgui.ui;

import com.dogepopcat.cpgui.completion.ParameterHint;

import javax.swing.BorderFactory;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JWindow;
import javax.swing.SwingUtilities;
import javax.swing.text.JTextComponent;
import java.awt.BorderLayout;
import java.awt.GraphicsEnvironment;
import java.awt.Point;
import java.awt.Rectangle;
import java.awt.geom.Rectangle2D;

/**
 * A small always-on-top window showing the signature of the command the caret is
 * currently inside.
 *
 * <p>Like the completion popup it never takes focus, so typing continues
 * uninterrupted.</p>
 */
public final class ParameterHintWindow {

    private static final int SCREEN_MARGIN = 8;

    private final JWindow window = new JWindow();
    private final JLabel title = new JLabel();
    private final JLabel detail = new JLabel();
    private final JLabel parameters = new JLabel();

    public ParameterHintWindow() {
        window.setFocusableWindowState(false);
        window.setAlwaysOnTop(true);

        title.setFont(Theme.mono(12, java.awt.Font.BOLD));
        title.setForeground(Theme.SYNTAX_COMMAND);
        detail.setFont(Theme.mono(11));
        detail.setForeground(Theme.CONSOLE_INFO);
        parameters.setFont(Theme.mono(11));
        parameters.setForeground(Theme.SYNTAX_PARAMETER);

        JPanel content = new JPanel(new BorderLayout(0, 2));
        content.setBackground(Theme.EDITOR_BACKGROUND);
        content.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(Theme.tint(Theme.GUTTER_BACKGROUND, 60)),
                BorderFactory.createEmptyBorder(5, 8, 5, 8)));
        content.add(title, BorderLayout.NORTH);
        content.add(detail, BorderLayout.CENTER);
        content.add(parameters, BorderLayout.SOUTH);

        window.getContentPane().setBackground(Theme.EDITOR_BACKGROUND);
        window.setLayout(new BorderLayout());
        window.add(content, BorderLayout.CENTER);
    }

    /**
     * Shows the hint under the caret.
     *
     * @param owner the component the hint is anchored to
     * @param caret caret offset used for positioning
     * @param hint  the hint to display
     */
    public void show(JTextComponent owner, int caret, ParameterHint hint) {
        title.setText(hint.command());
        detail.setText(hint.description() == null ? "" : hint.description());
        String line = hint.parameterLine();
        parameters.setText(line);
        parameters.setVisible(!line.isEmpty());

        window.pack();
        position(owner, caret);
        if (!window.isVisible()) {
            window.setVisible(true);
        }
        window.toFront();
    }

    private void position(JTextComponent owner, int caret) {
        Point location;
        try {
            Rectangle2D rect = owner.modelToView2D(caret);
            location = rect == null
                    ? new Point(owner.getWidth() / 2, 0)
                    : new Point((int) rect.getX(), (int) (rect.getY() + rect.getHeight() + 4));
        } catch (Exception e) {
            location = new Point(0, 0);
        }
        SwingUtilities.convertPointToScreen(location, owner);

        Rectangle screen = GraphicsEnvironment.getLocalGraphicsEnvironment()
                .getMaximumWindowBounds();
        int width = window.getWidth();
        int height = window.getHeight();
        if (location.x + width > screen.x + screen.width - SCREEN_MARGIN) {
            location.x = Math.max(screen.x + SCREEN_MARGIN,
                    screen.x + screen.width - width - SCREEN_MARGIN);
        }
        if (location.y + height > screen.y + screen.height - SCREEN_MARGIN) {
            location.y = Math.max(screen.y + SCREEN_MARGIN, location.y - height - 24);
        }
        window.setLocation(location);
    }

    /** Hides the hint. */
    public void hide() {
        if (window.isVisible()) {
            window.setVisible(false);
        }
    }

    /** @return {@code true} while the hint is on screen. */
    public boolean isVisible() {
        return window.isVisible();
    }

    /** @return the text currently shown in the title row, for tests. */
    public String shownCommand() {
        return title.getText();
    }
}
