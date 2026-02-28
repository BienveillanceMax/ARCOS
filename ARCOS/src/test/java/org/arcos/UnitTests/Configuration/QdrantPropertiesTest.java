package org.arcos.UnitTests.Configuration;

import org.arcos.Configuration.QdrantProperties;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class QdrantPropertiesTest {

    @Test
    void defaultValues_areCorrect() {
        QdrantProperties props = new QdrantProperties();

        assertEquals(1024, props.getEmbeddingDimension());
        assertEquals("COSINE", props.getDistanceMetric());
        assertEquals(6, props.getMaxRetries());
        assertEquals(1_000L, props.getInitialBackoffMs());
        assertEquals(30_000L, props.getMaxBackoffMs());
    }

    @Test
    void setValues_updateCorrectly() {
        QdrantProperties props = new QdrantProperties();

        props.setEmbeddingDimension(768);
        props.setDistanceMetric("EUCLID");
        props.setMaxRetries(3);
        props.setInitialBackoffMs(500L);
        props.setMaxBackoffMs(10_000L);

        assertEquals(768, props.getEmbeddingDimension());
        assertEquals("EUCLID", props.getDistanceMetric());
        assertEquals(3, props.getMaxRetries());
        assertEquals(500L, props.getInitialBackoffMs());
        assertEquals(10_000L, props.getMaxBackoffMs());
    }
}
