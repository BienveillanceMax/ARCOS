package LLM;

import Exceptions.ResponseParsingException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.function.Function;

@Service
@Slf4j
public class LLMService {

    private final LLMClient llmClient;

    public LLMService(LLMClient llmClient) {
        this.llmClient = llmClient;
    }

    /**
     * Génère une réponse à partir du LLM et la parse avec une logique de retry.
     *
     * @param llmCallFunction        La fonction de LLMClient à appeler (ex: llmClient::generatePlanningResponse).
     * @param prompt                 Le prompt à envoyer au LLM.
     * @param responseParserFunction La fonction de LLMResponseParser à utiliser pour le parsing.
     * @param maxRetries             Le nombre maximum de tentatives.
     * @param <T>                    Le type de l'objet retourné après parsing.
     * @return L'objet parsé.
     * @throws ResponseParsingException Si le parsing échoue après toutes les tentatives.
     */
    public <T> T generateAndParse(
            Function<String, String> llmCallFunction,
            String prompt,
            Function<String, T> responseParserFunction,
            int maxRetries) throws ResponseParsingException {

        ResponseParsingException lastException = null;

        for (int attempt = 1; attempt <= maxRetries; attempt++) {
            try {
                // 1. Appel au LLM
                String llmResponse = llmCallFunction.apply(prompt);
                log.debug("LLM Response (attempt {}):\n{}", attempt, llmResponse);

                // 2. Parsing de la réponse
                return responseParserFunction.apply(llmResponse);

            } catch (ResponseParsingException e) {
                lastException = e;
                log.warn("Parsing failed on attempt {}/{}. Error: {}. Retrying...", attempt, maxRetries, e.getMessage());
            } catch (Exception e) {
                // Gérer les autres exceptions (ex: erreur réseau de l'API LLM)
                lastException = new ResponseParsingException("An unexpected error occurred during LLM call or parsing: " + e.getMessage(), e);
                log.error("Unexpected error on attempt {}/{}. Retrying...", attempt, maxRetries, e);
            }

            if (attempt < maxRetries) {
                try {
                    // Attente exponentielle pour éviter de surcharger l'API
                    Thread.sleep(1000 * attempt);
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    throw new ResponseParsingException("Retry mechanism was interrupted.", ie);
                }
            }
        }

        // Si toutes les tentatives échouent
        throw new ResponseParsingException("Failed to get a valid response from LLM after " + maxRetries + " attempts.", lastException);
    }
}
