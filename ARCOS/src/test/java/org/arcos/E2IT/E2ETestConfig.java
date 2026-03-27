package org.arcos.E2IT;

import org.springframework.ai.transformers.TransformersEmbeddingModel;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.context.annotation.Profile;

/**
 * Registers a local all-MiniLM-L6-v2 embedding model (384 dims) for tests,
 * replacing the Mistral AI embedding model so non-LLM tests need no API key.
 * The model binary is bundled with spring-ai-starter-model-transformers.
 */
@TestConfiguration
@Profile("test-e2e")
public class E2ETestConfig {

    @Bean
    @Primary
    public TransformersEmbeddingModel testEmbeddingModel() throws Exception {
        TransformersEmbeddingModel model = new TransformersEmbeddingModel();
        model.afterPropertiesSet();
        return model;
    }
}
