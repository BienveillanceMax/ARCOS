package org.arcos.UnitTests.Setup.Boot;

import org.arcos.Configuration.PersonalityProperties;
import org.arcos.Configuration.SpeechToTextProperties;
import org.arcos.IO.InputHandling.STT.SttBackendType;
import org.arcos.Setup.Boot.BootReporter;
import org.arcos.Setup.Boot.PersonalityGreeting;
import org.arcos.Setup.Boot.ServiceStatusRegistry;
import org.arcos.Setup.Health.HealthResult;
import org.arcos.Setup.Health.ServiceHealthCheck;
import org.arcos.Setup.Health.ServiceStatus;
import org.arcos.Tools.CalendarTool.CalDavCalendarService;
import org.arcos.Tools.SearchTool.BraveSearchService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.context.ApplicationContext;
import org.springframework.core.env.Environment;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class BootReporterTest {

    @Mock PersonalityProperties personalityProperties;
    @Mock SpeechToTextProperties sttProperties;
    @Mock BraveSearchService brave;
    @Mock CalDavCalendarService cal;
    @Mock ApplicationContext ctx;

    private static ServiceHealthCheck fixed(HealthResult result) {
        return new ServiceHealthCheck() {
            @Override public String serviceName() { return "test"; }
            @Override public HealthResult check(ServiceConfig config) { return result; }
        };
    }

    private BootReporter buildReporter(ServiceStatusRegistry registry) {
        PersonalityGreeting greeting = mock(PersonalityGreeting.class);
        when(personalityProperties.getProfile()).thenReturn("GLADOS");
        when(sttProperties.getBackend()).thenReturn(SttBackendType.FASTER_WHISPER);
        when(sttProperties.getFasterWhisperUrl()).thenReturn("http://localhost:18999");
        Environment env = mock(Environment.class);
        when(ctx.getEnvironment()).thenReturn(env);
        when(env.getProperty("PORCUPINE_ACCESS_KEY", "")).thenReturn("");
        when(brave.isAvailable()).thenReturn(false);
        when(cal.isAvailable()).thenReturn(false);

        return new BootReporter(registry, greeting,
                personalityProperties, sttProperties, brave, cal, ctx);
    }

    @Test
    void vectorDbAndVoix_renderOffline_whenProbesDown() {
        ServiceStatusRegistry registry = new ServiceStatusRegistry();
        BootReporter reporter = buildReporter(registry);
        ServiceHealthCheck down = fixed(HealthResult.offline("connexion refusée"));
        reporter.setHealthCheckers(down, down);

        reporter.collectServiceStatuses();

        var vdb = registry.getAll().stream().filter(e -> e.getName().equals("VECTOR DB")).findFirst().orElseThrow();
        var voix = registry.getAll().stream().filter(e -> e.getName().equals("VOIX")).findFirst().orElseThrow();
        assertEquals(ServiceStatus.OFFLINE, vdb.getStatus());
        assertEquals(ServiceStatus.OFFLINE, voix.getStatus());
    }

    @Test
    void vectorDbAndVoix_renderOnline_whenProbesUp() {
        ServiceStatusRegistry registry = new ServiceStatusRegistry();
        BootReporter reporter = buildReporter(registry);
        ServiceHealthCheck up = fixed(HealthResult.online("ok", 5));
        reporter.setHealthCheckers(up, up);

        reporter.collectServiceStatuses();

        var vdb = registry.getAll().stream().filter(e -> e.getName().equals("VECTOR DB")).findFirst().orElseThrow();
        var voix = registry.getAll().stream().filter(e -> e.getName().equals("VOIX")).findFirst().orElseThrow();
        assertEquals(ServiceStatus.ONLINE, vdb.getStatus());
        assertEquals(ServiceStatus.ONLINE, voix.getStatus());
    }
}
