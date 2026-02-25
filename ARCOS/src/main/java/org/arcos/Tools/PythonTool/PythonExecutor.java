package org.arcos.Tools.PythonTool;

import org.springframework.stereotype.Component;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.IOException;
import java.util.concurrent.TimeUnit;

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

        public int getExitCode() {
            return exitCode;
        }

        public String getStdout() {
            return stdout;
        }

        public String getStderr() {
            return stderr;
        }

        public boolean isSuccess() {
            return exitCode == 0;
        }
    }

    private static final int TIMEOUT_SECONDS = 30;

    public ExecutionResult execute(String code) {
        Process process = null;
        try {
            ProcessBuilder pb = new ProcessBuilder("python3", "-c", code);
            // Merge stderr into stdout to avoid pipe-buffer deadlock from reading streams sequentially
            pb.redirectErrorStream(true);
            process = pb.start();

            // Wait for process completion with timeout
            boolean finished = process.waitFor(TIMEOUT_SECONDS, TimeUnit.SECONDS);
            if (!finished) {
                process.destroyForcibly();
                return new ExecutionResult(-1, "", "Process timed out after " + TIMEOUT_SECONDS + " seconds");
            }

            // Process has exited — safe to drain output
            StringBuilder output = new StringBuilder();
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    output.append(line).append("\n");
                }
            }

            return new ExecutionResult(process.exitValue(), output.toString(), "");

        } catch (IOException | InterruptedException e) {
            if (process != null) process.destroyForcibly();
            return new ExecutionResult(-1, "", e.getMessage());
        }
    }
}
