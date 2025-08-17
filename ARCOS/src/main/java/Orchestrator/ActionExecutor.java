package Orchestrator;

import Memory.Actions.Entities.ActionResult;
import Memory.Actions.Entities.Actions.DefaultAction;
import Memory.Actions.Entities.Actions.RespondAction;
import Memory.Actions.Entities.Actions.SearchAction;
import Memory.Actions.Entities.Actions.TimeAction;
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
        if (action.equals("Parler")){
            RespondAction respondAction = new RespondAction();
            return respondAction.execute(params);
        }
        if (action.equals("Action par défaut")){
            DefaultAction defaultAction = new DefaultAction();
            return defaultAction.execute(params);
        }
        if (action.equals("Accéder à la date et l'heure"))
        {
            TimeAction timeAction = new TimeAction();
            return timeAction.execute(params);
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
    }
}
