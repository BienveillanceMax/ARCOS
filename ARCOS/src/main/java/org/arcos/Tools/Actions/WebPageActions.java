package org.arcos.Tools.Actions;

import io.github.resilience4j.circuitbreaker.CallNotPermittedException;
import org.arcos.IO.OuputHandling.StateHandler.CentralFeedBackHandler;
import org.arcos.IO.OuputHandling.StateHandler.FeedBackEvent;
import org.arcos.IO.OuputHandling.StateHandler.UXEventType;
import org.arcos.Tools.WebPageTool.WebPageService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.net.http.HttpTimeoutException;
import java.util.ArrayList;
import java.util.List;

@Slf4j
@Component
public class WebPageActions {

    private final WebPageService webPageService;
    private final CentralFeedBackHandler centralFeedBackHandler;
    private final int maxContentLength;
    private final int maxTotalChars;
    private final int timeoutSeconds;

    public WebPageActions(WebPageService webPageService,
                          CentralFeedBackHandler centralFeedBackHandler,
                          @Value("${arcos.web-page.max-content-length:4000}") int maxContentLength,
                          @Value("${arcos.web-page.max-total-chars:40000}") int maxTotalChars,
                          @Value("${arcos.web-page.timeout-seconds:15}") int timeoutSeconds) {
        this.webPageService = webPageService;
        this.centralFeedBackHandler = centralFeedBackHandler;
        this.maxContentLength = maxContentLength;
        this.maxTotalChars = maxTotalChars;
        this.timeoutSeconds = timeoutSeconds;
    }

    @Tool(name = "Lire_une_page_web",
          description = "Lit et extrait le contenu textuel d'une page web à partir de son URL. "
                      + "Retourne le titre et le contenu par parties ; si la page est longue, "
                      + "rappeler l'outil avec page=2, page=3...")
    public ActionResult readWebPage(
            @ToolParam(description = "URL complète commençant par http:// ou https://") String url,
            @ToolParam(required = false, description = "Numéro de partie du contenu (1 par défaut)")
            Integer page) {
        long startTime = System.currentTimeMillis();

        if (url == null || (!url.startsWith("http://") && !url.startsWith("https://"))) {
            return ActionResult.failure("URL invalide : l'URL doit commencer par http:// ou https://")
                    .withExecutionTime(System.currentTimeMillis() - startTime);
        }

        int effectivePage = (page == null || page < 1) ? 1 : page;
        log.info("Lecture de la page web : {} (partie {})", url, effectivePage);

        centralFeedBackHandler.handleFeedBack(new FeedBackEvent(UXEventType.LONGTASK_START));
        try {
            WebPageService.PageSlice slice = webPageService.fetchPage(
                    url, effectivePage, maxContentLength, maxTotalChars, timeoutSeconds);

            List<String> data = new ArrayList<>();
            String pageIndicator = "Partie " + slice.page() + "/" + slice.totalPages();
            data.add(slice.title().isBlank() ? pageIndicator
                    : "Titre : " + slice.title() + " — " + pageIndicator);
            data.add(slice.content());
            if (slice.page() < slice.totalPages()) {
                data.add("Suite disponible : rappeler l'outil avec page=" + (slice.page() + 1) + ".");
            }

            return ActionResult.success(data, "Page lue avec succès")
                    .addMetadata("url", url)
                    .addMetadata("titre", slice.title())
                    .addMetadata("partie", slice.page() + "/" + slice.totalPages())
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

        } catch (IllegalArgumentException e) {
            log.warn("URL invalide {} : {}", url, e.getMessage());
            return ActionResult.failure("URL invalide : " + e.getMessage(), e)
                    .withExecutionTime(System.currentTimeMillis() - startTime);

        } catch (CallNotPermittedException e) {
            log.warn("Circuit breaker webPage ouvert : {}", e.getMessage());
            return ActionResult.failure("Service de lecture de page temporairement indisponible.", null)
                    .withExecutionTime(System.currentTimeMillis() - startTime);

        } finally {
            centralFeedBackHandler.handleFeedBack(new FeedBackEvent(UXEventType.LONGTASK_END));
        }
    }
}
