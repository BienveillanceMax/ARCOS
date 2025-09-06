package Orchestrator;

import Memory.Actions.ActionRegistry;
import Memory.Actions.Entities.ActionResult;
import Memory.Actions.Entities.Actions.Action;
import Orchestrator.Entities.ExecutionPlan;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Component
public class ActionExecutor {

    private final ActionRegistry actionRegistry;

    @Autowired
    public ActionExecutor(ActionRegistry actionRegistry) {
        this.actionRegistry = actionRegistry;
    }

    private ActionResult executeAction(String actionName, Map<String, Object> params) {
        Action action = actionRegistry.getAction(actionName);
        if (action != null) {
            return action.execute(params);
        }
        return ActionResult.failure("Action not found: " + actionName);
    }

    public Map<String, ActionResult> executeActions(ExecutionPlan plan) {
        Map<String, ActionResult> finalResponse = new HashMap<>();
        List<ExecutionPlan.PlannedAction> actions = plan.getActions();

        String actionName;
        ActionResult actionResult;

        int i = 0;
        for (ExecutionPlan.PlannedAction action : actions) {
            actionName = action.getName();
            actionResult = executeAction(actionName, action.getParameters());
            finalResponse.put(actionName + "_" + i , actionResult);
            i++;
        }
        return finalResponse;
    }
}
