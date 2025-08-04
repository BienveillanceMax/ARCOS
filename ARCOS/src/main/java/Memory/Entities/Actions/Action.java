package Memory.Entities.Actions;

import Memory.Entities.ActionResult;
import Orchestrator.Entities.Parameter;

import java.util.List;
import java.util.Map;

public abstract class Action
{
    protected final String name;
    protected final String description;
    protected final List<Parameter> parameters;

    protected Action(String name, String description, List<Parameter> parameters) {
        this.name = name;
        this.description = description;
        this.parameters = parameters;
    }

    public String getName() {
        return name;
    }

    public String getDescription() {
        return description;
    }

    public List<Parameter> getParameters() {
        return parameters;
    }

    public abstract ActionResult execute(Map<String, Object> params);
}
