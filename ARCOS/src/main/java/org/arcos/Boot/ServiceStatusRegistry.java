package org.arcos.Boot;

import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Registre centralisé des statuts de services au démarrage.
 * Les composants Spring peuvent s'y enregistrer via register().
 */
@Component
public class ServiceStatusRegistry {

    private final List<ServiceStatusEntry> entries = new ArrayList<>();

    public void register(String name, ServiceStatus status, String detail, String category) {
        entries.add(new ServiceStatusEntry(name, status, detail, category));
    }

    public List<ServiceStatusEntry> getAll() {
        return Collections.unmodifiableList(entries);
    }

    public long countOnline() {
        return entries.stream().filter(ServiceStatusEntry::isOnline).count();
    }

    public long countOffline() {
        return entries.stream().filter(ServiceStatusEntry::isOffline).count();
    }

    public long countDegraded() {
        return entries.stream().filter(ServiceStatusEntry::isDegraded).count();
    }

    public boolean hasIssues() {
        return entries.stream().anyMatch(e -> !e.isOnline());
    }
}
