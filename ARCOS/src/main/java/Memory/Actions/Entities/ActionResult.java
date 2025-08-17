package Memory.Actions.Entities;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.time.LocalDateTime;
import java.util.*;

/**
 * Classe représentant le résultat d'une exécution d'action.
 * Encapsule le succès/échec, les données, les messages et les métadonnées.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class ActionResult {

    @JsonProperty("success")
    private boolean success;

    @JsonProperty("data")
    private List<String> data;

    @JsonProperty("message")
    private String message;

    @JsonProperty("error_type")
    private String errorType;

    @JsonProperty("error_details")
    private String errorDetails;

    @JsonProperty("execution_time_ms")
    private long executionTimeMs;

    @JsonProperty("timestamp")
    private LocalDateTime timestamp;

    @JsonProperty("metadata")
    private Map<String, Object> metadata;

    @JsonProperty("warnings")
    private List<String> warnings;

    // Exception non sérialisée pour usage interne
    private transient Exception exception;

    // ===== CONSTRUCTEURS PRIVÉS =====

    private ActionResult() {
        this.timestamp = LocalDateTime.now();
        this.metadata = new HashMap<>();
        this.warnings = new ArrayList<>();
    }

    private ActionResult(boolean success, List<String> data, String message, Exception exception) {
        this();
        this.success = success;
        this.data = data;
        this.message = message;
        this.exception = exception;

        if (exception != null) {
            this.errorType = exception.getClass().getSimpleName();
            this.errorDetails = exception.getMessage();
        }
    }

    // ===== MÉTHODES FACTORY POUR SUCCÈS =====

    /**
     * Crée un résultat de succès simple
     */
    public static ActionResult success() {
        return new ActionResult(true, null, "Action exécutée avec succès", null);
    }

    /**
     * Crée un résultat de succès avec données
     */
    public static ActionResult success(List<String> data) {
        return new ActionResult(true, data, "Action exécutée avec succès", null);
    }

    /**
     * Crée un résultat de succès avec données et message personnalisé
     */
    public static ActionResult success(List<String> data, String message) {
        return new ActionResult(true, data, message, null);
    }

    /**
     * Crée un résultat de succès avec message seulement
     */
    public static ActionResult successWithMessage(String message) {
        return new ActionResult(true, null, message, null);
    }

    // ===== MÉTHODES FACTORY POUR ÉCHECS =====

    /**
     * Crée un résultat d'échec avec message
     */
    public static ActionResult failure(String message) {
        return new ActionResult(false, null, message, null);
    }

    /**
     * Crée un résultat d'échec avec message et exception
     */
    public static ActionResult failure(String message, Exception exception) {
        return new ActionResult(false, null, message, exception);
    }

    /**
     * Crée un résultat d'échec avec exception seulement
     */
    public static ActionResult failure(Exception exception) {
        String message = exception.getMessage() != null ?
                exception.getMessage() : "Erreur lors de l'exécution de l'action";
        return new ActionResult(false, null, message, exception);
    }

    /**
     * Crée un résultat d'échec avec données partielles
     */
    public static ActionResult failureWithData(String message, List<String>  partialData, Exception exception) {
        ActionResult result = new ActionResult(false, partialData, message, exception);
        result.addWarning("Données partielles disponibles malgré l'échec");
        return result;
    }

    // ===== MÉTHODES FACTORY POUR CAS SPÉCIAUX =====

    /**
     * Crée un résultat de succès avec avertissements
     */
    public static ActionResult successWithWarnings(List<String>  data, String message, List<String> warnings) {
        ActionResult result = new ActionResult(true, data, message, null);
        result.warnings.addAll(warnings);
        return result;
    }

    /**
     * Crée un résultat indiquant un timeout
     */
    public static ActionResult timeout(String message, long timeoutMs) {
        ActionResult result = new ActionResult(false, null, message, null);
        result.errorType = "TimeoutException";
        result.executionTimeMs = timeoutMs;
        result.addMetadata("timeout_reason", "exceeded_max_execution_time");
        return result;
    }

    /**
     * Crée un résultat indiquant une action annulée
     */
    public static ActionResult cancelled(String reason) {
        ActionResult result = new ActionResult(false, null, "Action annulée: " + reason, null);
        result.errorType = "ActionCancelledException";
        result.addMetadata("cancellation_reason", reason);
        return result;
    }

    // ===== MÉTHODES UTILITAIRES =====

    /**
     * Ajoute des métadonnées au résultat
     */
    public ActionResult addMetadata(String key, Object value) {
        this.metadata.put(key, value);
        return this;
    }

    /**
     * Ajoute plusieurs métadonnées
     */
    public ActionResult addMetadata(Map<String, Object> additionalMetadata) {
        this.metadata.putAll(additionalMetadata);
        return this;
    }

    /**
     * Ajoute un avertissement
     */
    public ActionResult addWarning(String warning) {
        this.warnings.add(warning);
        return this;
    }

    /**
     * Ajoute plusieurs avertissements
     */
    public ActionResult addWarnings(List<String> additionalWarnings) {
        this.warnings.addAll(additionalWarnings);
        return this;
    }

    /**
     * Définit le temps d'exécution
     */
    public ActionResult withExecutionTime(long executionTimeMs) {
        this.executionTimeMs = executionTimeMs;
        return this;
    }

    /**
     * Vérifie si le résultat a des avertissements
     */
    public boolean hasWarnings() {
        return !warnings.isEmpty();
    }

    /**
     * Vérifie si le résultat a des métadonnées
     */
    public boolean hasMetadata() {
        return !metadata.isEmpty();
    }

    /**
     * Récupère une métadonnée spécifique
     */
    @SuppressWarnings("unchecked")
    public <T> T getMetadata(String key, Class<T> type) {
        Object value = metadata.get(key);
        if (value != null && type.isInstance(value)) {
            return (T) value;
        }
        return null;
    }

    /**
     * Récupère une métadonnée avec valeur par défaut
     */
    public <T> T getMetadata(String key, T defaultValue) {
        Object value = metadata.get(key);
        if (value != null) {
            try {
                @SuppressWarnings("unchecked")
                T result = (T) value;
                return result;
            } catch (ClassCastException e) {
                return defaultValue;
            }
        }
        return defaultValue;
    }

    /**
     * Récupère les données avec un type spécifique
     */
    @SuppressWarnings("unchecked")
    public <T> T getData(Class<T> type) {
        if (data != null && type.isInstance(data)) {
            return (T) data;
        }
        return null;
    }

    /**
     * Récupère les données sous forme de Map
     */
    @SuppressWarnings("unchecked")
    public Map<String, Object> getDataAsMap() {
        if (data instanceof Map) {
            return (Map<String, Object>) data;
        }
        return null;
    }



    /**
     * Crée une copie du résultat avec un nouveau message
     */
    public ActionResult withMessage(String newMessage) {
        ActionResult copy = new ActionResult(this.success, this.data, newMessage, this.exception);
        copy.errorType = this.errorType;
        copy.errorDetails = this.errorDetails;
        copy.executionTimeMs = this.executionTimeMs;
        copy.timestamp = this.timestamp;
        copy.metadata = new HashMap<>(this.metadata);
        copy.warnings = new ArrayList<>(this.warnings);
        return copy;
    }

    /**
     * Génère un résumé du résultat pour logging
     */
    public String getSummary() {
        StringBuilder summary = new StringBuilder();
        summary.append("ActionResult{");
        summary.append("success=").append(success);
        summary.append(", message='").append(message).append("'");

        if (data != null) {
            summary.append(", dataType=").append(data.getClass().getSimpleName());
        }

        if (executionTimeMs > 0) {
            summary.append(", executionTime=").append(executionTimeMs).append("ms");
        }

        if (hasWarnings()) {
            summary.append(", warnings=").append(warnings.size());
        }

        if (!success && errorType != null) {
            summary.append(", errorType=").append(errorType);
        }

        summary.append("}");
        return summary.toString();
    }

    /**
     * Génère un résumé détaillé pour debugging
     */
    public String getDetailedSummary() {
        StringBuilder summary = new StringBuilder();
        summary.append("=== ActionResult Detailed Summary ===\n");
        summary.append("Success: ").append(success).append("\n");
        summary.append("Message: ").append(message).append("\n");
        summary.append("Timestamp: ").append(timestamp).append("\n");
        summary.append("Execution Time: ").append(executionTimeMs).append("ms\n");

        if (data != null) {
            summary.append("Data Type: ").append(data.getClass().getSimpleName()).append("\n");
            summary.append("Data Content: ").append(truncateString(data.toString(), 200)).append("\n");
        }

        if (!success) {
            summary.append("Error Type: ").append(errorType).append("\n");
            summary.append("Error Details: ").append(errorDetails).append("\n");
        }

        if (hasWarnings()) {
            summary.append("Warnings (").append(warnings.size()).append("):\n");
            warnings.forEach(warning -> summary.append("  - ").append(warning).append("\n"));
        }

        if (hasMetadata()) {
            summary.append("Metadata:\n");
            metadata.forEach((key, value) ->
                    summary.append("  ").append(key).append(": ").append(value).append("\n"));
        }

        return summary.toString();
    }

    /**
     * Tronque une chaîne pour l'affichage
     */
    private String truncateString(String str, int maxLength) {
        if (str == null || str.length() <= maxLength) {
            return str;
        }
        return str.substring(0, maxLength) + "...";
    }

    // ===== GETTERS ET SETTERS =====

    public boolean isSuccess() { return success; }
    public void setSuccess(boolean success) { this.success = success; }

    public Object getData() { return data; }
    public void setData(List<String> data) { this.data = data; }

    public String getMessage() { return message; }
    public void setMessage(String message) { this.message = message; }

    public String getErrorType() { return errorType; }
    public void setErrorType(String errorType) { this.errorType = errorType; }

    public String getErrorDetails() { return errorDetails; }
    public void setErrorDetails(String errorDetails) { this.errorDetails = errorDetails; }

    public long getExecutionTimeMs() { return executionTimeMs; }
    public void setExecutionTimeMs(long executionTimeMs) { this.executionTimeMs = executionTimeMs; }

    public LocalDateTime getTimestamp() { return timestamp; }
    public void setTimestamp(LocalDateTime timestamp) { this.timestamp = timestamp; }

    public Map<String, Object> getMetadata() { return new HashMap<>(metadata); }
    public void setMetadata(Map<String, Object> metadata) {
        this.metadata = new HashMap<>(metadata);
    }

    public List<String> getWarnings() { return new ArrayList<>(warnings); }
    public void setWarnings(List<String> warnings) {
        this.warnings = new ArrayList<>(warnings);
    }

    public Exception getException() { return exception; }
    public void setException(Exception exception) {
        this.exception = exception;
        if (exception != null) {
            this.errorType = exception.getClass().getSimpleName();
            this.errorDetails = exception.getMessage();
        }
    }

    @Override
    public String toString() {
        return getSummary();
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) return true;
        if (obj == null || getClass() != obj.getClass()) return false;

        ActionResult that = (ActionResult) obj;
        return success == that.success &&
                executionTimeMs == that.executionTimeMs &&
                Objects.equals(data, that.data) &&
                Objects.equals(message, that.message) &&
                Objects.equals(errorType, that.errorType) &&
                Objects.equals(timestamp, that.timestamp);
    }

    @Override
    public int hashCode() {
        return Objects.hash(success, data, message, errorType, executionTimeMs, timestamp);
    }
}