package org.arcos.Tools.WebPageTool;

import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import lombok.extern.slf4j.Slf4j;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.net.InetAddress;
import java.net.URI;
import java.net.UnknownHostException;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

@Slf4j
@Service
public class WebPageService {

    private static final int MAX_REDIRECTS = 5;
    private static final String METADATA_IP = "169.254.169.254";

    private final HttpClient httpClient;

    public WebPageService() {
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(10))
                .followRedirects(HttpClient.Redirect.NEVER) // SSRF: validate every hop ourselves
                .build();
    }

    @CircuitBreaker(name = "webPage")
    public String fetchAndExtract(String url, int maxContentLength, int timeoutSeconds)
            throws IOException, InterruptedException {
        log.info("Fetching web page: {}", url);

        URI uri = URI.create(url);
        HttpResponse<String> response = null;

        for (int hop = 0; hop <= MAX_REDIRECTS; hop++) {
            assertHostAllowed(uri); // re-validate after each redirect

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(uri)
                    .header("User-Agent", "ARCOS/1.0")
                    .timeout(Duration.ofSeconds(timeoutSeconds))
                    .GET()
                    .build();

            response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

            int status = response.statusCode();
            if (status >= 300 && status < 400) {
                String location = response.headers().firstValue("Location").orElse(null);
                if (location == null) break;
                uri = uri.resolve(location); // resolve relative redirects
                continue;
            }
            break;
        }

        int finalStatus = response.statusCode();
        if (finalStatus >= 300 && finalStatus < 400) {
            // Redirect budget exhausted — do NOT fall through and parse the 3xx body as page content.
            throw new IOException("Trop de redirections (max " + MAX_REDIRECTS + ") pour " + url);
        }
        if (finalStatus >= 400) {
            throw new IOException("HTTP " + finalStatus + " pour " + url);
        }

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

    /**
     * SSRF guard : refuse les hôtes résolvant vers une adresse privée, loopback,
     * link-local, wildcard ou l'endpoint de métadonnées cloud (169.254.169.254).
     */
    private void assertHostAllowed(URI uri) throws IOException {
        String scheme = uri.getScheme();
        if (!"http".equalsIgnoreCase(scheme) && !"https".equalsIgnoreCase(scheme)) {
            // Per-hop scheme check — also keeps a redirect to ftp://, file:// etc. from escaping
            // as an UNCHECKED IllegalArgumentException out of HttpRequest.newBuilder(), which
            // WebPageActions' catch(IOException) would NOT map to an ActionResult.failure.
            throw new IOException("Hôte interdit : schéma non supporté : " + scheme);
        }
        String host = uri.getHost();
        if (host == null || host.isBlank()) {
            throw new IOException("Hôte interdit : URL sans hôte valide");
        }
        InetAddress[] resolved;
        try {
            resolved = InetAddress.getAllByName(host);
        } catch (UnknownHostException e) {
            throw new IOException("Hôte interdit : résolution DNS impossible pour " + host, e);
        }
        for (InetAddress addr : resolved) {
            if (addr.isLoopbackAddress()
                    || addr.isSiteLocalAddress()      // 10/8, 172.16/12, 192.168/16
                    || addr.isLinkLocalAddress()      // 169.254/16 (couvre aussi l'IP métadonnées)
                    || addr.isAnyLocalAddress()
                    || isCgnatOrUla(addr)             // 100.64/10 (CGNAT), fc00::/7 (IPv6 ULA)
                    || METADATA_IP.equals(addr.getHostAddress())) {
                throw new IOException("Hôte interdit (réseau privé/métadonnées) : "
                        + host + " -> " + addr.getHostAddress());
            }
        }
    }

    /** 100.64.0.0/10 (CGNAT) et fc00::/7 (IPv6 ULA) — non couverts par isSiteLocalAddress(). */
    private static boolean isCgnatOrUla(InetAddress addr) {
        byte[] b = addr.getAddress();
        if (b.length == 4) {
            return (b[0] & 0xFF) == 100 && (b[1] & 0xC0) == 0x40;
        }
        return (b[0] & 0xFE) == 0xFC;
    }

    private String extractMainContent(Document doc) {
        Element article = doc.selectFirst("article");
        if (article != null) return article.text();
        Element main = doc.selectFirst("main");
        if (main != null) return main.text();
        Element body = doc.body();
        return body != null ? body.text() : "";
    }
}
