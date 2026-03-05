package org.arcos.UnitTests.UserModel;

import org.arcos.UserModel.Embedding.LocalEmbeddingService;
import org.arcos.UserModel.UserModelProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class LocalEmbeddingServiceTest {

    private LocalEmbeddingService service;

    @BeforeEach
    void setUp() {
        UserModelProperties properties = new UserModelProperties();
        service = new LocalEmbeddingService(properties);
        // Do not call init() — model not loaded, so isReady() stays false
    }

    @Test
    void isReady_ShouldBeFalseBeforeInit() {
        assertFalse(service.isReady());
    }

    @Test
    void embed_ShouldReturnNullWhenNotReady() {
        assertNull(service.embed("test text"));
    }

    @Test
    void cosineSimilarity_IdenticalVectors_ShouldReturn1() {
        float[] a = {1.0f, 2.0f, 3.0f};
        float[] b = {1.0f, 2.0f, 3.0f};
        assertEquals(1.0, service.cosineSimilarity(a, b), 0.0001);
    }

    @Test
    void cosineSimilarity_OrthogonalVectors_ShouldReturn0() {
        float[] a = {1.0f, 0.0f};
        float[] b = {0.0f, 1.0f};
        assertEquals(0.0, service.cosineSimilarity(a, b), 0.0001);
    }

    @Test
    void cosineSimilarity_OppositeVectors_ShouldReturnNeg1() {
        float[] a = {1.0f, 2.0f, 3.0f};
        float[] b = {-1.0f, -2.0f, -3.0f};
        assertEquals(-1.0, service.cosineSimilarity(a, b), 0.0001);
    }

    @Test
    void cosineSimilarity_NullVectors_ShouldReturn0() {
        assertEquals(0.0, service.cosineSimilarity(null, new float[]{1.0f}));
        assertEquals(0.0, service.cosineSimilarity(new float[]{1.0f}, null));
        assertEquals(0.0, service.cosineSimilarity(null, null));
    }

    @Test
    void cosineSimilarity_DifferentLengths_ShouldReturn0() {
        float[] a = {1.0f, 2.0f};
        float[] b = {1.0f, 2.0f, 3.0f};
        assertEquals(0.0, service.cosineSimilarity(a, b));
    }

    @Test
    void cosineSimilarity_ZeroVector_ShouldReturn0() {
        float[] a = {0.0f, 0.0f, 0.0f};
        float[] b = {1.0f, 2.0f, 3.0f};
        assertEquals(0.0, service.cosineSimilarity(a, b));
    }
}
