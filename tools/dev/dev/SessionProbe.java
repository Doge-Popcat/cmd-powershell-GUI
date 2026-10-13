package dev;

import com.dogepopcat.cpgui.shell.SessionRegistry;
import com.dogepopcat.cpgui.shell.ShellKind;
import com.dogepopcat.cpgui.shell.ShellSession;

import java.io.File;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

/**
 * Developer check for the shell session engine.
 *
 * <p>Runs read-only commands through real interpreters and prints exactly what
 * comes back, which is how the code page, prompt marker and exit-code protocol
 * were confirmed. Every command used here is safe to run.</p>
 *
 * <pre>
 *   java -cp "build/classes;build/tools" dev.SessionProbe
 * </pre>
 */
public final class SessionProbe {

    private static final StringBuilder LOG = new StringBuilder();

    private SessionProbe() {
    }

    public static void main(String[] args) throws Exception {
        File work = new File(System.getProperty("user.dir"));

        check("cmd basic + exit codes", ShellKind.CMD, work, new String[]{
                "echo HELLO-\u4e2d\u6587-END",
                "dir /b nonexistent_cpgui_probe 2>&1",
                "cd",
        });
        check("cmd state persistence", ShellKind.CMD, work, new String[]{
                "set CPGUI_PROBE=kept",
                "echo VAR=%CPGUI_PROBE%",
        });
        check("cmd for loop", ShellKind.CMD, work, new String[]{
                "for %i in (a b c) do @echo item-%i",
        });
        check("powershell basic + exit codes", ShellKind.POWERSHELL, work, new String[]{
                "echo HELLO-\u4e2d\u6587-END",
                "Get-Item 'nonexistent_cpgui_probe' -ErrorAction SilentlyContinue",
                "(Get-Location).Path",
        });
        check("powershell state persistence", ShellKind.POWERSHELL, work, new String[]{
                "$script:CPGUI_PROBE = 'kept'",
                "Write-Output ('VAR=' + $script:CPGUI_PROBE)",
        });
    }

    private static void check(String title, ShellKind kind, File work, String[] scripts)
            throws Exception {
        System.out.println("==================================================");
        System.out.println(title + "  [" + kind + "]");
        SessionRegistry registry = new SessionRegistry();
        CountDownLatch latch = new CountDownLatch(scripts.length);

        ShellSession.Listener listener = new ShellSession.Listener() {
            @Override
            public void onOutput(String text, boolean error) {
                LOG.append("  ").append(error ? "ERR| " : "OUT| ").append(text.replace("\r", ""));
            }

            @Override
            public void onAwaitingInput() {
                LOG.append("  -- waiting for input\n");
            }

            @Override
            public void onFinished(int exitCode, boolean success) {
                LOG.append("  --> finished exit=").append(exitCode)
                        .append(" success=").append(success).append('\n');
                latch.countDown();
            }

            @Override
            public void onClosed(int exitCode) {
                LOG.append("  --> session closed ").append(exitCode).append('\n');
            }

            @Override
            public void onFailure(String message) {
                LOG.append("  --> FAILURE ").append(message).append('\n');
            }
        };

        ShellSession session = registry.acquire("probe", kind, work, listener);
        System.out.println("  pid=" + session.pid() + " charset=" + session.charset().name());

        for (String script : scripts) {
            LOG.append("  $ ").append(script).append('\n');
            session.run(script);
            Thread.sleep(500);
        }
        latch.await(10, TimeUnit.SECONDS);

        System.out.print(LOG);
        System.out.println("  unfinished: " + latch.getCount());
        LOG.setLength(0);
        registry.closeAll();
        System.out.println();
    }
}
