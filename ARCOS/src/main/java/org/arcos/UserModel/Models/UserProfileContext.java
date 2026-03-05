package org.arcos.UserModel.Models;

public record UserProfileContext(
        String identitySummary,
        String communicationSummary,
        String onDemandLeafText,
        int conversationCount
) {

    public boolean isEmpty() {
        boolean noIdentity = identitySummary == null || identitySummary.isBlank();
        boolean noCommunication = communicationSummary == null || communicationSummary.isBlank();
        boolean noOnDemand = onDemandLeafText == null || onDemandLeafText.isBlank();
        return noIdentity && noCommunication && noOnDemand;
    }
}
