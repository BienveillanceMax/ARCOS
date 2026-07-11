package org.arcos.Tools.SearchTool;

import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Formate les résultats de recherche (snippets + pages lues en profondeur)
 * en texte brut, labels français — jamais de markdown : la réponse finale
 * est prononcée par le TTS.
 */
@Component
public class SearchResultFormatter {

    private static final int MIN_TRUNCATED_SECTION_CHARS = 50;

    public List<String> format(String query,
                               List<BraveSearchService.SearchResultItem> items,
                               List<DeepSearchService.DeepPage> deepPages,
                               int totalCharsBudget) {
        if (items.isEmpty()) {
            return List.of("Aucun résultat trouvé pour « " + query + " ».");
        }

        List<String> sections = new ArrayList<>();
        String header = "Recherche « " + query + " » : " + items.size() + " résultats"
                + (deepPages.isEmpty() ? "." : ", " + deepPages.size() + " pages lues en détail.");
        sections.add(header);

        for (DeepSearchService.DeepPage page : deepPages) {
            sections.add("Contenu de : " + page.title()
                    + "\nSource : " + page.url()
                    + "\n" + page.relevantContent());
        }

        Set<String> deepUrls = deepPages.stream()
                .map(DeepSearchService.DeepPage::url)
                .collect(Collectors.toSet());

        int index = 1;
        for (BraveSearchService.SearchResultItem item : items) {
            if (deepUrls.contains(item.getUrl())) {
                continue; // déjà présenté en détail, pas de doublon snippet
            }
            StringBuilder section = new StringBuilder("Résultat ").append(index++)
                    .append(" : ").append(item.getTitle());
            item.getPublishedDate().ifPresent(date -> section.append(" (").append(date).append(")"));
            if (item.getDescription() != null && !item.getDescription().isBlank()) {
                section.append("\n").append(item.getDescription());
            }
            if (!item.getExtraSnippets().isEmpty()) {
                section.append(" — ").append(String.join(" — ", item.getExtraSnippets()));
            }
            section.append("\nSource : ").append(item.getUrl());
            sections.add(section.toString());
        }

        return applyBudget(sections, totalCharsBudget);
    }

    /** Coupe la liste au budget total : la section à la frontière est tronquée, la suite abandonnée. */
    private static List<String> applyBudget(List<String> sections, int totalCharsBudget) {
        List<String> bounded = new ArrayList<>();
        int total = 0;
        for (String section : sections) {
            if (total + section.length() <= totalCharsBudget) {
                bounded.add(section);
                total += section.length();
            } else {
                int remaining = totalCharsBudget - total;
                if (remaining >= MIN_TRUNCATED_SECTION_CHARS) {
                    bounded.add(section.substring(0, remaining) + " [contenu tronqué]");
                }
                break;
            }
        }
        return bounded;
    }
}
