package com.dogepopcat.cpgui.shell;

import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

/**
 * Keeps one {@link ShellSession} per logical owner, keyed by an arbitrary
 * handle (the application uses editor tabs).
 *
 * <p>Sessions are created on demand, replaced when the requested interpreter
 * changes and closed when their owner goes away.</p>
 */
public final class SessionRegistry implements AutoCloseable {

    private final Map<Object, ShellSession> sessions = new HashMap<>();

    /**
     * Returns the owner's session, starting one when needed.
     *
     * @param key      the owner handle
     * @param kind     interpreter the session must run
     * @param workDir  directory the interpreter starts in
     * @param listener receiver for the session's events
     * @return a live session
     * @throws IOException if the interpreter process cannot be started
     */
    public synchronized ShellSession acquire(Object key, ShellKind kind, File workDir,
                                             ShellSession.Listener listener) throws IOException {
        ShellSession existing = sessions.get(key);
        if (existing != null && existing.isAlive() && existing.kind() == kind) {
            // The session is reused, so point it at this run's receiver.
            existing.setListener(listener);
            return existing;
        }
        if (existing != null) {
            existing.close();
            sessions.remove(key);
        }
        ShellSession created = new ShellSession(kind, workDir, listener);
        sessions.put(key, created);
        return created;
    }

    /** @return the owner's session, or {@code null} when it has none. */
    public synchronized ShellSession peek(Object key) {
        return sessions.get(key);
    }

    /** Closes and forgets the owner's session. */
    public synchronized void release(Object key) {
        ShellSession session = sessions.remove(key);
        if (session != null) {
            session.close();
        }
    }

    /** @return how many sessions are being tracked. */
    public synchronized int size() {
        return sessions.size();
    }

    /** Closes every session. */
    public synchronized void closeAll() {
        for (ShellSession session : sessions.values()) {
            session.close();
        }
        sessions.clear();
    }

    @Override
    public synchronized void close() {
        closeAll();
    }
}
