package Memory.Entities.Actions;

import Memory.Entities.ActionResult;
import Orchestrator.Entities.Parameter;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class RespondAction extends Action
{
    //The prompt being written in French, it is better to avoid mixing languages
    public RespondAction() {
        super("Répondre", "Répond directement à l'utilisateur",
                List.of(new Parameter("Response",String.class,true,
                        "La réponse à donner à l'utilisateur","Rappelle-toi des jours heureux.")
                ));
    }

    @Override
    public ActionResult execute(Map<String, Object> params) {

        long startTime = System.currentTimeMillis();

        try {
            String query = (String) params.get("query");
            List<String> results = new ArrayList<>();

            return ActionResult.success(results, "Recherche effectuée avec succès")
                    .addMetadata("query", query)
                    .addMetadata("source", "web_api")
                    .withExecutionTime(System.currentTimeMillis() - startTime);

        } catch (Exception e) {
            return ActionResult.failure("Erreur de Réponse: " + e.getMessage(), e)
                    .withExecutionTime(System.currentTimeMillis() - startTime);
        }
    }

}
