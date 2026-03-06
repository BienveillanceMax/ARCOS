package org.arcos.LLM.Local;

public enum ThinkingMode {
    THINK("/think\n"),
    NO_THINK("/no_think\n");

    private final String prefix;

    ThinkingMode(String prefix) {
        this.prefix = prefix;
    }

    public String getPrefix() {
        return prefix;
    }
}
