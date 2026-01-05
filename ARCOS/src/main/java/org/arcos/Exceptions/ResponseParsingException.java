package org.arcos.Exceptions;

public class ResponseParsingException extends Exception {

    public ResponseParsingException(String message) {
        super(message);
    }

    public ResponseParsingException(String message, Throwable cause) {
        super(message, cause);
    }
}