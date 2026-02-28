package org.arcos.UnitTests.Boot;

import org.arcos.Boot.Report.BootReportRenderer;
import org.arcos.Boot.ServiceStatus;
import org.arcos.Boot.ServiceStatusEntry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class BootReportRendererTest {

    private BootReportRenderer renderer;

    @BeforeEach
    void setUp() {
        renderer = new BootReportRenderer(0); // délai zéro pour les tests
    }

    @Test
    void render_DoesNotThrow_WithEmptyList() {
        PrintStream original = System.out;
        System.setOut(new PrintStream(new ByteArrayOutputStream()));
        try {
            assertDoesNotThrow(() -> renderer.render(List.of(), false));
        } finally {
            System.setOut(original);
        }
    }

    @Test
    void render_PlainMode_ContainsServiceNames() {
        // Given
        List<ServiceStatusEntry> entries = List.of(
                new ServiceStatusEntry("LLM", ServiceStatus.ONLINE, "Mistral OK", "CORE"),
                new ServiceStatusEntry("Piper", ServiceStatus.OFFLINE, "absent", "CORE")
        );

        PrintStream original = System.out;
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        System.setOut(new PrintStream(baos));

        try {
            // When
            renderer.render(entries, false);
            String output = baos.toString();

            // Then
            assertTrue(output.contains("LLM"), "L'output doit contenir le nom du service LLM");
            assertTrue(output.contains("Piper"), "L'output doit contenir le nom du service Piper");
            assertTrue(output.contains("ONLINE"), "L'output doit contenir ONLINE");
            assertTrue(output.contains("OFFLINE"), "L'output doit contenir OFFLINE");
        } finally {
            System.setOut(original);
        }
    }

    @Test
    void render_AllOnline_ShowsAllSystemsGo() {
        // Given
        List<ServiceStatusEntry> entries = List.of(
                new ServiceStatusEntry("LLM", ServiceStatus.ONLINE, "OK", "CORE"),
                new ServiceStatusEntry("Qdrant", ServiceStatus.ONLINE, "OK", "CORE")
        );

        PrintStream original = System.out;
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        System.setOut(new PrintStream(baos));

        try {
            renderer.render(entries, false);
            String output = baos.toString();
            assertTrue(output.contains("ALL SYSTEMS GO"), "Tous les systèmes en ligne → ALL SYSTEMS GO");
        } finally {
            System.setOut(original);
        }
    }

    @Test
    void render_WithIssues_ShowsAlertSection() {
        // Given
        List<ServiceStatusEntry> entries = List.of(
                new ServiceStatusEntry("LLM", ServiceStatus.ONLINE, "OK", "CORE"),
                new ServiceStatusEntry("Piper", ServiceStatus.OFFLINE, "absent", "CORE")
        );

        PrintStream original = System.out;
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        System.setOut(new PrintStream(baos));

        try {
            renderer.render(entries, false);
            String output = baos.toString();
            assertTrue(output.contains("ATTENTION"), "Des services en erreur → section ATTENTION");
        } finally {
            System.setOut(original);
        }
    }
}
