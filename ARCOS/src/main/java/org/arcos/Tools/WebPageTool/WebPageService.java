package org.arcos.Tools.WebPageTool;

import lombok.extern.slf4j.Slf4j;
import org.arcos.Tools.WebCommon.ContentExtractor;
import org.arcos.Tools.WebCommon.PageFetcher;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

@Slf4j
@Service
public class WebPageService {

    private final PageFetcher pageFetcher;
    private final ContentExtractor contentExtractor;

    public record PageSlice(String title, String content, int page, int totalPages) {}

    public WebPageService(PageFetcher pageFetcher, ContentExtractor contentExtractor) {
        this.pageFetcher = pageFetcher;
        this.contentExtractor = contentExtractor;
    }

    /**
     * Lit une page web et retourne la partie demandée de son texte (1-based),
     * découpée sur frontière de mot. Le numéro de partie est clampé dans
     * [1, totalPages]. Stateless : chaque partie re-télécharge la page.
     */
    public PageSlice fetchPage(String url, int page, int maxContentLength, int maxTotalChars, int timeoutSeconds)
            throws IOException, InterruptedException {
        log.info("Fetching web page: {} (partie {})", url, page);

        PageFetcher.FetchedPage fetched = pageFetcher.fetch(url, timeoutSeconds);
        ContentExtractor.PageContent content = contentExtractor.extract(fetched.html());
        if (contentExtractor.looksLikeSpa(fetched.html(), content)) {
            throw new IOException("Page probablement dynamique (JavaScript requis) — contenu non extractible.");
        }

        String fullText = content.fullText();
        if (fullText.length() > maxTotalChars) {
            fullText = fullText.substring(0, maxTotalChars);
        }

        List<String> slices = sliceOnWordBoundaries(fullText, maxContentLength);
        int totalPages = slices.size();
        int effectivePage = Math.min(Math.max(page, 1), totalPages);
        return new PageSlice(content.title(), slices.get(effectivePage - 1), effectivePage, totalPages);
    }

    private static List<String> sliceOnWordBoundaries(String text, int maxContentLength) {
        List<String> slices = new ArrayList<>();
        if (text.isBlank()) {
            slices.add("");
            return slices;
        }
        int start = 0;
        while (start < text.length()) {
            int end = Math.min(start + maxContentLength, text.length());
            if (end < text.length()) {
                int lastSpace = text.lastIndexOf(' ', end);
                if (lastSpace > start) {
                    end = lastSpace;
                }
            }
            slices.add(text.substring(start, end).trim());
            start = end;
        }
        return slices;
    }
}
