package org.arcos.Exceptions;

public class PlannedActionExecutionException extends RuntimeException {

    public PlannedActionExecutionException(String message) {
        super(message);
    }

    public PlannedActionExecutionException(String message, Throwable cause) {
        super(message, cause);
    }
}
