package de.formcycle.baybis;

/**
 * Custom exception for BayBIS Connector errors.
 * Wraps technical errors and provides a specific error code.
 */
public class BayBisConnectorException extends RuntimeException {

    private final String errorCode;

    public BayBisConnectorException(String message, String errorCode) {
        super(message);
        this.errorCode = errorCode;
    }

    public BayBisConnectorException(String message, String errorCode, Throwable cause) {
        super(message, cause);
        this.errorCode = errorCode;
    }

    public String getErrorCode() {
        return errorCode;
    }
}
