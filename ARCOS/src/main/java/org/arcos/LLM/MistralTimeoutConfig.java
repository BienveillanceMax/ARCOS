package org.arcos.LLM;

import io.netty.channel.ChannelOption;
import io.netty.handler.timeout.ReadTimeoutHandler;
import io.netty.handler.timeout.WriteTimeoutHandler;
import org.springframework.boot.web.reactive.function.client.WebClientCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Scope;
import org.springframework.http.client.ReactorClientHttpRequestFactory;
import org.springframework.http.client.reactive.ReactorClientHttpConnector;
import org.springframework.web.client.RestClient;
import reactor.netty.http.client.HttpClient;

import java.time.Duration;
import java.util.concurrent.TimeUnit;

/**
 * HTTP timeout configuration for LLM API calls.
 * <p>
 * Spring Boot's {@code HttpClientAutoConfiguration} (which creates the auto-configured
 * {@code RestClient.Builder} with timeout support) is gated behind
 * {@code @Conditional(NotReactiveWebApplicationCondition)} — it does not activate in
 * WebFlux apps. Since ARCOS uses {@code spring-boot-starter-webflux}, no auto-configured
 * {@code RestClient.Builder} bean exists. Spring AI's {@code MistralAiChatAutoConfiguration}
 * then falls back to a plain {@code RestClient.builder()} with Reactor Netty's default
 * ~10s read timeout — far too short for LLM calls with tool execution.
 * <p>
 * This config provides both a {@code RestClient.Builder} (for blocking calls) and a
 * {@code WebClientCustomizer} (for streaming calls) with 120s timeouts.
 */
@Configuration
public class MistralTimeoutConfig {

    private static final Duration READ_TIMEOUT = Duration.ofSeconds(120);
    private static final Duration CONNECT_TIMEOUT = Duration.ofSeconds(10);

    /**
     * WebClient (streaming path: conversations).
     */
    @Bean
    public WebClientCustomizer mistralWebClientCustomizer() {
        return webClientBuilder -> {
            HttpClient httpClient = HttpClient.create()
                    .option(ChannelOption.CONNECT_TIMEOUT_MILLIS, (int) CONNECT_TIMEOUT.toMillis())
                    .responseTimeout(READ_TIMEOUT)
                    .doOnConnected(conn ->
                            conn.addHandlerLast(new ReadTimeoutHandler(READ_TIMEOUT.toSeconds(), TimeUnit.SECONDS))
                                    .addHandlerLast(new WriteTimeoutHandler(READ_TIMEOUT.toSeconds(), TimeUnit.SECONDS)));
            webClientBuilder.clientConnector(new ReactorClientHttpConnector(httpClient));
        };
    }

    /**
     * RestClient.Builder (blocking path: initiatives, tool calls).
     * Fills the gap left by the disabled {@code RestClientAutoConfiguration} in WebFlux apps.
     * Spring AI's {@code ObjectProvider<RestClient.Builder>} picks this up instead of
     * falling back to a plain {@code RestClient.builder()}.
     */
    @Bean
    @Scope("prototype")
    public RestClient.Builder restClientBuilder() {
        ReactorClientHttpRequestFactory factory = new ReactorClientHttpRequestFactory();
        factory.setConnectTimeout(CONNECT_TIMEOUT);
        factory.setReadTimeout(READ_TIMEOUT);
        return RestClient.builder().requestFactory(factory);
    }
}
