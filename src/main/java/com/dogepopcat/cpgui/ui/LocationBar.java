package com.dogepopcat.cpgui.ui;

import com.dogepopcat.cpgui.i18n.Messages;
import com.dogepopcat.cpgui.i18n.Msg;

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
    private final JLabel caption = new JLabel();
    private final JButton browse = new JButton();
    private final JButton up = new JButton("\u2191");

    private Consumer<File> directoryConsumer = directory -> { };
    private Runnable browseAction = () -> { };
    private File current;
    private boolean live;
    private boolean rejected;

    public LocationBar() {
        super(new BorderLayout(6, 0));
        setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createMatteBorder(0, 0, 1, 0, Theme.tint(Theme.GUTTER_BACKGROUND, 40)),
                BorderFactory.createEmptyBorder(3, 6, 3, 6)));

        caption.setFont(caption.getFont().deriveFont(java.awt.Font.BOLD));

        path.setFont(Theme.mono(12));
        path.addActionListener(e -> commitTypedPath());
        path.addFocusListener(new FocusAdapter() {
            @Override
            public void focusLost(FocusEvent e) {
                rejected = false;
                show(current);
            }
        });

        up.setFont(Theme.mono(12));
        up.setMargin(new java.awt.Insets(0, 6, 0, 6));
        up.setFocusable(false);
        up.addActionListener(e -> goUp());

        browse.setFocusable(false);
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
        Messages.addListener(language -> refreshText());
        Theme.addListener(this::applyTheme);
        // The palette must be applied now, not only on a later change: the look
        // and feel defaults are light whatever the chosen theme is.
        applyTheme();
        refreshText();
        show(null);
    }

    /** Re-applies the palette after a theme change. */
    void applyTheme() {
        setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createMatteBorder(0, 0, 1, 0, Theme.tint(Theme.GUTTER_BACKGROUND, 40)),
                BorderFactory.createEmptyBorder(3, 6, 3, 6)));
        setBackground(Theme.WINDOW_BACKGROUND);
        caption.setForeground(Theme.WINDOW_FOREGROUND);
        path.setBackground(Theme.isDark() ? Theme.EDITOR_BACKGROUND : java.awt.Color.WHITE);
        path.setForeground(Theme.EDITOR_FOREGROUND);
        path.setCaretColor(Theme.EDITOR_FOREGROUND);
        refreshText();
        repaint();
    }

    /** Re-applies every label after a language change. */
    private void refreshText() {
        caption.setText(Msg.LOCATION_CAPTION.text());
        browse.setText(Msg.LOCATION_CHANGE.text());
        browse.setToolTipText(Msg.LOCATION_CHANGE_TIP.text());
        up.setToolTipText(Msg.LOCATION_UP_TIP.text());
        path.setToolTipText(Msg.LOCATION_PATH_TIP.text());
        if (rejected) {
            state.setText(Msg.LOCATION_REFUSED.text());
            return;
        }
        show(current);
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
        this.rejected = false;
        show(directory);
    }

    private void show(File directory) {
        path.setText(directory == null ? "" : directory.getAbsolutePath());
        if (directory == null) {
            state.setText("");
            return;
        }
        state.setText(live ? Msg.LOCATION_LIVE.text() : Msg.LOCATION_STARTUP.text());
        state.setForeground(live ? Theme.OK : Theme.CONSOLE_INFO);
        state.setToolTipText(live
                ? Msg.LOCATION_LIVE_TIP.text()
                : Msg.LOCATION_STARTUP_TIP.text());
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
            state.setText(Msg.LOCATION_NOT_FOUND.text());
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
        rejected = true;
        state.setText(Msg.LOCATION_REFUSED.text());
        state.setForeground(Theme.ERROR);
    }
}
