package Memory.LongTermMemory.Models;

import java.util.Map;

public interface QdrantEntry {
    String getId();
    void setId(String id);
    float[] getEmbedding();
    void setEmbedding(float[] embedding);

    Map<String, Object> getPayload();
}
