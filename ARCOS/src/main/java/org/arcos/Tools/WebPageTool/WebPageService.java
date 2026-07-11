package org.arcos.Tools.WebPageTool;

import lombok.extern.slf4j.Slf4j;
import org.arcos.Tools.WebCommon.ContentExtractor;
import org.arcos.Tools.WebCommon.PageFetcher;
import org.springframework.stereotype.Service;

import java.io.IOException;

@Slf4j
@Service
public class WebPageService {

    private final PageFetcher pageFetcher;
    private final ContentExtractor contentExtractor;

    public WebPageService(PageFetcher pageFetcher, ContentExtractor contentExtractor) {
        this.pageFetcher = pageFetcher;
        this.contentExtractor = contentExtractor;
    }

    public String fetchAndExtract(String url, int maxContentLength, int timeoutSeconds)
            throws IOException, InterruptedException {
        log.info("Fetching web page: {}", url);

        PageFetcher.FetchedPage page = pageFetcher.fetch(url, timeoutSeconds);
        ContentExtractor.PageContent content = contentExtractor.extract(page.html());

        String text = content.fullText();
        if (text.length() > maxContentLength) {
            text = text.substring(0, maxContentLength) + " ... [contenu tronqué]";
        }
        return text;
    }
}
