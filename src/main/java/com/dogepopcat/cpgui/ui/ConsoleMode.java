package com.dogepopcat.cpgui.ui;

import com.dogepopcat.cpgui.i18n.Msg;

/**
 * How the console behaves between runs.
 *
 * <p>Classic mode treats every run as its own block: a rule separates it from
 * the previous one, and stopping throws the interpreter away so the next run
 * starts a fresh one.</p>
 *
 * <p>Terminal mode behaves like a terminal window: output runs on continuously
 * and one interpreter is kept for the life of the tab, so the working directory,
 * variables and anything else it holds survive a stop. Only an explicit restart
 * replaces it.</p>
 */
public enum ConsoleMode {

    /** A rule between runs, and a fresh interpreter after a stop. */
    CLASSIC,
    /** Continuous output, and one interpreter kept for the whole tab. */
    TERMINAL;

    /** @return the name shown in the interface, in the current language. */
    public String label() {
        return this == CLASSIC ? Msg.MODE_CLASSIC.text() : Msg.MODE_TERMINAL.text();
    }

    /** @return the one line explanation shown as a tool tip. */
    public String description() {
        return this == CLASSIC ? Msg.MODE_CLASSIC_TIP.text() : Msg.MODE_TERMINAL_TIP.text();
    }

    @Override
    public String toString() {
        return label();
    }
}
