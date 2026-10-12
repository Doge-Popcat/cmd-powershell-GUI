package com.dogepopcat.cpgui.ui;

import com.dogepopcat.cpgui.shell.ShellKind;

import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JTabbedPane;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.Insets;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.ArrayList;
import java.util.List;
import java.util.function.IntConsumer;

/**
 * Tab strip holding one {@link EditorTab} per open script.
 *
 * <p>Each tab carries a custom header with the file name and a close button so
 * the interaction matches what users expect from an IDE, rather than the plain
 * Swing default header.</p>
 */
public class EditorTabs extends JTabbedPane {

    private static final long serialVersionUID = 1L;

    private final List<EditorTab> tabs = new ArrayList<>();
    private IntConsumer selectionListener = index -> { };
    private int untitledCounter;

    public EditorTabs() {
        setTabLayoutPolicy(SCROLL_TAB_LAYOUT);
        setBorder(BorderFactory.createEmptyBorder());
        addChangeListener(e -> selectionListener.accept(getSelectedIndex()));
    }

    /**
     * Creates a new empty tab.
     *
     * @param shellKind interpreter the script targets
     * @return the freshly created tab
     */
    public EditorTab newTab(ShellKind shellKind) {
        untitledCounter++;
        EditorTab tab = new EditorTab(shellKind);
        tab.setDisplayName("Untitled " + untitledCounter);
        tab.setDirtyListener(this::refreshHeaders);
        tabs.add(tab);
        addTab("Untitled " + untitledCounter, tab);
        int index = tabs.size() - 1;
        setTabComponentAt(index, new TabHeader(tab));
        setSelectedIndex(index);
        return tab;
    }

    /** @return the tab that is currently selected, or {@code null} when none is open. */
    public EditorTab current() {
        int index = getSelectedIndex();
        return index >= 0 && index < tabs.size() ? tabs.get(index) : null;
    }

    /** @return an immutable snapshot of the open tabs, in display order. */
    public List<EditorTab> tabs() {
        return List.copyOf(tabs);
    }

    /** @return how many tabs are open. */
    public int tabCount() {
        return tabs.size();
    }

    /**
     * Closes the tab at {@code index}, selecting a neighbour afterwards.
     *
     * @return the tab that was removed, or {@code null} if the index was invalid
     */
    public EditorTab closeTab(int index) {
        if (index < 0 || index >= tabs.size()) {
            return null;
        }
        EditorTab removed = tabs.remove(index);
        removeTabAt(index);
        if (!tabs.isEmpty()) {
            setSelectedIndex(Math.min(index, tabs.size() - 1));
        }
        refreshHeaders();
        return removed;
    }

    /** Registers a callback invoked whenever the selected tab changes. */
    public void setSelectionListener(IntConsumer listener) {
        this.selectionListener = listener == null ? index -> { } : listener;
    }

    /** Recomputes every tab title, which is how the dirty marker is refreshed. */
    private void refreshHeaders() {
        for (int i = 0; i < tabs.size(); i++) {
            if (getTabComponentAt(i) instanceof TabHeader header) {
                header.refresh();
            }
        }
    }

    /** Header widget: title label plus a close button. */
    private final class TabHeader extends JPanel {

        private static final long serialVersionUID = 1L;
        /** Horizontal gap between the title and the close button, in pixels. */
        private static final int GAP = 6;

        private final EditorTab tab;
        private final JLabel label = new JLabel();
        private final JButton close = new JButton("\u00d7");
        private final Color defaultForeground;

        TabHeader(EditorTab tab) {
            super(new BorderLayout(GAP, 0));
            this.tab = tab;
            setOpaque(false);
            setBorder(BorderFactory.createEmptyBorder(3, 6, 3, 2));

            label.setBorder(BorderFactory.createEmptyBorder());
            defaultForeground = label.getForeground();
            add(label, BorderLayout.CENTER);

            close.setFont(close.getFont().deriveFont(13f));
            close.setMargin(new Insets(0, 0, 0, 0));
            close.setPreferredSize(new Dimension(18, 18));
            close.setBorderPainted(false);
            close.setContentAreaFilled(false);
            close.setFocusable(false);
            close.setToolTipText("Close tab");
            close.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
            close.addActionListener(e -> closeRequested());
            close.addMouseListener(new MouseAdapter() {
                @Override
                public void mouseEntered(MouseEvent e) {
                    close.setForeground(Theme.ERROR);
                }

                @Override
                public void mouseExited(MouseEvent e) {
                    close.setForeground(null);
                }
            });
            add(close, BorderLayout.EAST);

            // Middle click closes the tab, like browsers and most IDEs.
            addMouseListener(new MouseAdapter() {
                @Override
                public void mouseClicked(MouseEvent e) {
                    if (e.getButton() == MouseEvent.BUTTON2) {
                        closeRequested();
                    }
                }
            });
            refresh();
        }

        /**
         * The tab strip sizes each tab from this component, so the width has to
         * account for the label and the close button explicitly. Relying on the
         * layout manager alone clips longer file names.
         */
        @Override
        public Dimension getPreferredSize() {
            Dimension text = label.getPreferredSize();
            Insets insets = getInsets();
            int width = insets.left + text.width + GAP + close.getPreferredSize().width
                    + insets.right;
            int height = Math.max(22, insets.top + text.height + insets.bottom);
            return new Dimension(width, height);
        }

        @Override
        public Dimension getMinimumSize() {
            return getPreferredSize();
        }

        private void closeRequested() {
            int index = indexOfComponent(tab);
            if (index >= 0) {
                tabCloseRequestHandler.accept(index);
            }
        }

        void refresh() {
            label.setText(tab.title());
            label.setToolTipText(tab.file() == null
                    ? "Unsaved script"
                    : tab.file().getAbsolutePath());
            label.setForeground(tab.isDirty() ? Theme.WARNING : defaultForeground);
            setToolTipText(tab.shellKind().description());
            revalidate();
        }
    }

    private IntConsumer tabCloseRequestHandler = index -> closeTab(index);

    /**
     * Overrides what happens when a tab's close button is used, so the frame can
     * first ask about unsaved changes.
     */
    public void setTabCloseRequestHandler(IntConsumer handler) {
        this.tabCloseRequestHandler = handler == null ? index -> closeTab(index) : handler;
    }
}
