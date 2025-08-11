package Exceptions;

public class GdeltServiceException extends RuntimeException {
    public GdeltServiceException(String message, Throwable cause) {
        super(message, cause);
    }
}
