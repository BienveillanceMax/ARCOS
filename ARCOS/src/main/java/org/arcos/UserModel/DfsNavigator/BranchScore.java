package org.arcos.UserModel.DfsNavigator;

public record BranchScore(String key, String description, float score) implements Comparable<BranchScore> {
    @Override
    public int compareTo(BranchScore other) {
        return Float.compare(other.score, this.score); // descending
    }
}
