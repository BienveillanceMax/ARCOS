package org.arcos.UserModel.PersonaTree;

public record TreeOperation(
    TreeOperationType type,
    String path,
    String value
) {}
