package org.arcos.UnitTests.Tools;

import org.arcos.IO.OuputHandling.StateHandler.CentralFeedBackHandler;
import org.arcos.Tools.Actions.ActionResult;
import org.arcos.Tools.Actions.WebPageActions;
import org.arcos.Tools.WebPageTool.WebPageService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@ExtendWith(MockitoExtension.class)
class WebPageServiceSsrfTest {

    @Mock
    private CentralFeedBackHandler centralFeedBackHandler;

    private final WebPageService service = new WebPageService();

    @Test
    @DisplayName("Given loopback URL, When fetched, Then IOException (SSRF blocked) — no network call")
    void fetchAndExtract_loopback_shouldBeBlocked() {
        assertThatThrownBy(() -> service.fetchAndExtract("http://localhost:6334", 4000, 5))
                .isInstanceOf(java.io.IOException.class)
                .hasMessageContaining("interdit");
    }

    @Test
    @DisplayName("Given cloud-metadata URL, When fetched, Then IOException (SSRF blocked)")
    void fetchAndExtract_metadataEndpoint_shouldBeBlocked() {
        assertThatThrownBy(() -> service.fetchAndExtract("http://169.254.169.254/", 4000, 5))
                .isInstanceOf(java.io.IOException.class)
                .hasMessageContaining("interdit");
    }

    @Test
    @DisplayName("Given 127.0.0.1 literal, When fetched, Then blocked")
    void fetchAndExtract_loopbackLiteral_shouldBeBlocked() {
        assertThatThrownBy(() -> service.fetchAndExtract("http://127.0.0.1/admin", 4000, 5))
                .isInstanceOf(java.io.IOException.class);
    }

    @Test
    @DisplayName("WebPageActions surfaces SSRF block as a failure ActionResult, not a crash")
    void readWebPage_loopback_shouldReturnFailure() {
        WebPageActions actions = new WebPageActions(service, centralFeedBackHandler, 4000, 5);
        ActionResult result = actions.readWebPage("http://169.254.169.254/latest/meta-data/");
        assertThat(result.isSuccess()).isFalse();
        assertThat(result.getMessage()).contains("Erreur de lecture");
    }
}
