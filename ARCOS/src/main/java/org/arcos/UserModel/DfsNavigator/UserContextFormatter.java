package org.arcos.UserModel.DfsNavigator;

import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.Optional;

@Component
public class UserContextFormatter {

    private final BranchDescriptionRegistry registry;

    public UserContextFormatter(BranchDescriptionRegistry registry) {
        this.registry = registry;
    }

    /**
     * Format leaves into a text block for prompt injection.
     * @param leaves map of dot-path to value
     * @return formatted markdown block, or empty string if no leaves
     */
    public String format(Map<String, String> leaves) {
        if (leaves == null || leaves.isEmpty()) {
            return "";
        }

        StringBuilder sb = new StringBuilder("## Profil utilisateur\n");
        for (Map.Entry<String, String> entry : leaves.entrySet()) {
            String readable = humanReadablePath(entry.getKey());
            sb.append("- ").append(readable).append(" : ").append(entry.getValue()).append("\n");
        }
        return sb.toString();
    }

    /**
     * Convert a dot-path like "1_Biological_Characteristics.Physical_Appearance.Hair.Scalp_Hair"
     * to a human-readable form like "Apparence physique > Cheveux".
     * Uses registry descriptions, extracting the short name (before ":").
     */
    public String humanReadablePath(String dotPath) {
        String[] segments = dotPath.split("\\.");
        if (segments.length < 2) {
            return dotPath;
        }

        // Second segment is the L1 key
        String l1Key = segments[1];
        String l1Short = getL1ShortName(l1Key);

        if (segments.length < 3) {
            return l1Short;
        }

        // Third segment is potentially an L2 key
        String l2Key = segments[2];
        String l2Short = getL2ShortName(l1Key, l2Key);

        if (l2Short != null) {
            return l1Short + " > " + l2Short;
        }

        // Fallback: use the segment name with underscores replaced
        return l1Short + " > " + l2Key.replace("_", " ");
    }

    private String getL1ShortName(String l1Key) {
        return registry.getL1Branches().stream()
                .filter(e -> e.key().equals(l1Key))
                .findFirst()
                .map(e -> extractShortName(e.description()))
                .orElse(l1Key.replace("_", " "));
    }

    private String getL2ShortName(String l1Key, String l2Key) {
        Optional<L2Group> group = registry.getL2GroupForParent(l1Key);
        if (group.isEmpty()) {
            return null;
        }
        return group.get().branches().stream()
                .filter(b -> b.key().equals(l2Key))
                .findFirst()
                .map(b -> extractShortName(b.description()))
                .orElse(null);
    }

    /**
     * Extract the short name from a description string.
     * E.g., "Apparence physique : morphologie, traits..." → "Apparence physique"
     */
    private String extractShortName(String description) {
        int colonIdx = description.indexOf(':');
        if (colonIdx > 0) {
            return description.substring(0, colonIdx).trim();
        }
        return description;
    }
}
