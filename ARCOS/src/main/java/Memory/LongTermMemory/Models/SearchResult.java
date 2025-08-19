package Memory.LongTermMemory.Models;

/**
 * Représente le résultat d'une recherche vectorielle avec le score de similarité.
 */
public class SearchResult {

    private final MemoryEntry memoryEntry;
    private final OpinionEntry opinionEntry;
    private final double similarityScore;

    public SearchResult(MemoryEntry memoryEntry, OpinionEntry opinionEntry, double similarityScore) {
        this.memoryEntry = memoryEntry;
        this.opinionEntry = opinionEntry;
        this.similarityScore = similarityScore;

    }

    public OpinionEntry getOpinionEntry() {
        return opinionEntry;
    }

    public MemoryEntry getMemoryEntry() {
        return memoryEntry;
    }

    public double getSimilarityScore() {
        return similarityScore;
    }

    @Override
    public String toString() {
        return String.format("SearchResult{score=%.4f, entry=%s}", similarityScore, memoryEntry);
    }
}
