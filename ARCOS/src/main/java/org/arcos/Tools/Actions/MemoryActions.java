package org.arcos.Tools.Actions;

import org.arcos.Memory.LongTermMemory.Models.DesireEntry;
import org.arcos.Memory.LongTermMemory.Models.MemoryEntry;
import org.arcos.Memory.LongTermMemory.Models.OpinionEntry;
import org.arcos.Memory.LongTermMemory.service.MemoryService;
import org.arcos.Personality.Desires.DesireService;
import org.arcos.Personality.Opinions.OpinionService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.stream.Collectors;

@Slf4j
@Component
public class MemoryActions {

    private final MemoryService memoryService;
    private final OpinionService opinionService;
    private final DesireService desireService;

    public MemoryActions(MemoryService memoryService, OpinionService opinionService, DesireService desireService) {
        this.memoryService = memoryService;
        this.opinionService = opinionService;
        this.desireService = desireService;
    }

    @Tool(name = "Chercher_dans_ma_memoire",
          description = "Recherche dans la mémoire interne de Calcifer : souvenirs, opinions ou désirs. "
                      + "Utilise ce tool quand on te demande ce que tu penses, ce dont tu te souviens, ou tes envies.")
    public ActionResult searchMemory(String query, String type) {
        long startTime = System.currentTimeMillis();

        String resolvedType = resolveType(type);
        log.info("Recherche mémoire interne — type: {}, query: {}", resolvedType, query);

        try {
            List<String> results = switch (resolvedType) {
                case "OPINION" -> formatOpinions(opinionService.searchOpinions(query));
                case "DESIR" -> formatDesires(desireService.getPendingDesires());
                default -> formatMemories(memoryService.searchMemories(query, 5));
            };

            if (results.isEmpty()) {
                return ActionResult.successWithMessage("Aucun résultat trouvé.")
                        .addMetadata("query", query)
                        .addMetadata("type", resolvedType)
                        .withExecutionTime(System.currentTimeMillis() - startTime);
            }

            return ActionResult.success(results, results.size() + " résultat(s) trouvé(s)")
                    .addMetadata("query", query)
                    .addMetadata("type", resolvedType)
                    .withExecutionTime(System.currentTimeMillis() - startTime);

        } catch (Exception e) {
            log.error("Erreur lors de la recherche mémoire : {}", e.getMessage());
            return ActionResult.failure("Erreur lors de la recherche mémoire : " + e.getMessage(), e)
                    .withExecutionTime(System.currentTimeMillis() - startTime);
        }
    }

    private String resolveType(String type) {
        if (type == null || type.isBlank()) {
            return "SOUVENIR";
        }
        String upper = type.trim().toUpperCase();
        return switch (upper) {
            case "OPINION", "DESIR" -> upper;
            default -> "SOUVENIR";
        };
    }

    private List<String> formatMemories(List<MemoryEntry> memories) {
        return memories.stream()
                .map(m -> String.format("[%s] %s (satisfaction: %.1f, sujet: %s)",
                        m.getTimestamp(), m.getContent(), m.getSatisfaction(), m.getSubject()))
                .collect(Collectors.toList());
    }

    private List<String> formatOpinions(List<OpinionEntry> opinions) {
        return opinions.stream()
                .map(o -> String.format("Sujet: %s — %s (polarité: %.2f, confiance: %.2f, stabilité: %.2f)",
                        o.getSubject(), o.getSummary(), o.getPolarity(), o.getConfidence(), o.getStability()))
                .collect(Collectors.toList());
    }

    private List<String> formatDesires(List<DesireEntry> desires) {
        return desires.stream()
                .map(d -> String.format("%s — %s (intensité: %.2f, statut: %s)",
                        d.getLabel(), d.getDescription(), d.getIntensity(), d.getStatus()))
                .collect(Collectors.toList());
    }
}
