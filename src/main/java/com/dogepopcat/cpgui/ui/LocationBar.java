package com.dogepopcat.cpgui.ui;

import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JTextField;
import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.event.FocusAdapter;
import java.awt.event.FocusEvent;
import java.io.File;
import java.util.function.Consumer;

/**
 * A prominent bar showing where commands will run.
 *
 * <p>The workspace used to be visible only as small text in the status bar,
 * which made it easy to lose track of the current directory. This bar states it
 * plainly, follows the live interpreter session, and allows editing the path in
 * place as well as browsing for it.</p>
 */
public class LocationBar extends JPanel {

    private static final long serialVersionUID = 1L;

    private final JTextField path = new JTextField();
    private final JLabel state = new JLabel();
    private final JButton browse = new JButton("Change\u2026");
    private final JButton up = new JButton("\u2191");

    private Consumer<File> directoryConsumer = directory -> { };
    private Runnable browseAction = () -> { };
    private File current;
    private boolean live;

    public LocationBar() {
        super(new BorderLayout(6, 0));
        setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createMatteBorder(0, 0, 1, 0, Theme.tint(Theme.GUTTER_BACKGROUND, 40)),
                BorderFactory.createEmptyBorder(3, 6, 3, 6)));

        JLabel caption = new JLabel("Working directory:");
        caption.setFont(caption.getFont().deriveFont(java.awt.Font.BOLD));

        path.setFont(Theme.mono(12));
        path.setToolTipText("Type a path and press Enter, or use Change\u2026 to browse");
        path.addActionListener(e -> commitTypedPath());
        path.addFocusListener(new FocusAdapter() {
            @Override
            public void focusLost(FocusEvent e) {
                show(current);
            }
        });

        up.setFont(Theme.mono(12));
        up.setMargin(new java.awt.Insets(0, 6, 0, 6));
        up.setFocusable(false);
        up.setToolTipText("Go to the parent folder");
        up.addActionListener(e -> goUp());

        browse.setFocusable(false);
        browse.setToolTipText("Choose the working directory");
        browse.addActionListener(e -> browseAction.run());

        JPanel left = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 0));
        left.setOpaque(false);
        left.add(caption);
        left.add(up);
        left.add(path);
        left.add(browse);
        left.add(state);

        add(left, BorderLayout.WEST);
        path.setPreferredSize(new Dimension(420, 24));
        show(null);
    }

    /**
     * Sets the directory to display.
     *
     * @param directory the directory, or {@code null} when unknown
     * @param live      {@code true} when the value came from the running
     *                  interpreter rather than from the configured workspace
     */
    public void setDirectory(File directory, boolean live) {
        this.current = directory;
        this.live = live;
        show(directory);
    }

    private void show(File directory) {
        path.setText(directory == null ? "" : directory.getAbsolutePath());
        if (directory == null) {
            state.setText("");
            return;
        }
        state.setText(live ? "(live)" : "(startup)");
        state.setForeground(live ? Theme.OK : Theme.CONSOLE_INFO);
        state.setToolTipText(live
                ? "Reported by the running interpreter"
                : "Directory new sessions will start in");
    }

    /** Registers the receiver for a directory the user typed or browsed to. */
    public void setDirectoryConsumer(Consumer<File> consumer) {
        this.directoryConsumer = consumer == null ? directory -> { } : consumer;
    }

    /** Registers the action behind the Change button. */
    public void setBrowseAction(Runnable action) {
        this.browseAction = action == null ? () -> { } : action;
    }

    /** Marks the displayed directory as coming from the workspace rather than a session. */
    public void clearLive() {
        live = false;
        show(current);
    }

    private void commitTypedPath() {
        String text = path.getText().strip();
        if (text.isEmpty()) {
            show(current);
            return;
        }
        File candidate = new File(text);
        if (!candidate.isDirectory()) {
            state.setText("(not found)");
            state.setForeground(Theme.ERROR);
            return;
        }
        directoryConsumer.accept(candidate);
    }

    private void goUp() {
        if (current == null) {
            return;
        }
        File parent = current.getParentFile();
        if (parent != null) {
            directoryConsumer.accept(parent);
        }
    }

    /** @return the directory currently displayed, or {@code null}. */
    public File directory() {
        return current;
    }

    /** @return the text field, exposed so tests can drive it. */
    public JTextField pathField() {
        return path;
    }

    /** Shows why a directory was refused, in place of the live/startup chip. */
    public void showRejected(String reason) {
        state.setText("(" + reason + ")");
        state.setForeground(Theme.ERROR);
    }
}
