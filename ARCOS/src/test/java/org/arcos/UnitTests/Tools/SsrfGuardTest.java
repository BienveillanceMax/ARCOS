package org.arcos.UnitTests.Tools;

import org.arcos.IO.OuputHandling.StateHandler.CentralFeedBackHandler;
import org.arcos.Tools.Actions.ActionResult;
import org.arcos.Tools.Actions.WebPageActions;
import org.arcos.Tools.WebCommon.ContentExtractor;
import org.arcos.Tools.WebCommon.PageFetcher;
import org.arcos.Tools.WebCommon.SsrfGuard;
import org.arcos.Tools.WebPageTool.WebPageService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.IOException;
import java.net.URI;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@ExtendWith(MockitoExtension.class)
class SsrfGuardTest {

    @Mock
    private CentralFeedBackHandler centralFeedBackHandler;

    private final SsrfGuard guard = new SsrfGuard();

    @Test
    @DisplayName("Given loopback host, When validated, Then IOException (SSRF blocked)")
    void assertHostAllowed_loopback_shouldBeBlocked() {
        assertThatThrownBy(() -> guard.assertHostAllowed(URI.create("http://localhost:6334")))
                .isInstanceOf(IOException.class)
                .hasMessageContaining("interdit");
    }

    @Test
    @DisplayName("Given cloud-metadata IP, When validated, Then IOException (SSRF blocked)")
    void assertHostAllowed_metadataEndpoint_shouldBeBlocked() {
        assertThatThrownBy(() -> guard.assertHostAllowed(URI.create("http://169.254.169.254/")))
                .isInstanceOf(IOException.class)
                .hasMessageContaining("interdit");
    }

    @Test
    @DisplayName("Given 127.0.0.1 literal, When validated, Then blocked")
    void assertHostAllowed_loopbackLiteral_shouldBeBlocked() {
        assertThatThrownBy(() -> guard.assertHostAllowed(URI.create("http://127.0.0.1/admin")))
                .isInstanceOf(IOException.class);
    }

    @Test
    @DisplayName("Given private site-local address, When validated, Then blocked")
    void assertHostAllowed_siteLocal_shouldBeBlocked() {
        assertThatThrownBy(() -> guard.assertHostAllowed(URI.create("http://192.168.1.10/router")))
                .isInstanceOf(IOException.class)
                .hasMessageContaining("interdit");
    }

    @Test
    @DisplayName("Given non-http scheme, When validated, Then blocked with explicit message")
    void assertHostAllowed_ftpScheme_shouldBeBlocked() {
        assertThatThrownBy(() -> guard.assertHostAllowed(URI.create("ftp://files.example.com/doc")))
                .isInstanceOf(IOException.class)
                .hasMessageContaining("schéma non supporté");
    }

    @Test
    @DisplayName("Given URL without host, When validated, Then blocked")
    void assertHostAllowed_noHost_shouldBeBlocked() {
        assertThatThrownBy(() -> guard.assertHostAllowed(URI.create("http:///path-only")))
                .isInstanceOf(IOException.class)
                .hasMessageContaining("sans hôte valide");
    }

    @Test
    @DisplayName("Given CGNAT address 100.64.0.1, When validated, Then blocked")
    void assertHostAllowed_cgnat_shouldBeBlocked() {
        assertThatThrownBy(() -> guard.assertHostAllowed(URI.create("http://100.64.0.1/")))
                .isInstanceOf(IOException.class)
                .hasMessageContaining("interdit");
    }

    @Test
    @DisplayName("Given a public IP literal, When validated, Then allowed (no DNS needed)")
    void assertHostAllowed_publicIp_shouldPass() {
        assertThatCode(() -> guard.assertHostAllowed(URI.create("https://1.1.1.1/page")))
                .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("WebPageActions surfaces SSRF block as a failure ActionResult, not a crash")
    void readWebPage_loopback_shouldReturnFailure() {
        WebPageService service = new WebPageService(new PageFetcher(guard), new ContentExtractor());
        WebPageActions actions = new WebPageActions(service, centralFeedBackHandler, 4000, 5);

        ActionResult result = actions.readWebPage("http://169.254.169.254/latest/meta-data/");

        assertThat(result.isSuccess()).isFalse();
        assertThat(result.getMessage()).contains("Erreur de lecture");
    }
}
