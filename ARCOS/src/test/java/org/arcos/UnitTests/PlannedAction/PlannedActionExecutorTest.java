package org.arcos.UnitTests.PlannedAction;

import org.arcos.LLM.Client.LLMClient;
import org.arcos.LLM.Prompts.PromptBuilder;
import org.arcos.PlannedAction.Models.ActionType;
import org.arcos.PlannedAction.Models.PlannedActionEntry;
import org.arcos.PlannedAction.Models.ReWOOPlan;
import org.arcos.PlannedAction.PlannedActionExecutor;
import org.arcos.Tools.Actions.ActionResult;
import org.arcos.Tools.Actions.CalendarActions;
import org.arcos.Tools.Actions.PythonActions;
import org.arcos.Tools.Actions.SearchActions;
import org.arcos.common.utils.ObjectCreationUtils;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.ai.chat.prompt.Prompt;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class PlannedActionExecutorTest {

    @Mock
    private CalendarActions calendarActions;

    @Mock
    private SearchActions searchActions;

    @Mock
    private PythonActions pythonActions;

    @Mock
    private LLMClient llmClient;

    @Mock
    private PromptBuilder promptBuilder;

    private PlannedActionExecutor executor;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        executor = new PlannedActionExecutor(calendarActions, searchActions, pythonActions, llmClient, promptBuilder);
    }

    @Test
    void execute_SimpleReminder_ShouldReturnReminderMessage() {
        // Given
        PlannedActionEntry entry = ObjectCreationUtils.createSimpleReminderEntry();

        // When
        String result = executor.execute(entry);

        // Then
        assertEquals("Rappel : Appeler le dentiste", result);
        verifyNoInteractions(calendarActions, searchActions, pythonActions, llmClient);
    }

    @Test
    void execute_ComplexHabit_ShouldExecuteAllStepsAndSynthesize() {
        // Given
        PlannedActionEntry entry = ObjectCreationUtils.createComplexHabitEntry();

        when(calendarActions.listCalendarEvents(5))
                .thenReturn(ActionResult.success(List.of("Réunion 10h"), "Événements récupérés"));
        when(searchActions.searchTheWeb("actualités France aujourd'hui"))
                .thenReturn(ActionResult.success(List.of("Actu 1"), "Recherche effectuée"));
        when(searchActions.searchTheWeb("météo Lyon aujourd'hui"))
                .thenReturn(ActionResult.success(List.of("20°C ensoleillé"), "Recherche effectuée"));

        when(promptBuilder.buildPlannedActionSynthesisPrompt(eq(entry), any()))
                .thenReturn(new Prompt("synthesis prompt"));
        when(llmClient.generateToollessResponse(any(Prompt.class)))
                .thenReturn("Bonjour ! Votre agenda : réunion à 10h. Actualités : rien de spécial. Météo : 20 degrés.");

        // When
        String result = executor.execute(entry);

        // Then
        assertEquals("Bonjour ! Votre agenda : réunion à 10h. Actualités : rien de spécial. Météo : 20 degrés.", result);
        verify(calendarActions).listCalendarEvents(5);
        verify(searchActions, times(2)).searchTheWeb(anyString());
        verify(llmClient).generateToollessResponse(any(Prompt.class));
    }

    @Test
    void execute_PartialFailure_ShouldContinueExecution() {
        // Given
        PlannedActionEntry entry = ObjectCreationUtils.createComplexHabitEntry();

        when(calendarActions.listCalendarEvents(5))
                .thenReturn(ActionResult.failure("Calendrier indisponible"));
        when(searchActions.searchTheWeb("actualités France aujourd'hui"))
                .thenReturn(ActionResult.success(List.of("Actu 1"), "Recherche effectuée"));
        when(searchActions.searchTheWeb("météo Lyon aujourd'hui"))
                .thenReturn(ActionResult.success(List.of("20°C"), "Recherche effectuée"));

        when(promptBuilder.buildPlannedActionSynthesisPrompt(eq(entry), any()))
                .thenReturn(new Prompt("synthesis prompt"));
        when(llmClient.generateToollessResponse(any(Prompt.class)))
                .thenReturn("Briefing partiel sans agenda.");

        // When
        String result = executor.execute(entry);

        // Then
        assertEquals("Briefing partiel sans agenda.", result);
        verify(calendarActions).listCalendarEvents(5);
        verify(searchActions, times(2)).searchTheWeb(anyString());
    }

    @Test
    void execute_SynthesisFailure_ShouldFallbackToRawConcatenation() {
        // Given
        PlannedActionEntry entry = ObjectCreationUtils.createComplexHabitEntry();

        when(calendarActions.listCalendarEvents(5))
                .thenReturn(ActionResult.success(List.of("Réunion 10h"), "Événements récupérés"));
        when(searchActions.searchTheWeb("actualités France aujourd'hui"))
                .thenReturn(ActionResult.success(List.of("Actu 1"), "Recherche effectuée"));
        when(searchActions.searchTheWeb("météo Lyon aujourd'hui"))
                .thenReturn(ActionResult.success(List.of("20°C"), "Recherche effectuée"));

        when(promptBuilder.buildPlannedActionSynthesisPrompt(eq(entry), any()))
                .thenReturn(new Prompt("synthesis prompt"));
        when(llmClient.generateToollessResponse(any(Prompt.class)))
                .thenThrow(new RuntimeException("LLM down"));

        // When
        String result = executor.execute(entry);

        // Then
        assertNotNull(result);
        assertFalse(result.isEmpty());
        // Fallback concatenation should contain variable names
        assertTrue(result.contains("agenda"));
        assertTrue(result.contains("actus"));
        assertTrue(result.contains("meteo"));
    }

    @Test
    void execute_WithVariableResolution_ShouldPassResolvedParams() {
        // Given — test variable resolution indirectly through execute
        PlannedActionEntry entry = new PlannedActionEntry();
        entry.setLabel("Test variable resolution");
        entry.setActionType(ActionType.TODO);

        ReWOOPlan.ReWOOStep step1 = new ReWOOPlan.ReWOOStep(
                1, "Chercher_sur_Internet", Map.of("query", "actualités"), "actus", "Search news"
        );
        ReWOOPlan.ReWOOStep step2 = new ReWOOPlan.ReWOOStep(
                2, "Python_Execution", Map.of("code", "$actus"), "processed", "Process results"
        );
        entry.setExecutionPlan(new ReWOOPlan(List.of(step1, step2)));

        when(searchActions.searchTheWeb("actualités"))
                .thenReturn(ActionResult.successWithMessage("Résultat actu"));
        when(pythonActions.executePythonCode("Résultat actu"))
                .thenReturn(ActionResult.successWithMessage("Processed"));

        // When
        String result = executor.execute(entry);

        // Then
        verify(searchActions).searchTheWeb("actualités");
        verify(pythonActions).executePythonCode("Résultat actu");
    }

    @Test
    void execute_UnknownTool_ShouldLogWarningAndContinue() {
        // Given
        PlannedActionEntry entry = new PlannedActionEntry();
        entry.setLabel("Test unknown tool");
        entry.setActionType(ActionType.TODO);

        ReWOOPlan.ReWOOStep step = new ReWOOPlan.ReWOOStep(
                1, "Outil_Inexistant", Map.of("param", "value"), "result", "Unknown tool test"
        );
        entry.setExecutionPlan(new ReWOOPlan(List.of(step)));
        entry.setSynthesisPromptTemplate(null);

        // When
        String result = executor.execute(entry);

        // Then
        assertNotNull(result);
        assertTrue(result.contains("Outil inconnu"));
    }
}
