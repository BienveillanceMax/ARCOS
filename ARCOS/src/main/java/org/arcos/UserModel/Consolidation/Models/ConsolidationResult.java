package org.arcos.UserModel.Consolidation.Models;

import java.nio.file.Path;

public record ConsolidationResult(
        int totalScanned,
        int conflictsResolved,
        int duplicatesMerged,
        int leavesArchived,
        int leavesRewritten,
        int parseErrors,
        int timeouts,
        boolean rolledBack,
        Path snapshotPath,
        long durationMs
) {
}
