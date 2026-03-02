package org.arcos.Tools.Actions;

import lombok.extern.slf4j.Slf4j;
import org.arcos.PlannedAction.ExecutionHistoryService;
import org.arcos.PlannedAction.Models.ActionType;
import org.arcos.PlannedAction.Models.ExecutionHistoryEntry;
import org.arcos.PlannedAction.Models.PlannedActionEntry;
import org.arcos.PlannedAction.PlannedActionService;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

@Slf4j
@Component
public class PlannedActionActions {

    private final PlannedActionService plannedActionService;
    private final ExecutionHistoryService executionHistoryService;

    public PlannedActionActions(PlannedActionService plannedActionService, ExecutionHistoryService executionHistoryService) {
        this.plannedActionService = plannedActionService;
        this.executionHistoryService = executionHistoryService;
    }

    @Tool(name = "Planifier_une_action", description = "Planifie une action future : rappel simple (TODO), habitude récurrente (HABIT), ou échéance avec rappels progressifs (DEADLINE). " +
            "Pour un TODO, fournir triggerDatetime au format ISO (yyyy-MM-dd'T'HH:mm:ss). " +
            "Pour un HABIT, fournir une cronExpression (ex: '0 30 8 * * *' pour tous les jours à 8h30). " +
            "Pour un DEADLINE, fournir deadlineDatetime (échéance) et optionnellement reminderDatetimes (liste de dates de rappels). " +
            "Mettre needsTools à true si l'action nécessite des outils (recherche web, calendrier, etc.). " +
            "Le champ context permet de stocker des métadonnées utiles (numéro de téléphone, URL, raison, etc.).")
    public ActionResult planAction(String label, String type, String triggerDatetime, String cronExpression,
                                   boolean needsTools, String context, String deadlineDatetime, List<String> reminderDatetimes) {
        try {
            PlannedActionEntry entry = new PlannedActionEntry();
            entry.setLabel(label);
            if (context != null && !context.isBlank()) {
                entry.setContext(context);
            }

            ActionType actionType;
            try {
                actionType = ActionType.valueOf(type.toUpperCase());
            } catch (IllegalArgumentException e) {
                return ActionResult.failure("Type d'action invalide : '" + type + "'. Valeurs acceptées : TODO, HABIT, DEADLINE.");
            }
            entry.setActionType(actionType);

            if (actionType == ActionType.TODO) {
                if (triggerDatetime == null || triggerDatetime.isBlank()) {
                    return ActionResult.failure("triggerDatetime est requis pour une action TODO.");
                }
                try {
                    entry.setTriggerDatetime(LocalDateTime.parse(triggerDatetime, DateTimeFormatter.ISO_LOCAL_DATE_TIME));
                } catch (DateTimeParseException e) {
                    return ActionResult.failure("Format de date invalide : " + triggerDatetime + ". Format attendu : yyyy-MM-dd'T'HH:mm:ss");
                }
            } else if (actionType == ActionType.HABIT) {
                if (cronExpression == null || cronExpression.isBlank()) {
                    return ActionResult.failure("cronExpression est requise pour une action HABIT.");
                }
                entry.setCronExpression(cronExpression);
            } else if (actionType == ActionType.DEADLINE) {
                if (deadlineDatetime == null || deadlineDatetime.isBlank()) {
                    return ActionResult.failure("deadlineDatetime est requis pour une action DEADLINE.");
                }
                try {
                    entry.setDeadlineDatetime(LocalDateTime.parse(deadlineDatetime, DateTimeFormatter.ISO_LOCAL_DATE_TIME));
                } catch (DateTimeParseException e) {
                    return ActionResult.failure("Format de date invalide pour deadlineDatetime : " + deadlineDatetime + ". Format attendu : yyyy-MM-dd'T'HH:mm:ss");
                }
                if (reminderDatetimes != null && !reminderDatetimes.isEmpty()) {
                    List<LocalDateTime> parsedReminders = new ArrayList<>();
                    for (String reminderStr : reminderDatetimes) {
                        try {
                            parsedReminders.add(LocalDateTime.parse(reminderStr, DateTimeFormatter.ISO_LOCAL_DATE_TIME));
                        } catch (DateTimeParseException e) {
                            return ActionResult.failure("Format de date invalide pour un rappel : " + reminderStr + ". Format attendu : yyyy-MM-dd'T'HH:mm:ss");
                        }
                    }
                    entry.setReminderDatetimes(parsedReminders);
                }
            }

            plannedActionService.createAction(entry);

            if (needsTools) {
                plannedActionService.generateExecutionPlan(entry);
            }

            return ActionResult.successWithMessage("Action planifiée : '" + label + "' (" + actionType + ").");
        } catch (Exception e) {
            log.error("Error creating planned action", e);
            return ActionResult.failure("Erreur lors de la planification : " + e.getMessage(), e);
        }
    }

    @Tool(name = "Annuler_une_action_planifiee", description = "Annule une action planifiée en recherchant par label (recherche partielle).")
    public ActionResult cancelAction(String label) {
        boolean cancelled = plannedActionService.cancelAction(label);
        if (cancelled) {
            return ActionResult.successWithMessage("Action '" + label + "' annulée avec succès.");
        } else {
            return ActionResult.failure("Aucune action active trouvée correspondant à '" + label + "'.");
        }
    }

    @Tool(name = "Lister_les_actions_planifiees", description = "Liste toutes les actions planifiées actives (rappels et habitudes). " +
            "Si historyLabel est fourni, retourne l'historique d'exécution correspondant au lieu de la liste des actions. " +
            "Si includeHistory est true, ajoute la dernière exécution de chaque habitude.")
    public ActionResult listActions(boolean includeHistory, String historyLabel) {
        if (historyLabel != null && !historyLabel.isBlank()) {
            List<ExecutionHistoryEntry> historyEntries = executionHistoryService.searchHistoryByLabel(historyLabel, 5);
            if (historyEntries.isEmpty()) {
                return ActionResult.successWithMessage("Aucun historique trouvé pour '" + historyLabel + "'.");
            }
            List<String> historyDescs = historyEntries.stream()
                    .map(h -> String.format("[%s] %s — %s (%s)",
                            h.getExecutedAt(),
                            h.getLabel(),
                            h.getResult() != null ? h.getResult() : "pas de résultat",
                            h.isSuccess() ? "succès" : "échec"))
                    .collect(Collectors.toList());
            return ActionResult.success(historyDescs, historyEntries.size() + " entrée(s) d'historique.");
        }

        List<PlannedActionEntry> activeActions = plannedActionService.listActiveActions();
        if (activeActions.isEmpty()) {
            return ActionResult.successWithMessage("Aucune action planifiée active.");
        }

        List<String> descriptions = activeActions.stream()
                .map(a -> {
                    String triggerInfo;
                    if (a.isHabit()) {
                        triggerInfo = "cron: " + a.getCronExpression();
                    } else if (a.isDeadline()) {
                        triggerInfo = "échéance le " + a.getDeadlineDatetime();
                        if (a.getReminderDatetimes() != null) {
                            triggerInfo += " (" + a.getReminderDatetimes().size() + " rappel(s))";
                        }
                    } else {
                        triggerInfo = "prévu le " + a.getTriggerDatetime();
                    }
                    String desc = String.format("%s (%s) — %s",
                            a.getLabel(),
                            a.getActionType(),
                            triggerInfo);
                    if (a.hasContext()) {
                        desc += " [contexte: " + a.getContext() + "]";
                    }
                    if (includeHistory && a.isHabit()) {
                        List<ExecutionHistoryEntry> lastExec = executionHistoryService.getHistoryForAction(a.getId(), 1);
                        if (!lastExec.isEmpty()) {
                            ExecutionHistoryEntry last = lastExec.get(0);
                            desc += " [dernière exécution: " + last.getExecutedAt() + " — " + (last.isSuccess() ? "succès" : "échec") + "]";
                        }
                    }
                    return desc;
                })
                .collect(Collectors.toList());

        return ActionResult.success(descriptions, activeActions.size() + " action(s) planifiée(s) active(s).");
    }
}
