package com.dogepopcat.cpgui.ui;

import com.dogepopcat.cpgui.analysis.ShellLexer;
import com.dogepopcat.cpgui.shell.ShellKind;

import javax.swing.Timer;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import javax.swing.text.SimpleAttributeSet;
import javax.swing.text.StyleConstants;
import javax.swing.text.StyledDocument;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * Applies the syntax colours of the active interpreter to an editor tab.
 *
 * <p>Re-colouring the whole document on every keystroke would be wasteful, so
 * edits are coalesced with a short timer. Attribute updates themselves raise
 * document events, which are ignored through the {@code applying} guard.</p>
 */
public final class SyntaxHighlighter implements DocumentListener {

    /** Delay before a pending edit is re-coloured, in milliseconds. */
    private static final int DEBOUNCE_MS = 90;

    private final EditorTab tab;
    private final Timer debounce;
    private final Map<ShellLexer.TokenType, SimpleAttributeSet> styles =
            new EnumMap<>(ShellLexer.TokenType.class);
    private final SimpleAttributeSet plain = new SimpleAttributeSet();
    private boolean applying;

    public SyntaxHighlighter(EditorTab tab) {
        this.tab = tab;
        this.debounce = new Timer(DEBOUNCE_MS, e -> apply());
        this.debounce.setRepeats(false);
        buildStyles();
        tab.document().addDocumentListener(this);
        Theme.addListener(this::restyle);
        apply();
    }

    /** Rebuilds the colours and reapplies them after a theme change. */
    public void restyle() {
        styles.clear();
        buildStyles();
        apply();
    }

    private void buildStyles() {
        StyleConstants.setForeground(plain, Theme.EDITOR_FOREGROUND);
        style(ShellLexer.TokenType.COMMAND, Theme.SYNTAX_COMMAND, true);
        style(ShellLexer.TokenType.KEYWORD, Theme.SYNTAX_KEYWORD, true);
        style(ShellLexer.TokenType.PARAMETER, Theme.SYNTAX_PARAMETER, false);
        style(ShellLexer.TokenType.SWITCH, Theme.SYNTAX_SWITCH, false);
        style(ShellLexer.TokenType.VARIABLE, Theme.SYNTAX_VARIABLE, false);
        style(ShellLexer.TokenType.STRING, Theme.SYNTAX_STRING, false);
        style(ShellLexer.TokenType.NUMBER, Theme.SYNTAX_NUMBER, false);
        style(ShellLexer.TokenType.OPERATOR, Theme.SYNTAX_OPERATOR, false);
        style(ShellLexer.TokenType.COMMENT, Theme.SYNTAX_COMMENT, false);
        style(ShellLexer.TokenType.LABEL, Theme.SYNTAX_LABEL, false);
    }

    private void style(ShellLexer.TokenType type, java.awt.Color colour, boolean bold) {
        SimpleAttributeSet attributes = new SimpleAttributeSet();
        StyleConstants.setForeground(attributes, colour);
        StyleConstants.setBold(attributes, bold);
        styles.put(type, attributes);
    }

    /** Schedules a re-colour; used when the interpreter of the tab changes. */
    public void refresh() {
        applying = false;
        apply();
    }

    private void apply() {
        if (applying) {
            return;
        }
        applying = true;
        try {
            StyledDocument document = tab.document();
            String text = tab.getText();
            ShellKind kind = tab.shellKind();

            document.setCharacterAttributes(0, text.length(), plain, true);
            List<ShellLexer.Token> tokens = ShellLexer.tokenize(kind, text, 0);
            for (ShellLexer.Token token : tokens) {
                SimpleAttributeSet attributes = styles.get(token.type());
                if (attributes != null && token.length() > 0) {
                    document.setCharacterAttributes(token.start(), token.length(), attributes, true);
                }
            }
        } finally {
            applying = false;
        }
    }

    private void schedule() {
        if (!applying) {
            debounce.restart();
        }
    }

    @Override
    public void insertUpdate(DocumentEvent e) {
        schedule();
    }

    @Override
    public void removeUpdate(DocumentEvent e) {
        schedule();
    }

    @Override
    public void changedUpdate(DocumentEvent e) {
        // Attribute changes are produced by this class; ignore them.
    }
}
