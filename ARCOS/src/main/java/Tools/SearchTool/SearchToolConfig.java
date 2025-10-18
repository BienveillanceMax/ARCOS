package Tools.SearchTool;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Description;

import java.util.function.Function;

@Configuration
public class SearchToolConfig {

    private final BraveSearchService braveSearchService;

    public SearchToolConfig(BraveSearchService braveSearchService) {
        this.braveSearchService = braveSearchService;
    }

    @Bean
    @Description("Search the web for a given query")
    public Function<String, BraveSearchService.SearchResult> search() {
        return query -> {
            try {
                return braveSearchService.search(query);
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        };
    }

    @Bean
    @Description("Search the web for a given query and extract the content of the top result")
    public Function<String, BraveSearchService.SearchResult> searchAndExtractContent() {
        return query -> {
            try {
                return braveSearchService.searchAndExtractContent(query);
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        };
    }
}
