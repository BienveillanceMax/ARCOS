package Tools.PythonTool;

import org.springframework.stereotype.Component;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.IOException;

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

    public ExecutionResult execute(String code) {
        try {
            ProcessBuilder pb = new ProcessBuilder("python", "-c", code);
            Process process = pb.start();

            StringBuilder stdout = new StringBuilder();
            StringBuilder stderr = new StringBuilder();

            try (BufferedReader stdoutReader = new BufferedReader(new InputStreamReader(process.getInputStream()));
                 BufferedReader stderrReader = new BufferedReader(new InputStreamReader(process.getErrorStream()))) {

                String line;
                while ((line = stdoutReader.readLine()) != null) {
                    stdout.append(line).append("\n");
                }

                while ((line = stderrReader.readLine()) != null) {
                    stderr.append(line).append("\n");
                }
            }

            int exitCode = process.waitFor();

            return new ExecutionResult(exitCode, stdout.toString(), stderr.toString());

        } catch (IOException | InterruptedException e) {
            return new ExecutionResult(-1, "", e.getMessage());
        }
    }
}
