package org.arcos.PlannedAction;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.arcos.EventBus.EventQueue;
import org.arcos.EventBus.Events.Event;
import org.arcos.EventBus.Events.EventPriority;
import org.arcos.EventBus.Events.EventType;
import org.arcos.IO.OuputHandling.StateHandler.CentralFeedBackHandler;
import org.arcos.LLM.Client.LLMClient;
import org.arcos.LLM.Client.PlannedActionLLMClient;
import org.arcos.LLM.Client.ResponseObject.PlannedActionPlanResponse;
import org.arcos.LLM.Prompts.PromptBuilder;
import org.arcos.PlannedAction.Models.ActionStatus;
import org.arcos.PlannedAction.Models.ActionType;
import org.arcos.PlannedAction.Models.PlannedActionEntry;
import org.arcos.PlannedAction.Models.ReWOOPlan;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;
import org.springframework.scheduling.support.CronTrigger;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ScheduledFuture;
import java.util.stream.Collectors;

@Slf4j
@Service
public class PlannedActionService {

    private final TaskScheduler taskScheduler;
    private final EventQueue eventQueue;
    private final LLMClient llmClient;
    private final PromptBuilder promptBuilder;

    private final Map<String, PlannedActionEntry> actions = new ConcurrentHashMap<>();
    private final Map<String, ScheduledFuture<?>> scheduledTasks = new ConcurrentHashMap<>();
    private final Path storageFile = Paths.get("data/planned-actions.json");
    private final ObjectMapper objectMapper;

    public PlannedActionService(EventQueue eventQueue,
                                 LLMClient llmClient,
                                 PromptBuilder promptBuilder) {
        this.eventQueue = eventQueue;
        this.llmClient = llmClient;
        this.promptBuilder = promptBuilder;

        ThreadPoolTaskScheduler scheduler = new ThreadPoolTaskScheduler();
        scheduler.setPoolSize(2);
        scheduler.setThreadNamePrefix("planned-action-");
        scheduler.setDaemon(true);
        scheduler.initialize();
        this.taskScheduler = scheduler;

        this.objectMapper = new ObjectMapper();
        this.objectMapper.registerModule(new JavaTimeModule());
        this.objectMapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
    }

    @PostConstruct
    public void init() {
        loadFromFile();
    }

    public void createAction(PlannedActionEntry entry) {
        actions.put(entry.getId(), entry);
        ScheduledFuture<?> future = registerInScheduler(entry);
        if (future != null) {
            scheduledTasks.put(entry.getId(), future);
        }
        persistToFile();
        log.info("Created planned action: {} ({})", entry.getLabel(), entry.getActionType());
    }

    public boolean cancelAction(String label) {
        PlannedActionEntry found = actions.values().stream()
                .filter(a -> a.getStatus() == ActionStatus.ACTIVE)
                .filter(a -> a.getLabel().toLowerCase().contains(label.toLowerCase()))
                .findFirst()
                .orElse(null);

        if (found == null) {
            log.warn("No active action found matching label: {}", label);
            return false;
        }

        ScheduledFuture<?> future = scheduledTasks.remove(found.getId());
        if (future != null) {
            future.cancel(false);
        }

        found.setStatus(ActionStatus.DISABLED);
        persistToFile();
        log.info("Cancelled planned action: {}", found.getLabel());
        return true;
    }

    public List<PlannedActionEntry> listActiveActions() {
        return actions.values().stream()
                .filter(a -> a.getStatus() == ActionStatus.ACTIVE)
                .collect(Collectors.toList());
    }

    public ReWOOPlan generateExecutionPlan(PlannedActionEntry entry) {
        int maxRetries = 3;
        for (int attempt = 1; attempt <= maxRetries; attempt++) {
            try {
                Prompt prompt = promptBuilder.buildReWOOPlanPrompt(entry);
                PlannedActionPlanResponse response = llmClient.generatePlannedActionPlanResponse(prompt);

                if (response != null && response.getExecutionPlan() != null) {
                    entry.setExecutionPlan(response.getExecutionPlan());
                    entry.setSynthesisPromptTemplate(response.getSynthesisPromptTemplate());
                    persistToFile();
                    log.info("Generated execution plan for '{}' with {} steps",
                            entry.getLabel(),
                            response.getExecutionPlan().getSteps() != null ? response.getExecutionPlan().getSteps().size() : 0);
                    return response.getExecutionPlan();
                }
            } catch (Exception e) {
                log.warn("Attempt {}/{} to generate execution plan failed: {}", attempt, maxRetries, e.getMessage());
            }
        }

        log.warn("All attempts to generate execution plan failed for '{}', storing as simple reminder", entry.getLabel());
        entry.setExecutionPlan(null);
        persistToFile();
        return null;
    }

    public void markCompleted(PlannedActionEntry entry) {
        entry.setStatus(ActionStatus.COMPLETED);
        ScheduledFuture<?> future = scheduledTasks.remove(entry.getId());
        if (future != null) {
            future.cancel(false);
        }
        persistToFile();
        log.info("Marked action as completed: {}", entry.getLabel());
    }

    public void onActionTriggered(PlannedActionEntry entry) {
        log.info("Planned action triggered: {}", entry.getLabel());
        entry.setLastExecutedAt(LocalDateTime.now());
        entry.setExecutionCount(entry.getExecutionCount() + 1);

        Event<PlannedActionEntry> event = new Event<>(
                EventType.PLANNED_ACTION,
                EventPriority.MEDIUM,
                entry,
                "PlannedActionService"
        );

        boolean offered = eventQueue.offer(event);
        if (!offered) {
            log.warn("EventQueue full, could not enqueue planned action: {}", entry.getLabel());
        }

        persistToFile();
    }

    public void deleteAction(String id) {
        PlannedActionEntry removed = actions.remove(id);
        if (removed != null) {
            ScheduledFuture<?> future = scheduledTasks.remove(id);
            if (future != null) {
                future.cancel(false);
            }
            persistToFile();
            log.info("Deleted planned action: {}", removed.getLabel());
        }
    }

    private ScheduledFuture<?> registerInScheduler(PlannedActionEntry entry) {
        if (entry.getStatus() != ActionStatus.ACTIVE) {
            return null;
        }

        try {
            if (entry.getActionType() == ActionType.HABIT && entry.getCronExpression() != null) {
                CronTrigger cronTrigger = new CronTrigger(entry.getCronExpression());
                return taskScheduler.schedule(() -> onActionTriggered(entry), cronTrigger);
            } else if (entry.getActionType() == ActionType.TODO && entry.getTriggerDatetime() != null) {
                if (entry.getTriggerDatetime().isBefore(LocalDateTime.now())) {
                    log.warn("TODO action '{}' has a trigger time in the past, triggering immediately", entry.getLabel());
                    onActionTriggered(entry);
                    return null;
                }
                return taskScheduler.schedule(
                        () -> onActionTriggered(entry),
                        entry.getTriggerDatetime().atZone(ZoneId.systemDefault()).toInstant()
                );
            }
        } catch (IllegalArgumentException e) {
            log.error("Failed to register action '{}' in scheduler: {}", entry.getLabel(), e.getMessage());
        }

        return null;
    }

    private void persistToFile() {
        try {
            Files.createDirectories(storageFile.getParent());
            objectMapper.writerWithDefaultPrettyPrinter().writeValue(storageFile.toFile(), actions);
            log.debug("Persisted {} planned actions to {}", actions.size(), storageFile);
        } catch (IOException e) {
            log.error("Failed to persist planned actions to {}", storageFile, e);
        }
    }

    private void loadFromFile() {
        if (!Files.exists(storageFile)) {
            log.info("No planned actions file found at {}, starting fresh", storageFile);
            return;
        }

        try {
            Map<String, PlannedActionEntry> loaded = objectMapper.readValue(
                    storageFile.toFile(),
                    new TypeReference<Map<String, PlannedActionEntry>>() {}
            );
            actions.putAll(loaded);

            int registered = 0;
            for (PlannedActionEntry entry : actions.values()) {
                if (entry.getStatus() == ActionStatus.ACTIVE) {
                    ScheduledFuture<?> future = registerInScheduler(entry);
                    if (future != null) {
                        scheduledTasks.put(entry.getId(), future);
                        registered++;
                    }
                }
            }

            log.info("Loaded {} planned actions from file, registered {} in scheduler", actions.size(), registered);
        } catch (IOException e) {
            log.error("Failed to load planned actions from {}, starting with empty map", storageFile, e);
        }
    }
}
