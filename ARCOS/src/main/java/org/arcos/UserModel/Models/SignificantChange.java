package org.arcos.UserModel.Models;

public record SignificantChange(
        String signalName,
        double oldValue,
        double newValue,
        TreeBranch branch
) {
}
