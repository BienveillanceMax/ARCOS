package Memory.Actions;

import Memory.Actions.Entities.Actions.Action;
import Memory.Actions.Entities.Actions.AddCalendarEventAction;
import Memory.Actions.Entities.Actions.DefaultAction;
import Memory.Actions.Entities.Actions.SearchAction;
import Memory.Actions.Entities.Actions.DeepSearchAction;
import Memory.Actions.Entities.Actions.ListCalendarEventsAction;
import Memory.Actions.Entities.Actions.DeleteCalendarEventAction;
import Memory.Actions.Entities.Actions.SearchCalendarEventsAction;
import Memory.Actions.Entities.Parameter;
import Tools.CalendarTool.CalendarService;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import Tools.SearchTool.BraveSearchService;


import java.util.HashMap;
import java.util.Map;

@Component
public class ActionRegistry
{
    private final Map<String, Action> actions = new HashMap<>();
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final BraveSearchService braveSearchService;
    private final CalendarService calendarService;

    @Autowired
    public ActionRegistry(BraveSearchService braveSearchService, CalendarService calendarService) {
        this.braveSearchService = braveSearchService;
        this.calendarService = calendarService;
        //registerAction("Parler", new RespondAction()); As of now of little use and not implemented (the speaking execution part, the rest is fine)
        registerAction("Rechercher sur internet", new SearchAction(braveSearchService));
        registerAction("Recherche approfondie sur internet", new DeepSearchAction(braveSearchService));
        registerAction("Lister les événements du calendrier", new ListCalendarEventsAction(calendarService));
        registerAction("Ajouter un événement au calendrier", new AddCalendarEventAction(calendarService));
        registerAction("Supprimer un événement du calendrier", new DeleteCalendarEventAction(calendarService));
        registerAction("Rechercher des événements dans le calendrier", new SearchCalendarEventsAction(calendarService));
        registerAction("Action par défaut", new DefaultAction());
    }

    public void registerAction(String name, Action action) {
        actions.put(name, action);
    }

    public Action getAction(String name) {
        return actions.get(name);
    }

    public boolean hasAction(String name) {
        return actions.containsKey(name);
    }

    public Map<String, Action> getActions() {
        return Map.copyOf(actions);
    }

    /**
     * Obtient un nom de type simplifié pour l'affichage
     */
    private String getSimpleTypeName(Class<?> type) {
        if (type == String.class) return "string";
        if (type == Integer.class || type == int.class) return "integer";
        if (type == Boolean.class || type == boolean.class) return "boolean";
        if (type == Double.class || type == double.class) return "number";
        if (type == Long.class || type == long.class) return "long";

        return type.getSimpleName().toLowerCase();
    }

    /**
     * Compte le nombre de paramètres requis pour une action
     */
    private long countRequiredParameters(Action action) {
        return action.getParameters().stream()
                .mapToLong(param -> param.isRequired() ? 1 : 0)
                .sum();
    }

    /**
     * Compte le nombre de paramètres optionnels pour une action
     */
    private long countOptionalParameters(Action action) {
        return action.getParameters().stream()
                .mapToLong(param -> param.isRequired() ? 0 : 1)
                .sum();
    }

    /**
     * Ajoute des contraintes de validation au nœud de paramètre
     */
    private void addValidationConstraints(ObjectNode paramNode, Parameter parameter) {
        Class<?> type = parameter.getType();

        if (type == String.class) {
            paramNode.put("accepts", "any string value");
        } else if (type == Integer.class || type == int.class) {
            paramNode.put("accepts", "integer numbers");
        } else if (type == Boolean.class || type == boolean.class) {
            paramNode.put("accepts", "true or false");
        } else if (type == Double.class || type == double.class) {
            paramNode.put("accepts", "decimal numbers");
        } else {
            paramNode.put("accepts", type.getSimpleName());
        }
    }


    /**
     * Crée un nœud JSON pour une action spécifique
     */
    private ObjectNode createActionNode(Action action) {
        ObjectNode actionNode = objectMapper.createObjectNode();

        // Informations de base de l'action
        actionNode.put("name", action.getName());
        actionNode.put("description", action.getDescription());

        // Paramètres de l'action
        ArrayNode parametersArray = objectMapper.createArrayNode();
        for (Parameter parameter : action.getParameters()) {
            ObjectNode paramNode = createParameterNode(parameter);
            parametersArray.add(paramNode);
        }
        actionNode.set("parameters", parametersArray);

        // Métadonnées additionnelles
        ObjectNode metadataNode = objectMapper.createObjectNode();
        metadataNode.put("parameter_count", action.getParameters().size());
        metadataNode.put("required_parameters", countRequiredParameters(action));
        metadataNode.put("optional_parameters", countOptionalParameters(action));
        actionNode.set("metadata", metadataNode);

        return actionNode;
    }

    /**
     * Crée un nœud JSON pour un paramètre spécifique
     */
    private ObjectNode createParameterNode(Parameter parameter) {
        ObjectNode paramNode = objectMapper.createObjectNode();

        paramNode.put("name", parameter.getName());
        paramNode.put("type", getSimpleTypeName(parameter.getType()));
        paramNode.put("required", parameter.isRequired());
        paramNode.put("description", parameter.getDescription());

        // Ajoute la valeur par défaut si elle existe
        if (parameter.getDefaultValue() != null) {
            paramNode.put("default_value", parameter.getDefaultValue().toString());
        }

        // Ajoute des contraintes de validation si disponibles -> A voir
        addValidationConstraints(paramNode, parameter);

        return paramNode;
    }


    /**
     * Génère une représentation JSON de toutes les actions disponibles
     * pour informer le LLM des capacités du système.
     *
     * @return JSON string décrivant toutes les actions et leurs paramètres
     */
    public String getActionsAsJson() {
        try {
            ArrayNode actionsArray = objectMapper.createArrayNode();

            for (Map.Entry<String, Action> entry : actions.entrySet()) {
                Action action = entry.getValue();
                ObjectNode actionNode = createActionNode(action);
                actionsArray.add(actionNode);
            }

            // Retourne le JSON formaté pour être lisible
            return objectMapper.writerWithDefaultPrettyPrinter()
                    .writeValueAsString(actionsArray);

        } catch (JsonProcessingException e) {
            throw new RuntimeException("Erreur lors de la génération du JSON des actions", e);
        }
    }


}
