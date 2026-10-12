package com.dogepopcat.cpgui.ui;

import com.dogepopcat.cpgui.completion.CompletionItem;
import com.dogepopcat.cpgui.completion.CompletionProvider;

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
import java.util.List;

/**
 * Drives the IntelliSense popup for one editor tab.
 *
 * <p>Suggestions appear automatically while typing and on Ctrl+Space. While the
 * popup is open the arrow keys, Enter, Tab and Escape are redirected to it; as
 * soon as it closes those keys fall back to the editor's own behaviour, so the
 * shortcut never takes anything away from normal typing.</p>
 */
public final class CompletionController implements DocumentListener {

    /** Delay before typing opens the popup, in milliseconds. */
    private static final int AUTO_DELAY_MS = 140;

    private final EditorTab tab;
    private final CompletionPopup popup = new CompletionPopup();
    private final Timer autoTrigger;

    private List<CompletionItem> items = List.of();
    private int replaceStart;
    private int replaceEnd;
    private boolean applying;
    private boolean suppressAutoOpen;

    public CompletionController(EditorTab tab) {
        this.tab = tab;
        this.autoTrigger = new Timer(AUTO_DELAY_MS, e -> openIfUseful());
        this.autoTrigger.setRepeats(false);
        installKeyBindings();
        tab.document().addDocumentListener(this);
    }

    private JTextComponent pane() {
        return tab.textPane();
    }

    // --- key bindings -------------------------------------------------------

    private void installKeyBindings() {
        InputMap input = pane().getInputMap(JComponent.WHEN_FOCUSED);
        ActionMap actions = pane().getActionMap();

        // Ctrl+Space opens the popup whether or not it is already showing.
        input.put(KeyStroke.getKeyStroke(KeyEvent.VK_SPACE, InputEvent.CTRL_DOWN_MASK),
                "cpgui.complete.trigger");
        actions.put("cpgui.complete.trigger", new AbstractAction() {
            private static final long serialVersionUID = 1L;

            @Override
            public void actionPerformed(ActionEvent event) {
                trigger(true);
            }
        });

        // The rest only act while the popup is open, then fall back.
        bind(input, actions, KeyStroke.getKeyStroke(KeyEvent.VK_UP, 0),
                "cpgui.complete.up", e -> navigate(-1));
        bind(input, actions, KeyStroke.getKeyStroke(KeyEvent.VK_DOWN, 0),
                "cpgui.complete.down", e -> navigate(1));
        bind(input, actions, KeyStroke.getKeyStroke(KeyEvent.VK_PAGE_UP, 0),
                "cpgui.complete.pageUp", e -> navigate(-8));
        bind(input, actions, KeyStroke.getKeyStroke(KeyEvent.VK_PAGE_DOWN, 0),
                "cpgui.complete.pageDown", e -> navigate(8));
        bind(input, actions, KeyStroke.getKeyStroke(KeyEvent.VK_ENTER, 0),
                "cpgui.complete.accept", e -> accept());
        bind(input, actions, KeyStroke.getKeyStroke(KeyEvent.VK_TAB, 0),
                "cpgui.complete.acceptTab", e -> accept());
        bind(input, actions, KeyStroke.getKeyStroke(KeyEvent.VK_ESCAPE, 0),
                "cpgui.complete.dismiss", e -> dismiss());
    }

    /**
     * Installs an action that defers to whatever the component did before, so
     * the popup never permanently steals a key.
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
    }

    // --- triggering ---------------------------------------------------------

    /** Opens the popup immediately and selects the best match. */
    public void trigger(boolean manual) {
        if (manual) {
            suppressAutoOpen = false;
        }
        refresh();
    }

    private void openIfUseful() {
        if (suppressAutoOpen) {
            return;
        }
        refresh();
    }

    private void refresh() {
        CompletionProvider.Result result = CompletionProvider.complete(
                new CompletionProvider.Context(tab.shellKind(), tab.getText(),
                        pane().getCaretPosition()));
        items = result.items();
        replaceStart = result.replaceStart();
        replaceEnd = result.replaceEnd();
        if (items.isEmpty()) {
            popup.hide();
            return;
        }
        popup.show(pane(), pane().getCaretPosition(), items);
    }

    /** Hides the popup and keeps it hidden until the caret moves to a new word. */
    private void dismiss() {
        suppressAutoOpen = true;
        popup.hide();
    }

    // --- accepting ----------------------------------------------------------

    /** Replaces the partial word with the highlighted suggestion. */
    public void accept() {
        CompletionItem item = popup.selected();
        popup.hide();
        if (item == null) {
            return;
        }
        applying = true;
        try {
            StyledDocument document = tab.document();
            int start = Math.min(replaceStart, document.getLength());
            int end = Math.min(replaceEnd, document.getLength());
            if (end > start) {
                document.remove(start, end - start);
            }
            document.insertString(start, item.insertText(), null);
            pane().setCaretPosition(Math.min(start + item.insertText().length(),
                    document.getLength()));
        } catch (BadLocationException ignored) {
            // The document moved under us; the next keystroke will try again.
        } finally {
            applying = false;
        }
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
            // Refilter against the new text so the list follows the typing.
            autoTrigger.restart();
            return;
        }
        if (e.getType() != DocumentEvent.EventType.INSERT) {
            return;
        }
        if (suppressAutoOpen || !isTriggerCharacter(e)) {
            return;
        }
        autoTrigger.restart();
    }

    private boolean insertedWhitespace(DocumentEvent e) {
        try {
            String inserted = e.getDocument().getText(e.getOffset(), e.getLength());
            return !inserted.isEmpty() && Character.isWhitespace(inserted.charAt(0));
        } catch (BadLocationException ex) {
            return false;
        }
    }

    private boolean isTriggerCharacter(DocumentEvent e) {
        try {
            String inserted = e.getDocument().getText(e.getOffset(), e.getLength());
            if (inserted.isEmpty()) {
                return false;
            }
            char last = inserted.charAt(inserted.length() - 1);
            return Character.isLetterOrDigit(last) || last == '\\' || last == '/'
                    || last == '$' || last == '%';
        } catch (BadLocationException ex) {
            return false;
        }
    }

    /** Hides the popup, for example when the tab loses focus. */
    public void close() {
        popup.hide();
    }
}
