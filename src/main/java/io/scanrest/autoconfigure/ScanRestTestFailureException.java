package io.scanrest.autoconfigure;

/**
 * Thrown when ScanRest tests fail and {@code scanrest.fail-on-error=true}.
 */
public class ScanRestTestFailureException extends RuntimeException {

    public ScanRestTestFailureException(String message) {
        super(message);
    }

    public ScanRestTestFailureException(String message, Throwable cause) {
        super(message, cause);
    }
}
