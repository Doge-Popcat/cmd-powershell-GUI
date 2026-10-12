package com.dogepopcat.cpgui.shell;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ThreadLocalRandom;

/**
 * A long lived {@code cmd.exe} or {@code powershell.exe} process driven through
 * its standard streams.
 *
 * <p>Keeping one process alive per editor tab means state survives between runs:
 * {@code cd}, {@code set} and PowerShell variables all behave the way they do in
 * a real console. Because a pipe has no prompt to synchronise on, every run
 * appends a uniquely numbered marker command. The reader thread watches for that
 * marker and only then reports the run as finished, together with the exit
 * code the interpreter reported.</p>
 */
public final class ShellSession implements AutoCloseable {

    /** Receives everything a session produces. Called from reader threads. */
    public interface Listener {

        /** @param error {@code true} when the text came from the error stream */
        void onOutput(String text, boolean error);

        /** Called once per submitted script, after the marker was seen. */
        void onFinished(int exitCode, boolean success);

        /** Called when the interpreter process ended. */
        void onClosed(int exitCode);

        /** Called when a script could not be handed to the interpreter. */
        void onFailure(String message);
    }

    private final ShellKind kind;
    private final Charset charset;
    private final Process process;
    private final BufferedWriter stdin;
    private final Listener listener;
    private final String sessionId;
    private final String promptToken;
    private final Object lock = new Object();

    private CompletableFuture<Integer> pending;
    private CompletableFuture<String> pendingCapture;
    private StringBuilder captureBuffer;
    private String pendingMarker;
    private long sequence;
    private volatile boolean closed;
    private volatile boolean closeRequested;
    private volatile int exitCode = -1;

    /**
     * Starts an interpreter process.
     *
     * @param kind       which interpreter to start
     * @param workingDir directory the process starts in, or {@code null} for the
     *                   JVM's working directory
     * @param listener   receiver for output and lifecycle events
     * @throws IOException if the interpreter cannot be started
     */
    public ShellSession(ShellKind kind, File workingDir, Listener listener) throws IOException {
        this.kind = kind;
        this.listener = listener;
        this.charset = ConsoleCharset.get();
        this.sessionId = Long.toHexString(ThreadLocalRandom.current().nextLong() & 0xFFFFFFFFL);

        List<String> command = new ArrayList<>();
        command.add(kind.executable());
        if (kind == ShellKind.CMD) {
            // A unique prompt is the only reliable way to spot - and remove -
            // the prompt cmd.exe prints before every command.
            this.promptToken = "#P#" + sessionId + "#";
            command.add("/Q");
            command.add("/K");
            command.add("prompt " + promptToken);
        } else {
            this.promptToken = null;
            command.add("-NoLogo");
            command.add("-NoProfile");
            command.add("-NoExit");
            command.add("-Command");
            command.add("-");
        }

        ProcessBuilder builder = new ProcessBuilder(command);
        if (workingDir != null && workingDir.isDirectory()) {
            builder.directory(workingDir);
        }
        this.process = builder.start();
        this.stdin = new BufferedWriter(new OutputStreamWriter(process.getOutputStream(), charset));
        startReaders();
    }

    private void startReaders() {
        Thread stdout = new Thread(() -> pump(process.getInputStream(), false), "cpgui-out-" + sessionId);
        stdout.setDaemon(true);
        stdout.start();

        Thread stderr = new Thread(() -> pump(process.getErrorStream(), true), "cpgui-err-" + sessionId);
        stderr.setDaemon(true);
        stderr.start();

        Thread waiter = new Thread(this::awaitExit, "cpgui-wait-" + sessionId);
        waiter.setDaemon(true);
        waiter.start();
    }

    private void awaitExit() {
        try {
            exitCode = process.waitFor();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        closed = true;
        synchronized (lock) {
            if (pending != null) {
                pending.complete(exitCode);
                pending = null;
                pendingMarker = null;
            }
            if (pendingCapture != null) {
                pendingCapture.completeExceptionally(
                        new IllegalStateException("The session ended."));
                pendingCapture = null;
                captureBuffer = null;
            }
        }
        // Only report an interpreter that stopped on its own; a session the
        // application deliberately closed needs no notice.
        if (!closeRequested) {
            listener.onClosed(exitCode);
        }
    }

    /** Reads a stream line by line, looking for the end-of-run marker. */
    private void pump(java.io.InputStream stream, boolean error) {
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(stream, charset))) {
            // cmd.exe terminates every command with a blank line before printing
            // its prompt, which would otherwise litter the console. A blank line
            // is therefore held back until the following line proves whether it
            // was real output or just that separator.
            String pendingBlank = null;
            String raw;
            while ((raw = reader.readLine()) != null) {
                if (promptToken != null && raw.isEmpty()) {
                    if (pendingBlank != null) {
                        emit(pendingBlank + System.lineSeparator(), error);
                    }
                    pendingBlank = raw;
                    continue;
                }
                if (pendingBlank != null) {
                    boolean beforePrompt = promptToken != null && raw.startsWith(promptToken);
                    if (!beforePrompt) {
                        emit(pendingBlank + System.lineSeparator(), error);
                    }
                    pendingBlank = null;
                }
                handleLine(raw, error);
            }
            if (pendingBlank != null) {
                emit(pendingBlank + System.lineSeparator(), error);
            }
        } catch (IOException e) {
            // The pipe closes when the interpreter exits; that is expected.
        }
    }

    private String stripPrompt(String line) {
        return promptToken == null ? line : line.replace(promptToken, "");
    }

    private void handleLine(String raw, boolean error) {
        // cmd.exe prints its prompt before every command, including once more
        // after the marker. Those lines carry no information, so drop them.
        if (!error && promptToken != null && raw.equals(promptToken)) {
            return;
        }
        String line = stripPrompt(raw);
        String marker = pendingMarker;
        if (!error && marker != null) {
            int index = line.indexOf(marker);
            if (index >= 0) {
                String before = line.substring(0, index);
                if (!before.isEmpty()) {
                    emit(before + System.lineSeparator(), false);
                }
                finishRun(line.substring(index + marker.length()).trim());
                return;
            }
        }
        emit(line + System.lineSeparator(), error);
    }

    private void emit(String text, boolean error) {
        if (text.isEmpty()) {
            return;
        }
        // A query keeps its output for the caller instead of showing it.
        if (!error && captureBuffer != null) {
            captureBuffer.append(text);
            return;
        }
        listener.onOutput(text, error);
    }

    /** Parses the marker payload and completes the pending run. */
    private void finishRun(String payload) {
        CompletableFuture<Integer> future;
        CompletableFuture<String> capture;
        String captured;
        synchronized (lock) {
            future = pending;
            pending = null;
            capture = pendingCapture;
            pendingCapture = null;
            captured = captureBuffer == null ? "" : captureBuffer.toString();
            captureBuffer = null;
            pendingMarker = null;
        }
        if (capture != null) {
            capture.complete(captured);
        }
        if (future == null) {
            return;
        }
        boolean success = true;
        int code = 0;
        if (payload.indexOf('|') >= 0) {
            // PowerShell reports "$?|<exit code>".
            String[] parts = payload.split("\\|", -1);
            success = !"False".equalsIgnoreCase(parts[0].trim());
            code = parseInt(parts.length > 1 ? parts[1].trim() : "0");
        } else {
            code = parseInt(payload);
            success = code == 0;
        }
        listener.onFinished(code, success);
        future.complete(code);
    }

    private static int parseInt(String value) {
        try {
            return Integer.parseInt(value.trim());
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    /**
     * Sends a script to the interpreter.
     *
     * <p>The returned future completes when the interpreter reports the exit
     * code, so callers must not block the event dispatch thread on it.</p>
     *
     * @param script one or more commands, separated by newlines
     * @return a future carrying the exit code
     * @throws IllegalStateException if the session is closed or already busy
     */
    public CompletableFuture<Integer> run(String script) {
        return submit(script, null);
    }

    /**
     * Runs a script whose output is returned instead of shown.
     *
     * <p>This is how the current directory is read back after a command: the
     * answer is needed for the location bar, but the command that produced it
     * should not appear in the console.</p>
     *
     * @param script the script to run
     * @return a future carrying the captured output
     * @throws IllegalStateException if the session is closed or already busy
     */
    public CompletableFuture<String> query(String script) {
        CompletableFuture<String> capture = new CompletableFuture<>();
        submit(script, capture);
        return capture;
    }

    /**
     * Sends a script to the interpreter.
     *
     * @param script  one or more commands, separated by newlines
     * @param capture when not {@code null}, output is collected for it rather
     *                than handed to the listener
     * @return a future carrying the exit code
     * @throws IllegalStateException if the session is closed or already busy
     */
    private CompletableFuture<Integer> submit(String script, CompletableFuture<String> capture) {
        CompletableFuture<Integer> future = new CompletableFuture<>();
        String marker;
        synchronized (lock) {
            if (closed) {
                throw new IllegalStateException("The " + kind.displayName() + " session has ended.");
            }
            if (pending != null) {
                throw new IllegalStateException("A command is still running in this tab.");
            }
            marker = "#M#" + sessionId + "#" + (++sequence) + "#";
            pendingMarker = marker;
            pending = future;
            if (capture != null) {
                pendingCapture = capture;
                captureBuffer = new StringBuilder();
            }
        }
        try {
            StringBuilder payload = new StringBuilder();
            if (script != null && !script.isEmpty()) {
                payload.append(script);
                if (script.charAt(script.length() - 1) != '\n') {
                    payload.append('\n');
                }
            }
            payload.append(markerCommand(marker)).append('\n');
            synchronized (lock) {
                stdin.write(payload.toString());
                stdin.flush();
            }
        } catch (IOException e) {
            synchronized (lock) {
                pending = null;
                pendingMarker = null;
                pendingCapture = null;
                captureBuffer = null;
            }
            if (capture != null) {
                capture.completeExceptionally(e);
            }
            listener.onFailure("Could not write to the interpreter: " + e.getMessage());
            future.completeExceptionally(e);
        }
        return future;
    }

    private String markerCommand(String marker) {
        if (kind == ShellKind.CMD) {
            return "echo " + marker + "%errorlevel%";
        }
        // "$?" covers cmdlet failures, $LASTEXITCODE covers native programs.
        return "Write-Output ('" + marker + "' + $? + '|' + [int]$LASTEXITCODE)";
    }

    /** @return the interpreter this session drives. */
    public ShellKind kind() {
        return kind;
    }

    /** @return the charset used on the session's standard streams. */
    public Charset charset() {
        return charset;
    }

    /** @return {@code true} while a script has been submitted but not finished. */
    public boolean isBusy() {
        synchronized (lock) {
            return pending != null;
        }
    }

    /** @return {@code true} while the interpreter process is running. */
    public boolean isAlive() {
        return !closed && process.isAlive();
    }

    /** @return the process id, useful for diagnostics. */
    public long pid() {
        return process.pid();
    }

    /** Kills the interpreter and releases its streams. */
    @Override
    public void close() {
        closeRequested = true;
        closed = true;
        process.destroy();
        try {
            if (!process.waitFor(2, java.util.concurrent.TimeUnit.SECONDS)) {
                process.destroyForcibly();
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            process.destroyForcibly();
        }
        try {
            stdin.close();
        } catch (IOException ignored) {
            // The pipe is already gone.
        }
    }
}
