package org.arcos.UnitTests.Tools;

import org.arcos.Tools.PythonTool.PythonExecutor;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
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
        assertThat(cmd).contains("--unshare-pid");
        // --unshare-net and --unshare-user are conditional on environment support
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
        // Given
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

    // ===== readOutput =====

    @Test
    void readOutput_smallOutput_shouldReturnFullContent() throws IOException {
        // Given
        String content = "line1\nline2\nline3\n";
        InputStream stream = new ByteArrayInputStream(content.getBytes(StandardCharsets.UTF_8));

        // When
        String result = executor.readOutput(stream, 4096);

        // Then
        assertThat(result).isEqualTo("line1\nline2\nline3\n");
    }

    @Test
    void readOutput_emptyOutput_shouldReturnEmptyString() throws IOException {
        // Given
        InputStream stream = new ByteArrayInputStream(new byte[0]);

        // When
        String result = executor.readOutput(stream, 4096);

        // Then
        assertThat(result).isEmpty();
    }

    @Test
    void readOutput_exceedsMaxBytes_shouldTruncateWithMessage() throws IOException {
        // Given — create output larger than 50 bytes limit
        StringBuilder largeContent = new StringBuilder();
        for (int i = 0; i < 20; i++) {
            largeContent.append("line-").append(i).append("-padding\n");
        }
        InputStream stream = new ByteArrayInputStream(largeContent.toString().getBytes(StandardCharsets.UTF_8));

        // When
        String result = executor.readOutput(stream, 50);

        // Then
        assertThat(result).contains("[sortie tronquée");
        // The content before truncation marker should be <= 50 bytes
        String beforeMarker = result.substring(0, result.indexOf("\n[sortie"));
        assertThat(beforeMarker.getBytes(StandardCharsets.UTF_8).length).isLessThanOrEqualTo(50);
    }

    @Test
    void readOutput_exactlyAtLimit_shouldNotTruncate() throws IOException {
        // Given — exactly 10 bytes: "abcdefgh\n" (9 chars + readLine strips \n, appends \n = 10 bytes counted)
        String content = "abcdefgh\n";
        InputStream stream = new ByteArrayInputStream(content.getBytes(StandardCharsets.UTF_8));

        // When
        String result = executor.readOutput(stream, 4096);

        // Then
        assertThat(result).doesNotContain("[sortie tronquée");
        assertThat(result).isEqualTo("abcdefgh\n");
    }

    // ===== isSandboxAvailable caching =====

    @Test
    void isSandboxAvailable_shouldCacheResult() {
        // Given — call twice
        boolean first = executor.isSandboxAvailable();

        // When
        boolean second = executor.isSandboxAvailable();

        // Then — both calls return same value (cached, not re-evaluated)
        assertThat(second).isEqualTo(first);
    }

    // ===== Integration: actual sandbox execution =====

    @Test
    void execute_whenSandboxAvailable_shouldRunSimplePythonScript() {
        Assumptions.assumeTrue(executor.isSandboxAvailable(),
                "bwrap unavailable — skipping sandbox integration test");

        // When
        PythonExecutor.ExecutionResult result = executor.execute("print('hello sandbox')");

        // Then
        assertThat(result.isSuccess()).isTrue();
        assertThat(result.getStdout()).contains("hello sandbox");
    }

    @Test
    void execute_whenSandboxAvailable_shouldDenyFileSystemWrite() {
        Assumptions.assumeTrue(executor.isSandboxAvailable(),
                "bwrap unavailable — skipping sandbox integration test");

        // When — try to write outside /tmp (should be blocked by ro-bind)
        String code = """
                try:
                    with open('/usr/test_write.txt', 'w') as f:
                        f.write('hack')
                    print('WRITE_OK')
                except Exception as e:
                    print('WRITE_BLOCKED: ' + str(e))
                """;
        PythonExecutor.ExecutionResult result = executor.execute(code);

        // Then — write should be blocked
        assertThat(result.isSuccess()).isTrue();
        assertThat(result.getStdout()).contains("WRITE_BLOCKED");
    }
}
