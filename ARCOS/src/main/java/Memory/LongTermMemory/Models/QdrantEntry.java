package Memory.LongTermMemory.Models;

import com.fasterxml.jackson.databind.node.ObjectNode;

public interface QdrantEntry {
    String getId();
    void setId(String id);
    float[] getEmbedding();
    void setEmbedding(float[] embedding);

    ObjectNode getPayload();
}
