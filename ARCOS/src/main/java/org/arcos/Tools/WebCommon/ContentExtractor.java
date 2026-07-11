package org.arcos.Tools.WebCommon;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Extraction du contenu textuel d'une page HTML (jsoup) : titre, blocs de
 * contenu principal et texte complet normalisé. Détecte les pages dynamiques
 * (SPA) dont le HTML ne contient quasiment aucun texte exploitable.
 */
@Component
public class ContentExtractor {

    private static final int MIN_BLOCK_LENGTH = 40;
    private static final int SPA_MIN_HTML_LENGTH = 5000;
    private static final int SPA_MAX_TEXT_LENGTH = 200;

    public record PageContent(String title, List<String> blocks, String fullText) {}

    public PageContent extract(String html) {
        Document doc = Jsoup.parse(html);

        String title = doc.title();
        if (title.isBlank()) {
            Element ogTitle = doc.selectFirst("meta[property=og:title]");
            title = ogTitle != null ? ogTitle.attr("content") : "";
        }

        doc.select("script, style, nav, footer, header, aside").remove();
        Element main = selectMainContent(doc);

        List<String> blocks = main == null ? List.of()
                : main.select("p, li, h2, h3, td, blockquote").stream()
                        .map(Element::text)
                        .filter(text -> text.length() >= MIN_BLOCK_LENGTH)
                        .toList();

        String fullText = main == null ? ""
                : main.text().replaceAll("\\s+", " ").trim();

        return new PageContent(title.trim(), blocks, fullText);
    }

    /** Page probablement rendue en JavaScript : beaucoup de HTML, presque pas de texte. */
    public boolean looksLikeSpa(String html, PageContent content) {
        return content.fullText().length() < SPA_MAX_TEXT_LENGTH
                && html.length() > SPA_MIN_HTML_LENGTH;
    }

    private Element selectMainContent(Document doc) {
        Element article = doc.selectFirst("article");
        if (article != null) return article;
        Element main = doc.selectFirst("main");
        if (main != null) return main;
        return doc.body();
    }
}
