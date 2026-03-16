package org.arcos.Tools.Actions;

import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import lombok.extern.slf4j.Slf4j;
import org.arcos.IO.OuputHandling.StateHandler.CentralFeedBackHandler;
import org.arcos.IO.OuputHandling.StateHandler.FeedBackEvent;
import org.arcos.IO.OuputHandling.StateHandler.UXEventType;
import org.arcos.Tools.GdeltTool.GdeltAnalysisService;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.stereotype.Component;

import java.util.List;

@Slf4j
@Component
@ConditionalOnBean(GdeltAnalysisService.class)
public class GdeltActions {

    private final GdeltAnalysisService gdeltAnalysisService;
    private final CentralFeedBackHandler centralFeedBackHandler;

    public GdeltActions(GdeltAnalysisService gdeltAnalysisService,
                        CentralFeedBackHandler centralFeedBackHandler) {
        this.gdeltAnalysisService = gdeltAnalysisService;
        this.centralFeedBackHandler = centralFeedBackHandler;
    }

    @Tool(name = "Rapport_Actualites",
          description = "Genere un rapport d'intelligence sur l'actualite mondiale. "
              + "Sans sujet : briefing personnalise base sur le profil de l'utilisateur. "
              + "Avec sujet : analyse geopolitique approfondie — croisement des couvertures "
              + "mediatiques inter-pays, tonalite dans le temps, citations de dirigeants, "
              + "detection d'incoherences narratives entre sources. "
              + "Operation longue (~30-60 secondes).")
    @CircuitBreaker(name = "gdelt", fallbackMethod = "worldReportFallback")
    public ActionResult worldReport(String subject) {
        long startTime = System.currentTimeMillis();
        boolean isBriefingMode = subject == null || subject.isBlank();
        centralFeedBackHandler.handleFeedBack(new FeedBackEvent(UXEventType.LONGTASK_START));

        try {
            String report;
            if (isBriefingMode) {
                log.info("GDELT briefing mode");
                report = gdeltAnalysisService.generateBriefing();
            } else {
                log.info("GDELT analysis mode — subject: {}", subject);
                report = gdeltAnalysisService.analyzeSubject(subject);
            }

            return ActionResult.success(List.of(report), "Rapport GDELT genere")
                    .addMetadata("mode", isBriefingMode ? "briefing" : "analysis")
                    .addMetadata("subject", subject)
                    .withExecutionTime(System.currentTimeMillis() - startTime);

        } catch (Exception e) {
            log.error("Erreur lors de la generation du rapport GDELT : {}", e.getMessage());
            return ActionResult.failure("Erreur GDELT : " + e.getMessage(), e)
                    .withExecutionTime(System.currentTimeMillis() - startTime);
        } finally {
            centralFeedBackHandler.handleFeedBack(new FeedBackEvent(UXEventType.LONGTASK_END));
        }
    }

    public ActionResult worldReportFallback(String subject, Throwable t) {
        log.warn("Circuit breaker gdelt ouvert : {}", t.getMessage());
        return ActionResult.failure("Service d'analyse GDELT temporairement indisponible.", null)
                .withExecutionTime(0);
    }
}
