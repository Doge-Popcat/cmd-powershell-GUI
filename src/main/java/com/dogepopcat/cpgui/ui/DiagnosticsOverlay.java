package com.dogepopcat.cpgui.ui;

import com.dogepopcat.cpgui.analysis.DangerAnalyzer;
import com.dogepopcat.cpgui.analysis.DangerAnalyzer.Finding;
import com.dogepopcat.cpgui.analysis.DangerAnalyzer.Severity;

import javax.swing.Timer;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import javax.swing.text.Highlighter;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

/**
 * Keeps an editor tab's diagnostics up to date.
 *
 * <p>Re-analysing on every keystroke is unnecessary, so edits are coalesced.
 * The resulting findings are drawn as wavy underlines and passed to a listener
 * so the application can list them in the problems view.</p>
 */
public final class DiagnosticsOverlay implements DocumentListener {

    /** Delay before a pending edit is analysed, in milliseconds. */
    private static final int DEBOUNCE_MS = 220;

    private final EditorTab tab;
    private final Timer debounce;
    private final Map<Severity, SquigglePainter> painters = new EnumMap<>(Severity.class);
    private final List<Object> applied = new ArrayList<>();

    private List<Finding> findings = List.of();
    private Consumer<List<Finding>> listener = list -> { };
    private boolean running;

    public DiagnosticsOverlay(EditorTab tab) {
        this.tab = tab;
        this.debounce = new Timer(DEBOUNCE_MS, e -> run());
        this.debounce.setRepeats(false);
        painters.put(Severity.DANGER, new SquigglePainter(Theme.ERROR));
        painters.put(Severity.WARNING, new SquigglePainter(Theme.WARNING));
        painters.put(Severity.INFO, new SquigglePainter(Theme.CONSOLE_INFO));
        tab.document().addDocumentListener(this);
        run();
    }

    /** Registers the receiver for the current findings. */
    public void setListener(Consumer<List<Finding>> listener) {
        this.listener = listener == null ? list -> { } : listener;
        this.listener.accept(findings);
    }

    /** @return the findings from the most recent analysis. */
    public List<Finding> findings() {
        return findings;
    }

    /** Forces an immediate re-analysis, for example after an interpreter switch. */
    public void refresh() {
        run();
    }

    private void run() {
        if (running) {
            return;
        }
        running = true;
        try {
            findings = DangerAnalyzer.analyze(tab.shellKind(), tab.getText());
            applyUnderlines();
        } finally {
            running = false;
        }
        listener.accept(findings);
    }

    private void applyUnderlines() {
        Highlighter highlighter = tab.textPane().getHighlighter();
        for (Object highlight : applied) {
            highlighter.removeHighlight(highlight);
        }
        applied.clear();

        int length = tab.textPane().getDocument().getLength();
        for (Finding finding : findings) {
            SquigglePainter painter = painters.get(finding.severity());
            if (painter == null) {
                continue;
            }
            int start = Math.max(0, Math.min(finding.start(), length));
            int end = Math.max(start, Math.min(finding.end(), length));
            if (start == end) {
                continue;
            }
            try {
                applied.add(highlighter.addHighlight(start, end, painter));
            } catch (javax.swing.text.BadLocationException ignored) {
                // The document changed under us; the next run will catch up.
            }
        }
    }

    private void schedule() {
        if (!running) {
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
        // Attribute-only changes never affect findings.
    }
}
