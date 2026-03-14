package org.arcos.UserModel.PersonaTree;

public record TreeOperationResult(
    TreeOperation operation,
    boolean success,
    String errorMessage
) {}
