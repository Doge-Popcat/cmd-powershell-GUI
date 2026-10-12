package com.dogepopcat.cpgui.ui;

import com.dogepopcat.cpgui.shell.ShellKind;

import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JToolBar;
import java.awt.Component;
import java.awt.Dimension;

/**
 * The main tool bar.
 *
 * <p>Every button delegates to an {@link javax.swing.Action} owned by
 * {@link MainFrame}, so the same command is reachable from the menu, the tool
 * bar and its keyboard shortcut, and the enabled state stays in sync
 * everywhere.</p>
 */
public class AppToolBar extends JToolBar {

    private static final long serialVersionUID = 1L;

    private final JComboBox<ShellKind> interpreter = new JComboBox<>(ShellKind.values());
    private boolean syncing;

    public AppToolBar(MainFrame frame) {
        super("Main");
        setFloatable(false);
        setRollover(true);
        setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createMatteBorder(0, 0, 1, 0, Theme.tint(Theme.GUTTER_BACKGROUND, 40)),
                BorderFactory.createEmptyBorder(3, 6, 3, 6)));

        add(button(frame.newTabAction()));
        add(button(frame.openAction()));
        add(button(frame.saveAction()));
        addSeparator(new Dimension(10, 0));

        add(button(frame.runAction()));
        add(button(frame.stopAction()));
        addSeparator(new Dimension(10, 0));

        add(button(frame.workspaceAction()));
        addSeparator(new Dimension(10, 0));

        add(Box.createHorizontalGlue());
        add(new JLabel("Interpreter: "));
        interpreter.setMaximumSize(new Dimension(150, 26));
        interpreter.setPreferredSize(new Dimension(140, 26));
        interpreter.setToolTipText("Interpreter used by the active tab");
        interpreter.addActionListener(e -> {
            if (!syncing) {
                frame.setActiveShellKind((ShellKind) interpreter.getSelectedItem());
            }
        });
        add(interpreter);
    }

    private JButton button(javax.swing.Action action) {
        JButton button = new JButton(action);
        button.setFocusable(false);
        button.setHorizontalTextPosition(JButton.CENTER);
        button.setVerticalTextPosition(JButton.BOTTOM);
        return button;
    }

    /** Reflects the active tab's interpreter without firing a change event. */
    public void setInterpreter(ShellKind kind) {
        syncing = true;
        try {
            interpreter.setSelectedItem(kind);
        } finally {
            syncing = false;
        }
    }

    /** @return the combo box listing the available interpreters. */
    public JComboBox<ShellKind> interpreterBox() {
        return interpreter;
    }

    @Override
    public void setEnabled(boolean enabled) {
        super.setEnabled(enabled);
        for (Component component : getComponents()) {
            component.setEnabled(enabled);
        }
    }
}
