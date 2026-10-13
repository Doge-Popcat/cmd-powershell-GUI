package com.dogepopcat.cpgui.ui;

import com.dogepopcat.cpgui.i18n.Msg;

import com.dogepopcat.cpgui.shell.ShellKind;

import javax.swing.AbstractAction;
import javax.swing.ActionMap;
import javax.swing.BorderFactory;
import javax.swing.InputMap;
import javax.swing.JComponent;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextPane;
import javax.swing.KeyStroke;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import javax.swing.text.BadLocationException;
import javax.swing.text.DefaultEditorKit;
import javax.swing.text.Element;
import javax.swing.text.StyledDocument;
import javax.swing.undo.CannotRedoException;
import javax.swing.undo.CannotUndoException;
import javax.swing.undo.UndoManager;
import java.awt.BorderLayout;
import java.awt.Font;
import java.awt.event.ActionEvent;
import java.awt.event.InputEvent;
import java.awt.event.KeyEvent;
import java.io.File;

/**
 * One editor tab: a plain-text code area with a line number gutter, undo
 * support and dirty tracking.
 *
 * <p>The widget intentionally exposes the raw {@link StyledDocument} so later
 * features (syntax highlighting, diagnostics, completion) can attach their own
 * document listeners without the tab having to know about them.</p>
 */
public class EditorTab extends JPanel {

    private static final long serialVersionUID = 1L;

    private final GhostTextPane textPane = new GhostTextPane();
    private final JScrollPane scroll;
    private final LineNumberView gutter;
    private final UndoManager undo = new CodeUndoManager();
    private final SyntaxHighlighter highlighter;
    private final DiagnosticsOverlay diagnostics;
    private final CompletionController completion;

    private ShellKind shellKind;
    /** Line results shown while single line mode is on: line index to success. */
    private final java.util.Map<Integer, Boolean> lineResults = new java.util.LinkedHashMap<>();
    private final java.util.List<Object> lineHighlights = new java.util.ArrayList<>();
    private boolean singleLineMode;

    private File file;
    private String displayName;
    private boolean dirty;
    private Runnable dirtyListener = () -> { };
    private boolean loading;

    /**
     * @param shellKind interpreter this tab's script targets, decided when the
     *                  tab is created
     */
    public EditorTab(ShellKind shellKind) {
        super(new BorderLayout());
        this.shellKind = shellKind;

        textPane.setFont(Theme.mono(13));
        textPane.setBackground(Theme.EDITOR_BACKGROUND);
        textPane.setForeground(Theme.EDITOR_FOREGROUND);
        textPane.setCaretColor(Theme.EDITOR_CARET);
        textPane.setSelectionColor(Theme.EDITOR_SELECTION);
        textPane.setSelectedTextColor(Theme.EDITOR_FOREGROUND);
        textPane.setBorder(BorderFactory.createEmptyBorder(4, 8, 4, 8));

        gutter = new LineNumberView(textPane);
        installUndoRedo();
        installCaretTracking();

        scroll = new JScrollPane(textPane);
        scroll.setRowHeaderView(gutter);
        scroll.setBorder(BorderFactory.createEmptyBorder());
        scroll.getViewport().setBackground(Theme.EDITOR_BACKGROUND);
        scroll.getVerticalScrollBar().setUnitIncrement(16);
        add(scroll, BorderLayout.CENTER);

        textPane.getDocument().addDocumentListener(new DocumentListener() {
            @Override
            public void insertUpdate(DocumentEvent e) {
                markDirty();
            }

            @Override
            public void removeUpdate(DocumentEvent e) {
                markDirty();
            }

            @Override
            public void changedUpdate(DocumentEvent e) {
                // Attribute-only changes (syntax colours) are not text edits.
            }
        });

        highlighter = new SyntaxHighlighter(this);
        diagnostics = new DiagnosticsOverlay(this);
        completion = new CompletionController(this);
        Theme.addListener(this::applyTheme);
        // The palette must be applied now, not only on a later change: the look
        // and feel defaults are light whatever the chosen theme is.
        applyTheme();
    }

    /** Re-applies the palette after a theme change. */
    void applyTheme() {
        // Refreshing the look and feel drops the input map, so the completion
        // keys are put back here.
        if (completion != null) {
            completion.installKeyBindings();
        }
        textPane.setBackground(Theme.EDITOR_BACKGROUND);
        textPane.setForeground(Theme.EDITOR_FOREGROUND);
        textPane.setCaretColor(Theme.EDITOR_CARET);
        textPane.setSelectionColor(Theme.EDITOR_SELECTION);
        textPane.setSelectedTextColor(Theme.EDITOR_FOREGROUND);
        scroll.getViewport().setBackground(Theme.EDITOR_BACKGROUND);
        scroll.setBackground(Theme.EDITOR_BACKGROUND);
        setBackground(Theme.WINDOW_BACKGROUND);
        highlighter.restyle();
        // Refreshing the look and feel installs a new highlighter on the text
        // component, which drops everything that was drawn with the old one: the
        // diagnostic underlines and the line marks vanish from the screen. Both
        // are put back, which also moves them onto the new palette.
        diagnostics.refresh();
        applyLineResults();
        repaint();
    }

    /**
     * An undo manager that ignores the edits the syntax highlighter makes.
     *
     * <p>Recolouring the text changes character attributes, and the document
     * reports each change as an undoable edit. Those edits filled the stack, so
     * Ctrl+Z undid a colour change rather than the typing and appeared to do
     * nothing at all.</p>
     */
    private static final class CodeUndoManager extends UndoManager {

        private static final long serialVersionUID = 1L;

        @Override
        public synchronized boolean addEdit(javax.swing.undo.UndoableEdit edit) {
            // The attribute edit class is not visible outside its package, so it
            // is recognised by name; it is what setCharacterAttributes adds.
            if (edit.getClass().getName().endsWith("AttributeUndoableEdit")) {
                return false;
            }
            if (edit instanceof javax.swing.text.AbstractDocument.DefaultDocumentEvent event
                    && event.getType() == javax.swing.event.DocumentEvent.EventType.CHANGE) {
                return false;
            }
            return super.addEdit(edit);
        }
    }

    /**
     * Forgets the edit history.
     *
     * <p>Called after a file is loaded into the tab, so the first Ctrl+Z does
     * not wipe out everything that was just read from disk.</p>
     */
    public void discardUndoHistory() {
        undo.discardAllEdits();
    }

    /** @return {@code true} when there is something to undo. */
    public boolean canUndo() {
        return undo.canUndo();
    }

    /** @return {@code true} when there is something to redo. */
    public boolean canRedo() {
        return undo.canRedo();
    }

    /** Undoes one edit, if there is one. */
    public void undoLastEdit() {
        try {
            if (undo.canUndo()) {
                undo.undo();
            }
        } catch (CannotUndoException ignored) {
            // The stack is out of step; there is nothing sensible to do.
        }
    }

    /** Redoes one edit, if there is one. */
    public void redoLastEdit() {
        try {
            if (undo.canRedo()) {
                undo.redo();
            }
        } catch (CannotRedoException ignored) {
            // Same as above.
        }
    }

    private void installUndoRedo() {
        textPane.getDocument().addUndoableEditListener(undo);
        InputMap im = textPane.getInputMap(JComponent.WHEN_FOCUSED);
        ActionMap am = textPane.getActionMap();

        im.put(KeyStroke.getKeyStroke(KeyEvent.VK_Z, InputEvent.CTRL_DOWN_MASK), "cpgui.undo");
        am.put("cpgui.undo", new AbstractAction() {
            private static final long serialVersionUID = 1L;

            @Override
            public void actionPerformed(ActionEvent e) {
                try {
                    if (undo.canUndo()) {
                        undo.undo();
                    }
                } catch (CannotUndoException ignored) {
                    // Nothing sensible to do; the stack is simply out of sync.
                }
            }
        });

        im.put(KeyStroke.getKeyStroke(KeyEvent.VK_Y, InputEvent.CTRL_DOWN_MASK), "cpgui.redo");
        im.put(KeyStroke.getKeyStroke(KeyEvent.VK_Z,
                InputEvent.CTRL_DOWN_MASK | InputEvent.SHIFT_DOWN_MASK), "cpgui.redo");
        am.put("cpgui.redo", new AbstractAction() {
            private static final long serialVersionUID = 1L;

            @Override
            public void actionPerformed(ActionEvent e) {
                try {
                    if (undo.canRedo()) {
                        undo.redo();
                    }
                } catch (CannotRedoException ignored) {
                    // Same as above.
                }
            }
        });

        // Select-all behaves like every other code editor.
        im.put(KeyStroke.getKeyStroke(KeyEvent.VK_A, InputEvent.CTRL_DOWN_MASK),
                DefaultEditorKit.selectAllAction);
    }

    private void installCaretTracking() {
        textPane.addCaretListener(e -> gutter.caretMoved());
    }

    private void markDirty() {
        if (loading || dirty) {
            return;
        }
        dirty = true;
        dirtyListener.run();
    }

    // --- document access ---------------------------------------------------

    /** @return the styled document backing this editor. */
    public StyledDocument document() {
        return textPane.getStyledDocument();
    }

    /** @return the underlying text component, for wiring extra key bindings. */
    public JTextPane textPane() {
        return textPane;
    }

    /** @return the full editor contents. */
    public String getText() {
        try {
            return textPane.getDocument().getText(0, textPane.getDocument().getLength());
        } catch (BadLocationException e) {
            return "";
        }
    }

    /** Replaces the whole document without marking the tab dirty. */
    public void setText(String text) {
        loading = true;
        try {
            textPane.setText(text == null ? "" : text);
            textPane.setCaretPosition(0);
            undo.discardAllEdits();
        } finally {
            loading = false;
        }
        dirty = false;
        dirtyListener.run();
        gutter.caretMoved();
    }

    /** @return the caret's zero based line and column, as {@code [line, column]}. */
    public int[] caretLineColumn() {
        Element root = textPane.getDocument().getDefaultRootElement();
        int caret = textPane.getCaretPosition();
        int line = root.getElementIndex(caret);
        int start = root.getElement(line).getStartOffset();
        return new int[]{line, caret - start};
    }

    /** Zero based index of the line containing the caret. */
    public int caretLine() {
        return caretLineColumn()[0];
    }

    /** Selects an entire zero based line and scrolls it into view. */
    public void selectLine(int line) {
        Element root = textPane.getDocument().getDefaultRootElement();
        if (line < 0 || line >= root.getElementCount()) {
            return;
        }
        Element element = root.getElement(line);
        int start = element.getStartOffset();
        int end = Math.max(start, element.getEndOffset() - 1);
        textPane.select(start, end);
        textPane.requestFocusInWindow();
    }

    /** @return the text of a zero based line, without its line terminator. */
    public String lineText(int line) {
        Element root = textPane.getDocument().getDefaultRootElement();
        if (line < 0 || line >= root.getElementCount()) {
            return "";
        }
        Element element = root.getElement(line);
        try {
            int start = element.getStartOffset();
            int end = Math.max(start, element.getEndOffset() - 1);
            return textPane.getDocument().getText(start, end - start);
        } catch (BadLocationException e) {
            return "";
        }
    }

    // --- file and dirty state ---------------------------------------------

    public File file() {
        return file;
    }

    public void setFile(File file) {
        this.file = file;
        if (file != null) {
            this.displayName = file.getName();
        }
    }

    /** Sets the name shown on the tab while no file is associated yet. */
    public void setDisplayName(String displayName) {
        this.displayName = displayName == null || displayName.isBlank()
                ? Msg.TAB_UNTITLED.text() : displayName;
    }

    public boolean isDirty() {
        return dirty;
    }

    public void clearDirty() {
        dirty = false;
        dirtyListener.run();
    }

    /** Registers a callback fired whenever the dirty flag flips. */
    public void setDirtyListener(Runnable listener) {
        this.dirtyListener = listener == null ? () -> { } : listener;
    }

    /** @return the shell kind this tab targets. */
    public ShellKind shellKind() {
        return shellKind;
    }

    /** Switches the interpreter this tab's scripts are sent to. */
    public void setShellKind(ShellKind shellKind) {
        this.shellKind = shellKind == null ? ShellKind.CMD : shellKind;
        // The syntax and the danger rules changed, so re-evaluate both.
        highlighter.refresh();
        diagnostics.refresh();
    }

    /** @return the highlighter colouring this tab. */
    public SyntaxHighlighter highlighter() {
        return highlighter;
    }

    /** @return the diagnostics overlay analysing this tab. */
    public DiagnosticsOverlay diagnostics() {
        return diagnostics;
    }

    /** @return the IntelliSense controller driving this tab's popup. */
    public CompletionController completion() {
        return completion;
    }

    /**
     * Changes the editor font, keeping the line number gutter in step.
     *
     * @param font the font to use
     */
    public void setEditorFont(Font font) {
        if (font == null) {
            return;
        }
        textPane.setFont(font);
        gutter.setGutterFont(font);
        highlighter.refresh();
    }

    /** @return {@code true} while single line mode is active. */
    public boolean isSingleLineMode() {
        return singleLineMode;
    }

    /**
     * Turns single line mode on or off, clearing the line marks when it goes off.
     *
     * @param singleLineMode the new state
     */
    public void setSingleLineMode(boolean singleLineMode) {
        this.singleLineMode = singleLineMode;
        if (!singleLineMode) {
            clearLineResults();
        }
    }

    /** @return how many lines the document has. */
    public int lineCount() {
        return textPane.getDocument().getDefaultRootElement().getElementCount();
    }

    /**
     * Moves the caret to the start of a line and scrolls it into view.
     *
     * @param line the zero based line index
     */
    public void moveCaretToLine(int line) {
        Element root = textPane.getDocument().getDefaultRootElement();
        int clamped = Math.max(0, Math.min(line, root.getElementCount() - 1));
        int offset = root.getElement(clamped).getStartOffset();
        textPane.setCaretPosition(offset);
        try {
            java.awt.Rectangle view = textPane.modelToView(offset);
            if (view != null) {
                textPane.scrollRectToVisible(view);
            }
        } catch (javax.swing.text.BadLocationException ignored) {
            // Scrolling is a convenience; the caret has still moved.
        }
    }

    /**
     * Records how a line turned out and washes it green or red.
     *
     * @param line    the zero based line index
     * @param success whether the command succeeded
     */
    public void markLineResult(int line, boolean success) {
        if (!singleLineMode) {
            return;
        }
        lineResults.put(line, success);
        applyLineResults();
    }

    /** Removes every line mark. */
    public void clearLineResults() {
        lineResults.clear();
        applyLineResults();
    }

    /** @return the recorded result of a line, or {@code null} when it has none. */
    public Boolean lineResult(int line) {
        return lineResults.get(line);
    }

    private void applyLineResults() {
        javax.swing.text.Highlighter highlighter = textPane.getHighlighter();
        for (Object highlight : lineHighlights) {
            highlighter.removeHighlight(highlight);
        }
        lineHighlights.clear();

        Element root = textPane.getDocument().getDefaultRootElement();
        lineResults.forEach((line, success) -> {
            if (line < 0 || line >= root.getElementCount()) {
                return;
            }
            Element element = root.getElement(line);
            int start = element.getStartOffset();
            int end = Math.max(start, element.getEndOffset() - 1);
            try {
                // The colour is resolved while the mark is painted, so marks made
                // before a palette change follow it instead of keeping the shade
                // of the palette they were made in.
                lineHighlights.add(highlighter.addHighlight(start, end,
                        new LineStatusPainter(success
                                ? () -> Theme.LINE_SUCCESS : () -> Theme.LINE_FAILURE)));
            } catch (javax.swing.text.BadLocationException ignored) {
                // The document changed under us; the next run will catch up.
            }
        });
    }

    /** @return the font the editor is currently using. */
    public Font editorFont() {
        return textPane.getFont();
    }

    /** @return the width of the line number gutter, for layout checks. */
    public int gutterWidth() {
        return gutter.getPreferredSize().width;
    }

    /** @return the tab title, prefixed with {@code *} when unsaved. */
    public String title() {
        String name = file != null ? file.getName()
                : (displayName == null ? Msg.TAB_UNTITLED.text() : displayName);
        return dirty ? "*" + name : name;
    }
}
