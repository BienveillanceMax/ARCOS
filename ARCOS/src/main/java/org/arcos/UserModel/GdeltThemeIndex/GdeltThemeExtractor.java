package org.arcos.UserModel.GdeltThemeIndex;

import lombok.extern.slf4j.Slf4j;
import org.arcos.LLM.Local.ThinkingMode;
import org.arcos.UserModel.DfsNavigator.UserContextFormatter;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.ollama.OllamaChatModel;
import org.springframework.ai.ollama.api.OllamaOptions;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Slf4j
@Component
@ConditionalOnProperty(name = "arcos.gdelt.enabled", havingValue = "true", matchIfMissing = true)
public class GdeltThemeExtractor {

    private static final Pattern KEYWORD_PATTERN = Pattern.compile("^(fr|en|gdelt):(.+)$");

    private final OllamaChatModel ollamaChatModel;
    private final GdeltThemeIndexProperties properties;
    private final UserContextFormatter userContextFormatter;

    public GdeltThemeExtractor(OllamaChatModel ollamaChatModel,
                               GdeltThemeIndexProperties properties,
                               UserContextFormatter userContextFormatter) {
        this.ollamaChatModel = ollamaChatModel;
        this.properties = properties;
        this.userContextFormatter = userContextFormatter;
    }

    public List<GdeltKeyword> extract(String leafPath, String leafValue) {
        try {
            String humanPath = userContextFormatter.humanReadablePath(leafPath);
            String prompt = buildPrompt(humanPath, leafValue);

            OllamaOptions options = OllamaOptions.builder()
                    .model(properties.getExtractorModel())
                    .temperature(properties.getExtractorTemperature())
                    .numPredict(properties.getExtractorMaxTokens())
                    .build();

            String fullPrompt = ThinkingMode.NO_THINK.getPrefix() + prompt;
            Prompt aiPrompt = new Prompt(fullPrompt, options);

            String result = CompletableFuture.supplyAsync(() ->
                    ollamaChatModel.call(aiPrompt).getResult().getOutput().getText()
            ).orTimeout(properties.getExtractorTimeoutMs(), TimeUnit.MILLISECONDS).join();

            if (result == null || result.isBlank()) {
                log.warn("Empty LLM response for GDELT keyword extraction on {}", leafPath);
                return List.of();
            }

            List<GdeltKeyword> keywords = parseResponse(result);
            if (keywords.size() > properties.getMaxKeywordsPerLeaf()) {
                keywords = keywords.subList(0, properties.getMaxKeywordsPerLeaf());
            }

            log.debug("Extracted {} GDELT keywords for {}", keywords.size(), leafPath);
            return keywords;
        } catch (Exception e) {
            Throwable cause = e.getCause();
            if (cause instanceof TimeoutException) {
                log.warn("GDELT keyword extraction timed out for {}", leafPath);
            } else {
                log.error("GDELT keyword extraction failed for {}: {}", leafPath, e.getMessage());
            }
            return List.of();
        }
    }

    private List<GdeltKeyword> parseResponse(String response) {
        List<GdeltKeyword> keywords = new ArrayList<>();
        String[] lines = response.split("\\r?\\n");

        for (String line : lines) {
            String trimmed = line.trim();
            if (trimmed.isEmpty()) continue;

            Matcher matcher = KEYWORD_PATTERN.matcher(trimmed);
            if (matcher.matches()) {
                String langCode = matcher.group(1);
                String term = matcher.group(2).trim();
                if (!term.isEmpty()) {
                    KeywordLanguage language = switch (langCode) {
                        case "fr" -> KeywordLanguage.FR;
                        case "en" -> KeywordLanguage.EN;
                        case "gdelt" -> KeywordLanguage.GDELT_THEME;
                        default -> KeywordLanguage.EN;
                    };
                    keywords.add(new GdeltKeyword(term, language));
                }
            } else {
                log.warn("Skipping unparseable GDELT keyword line: {}", trimmed);
            }
        }

        return keywords;
    }

    private String buildPrompt(String humanReadablePath, String leafValue) {
        return """
                Tu es un extracteur de mots-clés d'actualité pour le système GDELT.

                Étant donné un trait de personnalité, génère des mots-clés de recherche \
                d'actualités pertinents pour cette personne.

                Règles :
                - Entre 2 et 5 lignes au total
                - Inclure au moins 1 code thème GDELT GKG (préfixé gdelt:)
                - Inclure 1-3 termes de recherche libre en français et/ou anglais
                - Privilégie les termes concrets aux termes abstraits
                - Termes libres : 1 à 4 mots maximum

                Codes thèmes GDELT GKG disponibles (exemples) :
                GENERAL_GOVERNMENT, ELECTIONS, DEMOCRACY, LEGISLATION, CORRUPTION,
                ECON_INFLATION, ECON_TRADE, ECON_STOCKMARKET, ECON_UNEMPLOYMENT,
                ENV_CLIMATECHANGE, ENV_GREEN, ENV_NUCLEAR, ENV_SOLAR,
                MILITARY, TERROR, CYBER_ATTACK, HUMAN_RIGHTS, PROTEST,
                IMMIGRATION, REFUGEES, EDUCATION, HEALTH_PANDEMIC,
                TECH_AI, SCIENCE, MEDIA_SOCIAL, FOOD_SECURITY, NATURAL_DISASTER

                Format de sortie (un par ligne) :
                gdelt:CODE_THEME
                fr:terme en français
                en:term in english

                Catégorie : %s
                Valeur : %s

                Mots-clés :
                """.formatted(humanReadablePath, leafValue);
    }
}
