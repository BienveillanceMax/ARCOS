package org.arcos.Boot;

/**
 * Entrée de statut pour un service dans le rapport de boot.
 */
public class ServiceStatusEntry {

    private final String name;
    private final ServiceStatus status;
    private final String detail;
    private final String category;

    public ServiceStatusEntry(String name, ServiceStatus status, String detail, String category) {
        this.name = name;
        this.status = status;
        this.detail = detail;
        this.category = category;
    }

    public String getName() { return name; }
    public ServiceStatus getStatus() { return status; }
    public String getDetail() { return detail; }
    public String getCategory() { return category; }

    public boolean isOnline() { return status == ServiceStatus.ONLINE; }
    public boolean isOffline() { return status == ServiceStatus.OFFLINE; }
    public boolean isDegraded() { return status == ServiceStatus.DEGRADED; }
}
