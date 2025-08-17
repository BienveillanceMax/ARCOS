package Memory.Actions.Entities.Actions;

import Memory.Actions.Entities.ActionResult;
import Orchestrator.Entities.Parameter;

import java.util.*;

public class DefaultAction extends Action
{
    public DefaultAction() {
        super("Action par défaut",
                "Action par défault. A utiliser quand aucune autre action n'est suffisamment pertinente. Transmet des informations pour la formulation",
                List.of(new Parameter("contenu", String.class, true, "contenu à transmettre pour la formulation", "")));
    }


    @Override
    public ActionResult execute(Map<String, Object> params) {

        long startTime = System.currentTimeMillis();
        List<String> data = Collections.singletonList(params.get("contenu").toString());
        return ActionResult.success(data, "Message transmis depuis le planificateur" )
                .withExecutionTime(System.currentTimeMillis() - startTime);
    }
}
