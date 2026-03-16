package org.arcos.UnitTests.Tools;

import org.arcos.IO.OuputHandling.StateHandler.CentralFeedBackHandler;
import org.arcos.IO.OuputHandling.StateHandler.FeedBackEvent;
import org.arcos.IO.OuputHandling.StateHandler.UXEventType;
import org.arcos.Tools.Actions.ActionResult;
import org.arcos.Tools.Actions.PythonActions;
import org.arcos.Tools.PythonTool.PythonExecutor;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class PythonActionsTest {

    @Mock
    private PythonExecutor pythonExecutor;

    @Mock
    private CentralFeedBackHandler centralFeedBackHandler;

    private PythonActions pythonActions;

    @BeforeEach
    void setUp() {
        pythonActions = new PythonActions(pythonExecutor, centralFeedBackHandler);
    }

    // ===== Success path =====

    @Test
    void executePythonCode_success_shouldReturnSuccessResult() {
        // Given
        when(pythonExecutor.execute("print(42)"))
                .thenReturn(new PythonExecutor.ExecutionResult(0, "42\n", ""));

        // When
        ActionResult result = pythonActions.executePythonCode("print(42)");

        // Then
        assertThat(result.isSuccess()).isTrue();
        assertThat(result.getMessage()).isEqualTo("Code exécuté avec succès.");
        assertThat(result.getData()).isEqualTo(List.of("42\n"));
        assertThat(result.getMetadata()).containsEntry("exit_code", 0);
    }

    // ===== Failure path =====

    @Test
    void executePythonCode_failure_shouldReturnFailureResult() {
        // Given
        when(pythonExecutor.execute("invalid"))
                .thenReturn(new PythonExecutor.ExecutionResult(1, "", "SyntaxError: invalid syntax"));

        // When
        ActionResult result = pythonActions.executePythonCode("invalid");

        // Then
        assertThat(result.isSuccess()).isFalse();
        assertThat(result.getMessage()).contains("SyntaxError");
        assertThat(result.getMetadata()).containsEntry("exit_code", 1);
    }

    @Test
    void executePythonCode_failure_shouldIncludePartialStdoutInMetadata() {
        // Given
        when(pythonExecutor.execute(any()))
                .thenReturn(new PythonExecutor.ExecutionResult(1, "partial output", "then error"));

        // When
        ActionResult result = pythonActions.executePythonCode("code");

        // Then
        assertThat(result.isSuccess()).isFalse();
        assertThat(result.getMetadata()).containsEntry("stdout", "partial output");
    }

    // ===== UX Feedback =====

    @Test
    void executePythonCode_shouldSendLongTaskStartAndEnd() {
        // Given
        when(pythonExecutor.execute(any()))
                .thenReturn(new PythonExecutor.ExecutionResult(0, "", ""));

        // When
        pythonActions.executePythonCode("print('test')");

        // Then
        ArgumentCaptor<FeedBackEvent> captor = ArgumentCaptor.forClass(FeedBackEvent.class);
        verify(centralFeedBackHandler, times(2)).handleFeedBack(captor.capture());
        List<FeedBackEvent> events = captor.getAllValues();
        assertThat(events.get(0).getEventType()).isEqualTo(UXEventType.LONGTASK_START);
        assertThat(events.get(1).getEventType()).isEqualTo(UXEventType.LONGTASK_END);
    }

    @Test
    void executePythonCode_shouldSendLongTaskEndEvenOnFailure() {
        // Given
        when(pythonExecutor.execute(any()))
                .thenReturn(new PythonExecutor.ExecutionResult(1, "", "error"));

        // When
        pythonActions.executePythonCode("bad code");

        // Then
        ArgumentCaptor<FeedBackEvent> captor = ArgumentCaptor.forClass(FeedBackEvent.class);
        verify(centralFeedBackHandler, times(2)).handleFeedBack(captor.capture());
        assertThat(captor.getAllValues().get(1).getEventType()).isEqualTo(UXEventType.LONGTASK_END);
    }

    // ===== Execution time =====

    @Test
    void executePythonCode_shouldRecordExecutionTime() {
        // Given
        when(pythonExecutor.execute(any()))
                .thenReturn(new PythonExecutor.ExecutionResult(0, "ok", ""));

        // When
        ActionResult result = pythonActions.executePythonCode("print('ok')");

        // Then
        assertThat(result.getExecutionTimeMs()).isGreaterThanOrEqualTo(0);
    }
}
