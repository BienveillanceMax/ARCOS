package org.arcos.Tools.Actions;

import org.arcos.IO.OuputHandling.StateHandler.CentralFeedBackHandler;
import org.arcos.IO.OuputHandling.StateHandler.FeedBackEvent;
import org.arcos.IO.OuputHandling.StateHandler.UXEventType;
import org.arcos.Tools.PythonTool.PythonExecutor;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class PythonActions
{
    private final PythonExecutor pythonExecutor;
    private final CentralFeedBackHandler centralFeedBackHandler;

    public PythonActions(PythonExecutor pythonExecutor, CentralFeedBackHandler centralFeedBackHandler) {
        this.pythonExecutor = pythonExecutor;
        this.centralFeedBackHandler = centralFeedBackHandler;
    }

    @Tool(name = "Executer_du_code",
          description = "Exécute du code Python3 dans un sandbox et retourne stdout. "
                  + "Cas d'usage : calculs/conversions, manipulation de dates, traitement de texte, "
                  + "génération de données structurées, résolution de problèmes logiques. "
                  + "Limites : pas d'accès réseau ni filesystem. Librairie standard uniquement.")
    public ActionResult executePythonCode(String code) {
        long startTime = System.currentTimeMillis();
        centralFeedBackHandler.handleFeedBack(new FeedBackEvent(UXEventType.LONGTASK_START));
        try {
            PythonExecutor.ExecutionResult result = pythonExecutor.execute(code);

            if (result.isSuccess()) {
                return ActionResult.success(List.of(result.getStdout()), "Code exécuté avec succès.")
                        .addMetadata("exit_code", result.getExitCode())
                        .withExecutionTime(System.currentTimeMillis() - startTime);
            } else {
                return ActionResult.failure("Erreur d'exécution : " + result.getStderr(), null)
                        .addMetadata("exit_code", result.getExitCode())
                        .addMetadata("stdout", result.getStdout())
                        .withExecutionTime(System.currentTimeMillis() - startTime);
            }
        } finally {
            centralFeedBackHandler.handleFeedBack(new FeedBackEvent(UXEventType.LONGTASK_END));
        }
    }
}
