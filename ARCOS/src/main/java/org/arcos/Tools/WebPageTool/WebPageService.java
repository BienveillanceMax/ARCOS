package org.arcos.Tools.WebPageTool;

import lombok.extern.slf4j.Slf4j;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

@Slf4j
@Service
public class WebPageService {

    private final HttpClient httpClient;

    public WebPageService() {
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(10))
                .followRedirects(HttpClient.Redirect.NORMAL)
                .build();
    }

    public String fetchAndExtract(String url, int maxContentLength, int timeoutSeconds)
            throws IOException, InterruptedException {
        log.info("Fetching web page: {}", url);

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .header("User-Agent", "ARCOS/1.0")
                .timeout(Duration.ofSeconds(timeoutSeconds))
                .GET()
                .build();

        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        String html = response.body();

        Document doc = Jsoup.parse(html);

        doc.select("script, style, nav, footer, header, aside").remove();

        String text = extractMainContent(doc);

        text = text.replaceAll("\\s+", " ").trim();

        if (text.length() > maxContentLength) {
            text = text.substring(0, maxContentLength) + " ... [contenu tronqué]";
        }

        return text;
    }

    private String extractMainContent(Document doc) {
        Element article = doc.selectFirst("article");
        if (article != null) {
            return article.text();
        }

        Element main = doc.selectFirst("main");
        if (main != null) {
            return main.text();
        }

        Element body = doc.body();
        return body != null ? body.text() : "";
    }
}
