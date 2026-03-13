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

    public PythonActions(CentralFeedBackHandler centralFeedBackHandler) {
        this.pythonExecutor = new PythonExecutor();
        this.centralFeedBackHandler = centralFeedBackHandler;
    }

    @Tool(name = "Python_Execution", description = "Execute python code and return stdout's content. Can be used when other tools are insufficient." )
    public ActionResult executePythonCode(String code) {
        long startTime = System.currentTimeMillis();
        centralFeedBackHandler.handleFeedBack(new FeedBackEvent(UXEventType.LONGTASK_START));
        try {
            PythonExecutor.ExecutionResult result = pythonExecutor.execute(code);

            if (result.isSuccess()) {
                return ActionResult.success(List.of(result.getStdout()), "Code Python exécuté avec succès.")
                        .addMetadata("exit_code", result.getExitCode())
                        .withExecutionTime(System.currentTimeMillis() - startTime);
            } else {
                return ActionResult.failure("Erreur lors de l'exécution du code Python: " + result.getStderr(), null)
                        .addMetadata("exit_code", result.getExitCode())
                        .addMetadata("stdout", result.getStdout())
                        .withExecutionTime(System.currentTimeMillis() - startTime);
            }
        } finally {
            centralFeedBackHandler.handleFeedBack(new FeedBackEvent(UXEventType.LONGTASK_END));
        }
    }
}
