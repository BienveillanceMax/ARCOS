package org.arcos.UnitTests.Tools;

import org.arcos.Tools.WebCommon.PageFetcher;
import org.arcos.Tools.WebCommon.PageFetcher.FetchedPage;
import org.arcos.Tools.WebCommon.SsrfGuard;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpHeaders;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Tests HTTP de PageFetcher (HttpClient mocké, pattern WeatherServiceHttpTest).
 */
@ExtendWith(MockitoExtension.class)
class PageFetcherTest {

    @Mock
    private HttpClient httpClient;

    @Mock
    private SsrfGuard ssrfGuard;

    private PageFetcher pageFetcher;

    @BeforeEach
    void setUp() {
        pageFetcher = new PageFetcher(httpClient, ssrfGuard);
    }

    @SuppressWarnings("unchecked")
    private HttpResponse<String> mockResponse(int status, String body, Map<String, String> headers) {
        HttpResponse<String> response = mock(HttpResponse.class);
        lenient().when(response.statusCode()).thenReturn(status);
        lenient().when(response.body()).thenReturn(body);
        HttpHeaders httpHeaders = HttpHeaders.of(
                headers.entrySet().stream().collect(
                        java.util.stream.Collectors.toMap(Map.Entry::getKey, e -> List.of(e.getValue()))),
                (a, b) -> true);
        lenient().when(response.headers()).thenReturn(httpHeaders);
        return response;
    }

    @Test
    @DisplayName("Given 200 HTML response, When fetched, Then FetchedPage carries html and content type")
    void fetch_With200Html_ShouldReturnFetchedPage() throws Exception {
        // Given
        HttpResponse<String> response = mockResponse(200, "<html><body>Bonjour</body></html>",
                Map.of("Content-Type", "text/html; charset=utf-8"));
        when(httpClient.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class)))
                .thenReturn(response);

        // When
        FetchedPage page = pageFetcher.fetch("https://example.com/page", 5);

        // Then
        assertThat(page.html()).contains("Bonjour");
        assertThat(page.contentType()).contains("text/html");
        assertThat(page.finalUrl()).isEqualTo("https://example.com/page");
        verify(ssrfGuard).assertHostAllowed(URI.create("https://example.com/page"));
    }

    @Test
    @DisplayName("Given one redirect, When fetched, Then target is followed and SSRF guard re-validates each hop")
    void fetch_WithRedirect_ShouldRevalidateEachHop() throws Exception {
        // Given
        HttpResponse<String> redirect = mockResponse(301, "",
                Map.of("Location", "https://target.example.com/final"));
        HttpResponse<String> ok = mockResponse(200, "<html><body>Final</body></html>",
                Map.of("Content-Type", "text/html"));
        when(httpClient.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class)))
                .thenReturn(redirect, ok);

        // When
        FetchedPage page = pageFetcher.fetch("https://example.com/start", 5);

        // Then
        assertThat(page.finalUrl()).isEqualTo("https://target.example.com/final");
        verify(ssrfGuard).assertHostAllowed(URI.create("https://example.com/start"));
        verify(ssrfGuard).assertHostAllowed(URI.create("https://target.example.com/final"));
    }

    @Test
    @DisplayName("Given endless redirects, When fetched, Then IOException after redirect budget")
    void fetch_WithTooManyRedirects_ShouldThrow() throws Exception {
        // Given — every hop redirects again
        HttpResponse<String> redirect = mockResponse(302, "",
                Map.of("Location", "https://example.com/again"));
        when(httpClient.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class)))
                .thenReturn(redirect);

        // When/Then
        assertThatThrownBy(() -> pageFetcher.fetch("https://example.com/loop", 5))
                .isInstanceOf(IOException.class)
                .hasMessageContaining("Trop de redirections");
    }

    @Test
    @DisplayName("Given HTTP 404, When fetched, Then IOException with status")
    void fetch_With404_ShouldThrow() throws Exception {
        // Given
        HttpResponse<String> notFound = mockResponse(404, "Not found", Map.of());
        when(httpClient.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class)))
                .thenReturn(notFound);

        // When/Then
        assertThatThrownBy(() -> pageFetcher.fetch("https://example.com/missing", 5))
                .isInstanceOf(IOException.class)
                .hasMessageContaining("HTTP 404");
    }

    @Test
    @DisplayName("Given PDF content type, When fetched, Then IOException 'Type de contenu non supporté'")
    void fetch_WithPdfContentType_ShouldThrow() throws Exception {
        // Given
        HttpResponse<String> pdf = mockResponse(200, "%PDF-1.7 ...",
                Map.of("Content-Type", "application/pdf"));
        when(httpClient.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class)))
                .thenReturn(pdf);

        // When/Then
        assertThatThrownBy(() -> pageFetcher.fetch("https://example.com/doc.pdf", 5))
                .isInstanceOf(IOException.class)
                .hasMessageContaining("Type de contenu non supporté");
    }

    @Test
    @DisplayName("Given missing Content-Type header, When fetched, Then page is accepted")
    void fetch_WithoutContentType_ShouldReturnPage() throws Exception {
        // Given
        HttpResponse<String> response = mockResponse(200, "<html><body>Sans en-tête</body></html>", Map.of());
        when(httpClient.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class)))
                .thenReturn(response);

        // When
        FetchedPage page = pageFetcher.fetch("https://example.com/page", 5);

        // Then
        assertThat(page.html()).contains("Sans en-tête");
        assertThat(page.contentType()).isEmpty();
    }

    @Test
    @DisplayName("Given SSRF guard rejects host, When fetched, Then IOException propagates without network call")
    void fetch_WhenGuardRejects_ShouldPropagate() throws Exception {
        // Given
        doThrow(new IOException("Hôte interdit (réseau privé/métadonnées) : localhost"))
                .when(ssrfGuard).assertHostAllowed(any(URI.class));

        // When/Then
        assertThatThrownBy(() -> pageFetcher.fetch("http://localhost/admin", 5))
                .isInstanceOf(IOException.class)
                .hasMessageContaining("interdit");
        verifyNoInteractions(httpClient);
    }
}
