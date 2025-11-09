package Memory.LongTermMemory.Qdrant;

import Memory.LongTermMemory.Models.MemoryEntry;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.testcontainers.qdrant.QdrantContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import static org.junit.jupiter.api.Assertions.*;

@Testcontainers
class QdrantClientIT {

    @Container
    private static final QdrantContainer qdrant = new QdrantContainer("qdrant/qdrant:v1.7.4");

    private static QdrantClient qdrantClient;

    @BeforeAll
    static void setUp() {
        qdrant.start();
        qdrantClient = new QdrantClient(qdrant.getHost(), qdrant.getGrpcPort());
    }

    @AfterAll
    static void tearDown() {
        qdrantClient.close();
        qdrant.stop();
    }

    @Test
    void testCreateAndGetCollection() {
        String collectionName = "test-collection";
        assertTrue(qdrantClient.createCollection(collectionName, 4));
        assertTrue(qdrantClient.collectionExists(collectionName));
    }

    @Test
    void testUpsertAndGetPoint() {
        String collectionName = "test-upsert-collection";
        qdrantClient.createCollection(collectionName, 4);

        MemoryEntry memoryEntry = new MemoryEntry();
        memoryEntry.setId("test-point");
        memoryEntry.setContent("Test content");
        memoryEntry.setEmbedding(new float[]{0.1f, 0.2f, 0.3f, 0.4f});

        assertTrue(qdrantClient.upsertPoint(collectionName, memoryEntry));

        MemoryEntry retrievedEntry = qdrantClient.getPoint(collectionName, "test-point", (jsonNode) -> qdrantClient.parseMemoryEntry(jsonNode).getEntry());
        assertNotNull(retrievedEntry);
        assertEquals("Test content", retrievedEntry.getContent());
    }

    @Test
    void testSearch() {
        String collectionName = "test-search-collection";
        qdrantClient.createCollection(collectionName, 2);

        MemoryEntry memoryEntry1 = new MemoryEntry();
        memoryEntry1.setId("point1");
        memoryEntry1.setContent("one");
        memoryEntry1.setEmbedding(new float[]{0.1f, 0.2f});
        qdrantClient.upsertPoint(collectionName, memoryEntry1);

        MemoryEntry memoryEntry2 = new MemoryEntry();
        memoryEntry2.setId("point2");
        memoryEntry2.setContent("two");
        memoryEntry2.setEmbedding(new float[]{0.8f, 0.9f});
        qdrantClient.upsertPoint(collectionName, memoryEntry2);

        var results = qdrantClient.search(collectionName, new float[]{0.7f, 0.8f}, 1, qdrantClient::parseMemoryEntry);
        assertEquals(1, results.size());
        assertEquals("two", results.get(0).getEntry().getContent());
    }
}
