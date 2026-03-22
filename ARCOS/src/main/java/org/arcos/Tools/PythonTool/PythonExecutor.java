package org.arcos.Tools.PythonTool;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

@Slf4j
@Component
public class PythonExecutor {

    public static class ExecutionResult {
        private final int exitCode;
        private final String stdout;
        private final String stderr;

        public ExecutionResult(int exitCode, String stdout, String stderr) {
            this.exitCode = exitCode;
            this.stdout = stdout;
            this.stderr = stderr;
        }

        public int getExitCode() { return exitCode; }
        public String getStdout() { return stdout; }
        public String getStderr() { return stderr; }
        public boolean isSuccess() { return exitCode == 0; }
    }

    private static final int TIMEOUT_SECONDS = 30;
    private static final int MAX_OUTPUT_BYTES = 4096;
    private static final boolean HAS_LIB64 = Files.exists(Path.of("/lib64"));
    private static final boolean HAS_ETC_ALTERNATIVES = Files.exists(Path.of("/etc/alternatives"));

    private static volatile Boolean sandboxAvailable;

    public boolean isSandboxAvailable() {
        if (sandboxAvailable == null) {
            synchronized (PythonExecutor.class) {
                if (sandboxAvailable == null) {
                    try {
                        // Check bwrap is installed AND can actually execute
                        Process check = new ProcessBuilder("bwrap", "--ro-bind", "/", "/", "true").start();
                        sandboxAvailable = check.waitFor(5, TimeUnit.SECONDS) && check.exitValue() == 0;
                    } catch (IOException | InterruptedException e) {
                        sandboxAvailable = false;
                    }
                    if (!sandboxAvailable) {
                        log.warn("bubblewrap (bwrap) unavailable or lacking permissions — Python tool disabled");
                    }
                }
            }
        }
        return sandboxAvailable;
    }

    public ExecutionResult execute(String code) {
        if (!isSandboxAvailable()) {
            return new ExecutionResult(-1, "", "Sandbox indisponible : bubblewrap (bwrap) non installé.");
        }

        Path scriptFile = null;
        Process process = null;
        try {
            scriptFile = Files.createTempFile("arcos-python-", ".py");
            Files.writeString(scriptFile, code);

            List<String> command = buildSandboxCommand(scriptFile);
            log.debug("Python sandbox command: {}", String.join(" ", command));
            ProcessBuilder pb = new ProcessBuilder(command);
            pb.redirectErrorStream(true);
            process = pb.start();

            // Drain output BEFORE waitFor to prevent pipe buffer deadlock
            String output = readOutput(process.getInputStream(), MAX_OUTPUT_BYTES);

            boolean finished = process.waitFor(TIMEOUT_SECONDS, TimeUnit.SECONDS);
            if (!finished) {
                process.destroyForcibly();
                return new ExecutionResult(-1, "", "Timeout après " + TIMEOUT_SECONDS + " secondes.");
            }

            int exitCode = process.exitValue();
            if (exitCode != 0) {
                log.warn("Python execution failed with exit code {}: {}", exitCode, output.substring(0, Math.min(200, output.length())));
            }
            return new ExecutionResult(exitCode, output, "");

        } catch (IOException | InterruptedException e) {
            if (process != null) process.destroyForcibly();
            return new ExecutionResult(-1, "", e.getMessage());
        } finally {
            if (scriptFile != null) {
                try { Files.deleteIfExists(scriptFile); } catch (IOException ignored) {}
            }
        }
    }

    public List<String> buildSandboxCommand(Path scriptFile) {
        List<String> cmd = new ArrayList<>();
        cmd.add("bwrap");

        // Read-only bind mounts for Python runtime
        cmd.addAll(List.of("--ro-bind", "/usr", "/usr"));
        cmd.addAll(List.of("--ro-bind", "/bin", "/bin"));
        cmd.addAll(List.of("--ro-bind", "/lib", "/lib"));

        // /lib64 symlink if it exists (x86_64)
        if (HAS_LIB64) {
            cmd.addAll(List.of("--symlink", "usr/lib64", "/lib64"));
        }

        // /etc/alternatives for python3 symlink resolution
        if (HAS_ETC_ALTERNATIVES) {
            cmd.addAll(List.of("--ro-bind", "/etc/alternatives", "/etc/alternatives"));
        }

        // Ephemeral writable /tmp
        cmd.addAll(List.of("--tmpfs", "/tmp"));

        // Minimal /dev and /proc
        cmd.addAll(List.of("--dev", "/dev"));
        cmd.addAll(List.of("--proc", "/proc"));

        // Isolation
        cmd.add("--unshare-pid");
        if (canUnshareNet()) {
            cmd.add("--unshare-net");
        }
        if (canUnshareUser()) {
            cmd.add("--unshare-user");
        }

        // Bind-mount script read-only
        cmd.addAll(List.of("--ro-bind", scriptFile.toAbsolutePath().toString(), "/tmp/script.py"));

        // Execute
        cmd.addAll(List.of("python3", "/tmp/script.py"));

        return cmd;
    }

    private static final Map<String, Boolean> capabilityCache = new ConcurrentHashMap<>();

    private boolean probeBwrapCapability(String flag) {
        return capabilityCache.computeIfAbsent(flag, f -> {
            try {
                Process test = new ProcessBuilder("bwrap", f, "--ro-bind", "/", "/", "true").start();
                boolean supported = test.waitFor(5, TimeUnit.SECONDS) && test.exitValue() == 0;
                if (!supported) {
                    log.info("bwrap {} not supported in this environment, skipping", f);
                }
                return supported;
            } catch (IOException | InterruptedException e) {
                log.info("bwrap {} not supported in this environment, skipping", f);
                return false;
            }
        });
    }

    private boolean canUnshareNet() {
        return probeBwrapCapability("--unshare-net");
    }

    private boolean canUnshareUser() {
        return probeBwrapCapability("--unshare-user");
    }

    public String readOutput(InputStream inputStream, int maxBytes) throws IOException {
        StringBuilder output = new StringBuilder();
        int totalBytes = 0;
        boolean truncated = false;

        try (BufferedReader reader = new BufferedReader(new InputStreamReader(inputStream))) {
            String line;
            while ((line = reader.readLine()) != null) {
                int lineBytes = line.length() + 1;
                if (totalBytes + lineBytes > maxBytes) {
                    int remaining = maxBytes - totalBytes;
                    if (remaining > 0) {
                        output.append(line, 0, Math.min(remaining, line.length()));
                    }
                    truncated = true;
                    break;
                }
                output.append(line).append("\n");
                totalBytes += lineBytes;
            }
        }

        if (truncated) {
            output.append("\n[sortie tronquée à ").append(maxBytes / 1024).append(" Ko]");
        }

        return output.toString();
    }
}
