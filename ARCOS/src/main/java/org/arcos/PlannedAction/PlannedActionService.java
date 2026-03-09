package org.arcos.PlannedAction;

import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.arcos.Configuration.PlannedActionProperties;
import org.arcos.LLM.Client.PlannedActionLLMClient;
import org.arcos.LLM.Client.ResponseObject.PlannedActionPlanResponse;
import org.arcos.LLM.Prompts.PromptBuilder;
import org.arcos.PlannedAction.Models.ActionStatus;
import org.arcos.PlannedAction.Models.ActionType;
import org.arcos.PlannedAction.Models.PlannedActionEntry;
import org.arcos.PlannedAction.Models.ReWOOPlan;
import org.arcos.Producers.PlannedActionProducer;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;
import org.springframework.scheduling.support.CronTrigger;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ScheduledFuture;

@Slf4j
@Service
public class PlannedActionService {

    private final PlannedActionRepository repository;
    private final PlannedActionProducer producer;
    private final PlannedActionLLMClient llmClient;
    private final PromptBuilder promptBuilder;
    private final PlannedActionProperties properties;

    private final TaskScheduler taskScheduler;
    private final Map<String, List<ScheduledFuture<?>>> scheduledTasks = new ConcurrentHashMap<>();

    public PlannedActionService(PlannedActionRepository repository,
                                PlannedActionProducer producer,
                                PlannedActionLLMClient llmClient,
                                PromptBuilder promptBuilder,
                                PlannedActionProperties properties) {
        this.repository = repository;
        this.producer = producer;
        this.llmClient = llmClient;
        this.promptBuilder = promptBuilder;
        this.properties = properties;

        ThreadPoolTaskScheduler scheduler = new ThreadPoolTaskScheduler();
        scheduler.setPoolSize(properties.getThreadPoolSize());
        scheduler.setThreadNamePrefix("planned-action-");
        scheduler.setDaemon(true);
        scheduler.initialize();
        this.taskScheduler = scheduler;
    }

    @PostConstruct
    public void init() {
        int registered = 0;
        for (PlannedActionEntry entry : repository.findAll()) {
            if (entry.getStatus() == ActionStatus.ACTIVE) {
                List<ScheduledFuture<?>> futures = registerInScheduler(entry);
                if (!futures.isEmpty()) {
                    scheduledTasks.put(entry.getId(), futures);
                    registered++;
                }
            }
        }
        log.info("Registered {} active planned actions in scheduler", registered);
    }

    public void createAction(PlannedActionEntry entry) {
        repository.save(entry);
        List<ScheduledFuture<?>> futures = registerInScheduler(entry);
        if (!futures.isEmpty()) {
            scheduledTasks.put(entry.getId(), futures);
        }
        log.info("Created planned action: {} ({})", entry.getLabel(), entry.getActionType());
    }

    public boolean cancelAction(String label) {
        PlannedActionEntry found = repository.findActiveByLabelContaining(label);

        if (found == null) {
            log.warn("No active action found matching label: {}", label);
            return false;
        }

        cancelScheduledFutures(found.getId());
        found.setStatus(ActionStatus.DISABLED);
        repository.save(found);
        log.info("Cancelled planned action: {}", found.getLabel());
        return true;
    }

    public List<PlannedActionEntry> listActiveActions() {
        return repository.findAllActive();
    }

    public ReWOOPlan generateExecutionPlan(PlannedActionEntry entry) {
        int maxRetries = properties.getMaxPlanRetries();
        for (int attempt = 1; attempt <= maxRetries; attempt++) {
            try {
                Prompt prompt = promptBuilder.buildReWOOPlanPrompt(entry);
                PlannedActionPlanResponse response = llmClient.generatePlannedActionPlanResponse(prompt);

                if (response != null && response.getExecutionPlan() != null) {
                    entry.setExecutionPlan(response.getExecutionPlan());
                    entry.setSynthesisPromptTemplate(response.getSynthesisPromptTemplate());
                    repository.save(entry);
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
        repository.save(entry);
        return null;
    }

    public void markCompleted(PlannedActionEntry entry) {
        entry.setStatus(ActionStatus.COMPLETED);
        cancelScheduledFutures(entry.getId());
        repository.save(entry);
        log.info("Marked action as completed: {}", entry.getLabel());
    }

    public void deleteAction(String id) {
        PlannedActionEntry removed = repository.delete(id);
        if (removed != null) {
            cancelScheduledFutures(id);
            log.info("Deleted planned action: {}", removed.getLabel());
        }
    }

    private List<ScheduledFuture<?>> registerInScheduler(PlannedActionEntry entry) {
        if (entry.getStatus() != ActionStatus.ACTIVE) {
            return List.of();
        }

        try {
            if (entry.getActionType() == ActionType.HABIT && entry.getCronExpression() != null) {
                CronTrigger cronTrigger = new CronTrigger(entry.getCronExpression());
                ScheduledFuture<?> future = taskScheduler.schedule(() -> {
                    producer.onActionTriggered(entry);
                    repository.save(entry);
                }, cronTrigger);
                return future != null ? List.of(future) : List.of();

            } else if (entry.getActionType() == ActionType.TODO && entry.getTriggerDatetime() != null) {
                if (entry.getTriggerDatetime().isBefore(LocalDateTime.now())) {
                    log.warn("TODO action '{}' has a trigger time in the past, triggering immediately", entry.getLabel());
                    producer.onActionTriggered(entry);
                    repository.save(entry);
                    return List.of();
                }
                ScheduledFuture<?> future = taskScheduler.schedule(
                        () -> {
                            producer.onActionTriggered(entry);
                            repository.save(entry);
                        },
                        entry.getTriggerDatetime().atZone(ZoneId.systemDefault()).toInstant()
                );
                return future != null ? List.of(future) : List.of();

            } else if (entry.getActionType() == ActionType.DEADLINE && entry.getDeadlineDatetime() != null) {
                List<ScheduledFuture<?>> futures = new ArrayList<>();

                if (entry.getReminderDatetimes() != null) {
                    for (LocalDateTime reminderTime : entry.getReminderDatetimes()) {
                        if (reminderTime.isAfter(LocalDateTime.now())) {
                            ScheduledFuture<?> reminderFuture = taskScheduler.schedule(
                                    () -> {
                                        producer.onReminderTriggered(entry);
                                        repository.save(entry);
                                    },
                                    reminderTime.atZone(ZoneId.systemDefault()).toInstant()
                            );
                            if (reminderFuture != null) {
                                futures.add(reminderFuture);
                            }
                        }
                    }
                }

                if (entry.getDeadlineDatetime().isAfter(LocalDateTime.now())) {
                    ScheduledFuture<?> deadlineFuture = taskScheduler.schedule(
                            () -> {
                                producer.onActionTriggered(entry);
                                repository.save(entry);
                            },
                            entry.getDeadlineDatetime().atZone(ZoneId.systemDefault()).toInstant()
                    );
                    if (deadlineFuture != null) {
                        futures.add(deadlineFuture);
                    }
                } else {
                    log.warn("DEADLINE action '{}' has a deadline in the past, triggering immediately", entry.getLabel());
                    producer.onActionTriggered(entry);
                    repository.save(entry);
                }

                return futures;
            }
        } catch (IllegalArgumentException e) {
            log.error("Failed to register action '{}' in scheduler: {}", entry.getLabel(), e.getMessage());
        }

        return List.of();
    }

    private void cancelScheduledFutures(String actionId) {
        List<ScheduledFuture<?>> futures = scheduledTasks.remove(actionId);
        if (futures != null) {
            for (ScheduledFuture<?> future : futures) {
                future.cancel(false);
            }
        }
    }
}
