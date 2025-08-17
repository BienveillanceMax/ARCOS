package Memory;

import Memory.Actions.Entities.ActionResult;
import Orchestrator.Entities.ExecutionPlan;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * Contexte conversationnel maintenant l'état de la session utilisateur
 * et l'historique des interactions avec l'assistant IA.
 */
@Component
@JsonIgnoreProperties(ignoreUnknown = true)
public class ConversationContext {

    @JsonProperty("session_id")
    private String sessionId;

    @JsonProperty("user_id")
    private String userId;

    @JsonProperty("created_at")
    private LocalDateTime createdAt;

    @JsonProperty("last_updated")
    private LocalDateTime lastUpdated;

    @JsonProperty("message_history")
    private List<ConversationMessage> messageHistory;

    @JsonProperty("user_preferences")
    private Map<String, Object> userPreferences;

    @JsonProperty("session_data")
    private Map<String, Object> sessionData;

    @JsonProperty("action_history")
    private List<ExecutedAction> actionHistory;

    @JsonProperty("errors")
    private List<ContextError> errors;

    @JsonProperty("metadata")
    private Map<String, String> metadata;

    // Variables transientes (non sérialisées)
    private transient int maxHistorySize = 50;
    private transient int maxErrorSize = 10;

    // ===== CONSTRUCTEURS =====

    public ConversationContext() {
        this.sessionId = UUID.randomUUID().toString();
        this.createdAt = LocalDateTime.now();
        this.lastUpdated = LocalDateTime.now();
        this.messageHistory = new ArrayList<>();
        this.userPreferences = new ConcurrentHashMap<>();
        this.sessionData = new ConcurrentHashMap<>();
        this.actionHistory = new ArrayList<>();
        this.errors = new ArrayList<>();
        this.metadata = new ConcurrentHashMap<>();
    }

    public ConversationContext(String userId) {
        this();
        this.userId = userId;
    }

    public ConversationContext(String sessionId, String userId) {
        this(userId);
        this.sessionId = sessionId;
    }

    // ===== GESTION DES MESSAGES =====

    /**
     * Ajoute un message utilisateur au contexte
     */
    public void addUserMessage(String content) {
        addMessage(ConversationMessage.MessageType.USER, content, null);
    }

    /**
     * Ajoute une réponse de l'assistant au contexte
     */
    public void addAssistantMessage(String content, ExecutionPlan executionPlan) {
        addMessage(ConversationMessage.MessageType.ASSISTANT, content, executionPlan);
    }

    /**
     * Ajoute un message système au contexte
     */
    public void addSystemMessage(String content) {
        addMessage(ConversationMessage.MessageType.SYSTEM, content, null);
    }

    private void addMessage(ConversationMessage.MessageType type, String content, ExecutionPlan plan) {
        ConversationMessage message = new ConversationMessage(type, content, plan);
        messageHistory.add(message);

        // Maintient la taille de l'historique
        while (messageHistory.size() > maxHistorySize) {
            messageHistory.remove(0);
        }

        updateTimestamp();
    }

    /**
     * Récupère les N derniers messages
     */
    public List<ConversationMessage> getRecentMessages(int count) {
        if (messageHistory.isEmpty()) {
            return new ArrayList<>();
        }

        int start = Math.max(0, messageHistory.size() - count);
        return new ArrayList<>(messageHistory.subList(start, messageHistory.size()));
    }

    /**
     * Récupère les messages récents formatés pour le prompt
     */
    public List<String> getRecentMessages() {
        return getRecentMessages(10).stream()
                .map(msg -> msg.getType().name() + ": " + msg.getContent())
                .collect(Collectors.toList());
    }

    /**
     * Génère un résumé de la conversation pour le contexte
     */
    public String getSummary() {
        if (messageHistory.isEmpty()) {
            return "Nouvelle conversation";
        }

        StringBuilder summary = new StringBuilder();
        summary.append("Session: ").append(sessionId).append("\n");

        if (userId != null) {
            summary.append("Utilisateur: ").append(userId).append("\n");
        }

        summary.append("Messages: ").append(messageHistory.size()).append("\n");

        // Ajoute les derniers messages si pertinents
        List<ConversationMessage> recent = getRecentMessages(3);
        if (!recent.isEmpty()) {
            summary.append("Derniers échanges:\n");
            recent.forEach(msg ->
                    summary.append("- ").append(msg.getType()).append(": ")
                            .append(truncateContent(msg.getContent(), 100)).append("\n"));
        }

        return summary.toString();
    }

    // ===== GESTION DES PRÉFÉRENCES =====

    /**
     * Définit une préférence utilisateur
     */
    public void setUserPreference(String key, Object value) {
        userPreferences.put(key, value);
        updateTimestamp();
    }

    /**
     * Récupère une préférence utilisateur
     */
    @SuppressWarnings("unchecked")
    public <T> T getUserPreference(String key, Class<T> type) {
        Object value = userPreferences.get(key);
        if (value != null && type.isInstance(value)) {
            return (T) value;
        }
        return null;
    }

    /**
     * Récupère une préférence avec valeur par défaut
     */
    public <T> T getUserPreference(String key, T defaultValue) {
        Object value = userPreferences.get(key);
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

    // ===== GESTION DES DONNÉES DE SESSION =====

    /**
     * Stocke une donnée de session temporaire
     */
    public void setSessionData(String key, Object value) {
        sessionData.put(key, value);
        updateTimestamp();
    }

    /**
     * Récupère une donnée de session
     */
    @SuppressWarnings("unchecked")
    public <T> T getSessionData(String key, Class<T> type) {
        Object value = sessionData.get(key);
        if (value != null && type.isInstance(value)) {
            return (T) value;
        }
        return null;
    }

    /**
     * Supprime une donnée de session
     */
    public void removeSessionData(String key) {
        sessionData.remove(key);
        updateTimestamp();
    }

    // ===== GESTION DE L'HISTORIQUE DES ACTIONS =====

    /**
     * Ajoute une action exécutée à l'historique
     */
    public void addExecutedAction(String actionName, Map<String, Object> parameters,
                                  ActionResult result) {
        ExecutedAction executedAction = new ExecutedAction(actionName, parameters, result);
        actionHistory.add(executedAction);
        updateTimestamp();
    }

    /**
     * Récupère les dernières actions exécutées
     */
    public List<ExecutedAction> getRecentActions(int count) {
        if (actionHistory.isEmpty()) {
            return new ArrayList<>();
        }

        int start = Math.max(0, actionHistory.size() - count);
        return new ArrayList<>(actionHistory.subList(start, actionHistory.size()));
    }

    /**
     * Vérifie si une action similaire a été récemment exécutée
     */
    public boolean hasRecentSimilarAction(String actionName, Map<String, Object> parameters) {
        return getRecentActions(5).stream()
                .anyMatch(action -> action.getActionName().equals(actionName) &&
                        parametersAreSimilar(action.getParameters(), parameters));
    }

    // ===== GESTION DES ERREURS =====

    /**
     * Ajoute une erreur au contexte
     */
    public void addError(String message) {
        addError(message, null);
    }

    /**
     * Ajoute une erreur avec exception au contexte
     */
    public void addError(String message, Exception exception) {
        ContextError error = new ContextError(message, exception);
        errors.add(error);

        // Maintient la taille des erreurs
        while (errors.size() > maxErrorSize) {
            errors.remove(0);
        }

        updateTimestamp();
    }

    /**
     * Récupère les erreurs récentes
     */
    public List<ContextError> getRecentErrors() {
        return new ArrayList<>(errors);
    }

    /**
     * Vérifie s'il y a des erreurs récentes
     */
    public boolean hasRecentErrors() {
        return !errors.isEmpty();
    }

    // ===== MÉTHODES UTILITAIRES =====

    /**
     * Vide le contexte tout en gardant les préférences utilisateur
     */
    public void clearConversation() {
        messageHistory.clear();
        actionHistory.clear();
        errors.clear();
        sessionData.clear();
        // Les préférences utilisateur sont conservées
        updateTimestamp();
    }

    /**
     * Remet à zéro complètement le contexte
     */
    public void reset() {
        messageHistory.clear();
        userPreferences.clear();
        sessionData.clear();
        actionHistory.clear();
        errors.clear();
        metadata.clear();
        updateTimestamp();
    }

    /**
     * Vérifie si le contexte est vide
     */
    public boolean isEmpty() {
        return messageHistory.isEmpty() &&
                userPreferences.isEmpty() &&
                sessionData.isEmpty() &&
                actionHistory.isEmpty();
    }

    /**
     * Met à jour le timestamp
     */
    private void updateTimestamp() {
        this.lastUpdated = LocalDateTime.now();
    }

    /**
     * Tronque le contenu pour l'affichage
     */
    private String truncateContent(String content, int maxLength) {
        if (content == null || content.length() <= maxLength) {
            return content;
        }
        return content.substring(0, maxLength) + "...";
    }

    /**
     * Compare si deux sets de paramètres sont similaires
     */
    private boolean parametersAreSimilar(Map<String, Object> params1, Map<String, Object> params2) {
        if (params1 == null || params2 == null) {
            return params1 == params2;
        }

        // Comparaison simple - peut être raffinée selon les besoins
        return params1.equals(params2);
    }

    // ===== GETTERS ET SETTERS =====

    public String getSessionId() { return sessionId; }
    public void setSessionId(String sessionId) { this.sessionId = sessionId; }

    public String getUserId() { return userId; }
    public void setUserId(String userId) { this.userId = userId; }

    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }

    public LocalDateTime getLastUpdated() { return lastUpdated; }
    public void setLastUpdated(LocalDateTime lastUpdated) { this.lastUpdated = lastUpdated; }

    public List<ConversationMessage> getMessageHistory() {
        return new ArrayList<>(messageHistory);
    }
    public void setMessageHistory(List<ConversationMessage> messageHistory) {
        this.messageHistory = new ArrayList<>(messageHistory);
    }

    public Map<String, Object> getUserPreferences() {
        return new HashMap<>(userPreferences);
    }
    public void setUserPreferences(Map<String, Object> userPreferences) {
        this.userPreferences = new ConcurrentHashMap<>(userPreferences);
    }

    public Map<String, Object> getSessionData() {
        return new HashMap<>(sessionData);
    }
    public void setSessionData(Map<String, Object> sessionData) {
        this.sessionData = new ConcurrentHashMap<>(sessionData);
    }

    public List<ExecutedAction> getActionHistory() {
        return new ArrayList<>(actionHistory);
    }
    public void setActionHistory(List<ExecutedAction> actionHistory) {
        this.actionHistory = new ArrayList<>(actionHistory);
    }

    public List<ContextError> getErrors() {
        return new ArrayList<>(errors);
    }
    public void setErrors(List<ContextError> errors) {
        this.errors = new ArrayList<>(errors);
    }

    public Map<String, String> getMetadata() {
        return new HashMap<>(metadata);
    }
    public void setMetadata(Map<String, String> metadata) {
        this.metadata = new ConcurrentHashMap<>(metadata);
    }

    public int getMaxHistorySize() { return maxHistorySize; }
    public void setMaxHistorySize(int maxHistorySize) { this.maxHistorySize = maxHistorySize; }

    public int getMaxErrorSize() { return maxErrorSize; }
    public void setMaxErrorSize(int maxErrorSize) { this.maxErrorSize = maxErrorSize; }

    @Override
    public String toString() {
        return String.format("ConversationContext{sessionId='%s', userId='%s', messages=%d, actions=%d}",
                sessionId, userId, messageHistory.size(), actionHistory.size());
    }
}

// ===== CLASSES INTERNES ET DE SUPPORT =====

/**
 * Représente un message dans la conversation
 */
class ConversationMessage {

    public enum MessageType {
        USER, ASSISTANT, SYSTEM
    }

    @JsonProperty("type")
    private MessageType type;

    @JsonProperty("content")
    private String content;

    @JsonProperty("timestamp")
    private LocalDateTime timestamp;

    @JsonProperty("execution_plan")
    private ExecutionPlan executionPlan;

    @JsonProperty("metadata")
    private Map<String, String> metadata;

    public ConversationMessage() {
        this.timestamp = LocalDateTime.now();
        this.metadata = new HashMap<>();
    }

    public ConversationMessage(MessageType type, String content, ExecutionPlan executionPlan) {
        this();
        this.type = type;
        this.content = content;
        this.executionPlan = executionPlan;
    }

    // Getters et Setters
    public MessageType getType() { return type; }
    public void setType(MessageType type) { this.type = type; }

    public String getContent() { return content; }
    public void setContent(String content) { this.content = content; }

    public LocalDateTime getTimestamp() { return timestamp; }
    public void setTimestamp(LocalDateTime timestamp) { this.timestamp = timestamp; }

    public ExecutionPlan getExecutionPlan() { return executionPlan; }
    public void setExecutionPlan(ExecutionPlan executionPlan) { this.executionPlan = executionPlan; }

    public Map<String, String> getMetadata() { return metadata; }
    public void setMetadata(Map<String, String> metadata) { this.metadata = metadata; }
}

/**
 * Représente une action exécutée dans l'historique
 */
class ExecutedAction {

    @JsonProperty("action_name")
    private String actionName;

    @JsonProperty("parameters")
    private Map<String, Object> parameters;

    @JsonProperty("result")
    private ActionResult result;

    @JsonProperty("timestamp")
    private LocalDateTime timestamp;

    @JsonProperty("execution_time_ms")
    private long executionTimeMs;

    public ExecutedAction() {
        this.timestamp = LocalDateTime.now();
    }

    public ExecutedAction(String actionName, Map<String, Object> parameters, ActionResult result) {
        this();
        this.actionName = actionName;
        this.parameters = new HashMap<>(parameters);
        this.result = result;
    }

    // Getters et Setters
    public String getActionName() { return actionName; }
    public void setActionName(String actionName) { this.actionName = actionName; }

    public Map<String, Object> getParameters() { return parameters; }
    public void setParameters(Map<String, Object> parameters) { this.parameters = parameters; }

    public ActionResult getResult() { return result; }
    public void setResult(ActionResult result) { this.result = result; }

    public LocalDateTime getTimestamp() { return timestamp; }
    public void setTimestamp(LocalDateTime timestamp) { this.timestamp = timestamp; }

    public long getExecutionTimeMs() { return executionTimeMs; }
    public void setExecutionTimeMs(long executionTimeMs) { this.executionTimeMs = executionTimeMs; }
}

/**
 * Représente une erreur dans le contexte
 */
class ContextError {

    @JsonProperty("message")
    private String message;

    @JsonProperty("exception_type")
    private String exceptionType;

    @JsonProperty("stack_trace")
    private String stackTrace;

    @JsonProperty("timestamp")
    private LocalDateTime timestamp;

    public ContextError() {
        this.timestamp = LocalDateTime.now();
    }

    public ContextError(String message, Exception exception) {
        this();
        this.message = message;

        if (exception != null) {
            this.exceptionType = exception.getClass().getSimpleName();
            this.stackTrace = Arrays.toString(exception.getStackTrace());
        }
    }

    // Getters et Setters
    public String getMessage() { return message; }
    public void setMessage(String message) { this.message = message; }

    public String getExceptionType() { return exceptionType; }
    public void setExceptionType(String exceptionType) { this.exceptionType = exceptionType; }

    public String getStackTrace() { return stackTrace; }
    public void setStackTrace(String stackTrace) { this.stackTrace = stackTrace; }

    public LocalDateTime getTimestamp() { return timestamp; }
    public void setTimestamp(LocalDateTime timestamp) { this.timestamp = timestamp; }
}