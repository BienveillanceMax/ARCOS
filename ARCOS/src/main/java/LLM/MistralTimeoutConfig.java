package LLM;

import io.netty.channel.ChannelOption;
import io.netty.handler.timeout.ReadTimeoutHandler;
import io.netty.handler.timeout.WriteTimeoutHandler;
import org.springframework.boot.web.client.RestClientCustomizer;
import org.springframework.boot.web.reactive.function.client.WebClientCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.reactive.ReactorClientHttpConnector;
import reactor.netty.http.client.HttpClient;

import java.time.Duration;
import java.util.concurrent.TimeUnit;

@Configuration
public class MistralTimeoutConfig {

    @Bean
    public WebClientCustomizer mistralWebClientCustomizer() {
        return webClientBuilder -> {
            HttpClient httpClient = HttpClient.create()
                    .option(ChannelOption.CONNECT_TIMEOUT_MILLIS, 120_000)
                    .responseTimeout(Duration.ofSeconds(120))
                    .doOnConnected(conn ->
                            conn.addHandlerLast(new ReadTimeoutHandler(120, TimeUnit.SECONDS))
                                    .addHandlerLast(new WriteTimeoutHandler(120, TimeUnit.SECONDS)));

            webClientBuilder.clientConnector(new ReactorClientHttpConnector(httpClient));
        };
    }

    // Configuration pour le client Bloquant (RestClient) - Utilisé pour les appels classiques
    @Bean
    public RestClientCustomizer mistralRestClientCustomizer() {
        return restClientBuilder -> {
            // Ici, on peut utiliser simple request factory ou réutiliser Netty si on veut être uniforme
            // Pour faire simple comme dans votre exemple :
            restClientBuilder.requestFactory(new org.springframework.http.client.SimpleClientHttpRequestFactory() {{
                setConnectTimeout(120_000);
                setReadTimeout(120_000);
            }});
        };
    }
}