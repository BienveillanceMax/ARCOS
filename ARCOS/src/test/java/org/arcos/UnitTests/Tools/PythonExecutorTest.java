package org.arcos.UnitTests.Tools;

import org.arcos.Tools.PythonTool.PythonExecutor;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class PythonExecutorTest {

    private PythonExecutor executor;

    @BeforeEach
    void setUp() {
        executor = new PythonExecutor();
    }

    // ===== Sandbox command construction =====

    @Test
    void buildSandboxCommand_shouldContainBwrapWithIsolationFlags() {
        // Given
        Path scriptFile = Path.of("/tmp/test-script.py");

        // When
        List<String> cmd = executor.buildSandboxCommand(scriptFile);

        // Then
        assertThat(cmd.get(0)).isEqualTo("bwrap");
        assertThat(cmd).contains("--unshare-net", "--unshare-pid", "--unshare-user");
    }

    @Test
    void buildSandboxCommand_shouldBindMountSystemDirsReadOnly() {
        // Given
        Path scriptFile = Path.of("/tmp/test-script.py");

        // When
        List<String> cmd = executor.buildSandboxCommand(scriptFile);

        // Then
        assertThat(cmd).containsSequence("--ro-bind", "/usr", "/usr");
        assertThat(cmd).containsSequence("--ro-bind", "/bin", "/bin");
        assertThat(cmd).containsSequence("--ro-bind", "/lib", "/lib");
    }

    @Test
    void buildSandboxCommand_shouldMountEphemeralTmp() {
        // Given
        Path scriptFile = Path.of("/tmp/test-script.py");

        // When
        List<String> cmd = executor.buildSandboxCommand(scriptFile);

        // Then
        assertThat(cmd).containsSequence("--tmpfs", "/tmp");
    }

    @Test
    void buildSandboxCommand_shouldBindMountScriptReadOnly() {
        // Given
        Path scriptFile = Path.of("/tmp/arcos-python-123.py");

        // When
        List<String> cmd = executor.buildSandboxCommand(scriptFile);

        // Then
        assertThat(cmd).containsSequence("--ro-bind", "/tmp/arcos-python-123.py", "/tmp/script.py");
    }

    @Test
    void buildSandboxCommand_shouldEndWithPython3Invocation() {
        // Given
        Path scriptFile = Path.of("/tmp/test-script.py");

        // When
        List<String> cmd = executor.buildSandboxCommand(scriptFile);

        // Then
        int size = cmd.size();
        assertThat(cmd.get(size - 2)).isEqualTo("python3");
        assertThat(cmd.get(size - 1)).isEqualTo("/tmp/script.py");
    }

    @Test
    void buildSandboxCommand_shouldIncludeDevAndProc() {
        // Given
        Path scriptFile = Path.of("/tmp/test-script.py");

        // When
        List<String> cmd = executor.buildSandboxCommand(scriptFile);

        // Then
        assertThat(cmd).containsSequence("--dev", "/dev");
        assertThat(cmd).containsSequence("--proc", "/proc");
    }

    // ===== Execute without sandbox =====

    @Test
    void execute_whenSandboxUnavailable_shouldReturnFailure() {
        // Given — on a real CI/dev machine bwrap may or may not be present
        // We test the error message format regardless
        PythonExecutor testExecutor = new PythonExecutor() {
            @Override
            public boolean isSandboxAvailable() {
                return false;
            }
        };

        // When
        PythonExecutor.ExecutionResult result = testExecutor.execute("print('hello')");

        // Then
        assertThat(result.isSuccess()).isFalse();
        assertThat(result.getExitCode()).isEqualTo(-1);
        assertThat(result.getStderr()).contains("bubblewrap");
    }
}
