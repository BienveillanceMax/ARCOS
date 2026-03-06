package org.arcos.UserModel.Consolidation;

public class ConsolidationException extends RuntimeException {

    public ConsolidationException(String message) {
        super(message);
    }

    public ConsolidationException(String message, Throwable cause) {
        super(message, cause);
    }
}
