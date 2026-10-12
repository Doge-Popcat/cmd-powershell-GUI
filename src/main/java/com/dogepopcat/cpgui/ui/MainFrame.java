package com.dogepopcat.cpgui.ui;

import com.dogepopcat.cpgui.AppInfo;

import javax.swing.BorderFactory;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JPanel;
import java.awt.BorderLayout;
import java.awt.Dimension;

/**
 * The application main window.
 *
 * <p>At this stage it only establishes the shell of the application: a centred,
 * resizable, native window with a status bar. Toolbar, editor tabs and the
 * execution console are added by later commits.</p>
 */
public class MainFrame extends JFrame {

    private static final long serialVersionUID = 1L;

    private final JLabel statusLabel = new JLabel("Ready");

    public MainFrame() {
        super(AppInfo.title());
        setDefaultCloseOperation(EXIT_ON_CLOSE);
        setMinimumSize(new Dimension(720, 480));
        setPreferredSize(new Dimension(1100, 720));
        buildUi();
        pack();
        setLocationRelativeTo(null);
        setIconImages(java.util.List.of(
                Icons.appIcon(16), Icons.appIcon(32), Icons.appIcon(64), Icons.appIcon(256)));
    }

    private void buildUi() {
        setLayout(new BorderLayout());
        add(createPlaceholder(), BorderLayout.CENTER);
        add(createStatusBar(), BorderLayout.SOUTH);
    }

    private JPanel createPlaceholder() {
        JPanel panel = new JPanel(new BorderLayout());
        panel.setBorder(BorderFactory.createEmptyBorder(16, 16, 16, 16));
        JLabel hint = new JLabel("Workspace initialising \u2026");
        hint.setHorizontalAlignment(JLabel.CENTER);
        panel.add(hint, BorderLayout.CENTER);
        return panel;
    }

    private JPanel createStatusBar() {
        JPanel bar = new JPanel(new BorderLayout());
        bar.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createMatteBorder(1, 0, 0, 0, bar.getBackground().darker()),
                BorderFactory.createEmptyBorder(3, 8, 3, 8)));
        bar.add(statusLabel, BorderLayout.WEST);
        return bar;
    }

    /** @return the mutable status text shown at the bottom of the window. */
    protected JLabel statusLabel() {
        return statusLabel;
    }
}
