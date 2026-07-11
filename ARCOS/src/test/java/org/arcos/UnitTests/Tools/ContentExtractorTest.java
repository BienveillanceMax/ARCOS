package org.arcos.UnitTests.Tools;

import org.arcos.Tools.WebCommon.ContentExtractor;
import org.arcos.Tools.WebCommon.ContentExtractor.PageContent;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ContentExtractorTest {

    private final ContentExtractor extractor = new ContentExtractor();

    @Test
    @DisplayName("Given a page with title, When extracted, Then title is returned")
    void extract_WithTitle_ShouldReturnTitle() {
        // Given
        String html = "<html><head><title>Mon Article</title></head>"
                + "<body><p>Un paragraphe suffisamment long pour être conservé ici.</p></body></html>";

        // When
        PageContent content = extractor.extract(html);

        // Then
        assertThat(content.title()).isEqualTo("Mon Article");
    }

    @Test
    @DisplayName("Given no <title> but og:title, When extracted, Then og:title is used")
    void extract_WithOgTitleFallback_ShouldReturnOgTitle() {
        // Given
        String html = "<html><head><meta property=\"og:title\" content=\"Titre Social\"></head>"
                + "<body><p>Contenu de la page assez long pour être un bloc valide.</p></body></html>";

        // When
        PageContent content = extractor.extract(html);

        // Then
        assertThat(content.title()).isEqualTo("Titre Social");
    }

    @Test
    @DisplayName("Given an article element, When extracted, Then article content takes priority over body noise")
    void extract_WithArticle_ShouldPrioritizeArticleContent() {
        // Given
        String html = "<html><body>"
                + "<div>Bruit de page hors article, menus et liens divers partout.</div>"
                + "<article><p>Le vrai contenu de l'article, suffisamment long pour compter.</p></article>"
                + "</body></html>";

        // When
        PageContent content = extractor.extract(html);

        // Then
        assertThat(content.fullText()).contains("vrai contenu de l'article");
        assertThat(content.fullText()).doesNotContain("Bruit de page");
    }

    @Test
    @DisplayName("Given short and long blocks, When extracted, Then blocks under 40 chars are filtered out")
    void extract_ShouldFilterShortBlocks() {
        // Given
        String html = "<html><body>"
                + "<p>Court.</p>"
                + "<p>Ce paragraphe est assez long pour dépasser le seuil de quarante caractères.</p>"
                + "<li>Un item de liste également assez long pour être conservé dans les blocs.</li>"
                + "</body></html>";

        // When
        PageContent content = extractor.extract(html);

        // Then
        assertThat(content.blocks()).hasSize(2);
        assertThat(content.blocks()).noneMatch(b -> b.equals("Court."));
    }

    @Test
    @DisplayName("Given script/nav/footer noise, When extracted, Then noise is removed from text")
    void extract_ShouldRemoveNoiseElements() {
        // Given
        String html = "<html><body>"
                + "<nav>Accueil Contact Menu</nav>"
                + "<script>console.log('tracking');</script>"
                + "<p>Le contenu principal de la page, long et pertinent pour le lecteur.</p>"
                + "<footer>Copyright 2026 tous droits réservés</footer>"
                + "</body></html>";

        // When
        PageContent content = extractor.extract(html);

        // Then
        assertThat(content.fullText()).contains("contenu principal");
        assertThat(content.fullText()).doesNotContain("tracking");
        assertThat(content.fullText()).doesNotContain("Copyright");
        assertThat(content.fullText()).doesNotContain("Accueil Contact");
    }

    @Test
    @DisplayName("Given big HTML with almost no text, When checked, Then looksLikeSpa is true")
    void looksLikeSpa_WithEmptyBodyAndBigHtml_ShouldBeTrue() {
        // Given — 6k of markup, no text content
        String html = "<html><head>" + "<script src='app.js'></script>".repeat(200)
                + "</head><body><div id=\"root\"></div></body></html>";

        // When
        PageContent content = extractor.extract(html);

        // Then
        assertThat(extractor.looksLikeSpa(html, content)).isTrue();
    }

    @Test
    @DisplayName("Given a normal text page, When checked, Then looksLikeSpa is false")
    void looksLikeSpa_WithNormalPage_ShouldBeFalse() {
        // Given
        String paragraph = "<p>Un paragraphe de contenu réel avec suffisamment de texte pour un lecteur.</p>";
        String html = "<html><body>" + paragraph.repeat(30) + "</body></html>";

        // When
        PageContent content = extractor.extract(html);

        // Then
        assertThat(extractor.looksLikeSpa(html, content)).isFalse();
    }
}
