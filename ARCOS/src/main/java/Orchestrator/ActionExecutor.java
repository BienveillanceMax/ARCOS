package Orchestrator;

import Memory.Entities.ActionResult;
import Orchestrator.Entities.ExecutionPlan;
import org.springframework.stereotype.Component;

import java.util.Map;

@Component
public class ActionExecutor
{

    public Map<String, ActionResult> executeActions(ExecutionPlan plan) {
        return null;    //TODO
    }
}
