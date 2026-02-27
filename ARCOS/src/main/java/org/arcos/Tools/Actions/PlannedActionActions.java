package org.arcos.Tools.Actions;

import lombok.extern.slf4j.Slf4j;
import org.arcos.PlannedAction.Models.ActionType;
import org.arcos.PlannedAction.Models.PlannedActionEntry;
import org.arcos.PlannedAction.PlannedActionService;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.List;
import java.util.stream.Collectors;

@Slf4j
@Component
public class PlannedActionActions {

    private final PlannedActionService plannedActionService;

    public PlannedActionActions(PlannedActionService plannedActionService) {
        this.plannedActionService = plannedActionService;
    }

    @Tool(name = "Planifier_une_action", description = "Planifie une action future : rappel simple (TODO) ou habitude récurrente (HABIT). " +
            "Pour un TODO, fournir triggerDatetime au format ISO (yyyy-MM-dd'T'HH:mm:ss). " +
            "Pour un HABIT, fournir une cronExpression (ex: '0 30 8 * * *' pour tous les jours à 8h30). " +
            "Mettre needsTools à true si l'action nécessite des outils (recherche web, calendrier, etc.).")
    public ActionResult planAction(String label, String type, String triggerDatetime, String cronExpression, boolean needsTools) {
        try {
            PlannedActionEntry entry = new PlannedActionEntry();
            entry.setLabel(label);

            ActionType actionType;
            try {
                actionType = ActionType.valueOf(type.toUpperCase());
            } catch (IllegalArgumentException e) {
                return ActionResult.failure("Type d'action invalide : '" + type + "'. Valeurs acceptées : TODO, HABIT.");
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
            } else {
                if (cronExpression == null || cronExpression.isBlank()) {
                    return ActionResult.failure("cronExpression est requise pour une action HABIT.");
                }
                entry.setCronExpression(cronExpression);
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

    @Tool(name = "Lister_les_actions_planifiees", description = "Liste toutes les actions planifiées actives (rappels et habitudes).")
    public ActionResult listActions() {
        List<PlannedActionEntry> activeActions = plannedActionService.listActiveActions();
        if (activeActions.isEmpty()) {
            return ActionResult.successWithMessage("Aucune action planifiée active.");
        }

        List<String> descriptions = activeActions.stream()
                .map(a -> String.format("%s (%s) — %s",
                        a.getLabel(),
                        a.getActionType(),
                        a.isHabit() ? "cron: " + a.getCronExpression() : "prévu le " + a.getTriggerDatetime()))
                .collect(Collectors.toList());

        return ActionResult.success(descriptions, activeActions.size() + " action(s) planifiée(s) active(s).");
    }
}
