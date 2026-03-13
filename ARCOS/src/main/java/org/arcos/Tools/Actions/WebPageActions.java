package org.arcos.Tools.Actions;

import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import org.arcos.IO.OuputHandling.StateHandler.CentralFeedBackHandler;
import org.arcos.IO.OuputHandling.StateHandler.FeedBackEvent;
import org.arcos.IO.OuputHandling.StateHandler.UXEventType;
import org.arcos.Tools.WebPageTool.WebPageService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.net.http.HttpTimeoutException;
import java.util.List;

@Slf4j
@Component
public class WebPageActions {

    private final WebPageService webPageService;
    private final CentralFeedBackHandler centralFeedBackHandler;
    private final int maxContentLength;
    private final int timeoutSeconds;

    public WebPageActions(WebPageService webPageService,
                          CentralFeedBackHandler centralFeedBackHandler,
                          @Value("${arcos.web-page.max-content-length:4000}") int maxContentLength,
                          @Value("${arcos.web-page.timeout-seconds:15}") int timeoutSeconds) {
        this.webPageService = webPageService;
        this.centralFeedBackHandler = centralFeedBackHandler;
        this.maxContentLength = maxContentLength;
        this.timeoutSeconds = timeoutSeconds;
    }

    @Tool(name = "Lire_une_page_web",
          description = "Lit et extrait le contenu textuel d'une page web à partir de son URL. "
                      + "Utile après une recherche pour approfondir un résultat.")
    @CircuitBreaker(name = "webPage", fallbackMethod = "readWebPageFallback")
    public ActionResult readWebPage(String url) {
        long startTime = System.currentTimeMillis();

        if (url == null || (!url.startsWith("http://") && !url.startsWith("https://"))) {
            return ActionResult.failure("URL invalide : l'URL doit commencer par http:// ou https://")
                    .withExecutionTime(System.currentTimeMillis() - startTime);
        }

        log.info("Lecture de la page web : {}", url);

        centralFeedBackHandler.handleFeedBack(new FeedBackEvent(UXEventType.LONGTASK_START));
        try {
            String content = webPageService.fetchAndExtract(url, maxContentLength, timeoutSeconds);
            return ActionResult.success(List.of(content), "Page lue avec succès")
                    .addMetadata("url", url)
                    .withExecutionTime(System.currentTimeMillis() - startTime);

        } catch (HttpTimeoutException e) {
            long elapsed = System.currentTimeMillis() - startTime;
            log.warn("Timeout lors de la lecture de {} : {}", url, e.getMessage());
            return ActionResult.timeout("Délai d'attente dépassé", elapsed);

        } catch (IOException e) {
            log.error("Erreur de lecture de la page {} : {}", url, e.getMessage());
            return ActionResult.failure("Erreur de lecture : " + e.getMessage(), e)
                    .withExecutionTime(System.currentTimeMillis() - startTime);

        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.error("Lecture interrompue pour {} : {}", url, e.getMessage());
            return ActionResult.failure("Lecture interrompue : " + e.getMessage(), e)
                    .withExecutionTime(System.currentTimeMillis() - startTime);

        } finally {
            centralFeedBackHandler.handleFeedBack(new FeedBackEvent(UXEventType.LONGTASK_END));
        }
    }

    public ActionResult readWebPageFallback(String url, Throwable t) {
        log.warn("Circuit breaker webPage ouvert : {}", t.getMessage());
        return ActionResult.failure("Service de lecture de page temporairement indisponible.", null).withExecutionTime(0);
    }
}
