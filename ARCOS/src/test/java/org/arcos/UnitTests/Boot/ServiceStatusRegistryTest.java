package org.arcos.UnitTests.Boot;

import org.arcos.Boot.ServiceStatus;
import org.arcos.Boot.ServiceStatusEntry;
import org.arcos.Boot.ServiceStatusRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class ServiceStatusRegistryTest {

    private ServiceStatusRegistry registry;

    @BeforeEach
    void setUp() {
        registry = new ServiceStatusRegistry();
    }

    @Test
    void register_AddsEntry() {
        // When
        registry.register("LLM", ServiceStatus.ONLINE, "Mistral OK", "CORE");

        // Then
        List<ServiceStatusEntry> all = registry.getAll();
        assertEquals(1, all.size());
        assertEquals("LLM", all.get(0).getName());
        assertEquals(ServiceStatus.ONLINE, all.get(0).getStatus());
    }

    @Test
    void countOnline_CountsCorrectly() {
        registry.register("LLM", ServiceStatus.ONLINE, "OK", "CORE");
        registry.register("Qdrant", ServiceStatus.ONLINE, "OK", "CORE");
        registry.register("Piper", ServiceStatus.OFFLINE, "absent", "CORE");

        assertEquals(2, registry.countOnline());
    }

    @Test
    void countOffline_CountsCorrectly() {
        registry.register("Piper", ServiceStatus.OFFLINE, "absent", "CORE");
        registry.register("Porcupine", ServiceStatus.OFFLINE, "clé absente", "INTERACTION");

        assertEquals(2, registry.countOffline());
    }

    @Test
    void hasIssues_WhenAllOnline_ReturnsFalse() {
        registry.register("LLM", ServiceStatus.ONLINE, "OK", "CORE");
        assertFalse(registry.hasIssues());
    }

    @Test
    void hasIssues_WhenOfflineExists_ReturnsTrue() {
        registry.register("LLM", ServiceStatus.ONLINE, "OK", "CORE");
        registry.register("Piper", ServiceStatus.OFFLINE, "absent", "CORE");
        assertTrue(registry.hasIssues());
    }

    @Test
    void getAll_ReturnsUnmodifiableList() {
        registry.register("LLM", ServiceStatus.ONLINE, "OK", "CORE");
        List<ServiceStatusEntry> list = registry.getAll();

        assertThrows(UnsupportedOperationException.class, () -> list.add(
                new ServiceStatusEntry("test", ServiceStatus.ONLINE, "test", "test")));
    }
}
