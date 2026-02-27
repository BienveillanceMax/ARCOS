package org.arcos.Tools.SearchTool;

import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Slf4j
@Configuration
public class BraveSearchConfig {

    @Bean
    public BraveSearchService braveSearchService() {
        String apiKey = System.getenv("BRAVE_SEARCH_API_KEY");
        if (apiKey == null || apiKey.isEmpty()) {
            log.warn("BRAVE_SEARCH_API_KEY absent — recherche web désactivée.");
            return new BraveSearchService();
        }
        log.info("BraveSearchService initialisé.");
        return new BraveSearchService();
    }
}
