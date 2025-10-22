package Memory.Actions.Entities.Actions;

import Memory.Actions.Entities.ActionResult;
import Memory.Actions.Entities.Parameter;

import java.util.Collections;
import java.util.List;
import java.util.Map;

public class RespondAction extends Action
{
    //The prompt being written in French, it is better to avoid mixing languages
    public RespondAction() {
        super("Parler", "Parle à l'utilisateur pendant l'exécution du plan. Le but de cette action n'est PAS de donner la réponse finale à l'utilisateur.",
                List.of(new Parameter("Réponse",String.class,true,
                        "Le texte à dire à l'utilisateur.","Rappelle-toi des jours heureux.")
                ));
    }

    @Override
    public ActionResult execute(Map<String, Object> params) {

        long startTime = System.currentTimeMillis();
        List<String> data = Collections.singletonList(params.get("réponse").toString());

        try {


            return ActionResult.success(data, "Recherche effectuée avec succès")
                    .withExecutionTime(System.currentTimeMillis() - startTime);

        } catch (Exception e) {
            return ActionResult.failure("Erreur de Réponse: " + e.getMessage(), e)
                    .withExecutionTime(System.currentTimeMillis() - startTime);
        }
    }

}
