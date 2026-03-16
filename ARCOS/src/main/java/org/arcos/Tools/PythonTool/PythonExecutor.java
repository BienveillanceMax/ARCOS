package org.arcos.Tools.PythonTool;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
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

    private Boolean sandboxAvailable;

    public boolean isSandboxAvailable() {
        if (sandboxAvailable == null) {
            try {
                Process check = new ProcessBuilder("which", "bwrap").start();
                sandboxAvailable = check.waitFor(5, TimeUnit.SECONDS) && check.exitValue() == 0;
            } catch (IOException | InterruptedException e) {
                sandboxAvailable = false;
            }
            if (!sandboxAvailable) {
                log.warn("bubblewrap (bwrap) not found — Python tool disabled");
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
            ProcessBuilder pb = new ProcessBuilder(command);
            pb.redirectErrorStream(true);
            process = pb.start();

            boolean finished = process.waitFor(TIMEOUT_SECONDS, TimeUnit.SECONDS);
            if (!finished) {
                process.destroyForcibly();
                return new ExecutionResult(-1, "", "Timeout après " + TIMEOUT_SECONDS + " secondes.");
            }

            String output = readOutput(process, MAX_OUTPUT_BYTES);
            return new ExecutionResult(process.exitValue(), output, "");

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
        if (Files.exists(Path.of("/lib64"))) {
            cmd.addAll(List.of("--symlink", "usr/lib64", "/lib64"));
        }

        // /etc/alternatives for python3 symlink resolution
        if (Files.exists(Path.of("/etc/alternatives"))) {
            cmd.addAll(List.of("--ro-bind", "/etc/alternatives", "/etc/alternatives"));
        }

        // Ephemeral writable /tmp
        cmd.addAll(List.of("--tmpfs", "/tmp"));

        // Minimal /dev and /proc
        cmd.addAll(List.of("--dev", "/dev"));
        cmd.addAll(List.of("--proc", "/proc"));

        // Isolation
        cmd.add("--unshare-net");
        cmd.add("--unshare-pid");
        cmd.add("--unshare-user");

        // Bind-mount script read-only
        cmd.addAll(List.of("--ro-bind", scriptFile.toAbsolutePath().toString(), "/tmp/script.py"));

        // Execute
        cmd.addAll(List.of("python3", "/tmp/script.py"));

        return cmd;
    }

    private String readOutput(Process process, int maxBytes) throws IOException {
        StringBuilder output = new StringBuilder();
        int totalBytes = 0;
        boolean truncated = false;

        try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
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
