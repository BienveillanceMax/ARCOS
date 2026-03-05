package org.arcos.IntegrationTests.UserModel;

import org.arcos.UserModel.Embedding.LocalEmbeddingService;
import org.arcos.UserModel.UserModelAutoConfiguration;
import org.arcos.UserModel.UserModelProperties;
import org.mockito.Mockito;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.FilterType;

import java.util.Random;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

/**
 * Test-only configuration that provides just enough Spring context
 * to exercise the real UserModel pipeline without infrastructure
 * (Qdrant, Mistral API, ONNX model download, etc.).
 *
 * Only {@link LocalEmbeddingService} is mocked — all other beans are real.
 */
@Configuration
@EnableConfigurationProperties(UserModelProperties.class)
@ComponentScan(
        basePackages = "org.arcos.UserModel",
        excludeFilters = @ComponentScan.Filter(
                type = FilterType.ASSIGNABLE_TYPE,
                classes = {LocalEmbeddingService.class, UserModelAutoConfiguration.class}
        )
)
public class UserModelTestConfiguration {

    @Bean
    public LocalEmbeddingService localEmbeddingService() {
        LocalEmbeddingService mock = Mockito.mock(LocalEmbeddingService.class);

        when(mock.isReady()).thenReturn(true);

        when(mock.embed(anyString())).thenAnswer(invocation -> {
            String text = invocation.getArgument(0);
            float[] embedding = new float[384];
            Random random = new Random(text.hashCode());
            for (int i = 0; i < embedding.length; i++) {
                embedding[i] = random.nextFloat() * 2 - 1;
            }
            return embedding;
        });

        when(mock.cosineSimilarity(any(float[].class), any(float[].class))).thenAnswer(invocation -> {
            float[] a = invocation.getArgument(0);
            float[] b = invocation.getArgument(1);
            if (a == null || b == null || a.length != b.length || a.length == 0) return 0.0;
            double dotProduct = 0.0, normA = 0.0, normB = 0.0;
            for (int i = 0; i < a.length; i++) {
                dotProduct += a[i] * b[i];
                normA += a[i] * a[i];
                normB += b[i] * b[i];
            }
            double denominator = Math.sqrt(normA) * Math.sqrt(normB);
            return denominator == 0.0 ? 0.0 : dotProduct / denominator;
        });

        return mock;
    }
}
