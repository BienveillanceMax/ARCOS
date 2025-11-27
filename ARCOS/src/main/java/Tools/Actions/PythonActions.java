package Tools.Actions;

import Memory.Actions.Entities.ActionResult;
import Tools.PythonTool.PythonExecutor;
import io.github.resilience4j.ratelimiter.annotation.RateLimiter;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

@Component
public class PythonActions
{
    PythonExecutor pythonExecutor = new PythonExecutor();

    @RateLimiter(name = "mistral_free")
    @Tool(name = "Executeur_Python", description = "Execute du code python et retourne le contenu de stdout" )
    public ActionResult executePythonCode(String code) {
        long startTime = System.currentTimeMillis();
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
