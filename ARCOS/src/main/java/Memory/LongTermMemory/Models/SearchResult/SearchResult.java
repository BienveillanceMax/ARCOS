package Memory.LongTermMemory.Models.SearchResult;

/**
 * Représente le résultat d'une recherche vectorielle avec le score de similarité.
 * @param <T> Le type de l'entrée retournée par la recherche.
 */
public class SearchResult<T> {

    private final T entry;
    private final double similarityScore;

    public SearchResult(T entry, double similarityScore) {
        this.entry = entry;
        this.similarityScore = similarityScore;
    }

    public T getEntry() {
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
