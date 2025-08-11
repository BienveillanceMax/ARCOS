package Tools.NewsAnalysisTool.Config;


import Tools.NewsAnalysisTool.models.GdeltConfig;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestTemplate;
import org.springframework.http.client.HttpComponentsClientHttpRequestFactory;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.impl.client.CloseableHttpClient;

/**
 * Configuration Spring pour le service GDELT
 */
@Configuration
public class GdeltServiceConfiguration {

    @Bean
    public RestTemplate restTemplate() {
        CloseableHttpClient httpClient = HttpClients.custom()
                .setMaxConnTotal(20)
                .setMaxConnPerRoute(10)
                .build();

        HttpComponentsClientHttpRequestFactory factory = new HttpComponentsClientHttpRequestFactory();
        factory.setConnectTimeout(30000); // 30 secondes
        factory.setReadTimeout(60000); // 60 secondes

        return new RestTemplate(factory);
    }

    @Bean
    @ConfigurationProperties(prefix = "gdelt")
    public GdeltConfig gdeltConfig() {
        return GdeltConfig.builder()
                .apiBaseUrl("https://api.gdeltproject.org")
                .defaultTimeout(30000)
                .maxRetries(3)
                .requestDelay(1000) // 1 seconde entre les requêtes
                .enableCaching(true)
                .cacheDirectory("./gdelt-cache")
                .build();
    }
}

