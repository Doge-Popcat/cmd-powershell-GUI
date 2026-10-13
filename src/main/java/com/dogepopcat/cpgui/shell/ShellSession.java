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

    /** How long a run may stall before it is treated as waiting for input. */
    private static final long INTERACTIVE_AFTER_MS = 900;
    /** How long to wait after an answer before closing the run. */
    private static final long INPUT_SETTLE_MS = 600;

    /** Receives everything a session produces. Called from reader threads. */
    public interface Listener {

        /** @param error {@code true} when the text came from the error stream */
        void onOutput(String text, boolean error);

        /** Called once per submitted script, after the marker was seen. */
        void onFinished(int exitCode, boolean success);

        /**
         * Called when a script has stalled and looks like it is waiting for an
         * answer, as {@code time} or {@code set /p} do.
         *
         * <p>The console switches to passing lines straight through at that
         * point, instead of queueing them as new commands.</p>
         */
        void onAwaitingInput();

        /** Called when the interpreter process ended. */
        void onClosed(int exitCode);

        /** Called when a script could not be handed to the interpreter. */
        void onFailure(String message);
    }

    private final ShellKind kind;
    private final Charset charset;
    private final Process process;
    private final BufferedWriter stdin;
    /** The active receiver. Replaced whenever a session is reused for a run. */
    private volatile Listener listener;
    private final String sessionId;
    private final String promptToken;
    private final Object lock = new Object();

    /** Scripts waiting for the interpreter, in submission order. */
    private final java.util.ArrayDeque<Job> queue = new java.util.ArrayDeque<>();

    private CompletableFuture<Integer> pending;
    private CompletableFuture<String> pendingCapture;
    private StringBuilder captureBuffer;
    private String pendingMarker;
    private long sequence;
    /** Set while the interpreter looks like it is waiting for an answer. */
    private volatile boolean awaitingInput;
    /** Set once any output has arrived for the run in progress. */
    private volatile boolean runProducedOutput;
    /** Set while the script being written is a known interactive command. */
    private boolean interactiveScript;
    /** Set when the caller must be told the interpreter wants an answer. */
    private boolean notifyAwaitingInput;
    private final java.util.concurrent.ScheduledExecutorService watchdogs =
            java.util.concurrent.Executors.newSingleThreadScheduledExecutor(runnable -> {
                Thread thread = new Thread(runnable, "shell-watchdog");
                thread.setDaemon(true);
                return thread;
            });
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
        watchdogs.shutdownNow();
        synchronized (lock) {
            queue.clear();
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
        if (!error) {
            runProducedOutput = true;
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

        if (capture != null) {
            capture.complete(captured);
        }
        if (future != null) {
            future.complete(code);
        }
        // A query is bookkeeping rather than something the user asked to run, so
        // it must not raise a finish notification. Doing so would let a caller
        // that queries on finish recurse for ever.
        if (capture == null) {
            listener.onFinished(code, success);
        }
        synchronized (lock) {
            if (!closed) {
                startNextLocked();
            }
        }
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
        CompletableFuture<Integer> future = new CompletableFuture<>();
        enqueue(new Job(script, future, null));
        return future;
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
     */
    public CompletableFuture<String> query(String script) {
        CompletableFuture<String> capture = new CompletableFuture<>();
        enqueue(new Job(script, new CompletableFuture<>(), capture));
        return capture;
    }

    /** One script waiting to be written to the interpreter. */
    private record Job(String script, CompletableFuture<Integer> future,
                       CompletableFuture<String> capture) {
    }

    /**
     * Adds a script to the queue and starts it when the interpreter is free.
     *
     * <p>Requests are queued rather than rejected. Reading the working directory
     * back after a run occupies the interpreter for a few milliseconds, and
     * refusing a run that lands in that window would silently do nothing, which
     * is exactly what happened before.</p>
     */
    private void enqueue(Job job) {
        synchronized (lock) {
            if (closed) {
                throw new IllegalStateException("The " + kind.displayName() + " session has ended.");
            }
            queue.addLast(job);
            if (pending == null) {
                startNextLocked();
            }
        }
        if (notifyAwaitingInput) {
            notifyAwaitingInput = false;
            listener.onAwaitingInput();
        }
    }

    /**
     * @param script the script about to run
     * @return {@code true} when it is a command that asks the user a question
     */
    private static boolean looksInteractive(String script) {
        if (script == null || script.isBlank()) {
            return false;
        }
        String lowered = script.toLowerCase(java.util.Locale.ROOT);
        // Anything that reads a value from the console.
        if (lowered.contains("set /p") || lowered.contains("set/p")) {
            return true;
        }
        if (lowered.contains("pause") || lowered.contains("choice ")
                || lowered.contains("diskpart") || lowered.contains("ftp ")) {
            return true;
        }
        for (String line : lowered.split("\\R")) {
            String first = line.strip().split("\\s+")[0];
            if (first.equals("time") || first.equals("date") || first.equals("more")
                    || first.equals("sort") || first.equals("pause")
                    || first.equals("choice") || first.equals("diskpart")) {
                return true;
            }
        }
        return false;
    }

    /** Writes the next queued script. The caller must hold {@link #lock}. */
    private void startNextLocked() {
        Job job = queue.pollFirst();
        if (job == null) {
            return;
        }
        String marker = "#M#" + sessionId + "#" + (++sequence) + "#";
        pendingMarker = marker;
        pending = job.future();
        runProducedOutput = false;
        awaitingInput = false;
        interactiveScript = looksInteractive(job.script());
        notifyAwaitingInput = false;
        if (job.capture() != null) {
            pendingCapture = job.capture();
            captureBuffer = new StringBuilder();
        }
        try {
            StringBuilder payload = new StringBuilder();
            String script = job.script();
            if (script != null && !script.isEmpty()) {
                payload.append(script);
                if (script.charAt(script.length() - 1) != '\n') {
                    payload.append('\n');
                }
            }
            if (interactiveScript) {
                // An interactive command reads its answer from the same input
                // stream, so the marker would be read as that answer: answering
                // time with the marker echo is what produced "the system cannot
                // accept the input time". The marker is sent once the answer has
                // been dealt with instead.
                awaitingInput = true;
                interactiveScript = false;
                stdin.write(payload.toString());
                stdin.flush();
                notifyAwaitingInput = true;
            } else {
                payload.append(markerCommand(marker)).append('\n');
                stdin.write(payload.toString());
                stdin.flush();
            }
        } catch (IOException e) {
            pending = null;
            pendingMarker = null;
            CompletableFuture<String> capture = pendingCapture;
            pendingCapture = null;
            captureBuffer = null;
            if (capture != null) {
                capture.completeExceptionally(e);
            }
            job.future().completeExceptionally(e);
            listener.onFailure("Could not write to the interpreter: " + e.getMessage());
        }
    }

    // --- interactive input --------------------------------------------------

    /**
     * Starts the timer that decides a script is waiting for an answer.
     *
     * <p>An interactive command such as {@code time} prints its prompt without a
     * trailing newline and then blocks, so the reader never sees a complete line
     * and nothing else can tell us it is waiting. A stall after output has
     * arrived is the signal.</p>
     */
    private void scheduleInteractiveCheck() {
        if (closed) {
            return;
        }
        try {
            watchdogs.schedule(this::checkInteractive, INTERACTIVE_AFTER_MS,
                    java.util.concurrent.TimeUnit.MILLISECONDS);
        } catch (java.util.concurrent.RejectedExecutionException ignored) {
            // The session closed between the check and the scheduling.
        }
    }

    private void checkInteractive() {
        synchronized (lock) {
            if (closed || pending == null || awaitingInput || !runProducedOutput) {
                return;
            }
            awaitingInput = true;
        }
        listener.onAwaitingInput();
    }

    /** @return {@code true} while the interpreter is waiting for an answer. */
    public boolean isAwaitingInput() {
        return awaitingInput && !closed;
    }

    /**
     * Passes a line straight to the interpreter, without a marker.
     *
     * <p>This is what an answer to an interactive prompt is. A marker cannot be
     * appended, because the interpreter would read it as the answer, which is
     * exactly what happened before: {@code time} was answered with the marker
     * echo and reported an unusable time.</p>
     *
     * @param line the text to send, without a newline
     */
    public void sendInput(String line) {
        if (closed) {
            return;
        }
        try {
            synchronized (lock) {
                stdin.write((line == null ? "" : line) + "\n");
                stdin.flush();
                runProducedOutput = false;
            }
        } catch (IOException e) {
            listener.onFailure("Could not write to the interpreter: " + e.getMessage());
            return;
        }
        scheduleInputSettledCheck();
    }

    /**
     * Closes the run once the answer has been dealt with.
     *
     * <p>When the interpreter finished with the answer it goes back to its
     * prompt and blocks, so the end-of-run marker that was sent with the
     * original command has already been consumed. A fresh marker is sent to
     * learn the exit code, but only if the command has gone quiet: if it asked
     * another question the marker would be swallowed as that answer.</p>
     */
    private void scheduleInputSettledCheck() {
        if (closed) {
            return;
        }
        try {
            watchdogs.schedule(() -> {
                synchronized (lock) {
                    if (closed || pending == null || !awaitingInput) {
                        return;
                    }
                    // The marker is sent whatever the command printed in the
                    // meantime. Waiting for silence looked safer, but finishing
                    // the answer always prints something, so the run was never
                    // closed at all.
                    pendingMarker = "#M#" + sessionId + "#" + (++sequence) + "#";
                    try {
                        stdin.write(markerCommand(pendingMarker) + "\n");
                        stdin.flush();
                    } catch (IOException e) {
                        listener.onFailure("Could not write to the interpreter: "
                                + e.getMessage());
                    }
                }
            }, INPUT_SETTLE_MS, java.util.concurrent.TimeUnit.MILLISECONDS);
        } catch (java.util.concurrent.RejectedExecutionException ignored) {
            // The session is closing.
        }
    }

    private String markerCommand(String marker) {
        if (kind == ShellKind.CMD) {
            return "echo " + marker + "%errorlevel%";
        }
        // "$?" covers cmdlet failures, $LASTEXITCODE covers native programs.
        return "Write-Output ('" + marker + "' + $? + '|' + [int]$LASTEXITCODE)";
    }

    /**
     * Replaces the receiver.
     *
     * <p>A session is reused across runs, and each run may want to report its
     * outcome somewhere different, so the listener has to be swappable. Without
     * this, every run after the first would still report to the first run's
     * listener.</p>
     *
     * @param listener the new receiver
     */
    public void setListener(Listener listener) {
        if (listener != null) {
            this.listener = listener;
        }
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
            return pending != null || !queue.isEmpty();
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
