package org.arcos.UserModel.Models;

public record UserProfileContext(
        String identitySummary,
        String communicationSummary,
        String onDemandLeafText,
        int conversationCount,
        String proactiveGapHint,
        boolean engagementDecayDetected,
        String greetingContext
) {

    public UserProfileContext(String identitySummary, String communicationSummary,
                              String onDemandLeafText, int conversationCount) {
        this(identitySummary, communicationSummary, onDemandLeafText, conversationCount, null, false, null);
    }

    public boolean isEmpty() {
        boolean noIdentity = identitySummary == null || identitySummary.isBlank();
        boolean noCommunication = communicationSummary == null || communicationSummary.isBlank();
        boolean noOnDemand = onDemandLeafText == null || onDemandLeafText.isBlank();
        return noIdentity && noCommunication && noOnDemand;
    }

    public boolean hasProactiveHint() {
        return proactiveGapHint != null && !proactiveGapHint.isBlank();
    }

    public boolean hasGreetingContext() {
        return greetingContext != null && !greetingContext.isBlank();
    }
}
