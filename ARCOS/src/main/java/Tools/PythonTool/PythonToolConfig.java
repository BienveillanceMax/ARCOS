package Tools.PythonTool;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Description;

import java.util.function.Function;

@Configuration
public class PythonToolConfig {

    private final PythonExecutor pythonExecutor;

    public PythonToolConfig(PythonExecutor pythonExecutor) {
        this.pythonExecutor = pythonExecutor;
    }

    @Bean
    @Description("Execute Python code")
    public Function<String, PythonExecutor.ExecutionResult> python() {
        return code -> pythonExecutor.execute(code);
    }
}
