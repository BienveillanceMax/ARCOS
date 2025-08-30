package Memory.LongTermMemory.Models.SearchResult;

import Memory.LongTermMemory.Models.DesireEntry;
import Memory.LongTermMemory.Models.MemoryEntry;
import Memory.LongTermMemory.Models.OpinionEntry;

public class DesireSearchResult
{
    private final DesireEntry entry;
    private final double similarityScore;

    public DesireSearchResult(DesireEntry desireEntry, double similarityScore) {
        this.similarityScore = similarityScore;
        entry = desireEntry;

    }

    public DesireEntry getEntry() {
        return entry;
    }

    public double getSimilarityScore() {
        return similarityScore;
    }

    @Override
    public String toString() {
        return String.format("SearchResult{score=%.4f, entry=%s}", similarityScore, entry.toString());
    }
}
