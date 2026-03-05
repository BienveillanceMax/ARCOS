package org.arcos.UserModel.Models;

public record ObservationCandidate(
        String text,
        TreeBranch branch,
        String replacesText,
        boolean explicit,
        float emotionalImportance
) {

    public ObservationCandidate(String text, TreeBranch branch, String replacesText) {
        this(text, branch, replacesText, false, 0.5f);
    }

    public static ObservationCandidate fromDto(ObservationCandidateDto dto) {
        TreeBranch branch;
        try {
            branch = TreeBranch.valueOf(dto.getBranche().toUpperCase());
        } catch (IllegalArgumentException | NullPointerException e) {
            branch = TreeBranch.INTERETS;
        }
        boolean isExplicit = dto.isExplicite();
        float importance = isExplicit ? 0.8f : 0.5f;
        return new ObservationCandidate(
                dto.getObservation(),
                branch,
                dto.getRemplace(),
                isExplicit,
                importance
        );
    }
}
