package org.arcos.Tools.SearchTool;

import lombok.extern.slf4j.Slf4j;
import org.arcos.Tools.WebCommon.ContentExtractor;
import org.arcos.Tools.WebCommon.PageFetcher;
import org.arcos.Tools.WebCommon.QueryRelevanceRanker;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * Enrichit une recherche web en lisant en parallèle le contenu des meilleures
 * pages de résultats (deep search). Chaque échec de page dégrade silencieusement
 * vers les snippets : aucune exception ne sort de {@link #enrich}.
 */
@Slf4j
@Service
public class DeepSearchService {

    private static final Set<String> EXCLUDED_EXTENSIONS = Set.of(
            ".pdf", ".doc", ".docx", ".ppt", ".pptx", ".xls", ".xlsx", ".zip");

    /** Hosts SPA/anti-bot dont le HTML est inexploitable sans JavaScript. */
    private static final List<String> EXCLUDED_HOSTS = List.of(
            "youtube.com", "youtu.be", "x.com", "twitter.com", "facebook.com",
            "instagram.com", "tiktok.com", "linkedin.com", "pinterest.com",
            "pinterest.fr", "reddit.com");

    private final PageFetcher pageFetcher;
    private final ContentExtractor contentExtractor;
    private final QueryRelevanceRanker ranker;
    private final int pagesToKeep;
    private final int candidatesToFetch;
    private final int perPageChars;
    private final int fetchTimeoutSeconds;

    public record DeepPage(String url, String title, String relevantContent) {}

    public record DeepSearchOutcome(List<DeepPage> pages, List<String> warnings) {}

    public DeepSearchService(PageFetcher pageFetcher,
                             ContentExtractor contentExtractor,
                             QueryRelevanceRanker ranker,
                             @Value("${arcos.search.deep.pages:2}") int pagesToKeep,
                             @Value("${arcos.search.deep.candidates:3}") int candidatesToFetch,
                             @Value("${arcos.search.deep.per-page-chars:1200}") int perPageChars,
                             @Value("${arcos.search.deep.fetch-timeout-seconds:4}") int fetchTimeoutSeconds) {
        this.pageFetcher = pageFetcher;
        this.contentExtractor = contentExtractor;
        this.ranker = ranker;
        this.pagesToKeep = pagesToKeep;
        this.candidatesToFetch = candidatesToFetch;
        this.perPageChars = perPageChars;
        this.fetchTimeoutSeconds = fetchTimeoutSeconds;
    }

    public DeepSearchOutcome enrich(String query, List<BraveSearchService.SearchResultItem> results) {
        List<String> candidates = results.stream()
                .map(BraveSearchService.SearchResultItem::getUrl)
                .filter(DeepSearchService::isFetchCandidate)
                .limit(candidatesToFetch)
                .toList();

        List<DeepPage> pages = new ArrayList<>();
        List<String> warnings = new ArrayList<>();
        if (candidates.isEmpty()) {
            return new DeepSearchOutcome(pages, warnings);
        }

        try (ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor()) {
            List<Future<DeepPage>> futures = candidates.stream()
                    .map(url -> executor.submit(() -> readPage(query, url)))
                    .toList();

            // Deadline globale : les fetchs sont parallèles, on n'attend pas N × timeout.
            long deadlineNanos = System.nanoTime()
                    + TimeUnit.SECONDS.toNanos(fetchTimeoutSeconds + 1L);

            for (int i = 0; i < futures.size() && pages.size() < pagesToKeep; i++) {
                String url = candidates.get(i);
                try {
                    long remaining = Math.max(1, deadlineNanos - System.nanoTime());
                    pages.add(futures.get(i).get(remaining, TimeUnit.NANOSECONDS));
                } catch (ExecutionException e) {
                    Throwable cause = e.getCause() != null ? e.getCause() : e;
                    log.warn("Deep search : page non lue {} : {}", url, cause.getMessage());
                    warnings.add("Page non lue : " + url + " (" + cause.getMessage() + ")");
                } catch (TimeoutException e) {
                    futures.get(i).cancel(true);
                    log.warn("Deep search : délai dépassé pour {}", url);
                    warnings.add("Page non lue : " + url + " (délai dépassé)");
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    warnings.add("Page non lue : " + url + " (interrompu)");
                    break;
                }
            }
            futures.forEach(future -> future.cancel(true)); // candidats surnuméraires
        }

        log.info("Deep search : {} pages lues sur {} candidates pour « {} »",
                pages.size(), candidates.size(), query);
        return new DeepSearchOutcome(pages, warnings);
    }

    private DeepPage readPage(String query, String url) throws IOException, InterruptedException {
        PageFetcher.FetchedPage fetched = pageFetcher.fetch(url, fetchTimeoutSeconds);
        ContentExtractor.PageContent content = contentExtractor.extract(fetched.html());
        if (contentExtractor.looksLikeSpa(fetched.html(), content)) {
            throw new IOException("page dynamique, JavaScript requis");
        }
        String relevantContent = ranker.selectRelevant(query, content.blocks(), content.fullText(), perPageChars);
        String title = content.title().isBlank() ? url : content.title();
        return new DeepPage(url, title, relevantContent);
    }

    /** Filtre les URLs qui ne valent pas un fetch : binaires et hosts SPA/anti-bot. */
    public static boolean isFetchCandidate(String url) {
        if (url == null || url.isBlank()) {
            return false;
        }
        URI uri;
        try {
            uri = URI.create(url);
        } catch (IllegalArgumentException e) {
            return false;
        }
        String host = uri.getHost();
        if (host == null) {
            return false;
        }
        String normalizedHost = host.toLowerCase();
        for (String excluded : EXCLUDED_HOSTS) {
            if (normalizedHost.equals(excluded) || normalizedHost.endsWith("." + excluded)) {
                return false;
            }
        }
        String path = uri.getPath() == null ? "" : uri.getPath().toLowerCase();
        for (String extension : EXCLUDED_EXTENSIONS) {
            if (path.endsWith(extension)) {
                return false;
            }
        }
        return true;
    }
}
