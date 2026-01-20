package org.arcos.Tools.SearchTool;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class BraveSearchConfig {

    @Bean
    public BraveSearchService braveSearchService() {
        String apiKey = System.getenv("BRAVE_SEARCH_API_KEY");
        if (apiKey == null || apiKey.isEmpty()) {
            throw new IllegalStateException("BRAVE_SEARCH_API_KEY environment variable not set.");
        }
        return new BraveSearchService();
    }
}
