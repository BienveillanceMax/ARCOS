package Orchestrator.Entities;

public class Parameter
{
    private final String name;
    private final Class<?> type;
    private final boolean required;
    private final String description;
    private final Object defaultValue;

    public Parameter(String name, Class<?> type, boolean required, String description, Object defaultValue) {
        this.name = name;
        this.type = type;
        this.required = required;
        this.description = description;
        this.defaultValue = defaultValue;
    }


    public String getName() {
        return name;
    }

    public Class<?> getType() {
        return type;
    }

    public boolean isRequired() {
        return required;
    }

    public String getDescription() {
        return description;
    }

    public Object getDefaultValue() {
        return defaultValue;
    }
}

