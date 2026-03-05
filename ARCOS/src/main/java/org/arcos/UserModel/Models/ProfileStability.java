package org.arcos.UserModel.Models;

public enum ProfileStability {

    LOW,
    MEDIUM,
    HIGH;

    public static ProfileStability fromConversationCount(int count) {
        if (count >= 10) {
            return HIGH;
        } else if (count >= 5) {
            return MEDIUM;
        } else {
            return LOW;
        }
    }
}
