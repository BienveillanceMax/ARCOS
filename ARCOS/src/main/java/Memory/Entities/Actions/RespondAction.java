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
        super("Parler", "Parle à l'utilisateur pendant l'exécution du plan. Le but de cette action n'est PAS de donner la réponse finale à l'utilisateur.",
                List.of(new Parameter("Response",String.class,true,
                        "Le texte à dire à l'utilisateur.","Rappelle-toi des jours heureux.")
                ));
    }

    @Override
    public ActionResult execute(Map<String, Object> params) {

        long startTime = System.currentTimeMillis();


        try {

            return ActionResult.success(new ArrayList<String>(), "Recherche effectuée avec succès")
                    .addMetadata("query", "aled")
                    .addMetadata("source", "web_api")
                    .withExecutionTime(System.currentTimeMillis() - startTime);

        } catch (Exception e) {
            return ActionResult.failure("Erreur de Réponse: " + e.getMessage(), e)
                    .withExecutionTime(System.currentTimeMillis() - startTime);
        }
    }

}
