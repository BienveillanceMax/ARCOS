package Orchestrator;

import Memory.Entities.ActionResult;
import Memory.Entities.Actions.Action;
import Memory.Entities.Actions.SearchAction;
import Orchestrator.Entities.ExecutionPlan;
import org.springframework.stereotype.Component;


import java.util.*;

@Component
public class ActionExecutor
{


    // Détermine quelle action faire
    private ActionResult executeAction(String action, Map<String, Object> params)
    {

        if (action.equals("Rechercher sur internet")){
            SearchAction searchAction = new SearchAction();
            return searchAction.execute(params);
        }
        return null;
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

        //TODO
    }
}
