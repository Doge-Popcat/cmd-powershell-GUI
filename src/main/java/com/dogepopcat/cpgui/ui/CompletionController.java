package com.dogepopcat.cpgui.ui;

import com.dogepopcat.cpgui.completion.CompletionItem;
import com.dogepopcat.cpgui.completion.CompletionProvider;
import com.dogepopcat.cpgui.completion.ParameterHint;

import javax.swing.AbstractAction;
import javax.swing.Action;
import javax.swing.ActionMap;
import javax.swing.InputMap;
import javax.swing.JComponent;
import javax.swing.KeyStroke;
import javax.swing.Timer;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import javax.swing.text.BadLocationException;
import javax.swing.text.JTextComponent;
import javax.swing.text.StyledDocument;
import java.awt.event.ActionEvent;
import java.awt.event.InputEvent;
import java.awt.event.KeyEvent;
import java.io.File;
import java.util.List;
import java.util.function.Consumer;

/**
 * Drives the IntelliSense behaviour of one editor tab.
 *
 * <p>Suggestions are deliberately quiet. While the command name itself is being
 * typed, a single match is shown as greyed ghost text in the editor and several
 * matches open the suggestion list. Once a space ends the command name nothing
 * pops up on its own any more: switches, parameters and paths still appear,
 * because those are what legitimately follow a command, and Ctrl+Space can
 * always ask for more.</p>
 *
 * <p>Enter is never intercepted, so it always inserts a newline. Tab accepts the
 * highlighted suggestion, matching the JetBrains IDEs, and the arrow keys move
 * through the list.</p>
 */
public final class CompletionController implements DocumentListener {

    /** Delay before typing opens the suggestion list, in milliseconds. */
    private static final int AUTO_DELAY_MS = 120;
    /** Delay before the status bar signature is refreshed, in milliseconds. */
    private static final int HINT_DELAY_MS = 250;

    private final EditorTab tab;
    private final CompletionPopup popup = new CompletionPopup();
    private final Timer autoTrigger;
    private final Timer hintTimer;

    private List<CompletionItem> items = List.of();
    private CompletionItem ghostItem;
    private Consumer<ParameterHint> hintConsumer = hint -> { };
    private int replaceStart;
    private int replaceEnd;
    private boolean applying;
    private boolean suppressAutoOpen;
    private File baseDirectory;

    public CompletionController(EditorTab tab) {
        this.tab = tab;
        this.autoTrigger = new Timer(AUTO_DELAY_MS, e -> autoOpen());
        this.autoTrigger.setRepeats(false);
        this.hintTimer = new Timer(HINT_DELAY_MS, e -> updateHint());
        this.hintTimer.setRepeats(false);
        installKeyBindings();
        tab.document().addDocumentListener(this);
        pane().addCaretListener(e -> hintTimer.restart());
    }

    private JTextComponent pane() {
        return tab.textPane();
    }

    private GhostTextPane ghostPane() {
        return (GhostTextPane) tab.textPane();
    }

    /**
     * Sets the directory relative paths in the document are completed against.
     *
     * @param baseDirectory the workspace, or {@code null} to disable path
     *                      completion
     */
    public void setBaseDirectory(File baseDirectory) {
        this.baseDirectory = baseDirectory;
    }

    /**
     * Registers the receiver for the current command's signature. The text is
     * shown in the status bar rather than in a floating window, so it can never
     * cover the input method candidate list.
     */
    public void setHintConsumer(Consumer<ParameterHint> hintConsumer) {
        this.hintConsumer = hintConsumer == null ? hint -> { } : hintConsumer;
    }

    // --- key bindings -------------------------------------------------------

    /**
     * Binds Tab to accept a suggestion, and the arrow keys to walk the list.
     *
     * <p>Reinstalled after a theme change, because refreshing the look and feel
     * replaces the input and action maps; without that, Tab silently stopped
     * completing and the inline suggestion stopped appearing.</p>
     */
    public void installKeyBindings() {
        InputMap input = pane().getInputMap(JComponent.WHEN_FOCUSED);
        ActionMap actions = pane().getActionMap();

        // Ctrl+Space asks for suggestions wherever the caret happens to be.
        input.put(KeyStroke.getKeyStroke(KeyEvent.VK_SPACE, InputEvent.CTRL_DOWN_MASK),
                "cpgui.complete.trigger");
        actions.put("cpgui.complete.trigger", new AbstractAction() {
            private static final long serialVersionUID = 1L;

            @Override
            public void actionPerformed(ActionEvent event) {
                trigger(true);
            }
        });

        // Navigation works only while the list is open, then falls through.
        bind(input, actions, KeyStroke.getKeyStroke(KeyEvent.VK_UP, 0),
                "cpgui.complete.up", e -> navigate(-1));
        bind(input, actions, KeyStroke.getKeyStroke(KeyEvent.VK_DOWN, 0),
                "cpgui.complete.down", e -> navigate(1));
        bind(input, actions, KeyStroke.getKeyStroke(KeyEvent.VK_PAGE_UP, 0),
                "cpgui.complete.pageUp", e -> navigate(-8));
        bind(input, actions, KeyStroke.getKeyStroke(KeyEvent.VK_PAGE_DOWN, 0),
                "cpgui.complete.pageDown", e -> navigate(8));
        bind(input, actions, KeyStroke.getKeyStroke(KeyEvent.VK_ESCAPE, 0),
                "cpgui.complete.dismiss", e -> dismiss());

        // Tab accepts a suggestion, or the ghost text, and otherwise keeps its
        // normal meaning. Enter is deliberately left alone so it always inserts
        // a newline, and Ctrl+Enter still runs the script.
        Object tabKey = input.get(KeyStroke.getKeyStroke(KeyEvent.VK_TAB, 0));
        Action defaultTab = tabKey == null ? null : actions.get(tabKey);
        input.put(KeyStroke.getKeyStroke(KeyEvent.VK_TAB, 0), "cpgui.complete.acceptTab");
        actions.put("cpgui.complete.acceptTab", new AbstractAction() {
            private static final long serialVersionUID = 1L;

            @Override
            public void actionPerformed(ActionEvent event) {
                if (!acceptSuggestion() && defaultTab != null) {
                    defaultTab.actionPerformed(event);
                }
            }
        });
    }

    /**
     * Installs an action that defers to whatever the component did before, so
     * the suggestion list never permanently steals a key.
     */
    private void bind(InputMap input, ActionMap actions, KeyStroke keyStroke, String name,
                      java.util.function.Consumer<ActionEvent> body) {
        Object existingKey = input.get(keyStroke);
        Action fallback = existingKey == null ? null : actions.get(existingKey);
        input.put(keyStroke, name);
        actions.put(name, new AbstractAction() {
            private static final long serialVersionUID = 1L;

            @Override
            public void actionPerformed(ActionEvent event) {
                if (popup.isVisible()) {
                    body.accept(event);
                } else if (fallback != null) {
                    fallback.actionPerformed(event);
                }
            }
        });
    }

    private void navigate(int delta) {
        popup.moveSelection(delta);
        // Keep the inline suggestion in step with the highlighted row, so Tab
        // inserts exactly what the greyed text is showing.
        showGhost(popup.selected());
    }

    // --- triggering ---------------------------------------------------------

    /** Opens the list immediately, ignoring the automatic quiet rules. */
    public void trigger(boolean manual) {
        if (manual) {
            suppressAutoOpen = false;
        }
        refresh(manual);
    }

    /**
     * Opens the list only when the caret is somewhere suggestions are genuinely
     * expected. The provider returns nothing for a plain argument word, so the
     * check is simply whether there is something to show.
     */
    private void autoOpen() {
        if (suppressAutoOpen) {
            return;
        }
        refresh(false);
    }

    private void refresh(boolean manual) {
        if (!pane().isShowing()) {
            close();
            return;
        }
        CompletionProvider.Result result = CompletionProvider.complete(
                new CompletionProvider.Context(tab.shellKind(), tab.getText(),
                        pane().getCaretPosition(), baseDirectory, manual));
        items = result.items();
        replaceStart = result.replaceStart();
        replaceEnd = result.replaceEnd();

        if (items.isEmpty()) {
            close();
            return;
        }
        if (!manual && items.size() == 1) {
            // One clear answer is shown inline instead of in a list.
            popup.hide();
            showGhost(items.get(0));
            return;
        }
        // Both are shown: the list to choose from, and the highlighted entry
        // inline after the caret. Only ever one of them may be live, otherwise
        // Tab accepts whatever the list happens to hold rather than what the
        // greyed text is promising.
        popup.show(pane(), pane().getCaretPosition(), items);
        showGhost(popup.selected());
    }

    private void showGhost(CompletionItem item) {
        String typed = "";
        try {
            typed = tab.getText().substring(
                    Math.max(0, replaceStart), Math.min(replaceEnd, tab.getText().length()));
        } catch (RuntimeException ignored) {
            // Fall through with an empty prefix.
        }
        if (item == null) {
            clearGhost();
            return;
        }
        String insertion = item.insertText();
        // The comparison ignores case, so typing MK still suggests mkdir; the
        // completion is an extension of what was typed either way.
        if (insertion.length() <= typed.length()
                || !insertion.regionMatches(true, 0, typed, 0, typed.length())) {
            // Path and parameter suggestions are not always a plain extension of
            // what was typed, so they are not shown as ghost text.
            clearGhost();
            return;
        }
        ghostItem = item;
        ghostPane().setGhostText(insertion.substring(typed.length()));
        hintConsumer.accept(new ParameterHint(item.label(), item.detail(), List.of()));
    }

    private void clearGhost() {
        ghostItem = null;
        ghostPane().setGhostText("");
    }

    /** Refreshes the status bar signature for the command under the caret. */
    private void updateHint() {
        if (popup.isVisible()) {
            return;
        }
        if (ghostItem != null) {
            hintConsumer.accept(new ParameterHint(ghostItem.label(), ghostItem.detail(),
                    List.of()));
            return;
        }
        hintConsumer.accept(ParameterHint.forCaret(tab.shellKind(), tab.getText(),
                pane().getCaretPosition()));
    }

    /** Hides the list and keeps it hidden until the user asks again. */
    private void dismiss() {
        suppressAutoOpen = true;
        close();
    }

    // --- accepting ----------------------------------------------------------

    /**
     * Accepts the highlighted suggestion, or the ghost text when the list is
     * closed.
     *
     * @return {@code true} when something was inserted
     */
    public boolean acceptSuggestion() {
        if (popup.isVisible()) {
            // The highlighted row wins, and the ghost text mirrors it, so the
            // two can never disagree.
            accept();
            return true;
        }
        if (ghostItem != null) {
            insert(ghostItem, replaceStart, replaceEnd);
            return true;
        }
        return false;
    }

    /** Replaces the partial word with the highlighted suggestion. */
    public void accept() {
        CompletionItem item = popup.selected();
        popup.hide();
        if (item != null) {
            insert(item, replaceStart, replaceEnd);
        }
    }

    private void insert(CompletionItem item, int start, int end) {
        applying = true;
        try {
            StyledDocument document = tab.document();
            int from = Math.min(Math.max(0, start), document.getLength());
            int to = Math.min(Math.max(from, end), document.getLength());
            if (to > from) {
                document.remove(from, to - from);
            }
            document.insertString(from, item.insertText(), null);
            pane().setCaretPosition(Math.min(from + item.insertText().length(),
                    document.getLength()));
        } catch (BadLocationException ignored) {
            // The document moved under us; the next keystroke will try again.
        } finally {
            applying = false;
        }
        clearGhost();
        popup.hide();
        tab.highlighter().refresh();
        tab.diagnostics().refresh();
    }

    /** @return the suggestions currently offered, for diagnostics and tests. */
    public List<CompletionItem> items() {
        return items;
    }

    /** @return {@code true} while suggestions are on screen. */
    public boolean isPopupVisible() {
        return popup.isVisible();
    }

    /** @return the popup, exposed so tests can inspect the selection. */
    public CompletionPopup popup() {
        return popup;
    }

    /** @return the inline suggestion, or {@code null} when there is none. */
    public CompletionItem ghostItem() {
        return ghostItem;
    }

    // --- document tracking --------------------------------------------------

    @Override
    public void insertUpdate(DocumentEvent e) {
        handleChange(e);
    }

    @Override
    public void removeUpdate(DocumentEvent e) {
        handleChange(e);
    }

    @Override
    public void changedUpdate(DocumentEvent e) {
        // Attribute-only changes never affect completion.
    }

    private void handleChange(DocumentEvent e) {
        if (applying) {
            return;
        }
        // Starting a new word re-arms automatic suggestions after Escape.
        if (e.getType() == DocumentEvent.EventType.INSERT && insertedWhitespace(e)) {
            suppressAutoOpen = false;
        }
        if (popup.isVisible()) {
            autoTrigger.restart();
            return;
        }
        clearGhost();
        if (e.getType() != DocumentEvent.EventType.INSERT) {
            hintTimer.restart();
            return;
        }
        hintTimer.restart();
        if (suppressAutoOpen || !isTriggerCharacter(e)) {
            return;
        }
        autoTrigger.restart();
    }

    private boolean isTriggerCharacter(DocumentEvent e) {
        // Loading a file, pasting or undoing inserts many characters at once and
        // must not ambush the user with a suggestion list.
        if (e.getLength() != 1) {
            return false;
        }
        try {
            String inserted = e.getDocument().getText(e.getOffset(), e.getLength());
            if (inserted.isEmpty()) {
                return false;
            }
            char last = inserted.charAt(inserted.length() - 1);
            // Letters and digits type a name; the rest start a switch, a
            // variable or a path, all of which are worth suggesting.
            return Character.isLetterOrDigit(last) || last == '\\' || last == '/'
                    || last == '$' || last == '%' || last == '-' || last == '.';
        } catch (BadLocationException ex) {
            return false;
        }
    }

    private boolean insertedWhitespace(DocumentEvent e) {
        try {
            String inserted = e.getDocument().getText(e.getOffset(), e.getLength());
            return !inserted.isEmpty() && Character.isWhitespace(inserted.charAt(0));
        } catch (BadLocationException ex) {
            return false;
        }
    }

    /** Hides everything this controller shows. */
    public void close() {
        popup.hide();
        clearGhost();
    }
}
