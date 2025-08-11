package Exceptions;

public class GdeltClientException extends RuntimeException
{
    public GdeltClientException(String message, Throwable cause) {
        super(message, cause);
    }

    public GdeltClientException(String message) {
        super(message);
    }

    /**
     * Exception de base pour les erreurs du service GDELT
     */
    public static class GdeltServiceException extends RuntimeException {

        private final String errorCode;
        private final String userMessage;

        public GdeltServiceException(String message) {
            super(message);
            this.errorCode = "GDELT_ERROR";
            this.userMessage = message;
        }

        public GdeltServiceException(String message, Throwable cause) {
            super(message, cause);
            this.errorCode = "GDELT_ERROR";
            this.userMessage = message;
        }

        public GdeltServiceException(String errorCode, String message, String userMessage) {
            super(message);
            this.errorCode = errorCode;
            this.userMessage = userMessage;
        }

        public GdeltServiceException(String errorCode, String message, String userMessage, Throwable cause) {
            super(message, cause);
            this.errorCode = errorCode;
            this.userMessage = userMessage;
        }

        public String getErrorCode() { return errorCode; }
        public String getUserMessage() { return userMessage; }
    }
}
