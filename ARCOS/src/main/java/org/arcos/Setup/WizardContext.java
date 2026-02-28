package org.arcos.Setup;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * État mutable du wizard pendant son exécution.
 * Contient les valeurs collectées auprès de l'utilisateur et les résultats des health checks.
 * Aucune dépendance Spring — plain Java uniquement.
 */
public class WizardContext {

    private final ConfigurationModel model;
    private final Map<String, Object> serviceCheckResults = new LinkedHashMap<>();
    private boolean userConfirmedSave = false;
    private final List<String> warnings = new ArrayList<>();

    public WizardContext() {
        this.model = new ConfigurationModel();
    }

    public WizardContext(ConfigurationModel existingConfig) {
        this.model = existingConfig;
    }

    public ConfigurationModel getModel() {
        return model;
    }

    public void addServiceCheckResult(String serviceName, Object result) {
        serviceCheckResults.put(serviceName, result);
    }

    public Map<String, Object> getServiceCheckResults() {
        return serviceCheckResults;
    }

    public boolean isUserConfirmedSave() {
        return userConfirmedSave;
    }

    public void setUserConfirmedSave(boolean userConfirmedSave) {
        this.userConfirmedSave = userConfirmedSave;
    }

    public void addWarning(String warning) {
        warnings.add(warning);
    }

    public List<String> getWarnings() {
        return List.copyOf(warnings);
    }
}
