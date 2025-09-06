package Orchestrator;

import Memory.Actions.Entities.ActionResult;
import Memory.Actions.Entities.Actions.*;
import Orchestrator.Entities.ExecutionPlan;
import Tools.PythonTool.PythonExecutor;
import Tools.SearchTool.BraveSearchService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;


import java.util.*;

@Component
public class ActionExecutor
{
    private final BraveSearchService braveSearchService;
    PythonExecutor pythonExecutor;
    BraveSearchService searchService;


    @Autowired
    public ActionExecutor(PythonExecutor pythonExecutor, BraveSearchService searchService, BraveSearchService braveSearchService) {
        this.pythonExecutor = pythonExecutor;
        this.searchService = searchService;
        this.braveSearchService = braveSearchService;
    }

    // Détermine quelle action faire
    private ActionResult executeAction(String action, Map<String, Object> params)
    {

        if (action.equals("Rechercher sur internet")){
            SearchAction searchAction = new SearchAction(braveSearchService);
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
        if (action.equals("Executer du code Python"))
        {
            PythonAction pythonAction = new PythonAction(pythonExecutor);
            return pythonAction.execute(params);
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
