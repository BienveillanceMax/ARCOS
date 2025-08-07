package Memory.Entities.Actions;

import Memory.Entities.ActionResult;
import Orchestrator.Entities.Parameter;

import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class TimeAction extends Action
{
    public TimeAction() {
        super("Accéder à la date et l'heure", "Retourne l'heure et la date actuelle",
                List.of(new Parameter("Réponse",String.class,true,
                        "Le texte à dire à l'utilisateur.","Rappelle-toi des jours heureux.")
                ));
    }
    @Override
    public ActionResult execute(Map<String, Object> params) {
        ZonedDateTime parisTime = ZonedDateTime.now(ZoneId.of("Europe/Paris"));
        List<String> data = new ArrayList<>();
        data.add(parisTime.format(DateTimeFormatter.ISO_DATE_TIME));
        return ActionResult.success(data);
    }
}
