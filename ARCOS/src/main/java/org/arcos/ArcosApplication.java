package org.arcos;

import EventLoop.EventLoopRunner;
import EventLoop.InputHandling.WakeWordDetector;
import Orchestrator.Orchestrator;
import Tools.NewsAnalysisTool.GdeltApiClient;
import Tools.NewsAnalysisTool.GdeltNewsAnalysisService;
import Tools.NewsAnalysisTool.models.EventFilter;
import Tools.NewsAnalysisTool.models.GdeltConfig;
import Tools.NewsAnalysisTool.models.GdeltEvent;
import Tools.NewsAnalysisTool.models.SentimentAnalysis;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.web.client.RestTemplate;

import java.util.Arrays;
import java.util.List;

@SpringBootApplication(scanBasePackages = {"LLM", "Orchestrator", "Memory", "Prompts", "org.arcos"})
public class ArcosApplication
{

    public static void main(String[] args) {
        ConfigurableApplicationContext context = SpringApplication.run(ArcosApplication.class, args);

        //EventLoopRunner eventLoopRunner = new EventLoopRunner();
        //eventLoopRunner.run();
        //WakeWordDetector.showAudioDevices();
        Orchestrator orchestrator = context.getBean(Orchestrator.class);
        //System.out.println(orchestrator.processQuery("Je suis ton créateur, quelles actions et fonctionnalités voudrais-tu que je te rajoute ?"));
        //System.out.println(orchestrator.processQuery("Te rappelle-tu de la question que je t'ai posé précédemment ?"));
        //EventLoopRunner eventLoopRunner = new EventLoopRunner(orchestrator);
        //eventLoopRunner.run();
        GdeltApiClient  gdeltApiClient = new GdeltApiClient(new RestTemplate(),new GdeltConfig());
        GdeltNewsAnalysisService gdeltService = new GdeltNewsAnalysisService(gdeltApiClient);

        System.out.println("\n=== Exemple: Analyse de sentiment ===");

        // D'abord récupérer un événement
        EventFilter filter = EventFilter.builder()
                .keywords(Arrays.asList("summit", "diplomacy"))
                .maxResults(1)
                .build();

        List<GdeltEvent> events = gdeltService.getRecentEvents(filter);

        if (!events.isEmpty()) {
            String eventId = events.get(0).getGlobalEventId();
            SentimentAnalysis sentiment = gdeltService.analyzeSentiment(eventId);

            System.out.println("Sentiment global: " + sentiment.getOverallSentiment());
            System.out.printf("Score de polarité: %.2f\n", sentiment.getPolarityScore());
            System.out.printf("Positivité: %.2f, Négativité: %.2f, Neutralité: %.2f\n",
                    sentiment.getPositivityScore(),
                    sentiment.getNegativityScore(),
                    sentiment.getNeutralityScore());
            System.out.println("Confiance: " + sentiment.getConfidenceLevel());

            System.out.println("Émotions détectées:");
            sentiment.getEmotionScores().forEach((emotion, score) ->
                    System.out.printf("  %s: %.2f\n", emotion, score));
        }

        //WakeWordDetector.showAudioDevices();
    }

}
