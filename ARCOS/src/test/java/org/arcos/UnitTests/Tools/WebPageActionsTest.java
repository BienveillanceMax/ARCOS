package org.arcos.UnitTests.Tools;

import org.arcos.Tools.Actions.ActionResult;
import org.arcos.Tools.Actions.WebPageActions;
import org.arcos.Tools.WebPageTool.WebPageService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.IOException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class WebPageActionsTest {

    @Mock
    private WebPageService webPageService;

    private WebPageActions webPageActions;

    @BeforeEach
    void setUp() {
        webPageActions = new WebPageActions(webPageService, 4000, 15);
    }

    @Test
    void readWebPage_WithValidUrl_ShouldReturnContent() throws Exception {
        // Given
        String url = "https://example.com/article";
        String content = "This is the extracted content of the page.";
        when(webPageService.fetchAndExtract(url, 4000, 15)).thenReturn(content);

        // When
        ActionResult result = webPageActions.readWebPage(url);

        // Then
        assertThat(result.isSuccess()).isTrue();
        assertThat(result.getMessage()).isEqualTo("Page lue avec succès");
        verify(webPageService).fetchAndExtract(url, 4000, 15);
    }

    @Test
    void readWebPage_WithInvalidUrl_ShouldReturnFailure() {
        // Given
        String url = "ftp://invalid.com";

        // When
        ActionResult result = webPageActions.readWebPage(url);

        // Then
        assertThat(result.isSuccess()).isFalse();
        assertThat(result.getMessage()).contains("URL invalide");
        verifyNoInteractions(webPageService);
    }

    @Test
    void readWebPage_WhenServiceThrowsIOException_ShouldReturnFailure() throws Exception {
        // Given
        String url = "https://example.com/broken";
        when(webPageService.fetchAndExtract(url, 4000, 15))
                .thenThrow(new IOException("Connection refused"));

        // When
        ActionResult result = webPageActions.readWebPage(url);

        // Then
        assertThat(result.isSuccess()).isFalse();
        assertThat(result.getMessage()).contains("Erreur de lecture");
        assertThat(result.getMessage()).contains("Connection refused");
    }

    @Test
    void readWebPage_WhenServiceThrowsInterruptedException_ShouldReturnFailure() throws Exception {
        // Given
        String url = "https://example.com/slow";
        when(webPageService.fetchAndExtract(url, 4000, 15))
                .thenThrow(new InterruptedException("Thread interrupted"));

        // When
        ActionResult result = webPageActions.readWebPage(url);

        // Then
        assertThat(result.isSuccess()).isFalse();
        assertThat(result.getMessage()).contains("Lecture interrompue");
    }
}
