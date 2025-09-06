package Memory.Actions.Entities.Actions;

import Memory.Actions.Entities.ActionResult;
import Orchestrator.Entities.Parameter;
import Tools.PythonTool.PythonExecutor;

import java.util.Collections;
import java.util.List;
import java.util.Map;

public class PythonAction extends Action {

    private final PythonExecutor pythonExecutor;

    private static List<Parameter> createParameters() {
        return Collections.singletonList(
                new Parameter(
                        "code",
                        String.class,
                        true,
                        "Le code python à exécuter.",
                        null
                )
        );
    }

    public PythonAction(PythonExecutor pythonExecutor) {
        super("Executer du code Python",
                "Exécute un bloc de code Python. Le code ne doit pas avoir d'effets de bord dangereux.",
                createParameters());
        this.pythonExecutor = pythonExecutor;
    }

    @Override
    public ActionResult execute(Map<String, Object> params) {
        long startTime = System.currentTimeMillis();
        String code = (String) params.get("code");

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
    }
}
