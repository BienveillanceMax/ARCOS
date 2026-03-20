package org.arcos.UserModel.GdeltThemeIndex;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.stream.Collectors;

@Service
@ConditionalOnProperty(name = "arcos.gdelt.enabled", havingValue = "true", matchIfMissing = true)
public class GdeltThemeIndexGate {

    private final GdeltThemeIndexService service;

    public GdeltThemeIndexGate(GdeltThemeIndexService service) {
        this.service = service;
    }

    public List<GdeltKeyword> getAllKeywords() {
        return service.getIndex().values().stream()
                .flatMap(entry -> entry.keywords().stream())
                .distinct()
                .collect(Collectors.toList());
    }

    public List<GdeltKeyword> getKeywordsForPath(String leafPath) {
        GdeltLeafThemes entry = service.getIndex().get(leafPath);
        if (entry == null) {
            return List.of();
        }
        return entry.keywords();
    }

    public int getIndexedLeafCount() {
        return service.getIndex().size();
    }

    public boolean isEmpty() {
        return service.getIndex().isEmpty();
    }
}
