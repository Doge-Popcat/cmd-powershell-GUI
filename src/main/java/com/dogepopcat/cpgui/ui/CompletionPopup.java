package com.dogepopcat.cpgui.ui;

import com.dogepopcat.cpgui.completion.CompletionItem;

import javax.swing.BorderFactory;
import javax.swing.DefaultListCellRenderer;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JScrollPane;
import javax.swing.JWindow;
import javax.swing.ListSelectionModel;
import javax.swing.ScrollPaneConstants;
import javax.swing.SwingUtilities;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.FontMetrics;
import java.awt.GraphicsEnvironment;
import java.awt.Point;
import java.awt.Rectangle;
import java.awt.geom.Rectangle2D;
import java.util.List;

/**
 * The IntelliSense popup: a small, non-focusable window listing suggestions.
 *
 * <p>A {@link JWindow} is used rather than a popup menu because
 * {@code setFocusableWindowState(false)} guarantees the caret keeps blinking in
 * the editor while the list is shown, which is what makes typing and accepting a
 * suggestion feel seamless.</p>
 */
public final class CompletionPopup {

    private static final int MAX_VISIBLE_ROWS = 12;
    private static final int ROW_HEIGHT = 20;
    private static final int MIN_WIDTH = 360;
    private static final int MAX_WIDTH = 720;
    private static final int VERTICAL_PADDING = 4;

    private final JWindow window = new JWindow();
    private final JList<CompletionItem> list = new JList<>();

    public CompletionPopup() {
        list.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        list.setFixedCellHeight(ROW_HEIGHT);
        list.setBackground(Theme.EDITOR_BACKGROUND);
        list.setForeground(Theme.EDITOR_FOREGROUND);
        list.setFont(Theme.mono(12));
        list.setCellRenderer(new Renderer());
        list.setFocusable(false);

        JScrollPane scroll = new JScrollPane(list);
        scroll.setBorder(BorderFactory.createEmptyBorder());
        scroll.setHorizontalScrollBarPolicy(ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);
        scroll.getViewport().setBackground(Theme.EDITOR_BACKGROUND);
        scroll.getVerticalScrollBar().setUnitIncrement(ROW_HEIGHT);

        window.setFocusableWindowState(false);
        window.setAlwaysOnTop(true);
        window.getContentPane().setBackground(Theme.EDITOR_BACKGROUND);
        window.setLayout(new BorderLayout());
        window.add(scroll, BorderLayout.CENTER);

        javax.swing.border.Border outer = BorderFactory.createLineBorder(
                Theme.tint(Theme.GUTTER_BACKGROUND, 60));
        javax.swing.border.Border inner = BorderFactory.createEmptyBorder(
                VERTICAL_PADDING, 2, VERTICAL_PADDING, 2);
        window.getRootPane().setBorder(BorderFactory.createCompoundBorder(outer, inner));
    }

    /**
     * Shows the suggestions next to the caret.
     *
     * @param owner  the component the popup belongs to
     * @param caret  caret offset used to place the popup
     * @param items  suggestions to show, must not be empty
     */
    public void show(javax.swing.text.JTextComponent owner, int caret,
                     List<CompletionItem> items) {
        if (items.isEmpty()) {
            hide();
            return;
        }
        list.setListData(items.toArray(new CompletionItem[0]));
        list.setSelectedIndex(0);

        int rows = Math.min(items.size(), MAX_VISIBLE_ROWS);
        int width = Math.min(MAX_WIDTH, Math.max(MIN_WIDTH, measureWidth(items, owner)));
        window.setSize(new Dimension(width, rows * ROW_HEIGHT + 2 * VERTICAL_PADDING + 2));
        position(owner, caret, rows * ROW_HEIGHT);

        if (!window.isVisible()) {
            window.setVisible(true);
        }
        window.toFront();
    }

    /** Places the popup just under the caret, flipping above if there is no room. */
    private void position(javax.swing.text.JTextComponent owner, int caret, int contentHeight) {
        Point location;
        try {
            Rectangle2D rect = owner.modelToView2D(caret);
            if (rect == null) {
                return;
            }
            location = new Point((int) rect.getX(), (int) (rect.getY() + rect.getHeight()));
        } catch (Exception e) {
            location = new Point(0, owner.getHeight());
        }
        SwingUtilities.convertPointToScreen(location, owner);

        Rectangle screen = GraphicsEnvironment.getLocalGraphicsEnvironment()
                .getMaximumWindowBounds();
        int height = contentHeight + 2 * VERTICAL_PADDING + 2;
        if (location.y + height > screen.y + screen.height) {
            location.y = Math.max(screen.y, location.y - height - ROW_HEIGHT);
        }
        if (location.x + window.getWidth() > screen.x + screen.width) {
            location.x = Math.max(screen.x, screen.x + screen.width - window.getWidth());
        }
        window.setLocation(location);
    }

    private int measureWidth(List<CompletionItem> items, Component owner) {
        FontMetrics metrics = owner.getFontMetrics(Theme.mono(12));
        int widest = 0;
        int examined = Math.min(items.size(), 60);
        for (int i = 0; i < examined; i++) {
            CompletionItem item = items.get(i);
            int text = metrics.stringWidth(item.label()) + 24
                    + metrics.stringWidth(shorten(item.detail(), 70));
            widest = Math.max(widest, text);
        }
        return widest + 48;
    }

    private static String shorten(String value, int max) {
        if (value == null) {
            return "";
        }
        return value.length() <= max ? value : value.substring(0, max - 1) + "\u2026";
    }

    /** Hides the popup if it is showing. */
    public void hide() {
        if (window.isVisible()) {
            window.setVisible(false);
        }
        list.setListData(new CompletionItem[0]);
    }

    /** @return {@code true} while the suggestion list is on screen. */
    public boolean isVisible() {
        return window.isVisible();
    }

    /**
     * Moves the selection by {@code delta}, wrapping around the list.
     *
     * @param delta number of rows to move, negative to move up
     */
    public void moveSelection(int delta) {
        int size = list.getModel().getSize();
        if (size == 0) {
            return;
        }
        int index = list.getSelectedIndex();
        if (index < 0) {
            index = 0;
        } else {
            index = Math.floorMod(index + delta, size);
        }
        list.setSelectedIndex(index);
        list.ensureIndexIsVisible(index);
    }

    /** @return the highlighted suggestion, or {@code null} when none is. */
    public CompletionItem selected() {
        return list.getSelectedValue();
    }

    /** @return how many suggestions are currently listed. */
    public int itemCount() {
        return list.getModel().getSize();
    }

    /** Renders one suggestion: coloured label followed by a grey description. */
    private static final class Renderer extends DefaultListCellRenderer {

        private static final long serialVersionUID = 1L;

        @Override
        public Component getListCellRendererComponent(JList<?> list, Object value, int index,
                                                      boolean selected, boolean focused) {
            JLabel label = (JLabel) super.getListCellRendererComponent(
                    list, value, index, selected, focused);
            label.setBorder(BorderFactory.createEmptyBorder(1, 8, 1, 8));
            label.setFont(Theme.mono(12));
            if (value instanceof CompletionItem item) {
                label.setText("<html><font color='" + hex(colourFor(item))
                        + "'>" + escape(item.label()) + "</font>"
                        + "&nbsp;&nbsp;<font color='" + hex(Theme.CONSOLE_INFO) + "'>"
                        + escape(shorten(item.detail(), 78)) + "</font></html>");
            }
            if (selected) {
                label.setBackground(Theme.EDITOR_SELECTION);
                label.setOpaque(true);
            }
            return label;
        }

        private static Color colourFor(CompletionItem item) {
            return switch (item.kind()) {
                case COMMAND -> Theme.SYNTAX_COMMAND;
                case KEYWORD -> Theme.SYNTAX_KEYWORD;
                case PARAMETER -> Theme.SYNTAX_PARAMETER;
                case VARIABLE -> Theme.SYNTAX_VARIABLE;
                case PATH -> Theme.SYNTAX_STRING;
            };
        }

        private static String hex(Color colour) {
            return String.format("#%02x%02x%02x",
                    colour.getRed(), colour.getGreen(), colour.getBlue());
        }

        private static String escape(String value) {
            return value.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
        }
    }

    /** Padding used when measuring the popup height. */
    static int verticalPadding() {
        return VERTICAL_PADDING;
    }
}
