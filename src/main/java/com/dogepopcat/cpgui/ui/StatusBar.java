package com.dogepopcat.cpgui.ui;

import com.dogepopcat.cpgui.shell.ShellKind;

import javax.swing.BorderFactory;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JSeparator;
import javax.swing.SwingConstants;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.io.File;

/**
 * Bottom status bar showing the current message, workspace, shell, elevation
 * state and caret position.
 */
public class StatusBar extends JPanel {

    private static final long serialVersionUID = 1L;

    private final JLabel message = new JLabel("Ready");
    private final JLabel workspace = new JLabel();
    private final JLabel shell = new JLabel();
    private final JLabel elevation = new JLabel();
    private final JLabel caret = new JLabel("Ln 1, Col 1");

    public StatusBar() {
        super(new BorderLayout());
        setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createMatteBorder(1, 0, 0, 0, Theme.tint(Theme.GUTTER_BACKGROUND, 40)),
                BorderFactory.createEmptyBorder(3, 8, 3, 8)));

        JPanel left = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 0));
        left.setOpaque(false);
        left.add(message);
        add(left, BorderLayout.WEST);

        JPanel right = new JPanel(new FlowLayout(FlowLayout.RIGHT, 6, 0));
        right.setOpaque(false);
        right.add(workspace);
        right.add(separator());
        right.add(shell);
        right.add(separator());
        right.add(elevation);
        right.add(separator());
        right.add(caret);
        add(right, BorderLayout.EAST);
    }

    private JSeparator separator() {
        JSeparator sep = new JSeparator(SwingConstants.VERTICAL);
        sep.setPreferredSize(new Dimension(1, 14));
        return sep;
    }

    /** Sets the transient message on the left hand side. */
    public void setMessage(String text) {
        message.setText(text == null || text.isEmpty() ? "Ready" : text);
    }

    /** Shows the directory new shells start in. */
    public void setWorkspace(File dir) {
        workspace.setText("\u25b8 " + (dir == null ? "(none)" : dir.getAbsolutePath()));
        workspace.setToolTipText(dir == null ? null : "Working directory: " + dir.getAbsolutePath());
    }

    /** Shows which interpreter the active tab targets. */
    public void setShell(ShellKind shellKind) {
        shell.setText(String.valueOf(shellKind));
        shell.setToolTipText("Active interpreter: " + shellKind.description());
    }

    /** Shows whether the process is elevated. */
    public void setElevated(boolean elevated) {
        elevation.setText(elevated ? "Administrator" : "Standard user");
        elevation.setForeground(elevated ? Theme.ERROR : null);
        elevation.setToolTipText(elevated
                ? "Running with administrator privileges"
                : "Running as a standard user");
    }

    /** Shows the caret's line and column, one based. */
    public void setCaret(int line, int column) {
        caret.setText("Ln " + (line + 1) + ", Col " + (column + 1));
    }

    /** Sets the colour of the message label, for warnings and errors. */
    public void setMessageColor(Color color) {
        message.setForeground(color);
    }
}
